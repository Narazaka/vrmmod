package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.animation.BonePoseMap
import com.github.narazaka.vrmmod.animation.PoseContext
import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmModel
import com.github.narazaka.vrmmod.vrm.VrmPrimitive
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Renders a VRM model with skeletal animation using Minecraft's
 * VertexConsumer rendering pipeline.
 */
object VrmRenderer {

    /** Target height in blocks for the rendered model. */
    private const val TARGET_HEIGHT = 1.8f

    /** Fallback scale if hips position cannot be determined. */
    private const val DEFAULT_SCALE = 0.9f

    private var lastRenderTimeNano = 0L
    private var fpXzDebugCounter = 0

    /**
     * Renders the VRM model with animation driven by [poseContext].
     *
     * @param state the VRM state containing model data, textures, and pose provider
     * @param poseContext the current animation context (from player render state)
     * @param poseStack the current matrix stack
     * @param bufferSource the buffer source to obtain VertexConsumers from
     * @param packedLight the packed light value for lighting
     */
    fun render(
        state: VrmState,
        poseContext: PoseContext,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        isFirstPerson: Boolean = false,
    ) {
        val model = state.model
        val scale = estimateScale(state)
        val bodyYawRad = Math.toRadians(poseContext.bodyYaw.toDouble()).toFloat()

        // Compute bone poses from the animation provider
        val bonePoseMap = state.poseProvider.computePose(model.skeleton, poseContext)
        val nodeOverrides = convertToNodeOverrides(
            model, bonePoseMap, isAbsolute = state.poseProvider.isAbsoluteRotation
        ).toMutableMap()

        // Compute delta time for physics and expression animation
        val now = System.nanoTime()
        val deltaTime = if (lastRenderTimeNano == 0L) {
            1f / 60f // first frame fallback
        } else {
            ((now - lastRenderTimeNano) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        }
        lastRenderTimeNano = now

        // Update auto-blink and expression animations
        state.expressionController.update(deltaTime, poseContext.hurtTime)

        // SpringBone simulation
        val simulator = state.springBoneSimulator
        if (simulator != null) {
            val modelSpaceMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)

            // Inject entity world transform into worldMatrices so SpringBone
            // sees true world-space positions (matching three-vrm's matrixWorld).
            // Include entity position + bodyYaw rotation (Y-axis only).
            // Z-flip and scale are NOT included (Z-flip can't be a quaternion).
            val entityTransform = Matrix4f()
                .translate(poseContext.entityX, poseContext.entityY, poseContext.entityZ)
                .rotateY(-bodyYawRad)
            val worldMatrices = modelSpaceMatrices.map { m ->
                Matrix4f(entityTransform).mul(m)
            }

            val springRotations = simulator.update(worldMatrices, deltaTime)
            // Apply spring bone rotations as local rotation overrides
            for ((nodeIndex, rotation) in springRotations) {
                val node = model.skeleton.nodes.getOrNull(nodeIndex) ?: continue
                val matrix = Matrix4f()
                    .translate(node.translation)
                    .rotate(rotation)
                    .scale(node.scale)
                nodeOverrides[nodeIndex] = matrix
            }
        }

        // Compute skinning matrices
        val skinningMatrices = if (model.skeleton.jointNodeIndices.isNotEmpty()) {
            VrmSkinningEngine.computeSkinningMatrices(model.skeleton, nodeOverrides)
        } else {
            emptyList()
        }

        // Update eye position for VRM_VRM_CAMERA mode.
        // Compute from HEAD bone's current world matrix (with animation rotation applied)
        // but using rest-pose hips Y for height baseline (avoids walk-animation jitter).
        updateEyeOffset(state, model, nodeOverrides, scale)

        poseStack.pushPose()

        // Body rotation in MC space
        poseStack.mulPose(org.joml.Quaternionf().rotateY(-bodyYawRad))

        // VRM model faces +Z. After Z-flip it faces -Z (north in MC).
        // MC entities face south (+Z) at yaw=0, so rotate 180 degrees,
        // then Z-flip to convert coordinate system.
        poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
        poseStack.scale(1f, 1f, -1f)

        // Scale model to approximately player height (~1.8 blocks).
        poseStack.scale(scale, scale, scale)

        val pose = poseStack.last()

        // Compute morph target weights from expressions
        val expressionController = state.expressionController
        val morphWeightsMap = expressionController.computeMorphWeights(model.expressions)

        // Group primitives by (texture, alphaMode) to avoid buffer interleaving
        // and use appropriate RenderType per alpha mode.
        data class IndexedPrimitive(val meshIndex: Int, val primitive: com.github.narazaka.vrmmod.vrm.VrmPrimitive)
        val allPrimitives = model.meshes.flatMapIndexed { meshIndex, mesh ->
            mesh.primitives.map { IndexedPrimitive(meshIndex, it) }
        }.filter { (meshIndex, _) ->
            if (!isFirstPerson) {
                val annotation = model.firstPersonAnnotations[meshIndex]
                annotation != com.github.narazaka.vrmmod.vrm.FirstPersonType.FIRST_PERSON_ONLY
            } else {
                val annotation = model.firstPersonAnnotations[meshIndex]
                when (annotation) {
                    com.github.narazaka.vrmmod.vrm.FirstPersonType.BOTH -> true
                    com.github.narazaka.vrmmod.vrm.FirstPersonType.FIRST_PERSON_ONLY -> true
                    com.github.narazaka.vrmmod.vrm.FirstPersonType.THIRD_PERSON_ONLY -> false
                    // AUTO and null: keep mesh, but skip head triangles in drawPrimitive
                    com.github.narazaka.vrmmod.vrm.FirstPersonType.AUTO -> true
                    null -> true
                }
            }
        }
        data class RenderKey(val texture: ResourceLocation, val alphaMode: com.github.narazaka.vrmmod.vrm.AlphaMode)
        val grouped = allPrimitives.groupBy {
            RenderKey(resolveTexture(state, it.primitive.imageIndex), it.primitive.alphaMode)
        }

        // Draw opaque/mask first, then translucent
        val sortedGroups = grouped.entries.sortedBy { if (it.key.alphaMode == com.github.narazaka.vrmmod.vrm.AlphaMode.BLEND) 1 else 0 }

        for ((key, indexedPrimitives) in sortedGroups) {
            val renderType = when (key.alphaMode) {
                com.github.narazaka.vrmmod.vrm.AlphaMode.BLEND -> RenderType.entityTranslucent(key.texture)
                else -> RenderType.entityCutoutNoCull(key.texture)
            }
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val isQuadMode = renderType.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
            for ((meshIndex, primitive) in indexedPrimitives) {
                // Collect morph weights relevant to this primitive's mesh
                val primitiveMorphWeights = mutableMapOf<Int, Float>()
                for ((key2, weight) in morphWeightsMap) {
                    if (key2.first == meshIndex) {
                        primitiveMorphWeights[key2.second] = weight
                    }
                }
                // For first-person auto mode, pass head joint indices so drawPrimitive can skip head triangles
                val headJoints = if (isFirstPerson) {
                    val annotation = model.firstPersonAnnotations[meshIndex]
                    if (annotation == null || annotation == com.github.narazaka.vrmmod.vrm.FirstPersonType.AUTO) {
                        collectHeadJointIndices(model)
                    } else emptySet()
                } else emptySet()

                drawPrimitive(primitive, vertexConsumer, pose, packedLight, skinningMatrices, isQuadMode, primitiveMorphWeights, headJoints)
            }
        }

        poseStack.popPose()
    }

    /**
     * Converts a [BonePoseMap] into per-node-index local transform overrides.
     *
     * For each animated bone, the override matrix combines the node's rest
     * TRS with the pose's delta TRS.
     */
    /**
     * @param isAbsolute if true, pose rotations replace the rest rotation entirely
     *   (used for vrma animation). If false, pose rotations are applied as deltas
     *   in parent space before rest rotation (used for VanillaPoseProvider).
     */
    private fun convertToNodeOverrides(
        model: VrmModel,
        bonePoseMap: BonePoseMap,
        isAbsolute: Boolean = false,
    ): Map<Int, Matrix4f> {
        val overrides = mutableMapOf<Int, Matrix4f>()
        for ((bone, pose) in bonePoseMap) {
            val boneNode = model.humanoid.humanBones[bone] ?: continue
            val nodeIndex = boneNode.nodeIndex
            val node = model.skeleton.nodes.getOrNull(nodeIndex) ?: continue
            val matrix = if (isAbsolute) {
                // vrma: normalized rotation pre-multiplied by model rest rotation
                // finalLocalRot = restRot * normalizedRot
                // For hips translation: pose.translation replaces node.translation
                // For other bones: use node.translation (no translation from animation)
                val translation = if (pose.translation.x != 0f || pose.translation.y != 0f || pose.translation.z != 0f) {
                    pose.translation  // hips: use animation translation directly
                } else {
                    node.translation  // others: keep rest position
                }
                Matrix4f()
                    .translate(translation)
                    .rotate(Quaternionf(node.rotation).mul(pose.rotation))
                    .scale(node.scale)
            } else {
                // VanillaPoseProvider: delta rotation applied in parent space before rest
                Matrix4f()
                    .translate(node.translation)
                    .translate(pose.translation)
                    .rotate(pose.rotation)
                    .rotate(node.rotation)
                    .scale(node.scale)
                    .scale(pose.scale)
            }
            overrides[nodeIndex] = matrix
        }
        return overrides
    }

    /**
     * Estimates a uniform scale factor so the model is approximately
     * [TARGET_HEIGHT] blocks tall, based on the hips bone Y position.
     */
    /** Cache for head mesh detection results. */
    private val headMeshCache = mutableMapOf<Int, Boolean>()

    /**
     * Checks if a mesh is primarily associated with the head.
     *
     * Uses a majority-vote approach: if more than half the weighted vertices
     * in the mesh are influenced by HEAD or its descendant joints, the mesh
     * is considered a head mesh.
     *
     * Also uses mesh name heuristics as a fallback.
     */
    private fun isHeadMesh(model: VrmModel, meshIndex: Int): Boolean {
        headMeshCache[meshIndex]?.let { return it }

        val mesh = model.meshes.getOrNull(meshIndex)
        if (mesh == null) {
            headMeshCache[meshIndex] = false
            return false
        }

        // Name-based heuristic: "Face" mesh is head
        val nameLower = mesh.name.lowercase()
        if (nameLower.contains("face")) {
            headMeshCache[meshIndex] = true
            return true
        }

        val headBoneNode = model.humanoid.humanBones[com.github.narazaka.vrmmod.vrm.HumanBone.HEAD]
        if (headBoneNode == null) {
            headMeshCache[meshIndex] = false
            return false
        }

        // Collect all joint indices that are HEAD or descendants of HEAD
        val headJointIndices = collectHeadJointIndices(model)

        // Majority vote: count vertices primarily weighted to head joints
        var headVertices = 0
        var totalVertices = 0
        for (prim in mesh.primitives) {
            if (prim.joints.isEmpty()) continue
            for (v in 0 until prim.vertexCount) {
                totalVertices++
                // Find the joint with the highest weight for this vertex
                var maxWeight = 0f
                var maxJoint = -1
                for (i in 0 until 4) {
                    val idx = v * 4 + i
                    if (idx >= prim.joints.size) break
                    val w = if (idx < prim.weights.size) prim.weights[idx] else 0f
                    if (w > maxWeight) {
                        maxWeight = w
                        maxJoint = prim.joints[idx]
                    }
                }
                if (maxJoint in headJointIndices) headVertices++
            }
        }

        // If majority of vertices are head-weighted, it's a head mesh
        val isHead = totalVertices > 0 && headVertices.toFloat() / totalVertices > 0.5f
        headMeshCache[meshIndex] = isHead
        return isHead
    }

    private var headJointIndicesCache: Set<Int>? = null

    private fun collectHeadJointIndices(model: VrmModel): Set<Int> {
        headJointIndicesCache?.let { return it }
        val headBoneNode = model.humanoid.humanBones[com.github.narazaka.vrmmod.vrm.HumanBone.HEAD]
            ?: return emptySet()
        val skeleton = model.skeleton
        val result = mutableSetOf<Int>()
        for ((jointIdx, nodeIdx) in skeleton.jointNodeIndices.withIndex()) {
            if (isDescendantOfNode(skeleton, nodeIdx, headBoneNode.nodeIndex)) {
                result.add(jointIdx)
            }
        }
        headJointIndicesCache = result
        return result
    }

    private fun isDescendantOfNode(skeleton: com.github.narazaka.vrmmod.vrm.VrmSkeleton, nodeIndex: Int, ancestorIndex: Int): Boolean {
        if (nodeIndex == ancestorIndex) return true
        for ((idx, node) in skeleton.nodes.withIndex()) {
            if (nodeIndex in node.childIndices) {
                return isDescendantOfNode(skeleton, idx, ancestorIndex)
            }
        }
        return false
    }

    /**
     * Updates [VrmState.currentEyeOffset] from the current HEAD bone position.
     *
     * Per three-vrm's VRMLookAt.getLookAtWorldPosition():
     *   eyePos = headWorldMatrix * lookAt.offsetFromHeadBone
     *
     * The Y component uses the animated HEAD position (captures look direction offset),
     * but the base height stays close to rest-pose eye height via the scale factor.
     * XZ offset from HEAD rotation is included so the camera follows head turns
     * and the neck interior stays hidden.
     */
    private fun updateEyeOffset(
        state: VrmState,
        model: VrmModel,
        nodeOverrides: Map<Int, Matrix4f>,
        scale: Float,
    ) {
        val headBoneNode = model.humanoid.humanBones[com.github.narazaka.vrmmod.vrm.HumanBone.HEAD] ?: return

        // Rest-pose eye position (for baseline XZ)
        val restWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
        val restHeadMatrix = restWorldMatrices[headBoneNode.nodeIndex]
        val offset = model.lookAtOffsetFromHeadBone
        val restEyePos = Vector3f(offset)
        restHeadMatrix.transformPosition(restEyePos)

        // Animated eye position (includes body lean, head rotation)
        val animWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)
        val animHeadMatrix = animWorldMatrices[headBoneNode.nodeIndex]
        val animEyePos = Vector3f(offset)
        animHeadMatrix.transformPosition(animEyePos)

        // XZ: delta from rest pose (only animation-driven offset)
        // Y: unchanged (animated absolute position as before)
        val xzDeltaX = (animEyePos.x - restEyePos.x) * scale
        val xzDeltaZ = (animEyePos.z - restEyePos.z) * scale

        // Debug log once per second
        fpXzDebugCounter++
        if (fpXzDebugCounter % 60 == 0 && (kotlin.math.abs(xzDeltaX) > 0.001f || kotlin.math.abs(xzDeltaZ) > 0.001f)) {
            com.github.narazaka.vrmmod.VrmMod.logger.info(
                "[VRM FP XZ] restEye=({},{},{}) animEye=({},{},{}) deltaXZ=({},{})",
                restEyePos.x, restEyePos.y, restEyePos.z,
                animEyePos.x, animEyePos.y, animEyePos.z,
                xzDeltaX, xzDeltaZ,
            )
        }

        state.currentEyeOffset = Vector3f(
            xzDeltaX,
            animEyePos.y * scale,
            xzDeltaZ,
        )
    }

    private fun estimateScale(state: VrmState): Float {
        val model = state.model
        val hipsNode = model.humanoid.humanBones[HumanBone.HIPS]
        if (hipsNode != null) {
            val nodeIndex = hipsNode.nodeIndex
            val nodes = model.skeleton.nodes
            if (nodeIndex in nodes.indices) {
                val hipsY = nodes[nodeIndex].translation.y
                if (hipsY > 0f) {
                    return TARGET_HEIGHT / (hipsY * 2f)
                }
            }
        }
        return DEFAULT_SCALE
    }

    /**
     * Resolves the texture ResourceLocation for a given image index.
     */
    private fun resolveTexture(state: VrmState, imageIndex: Int): ResourceLocation {
        val locations = state.textureLocations
        if (locations.isEmpty()) {
            return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png")
        }
        return if (imageIndex in locations.indices) {
            locations[imageIndex]
        } else {
            locations[0]
        }
    }

    /**
     * Draws a single primitive's indexed triangles through the VertexConsumer.
     *
     * When [isQuadMode] is true (Minecraft entity RenderTypes use QUADS), each
     * triangle (v0, v1, v2) is emitted as a degenerate quad (v0, v1, v2, v2)
     * so that the vertex data aligns with the 4-vertex-per-primitive expectation.
     *
     * When [skinningMatrices] is non-empty and the primitive has joint/weight data,
     * each vertex is transformed by the skinning engine before being emitted.
     */
    private fun drawPrimitive(
        primitive: VrmPrimitive,
        vertexConsumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        packedLight: Int,
        skinningMatrices: List<Matrix4f>,
        isQuadMode: Boolean,
        morphWeights: Map<Int, Float> = emptyMap(),
        skipHeadJoints: Set<Int> = emptySet(),
    ) {
        val positions = primitive.positions
        val normals = primitive.normals
        val texCoords = primitive.texCoords
        val indices = primitive.indices
        val hasNormals = normals.size >= positions.size
        val hasUVs = texCoords.size >= (primitive.vertexCount * 2)

        val hasSkinning = skinningMatrices.isNotEmpty() &&
            primitive.joints.isNotEmpty() &&
            primitive.weights.isNotEmpty()

        val shouldSkipHeadTris = skipHeadJoints.isNotEmpty() && hasSkinning

        // Reusable arrays for per-vertex joint/weight data
        val vertJoints = if (hasSkinning) IntArray(4) else null
        val vertWeights = if (hasSkinning) FloatArray(4) else null

        // Process triangles: indices are triplets (i0, i1, i2)
        val triCount = indices.size / 3
        for (tri in 0 until triCount) {
            val baseIdx = tri * 3

            // Skip triangles where all 3 vertices are primarily weighted to head joints
            if (shouldSkipHeadTris) {
                var headVertCount = 0
                for (vi in 0 until 3) {
                    val idx = indices[baseIdx + vi]
                    // Find dominant joint for this vertex
                    var maxW = 0f
                    var maxJ = -1
                    for (i in 0 until 4) {
                        val di = idx * 4 + i
                        if (di >= primitive.joints.size) break
                        val w = if (di < primitive.weights.size) primitive.weights[di] else 0f
                        if (w > maxW) { maxW = w; maxJ = primitive.joints[di] }
                    }
                    if (maxJ in skipHeadJoints) headVertCount++
                }
                if (headVertCount >= 3) continue // skip this triangle
            }
            // Emit 3 vertices of the triangle, plus a 4th (duplicate of v2) if QUADS mode
            val verticesInPrimitive = if (isQuadMode) 4 else 3
            for (v in 0 until verticesInPrimitive) {
                // For the 4th vertex in quad mode, repeat the 3rd vertex (index 2)
                val indexSlot = if (v < 3) v else 2
                val index = indices[baseIdx + indexSlot]

                var px = positions[index * 3]
                var py = positions[index * 3 + 1]
                var pz = positions[index * 3 + 2]

                var nx: Float
                var ny: Float
                var nz: Float
                if (hasNormals) {
                    nx = normals[index * 3]
                    ny = normals[index * 3 + 1]
                    nz = normals[index * 3 + 2]
                } else {
                    nx = 0f
                    ny = 1f
                    nz = 0f
                }

                // Apply morph target deltas before skinning
                if (morphWeights.isNotEmpty()) {
                    for ((morphIdx, weight) in morphWeights) {
                        val morph = primitive.morphTargets.getOrNull(morphIdx) ?: continue
                        if (morph.positionDeltas.size > index * 3 + 2) {
                            px += morph.positionDeltas[index * 3] * weight
                            py += morph.positionDeltas[index * 3 + 1] * weight
                            pz += morph.positionDeltas[index * 3 + 2] * weight
                        }
                        if (hasNormals && morph.normalDeltas.size > index * 3 + 2) {
                            nx += morph.normalDeltas[index * 3] * weight
                            ny += morph.normalDeltas[index * 3 + 1] * weight
                            nz += morph.normalDeltas[index * 3 + 2] * weight
                        }
                    }
                }

                if (hasSkinning) {
                    for (i in 0 until 4) {
                        val dataIdx = index * 4 + i
                        vertJoints!![i] = if (dataIdx < primitive.joints.size) primitive.joints[dataIdx] else 0
                        vertWeights!![i] = if (dataIdx < primitive.weights.size) primitive.weights[dataIdx] else 0f
                    }

                    val skinnedPos = VrmSkinningEngine.skinVertex(
                        Vector3f(px, py, pz),
                        vertJoints!!,
                        vertWeights!!,
                        skinningMatrices,
                    )
                    px = skinnedPos.x
                    py = skinnedPos.y
                    pz = skinnedPos.z

                    if (hasNormals) {
                        val skinnedNormal = VrmSkinningEngine.skinNormal(
                            Vector3f(nx, ny, nz),
                            vertJoints,
                            vertWeights,
                            skinningMatrices,
                        )
                        nx = skinnedNormal.x
                        ny = skinnedNormal.y
                        nz = skinnedNormal.z
                    }
                }

                val u: Float
                val vCoord: Float
                if (hasUVs) {
                    u = texCoords[index * 2]
                    vCoord = texCoords[index * 2 + 1]
                } else {
                    u = 0f
                    vCoord = 0f
                }

                // Unlit-style: uniform white vertex color.
                // MC's entity lighting (packedLight) still applies ambient/block light,
                // but no per-vertex normal-based shading from our side.
                val r = 255
                val g = 255
                val b = 255

                vertexConsumer
                    .addVertex(pose, px, py, pz)
                    .setColor(r, g, b, 255)
                    .setUv(u, vCoord)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    // TODO: When Iris MToon shader is implemented, use actual normals (nx, ny, nz) instead
                    .setNormal(pose, 0f, 1f, 0f) // uniform upward normal for unlit look
            }
        }
    }
}
