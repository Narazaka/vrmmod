package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.BonePoseMap
import net.narazaka.vrmmod.animation.PoseContext
import net.narazaka.vrmmod.vrm.HumanBone
import net.narazaka.vrmmod.vrm.VrmModel
import net.narazaka.vrmmod.vrm.VrmPrimitive
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

    private data class IndexedPrimitive(val meshIndex: Int, val skinIndex: Int, val primitive: VrmPrimitive)
    private data class RenderKey(val texture: ResourceLocation, val alphaMode: net.narazaka.vrmmod.vrm.AlphaMode)

    /** Target height in blocks for the rendered model. */
    private const val TARGET_HEIGHT = 1.8f

    /** Fallback scale if hips position cannot be determined. */
    private const val DEFAULT_SCALE = 0.9f

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
        val rawDelta = if (state.lastRenderTimeNano == 0L) {
            1f / 60f
        } else {
            (now - state.lastRenderTimeNano) / 1_000_000_000f
        }
        state.lastRenderTimeNano = now
        val deltaTime = if (rawDelta > 0.1f) 0f else rawDelta.coerceAtLeast(0.001f)

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

        // Compute animated world matrices once for all subsequent uses
        val animatedWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)

        // Compute skinning matrices per skin (cached to avoid recomputation)
        val skinningMatricesCache = mutableMapOf<Int, List<Matrix4f>>()
        fun getSkinningMatrices(skinIndex: Int): List<Matrix4f> {
            return skinningMatricesCache.getOrPut(skinIndex) {
                VrmSkinningEngine.computeSkinningMatrices(model.skeleton, animatedWorldMatrices, skinIndex)
            }
        }
        // Pre-compute primary skin for eye offset etc.
        val skinningMatrices = getSkinningMatrices(0)

        // Update eye position for VRM_VRM_CAMERA mode.
        // Compute from HEAD bone's current world matrix (with animation rotation applied)
        // but using rest-pose hips Y for height baseline (avoids walk-animation jitter).
        updateEyeOffset(state, model, animatedWorldMatrices, scale)

        poseStack.pushPose()

        applyModelTransform(poseStack, bodyYawRad, scale)

        val pose = poseStack.last()

        // Compute morph target weights from expressions
        val expressionController = state.expressionController
        val morphWeightsMap = expressionController.computeMorphWeights(model.expressions)

        // Group primitives by (texture, alphaMode) to avoid buffer interleaving
        // and use appropriate RenderType per alpha mode.
        // Build mesh index -> node index map for unskinned mesh transform
        val meshToNodeIndex = mutableMapOf<Int, Int>()
        for ((nodeIdx, node) in model.skeleton.nodes.withIndex()) {
            if (node.meshIndex >= 0) meshToNodeIndex[node.meshIndex] = nodeIdx
        }

        // Save hand bone world matrices for held item rendering
        val rightHandBone = model.humanoid.humanBones[HumanBone.RIGHT_HAND]
        state.rightHandMatrix = if (rightHandBone != null) Matrix4f(animatedWorldMatrices[rightHandBone.nodeIndex]) else null
        val leftHandBone = model.humanoid.humanBones[HumanBone.LEFT_HAND]
        state.leftHandMatrix = if (leftHandBone != null) Matrix4f(animatedWorldMatrices[leftHandBone.nodeIndex]) else null

        val allPrimitives = model.meshes.flatMapIndexed { meshIndex, mesh ->
            mesh.primitives.map { IndexedPrimitive(meshIndex, mesh.skinIndex, it) }
        }.filter { (meshIndex, skinIndex, primitive) ->
            if (!isFirstPerson) {
                val annotation = model.firstPersonAnnotations[meshIndex]
                annotation != net.narazaka.vrmmod.vrm.FirstPersonType.FIRST_PERSON_ONLY
            } else {
                val annotation = model.firstPersonAnnotations[meshIndex]
                when (annotation) {
                    net.narazaka.vrmmod.vrm.FirstPersonType.BOTH -> true
                    net.narazaka.vrmmod.vrm.FirstPersonType.FIRST_PERSON_ONLY -> true
                    net.narazaka.vrmmod.vrm.FirstPersonType.THIRD_PERSON_ONLY -> false
                    // AUTO and null: skinned meshes → skip head triangles in drawPrimitive
                    // Unskinned meshes parented to HEAD descendants → hide entirely
                    net.narazaka.vrmmod.vrm.FirstPersonType.AUTO, null -> {
                        if (primitive.joints.isEmpty()) {
                            // Unskinned: check if mesh node is a HEAD descendant
                            val nodeIdx = meshToNodeIndex[meshIndex]
                            nodeIdx == null || !isHeadDescendantNode(model, nodeIdx)
                        } else {
                            true // Skinned: handled by triangle skipping in drawPrimitive
                        }
                    }
                }
            }
        }
        val grouped = allPrimitives.groupBy {
            RenderKey(resolveTexture(state, it.primitive.imageIndex), it.primitive.alphaMode)
        }

        // Draw opaque/mask first, then translucent
        val sortedGroups = grouped.entries.sortedBy { if (it.key.alphaMode == net.narazaka.vrmmod.vrm.AlphaMode.BLEND) 1 else 0 }

        for ((key, indexedPrimitives) in sortedGroups) {
            val renderType = when (key.alphaMode) {
                net.narazaka.vrmmod.vrm.AlphaMode.BLEND -> RenderType.entityTranslucent(key.texture)
                else -> RenderType.entityCutoutNoCull(key.texture)
            }
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val isQuadMode = renderType.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
            for ((meshIndex, meshSkinIndex, primitive) in indexedPrimitives) {
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
                    if (annotation == null || annotation == net.narazaka.vrmmod.vrm.FirstPersonType.AUTO) {
                        collectHeadJointIndices(model, meshSkinIndex.coerceAtLeast(0))
                    } else emptySet()
                } else emptySet()

                // Use the correct skin's skinning matrices for this mesh
                val meshSkinningMatrices = if (meshSkinIndex >= 0) getSkinningMatrices(meshSkinIndex) else skinningMatrices

                // For unskinned meshes parented to a bone node, apply the node's world matrix.
                // This is equivalent to three.js's automatic matrixWorld propagation.
                val nodeWorldMatrix = if (primitive.joints.isEmpty()) {
                    meshToNodeIndex[meshIndex]?.let { animatedWorldMatrices.getOrNull(it) }
                } else null

                drawPrimitive(primitive, vertexConsumer, pose, packedLight, meshSkinningMatrices, isQuadMode, primitiveMorphWeights, headJoints, nodeWorldMatrix)
            }
        }

        poseStack.popPose()
    }

    /**
     * Renders held items at VRM hand bone positions.
     */
    fun renderHeldItems(
        state: VrmState,
        rightHandItem: net.minecraft.client.renderer.item.ItemStackRenderState,
        leftHandItem: net.minecraft.client.renderer.item.ItemStackRenderState,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        bodyYawRad: Float,
        config: net.narazaka.vrmmod.animation.AnimationConfig? = null,
    ) {
        val itemScale = config?.heldItemScale ?: 0.67f
        val offset = config?.heldItemOffset ?: listOf(0f, 0.0625f, -0.125f)
        val ox = offset.getOrElse(0) { 0f }
        val oy = offset.getOrElse(1) { 0.0625f }
        val oz = offset.getOrElse(2) { -0.125f }
        val scale = estimateScale(state)
        renderSingleHandItem(state.rightHandMatrix, rightHandItem, false, poseStack, bufferSource, packedLight, bodyYawRad, scale, itemScale, ox, oy, oz)
        renderSingleHandItem(state.leftHandMatrix, leftHandItem, true, poseStack, bufferSource, packedLight, bodyYawRad, scale, itemScale, ox, oy, oz)
    }

    private fun renderSingleHandItem(
        handMatrix: Matrix4f?,
        itemRenderState: net.minecraft.client.renderer.item.ItemStackRenderState,
        isLeft: Boolean,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        bodyYawRad: Float,
        scale: Float,
        itemScale: Float,
        offsetX: Float,
        offsetY: Float,
        offsetZ: Float,
    ) {
        if (handMatrix == null || itemRenderState.isEmpty) return

        poseStack.pushPose()
        applyModelTransform(poseStack, bodyYawRad, scale)

        // Apply hand bone world matrix
        poseStack.mulPose(handMatrix)

        // Item scale (configurable)
        poseStack.scale(itemScale, itemScale, itemScale)

        // Item orientation adjustments for VRM hand bone coordinate system
        // VRM hand bone: fingers along ±X, palm faces -Y in T-pose
        // MC items: handle at origin, blade/tip extends +Y
        // 1. Z rotation: align blade (+Y) with finger direction (±X)
        poseStack.mulPose(org.joml.Quaternionf().rotateZ((if (isLeft) Math.PI / 2 else -Math.PI / 2).toFloat()))
        // 2. Y rotation: flip blade face to correct side
        poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
        // 3. X rotation: tilt blade forward 90° (natural hold angle)
        poseStack.mulPose(org.joml.Quaternionf().rotateX((-Math.PI / 2).toFloat()))
        // 4. Offset: shift grip point to hand bone position (configurable)
        poseStack.translate(offsetX, offsetY, offsetZ)

        itemRenderState.render(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY)
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
                // vrma: normalized rotation converted to raw bone local space.
                // Follows three-vrm's VRMHumanoidRig.update() transfer formula:
                //   rawLocalRot = invParentWorldRot * normalizedRot * parentWorldRot * restLocalRot
                val info = model.normalizedBoneInfo[bone]
                val localRot = if (info != null) {
                    val invParent = Quaternionf(info.parentWorldRotation).invert()
                    Quaternionf(invParent)
                        .mul(pose.rotation)
                        .mul(info.parentWorldRotation)
                        .mul(info.boneRotation)
                } else {
                    // Fallback: no info available, use simple multiplication
                    Quaternionf(node.rotation).mul(pose.rotation)
                }

                // For hips translation: convert from normalized world space to
                // raw bone's parent local space (three-vrm: parent.matrixWorld.inverse())
                val translation = if (pose.translation.x != 0f || pose.translation.y != 0f || pose.translation.z != 0f) {
                    if (info != null && info.parentNodeIndex >= 0) {
                        val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
                        val parentWorldMatrix = worldMatrices[info.parentNodeIndex]
                        val localPos = Vector3f(pose.translation)
                        Matrix4f(parentWorldMatrix).invert().transformPosition(localPos)
                        localPos
                    } else {
                        pose.translation
                    }
                } else {
                    node.translation
                }

                Matrix4f()
                    .translate(translation)
                    .rotate(localRot)
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
    private var headJointCacheModelId: Int = 0
    private val headJointIndicesCache = mutableMapOf<Int, Set<Int>>()

    /**
     * Collects joint indices that are HEAD descendants for a specific skin.
     * Joint indices are per-skin (each skin has its own jointNodeIndices array),
     * so HEAD joint detection must be done per-skin.
     * Cache is invalidated when the model changes (identified by identity hash).
     */
    private fun collectHeadJointIndices(model: VrmModel, skinIndex: Int): Set<Int> {
        val modelId = System.identityHashCode(model)
        if (modelId != headJointCacheModelId) {
            headJointIndicesCache.clear()
            headJointCacheModelId = modelId
        }
        headJointIndicesCache[skinIndex]?.let { return it }
        val headBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD]
            ?: return emptySet()
        val skin = model.skeleton.skins.getOrNull(skinIndex) ?: return emptySet()
        val result = mutableSetOf<Int>()
        for ((jointIdx, nodeIdx) in skin.jointNodeIndices.withIndex()) {
            if (isDescendantOfNode(model.skeleton, nodeIdx, headBoneNode.nodeIndex)) {
                result.add(jointIdx)
            }
        }
        headJointIndicesCache[skinIndex] = result
        return result
    }

    private fun isDescendantOfNode(skeleton: net.narazaka.vrmmod.vrm.VrmSkeleton, nodeIndex: Int, ancestorIndex: Int): Boolean {
        if (nodeIndex == ancestorIndex) return true
        for ((idx, node) in skeleton.nodes.withIndex()) {
            if (nodeIndex in node.childIndices) {
                return isDescendantOfNode(skeleton, idx, ancestorIndex)
            }
        }
        return false
    }

    /**
     * Checks if a node is the HEAD bone or a descendant of HEAD in the node tree.
     * Used for unskinned meshes in first-person mode (three-vrm's _isEraseTarget).
     */
    private fun isHeadDescendantNode(model: VrmModel, nodeIndex: Int): Boolean {
        val headBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD]
            ?: return false
        return isDescendantOfNode(model.skeleton, nodeIndex, headBoneNode.nodeIndex)
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
        animatedWorldMatrices: List<Matrix4f>,
        scale: Float,
    ) {
        val headBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD] ?: return

        // Rest-pose eye position (for baseline XZ)
        val restWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
        val restHeadMatrix = restWorldMatrices[headBoneNode.nodeIndex]
        val offset = model.lookAtOffsetFromHeadBone
        val restEyePos = Vector3f(offset)
        restHeadMatrix.transformPosition(restEyePos)

        // Animated eye position (includes body lean, head rotation)
        val animHeadMatrix = animatedWorldMatrices[headBoneNode.nodeIndex]
        val animEyePos = Vector3f(offset)
        animHeadMatrix.transformPosition(animEyePos)

        // XZ: delta from rest pose
        // During one-shot actions (attack, etc.), freeze XZ to avoid camera jitter
        // Y: animated absolute position (Camera.tick() applies its own lerp on eyeHeight)
        val prev = state.currentEyeOffset
        val isOneShot = (state.poseProvider as? net.narazaka.vrmmod.animation.AnimationPoseProvider)?.isPlayingOneShot ?: false
        val newX = if (isOneShot) prev.x else (animEyePos.x - restEyePos.x) * scale
        val newZ = if (isOneShot) prev.z else (animEyePos.z - restEyePos.z) * scale
        state.currentEyeOffset = Vector3f(newX, animEyePos.y * scale, newZ)
    }

    private fun applyModelTransform(poseStack: PoseStack, bodyYawRad: Float, scale: Float) {
        poseStack.mulPose(org.joml.Quaternionf().rotateY(-bodyYawRad))
        poseStack.scale(scale, scale, scale)
    }

    private fun estimateScale(state: VrmState): Float {
        val model = state.model
        val hipsNode = model.humanoid.humanBones[HumanBone.HIPS] ?: return DEFAULT_SCALE
        val nodeIndex = hipsNode.nodeIndex
        if (nodeIndex !in model.skeleton.nodes.indices) return DEFAULT_SCALE

        val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
        val hipsWorldPos = Vector3f()
        worldMatrices[nodeIndex].getTranslation(hipsWorldPos)
        val hipsY = hipsWorldPos.y
        return if (hipsY > 0f) TARGET_HEIGHT / (hipsY * 2f) else DEFAULT_SCALE
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
        nodeWorldMatrix: Matrix4f? = null,
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
        val reusablePos = Vector3f()
        val reusableNorm = Vector3f()

        // Process triangles: indices are triplets (i0, i1, i2)
        val triCount = indices.size / 3
        for (tri in 0 until triCount) {
            val baseIdx = tri * 3

            // Skip triangles where ANY vertex has ANY weight > 0 on a head joint.
            // Follows three-vrm's _excludeTriangles(): if any of the 3 vertices has
            // any bone weight referencing a HEAD descendant joint, the entire triangle
            // is excluded. This is intentionally aggressive to prevent head geometry
            // from being visible in first-person view.
            if (shouldSkipHeadTris) {
                var shouldSkip = false
                for (vi in 0 until 3) {
                    if (shouldSkip) break
                    val idx = indices[baseIdx + vi]
                    for (i in 0 until 4) {
                        val di = idx * 4 + i
                        if (di >= primitive.joints.size) break
                        val w = if (di < primitive.weights.size) primitive.weights[di] else 0f
                        if (w > 0f && primitive.joints[di] in skipHeadJoints) {
                            shouldSkip = true
                            break
                        }
                    }
                }
                if (shouldSkip) continue
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

                    reusablePos.set(px, py, pz)
                    val skinnedPos = VrmSkinningEngine.skinVertex(
                        reusablePos,
                        vertJoints!!,
                        vertWeights!!,
                        skinningMatrices,
                    )
                    px = skinnedPos.x
                    py = skinnedPos.y
                    pz = skinnedPos.z

                    if (hasNormals) {
                        reusableNorm.set(nx, ny, nz)
                        val skinnedNormal = VrmSkinningEngine.skinNormal(
                            reusableNorm,
                            vertJoints,
                            vertWeights,
                            skinningMatrices,
                        )
                        nx = skinnedNormal.x
                        ny = skinnedNormal.y
                        nz = skinnedNormal.z
                    }
                } else if (nodeWorldMatrix != null) {
                    // Unskinned mesh: apply parent node's world matrix
                    // (three.js does this automatically via Object3D.matrixWorld propagation)
                    reusablePos.set(px, py, pz)
                    nodeWorldMatrix.transformPosition(reusablePos)
                    px = reusablePos.x; py = reusablePos.y; pz = reusablePos.z

                    if (hasNormals) {
                        reusableNorm.set(nx, ny, nz)
                        nodeWorldMatrix.transformDirection(reusableNorm)
                        val len = reusableNorm.length()
                        if (len > 1e-6f) reusableNorm.div(len)
                        nx = reusableNorm.x; ny = reusableNorm.y; nz = reusableNorm.z
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
