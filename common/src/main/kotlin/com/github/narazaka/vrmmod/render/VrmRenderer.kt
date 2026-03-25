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

    /** Fixed delta time per render frame (~60fps). */
    private const val DELTA_TIME = 1f / 60f

    private var springBoneDebugLogged = false
    private var springBoneFrameCount = 0

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
    ) {
        val model = state.model
        val scale = estimateScale(state)
        val bodyYawRad = Math.toRadians(poseContext.bodyYaw.toDouble()).toFloat()

        // Compute bone poses from the animation provider
        val bonePoseMap = state.poseProvider.computePose(model.skeleton, poseContext)
        val nodeOverrides = convertToNodeOverrides(model, bonePoseMap).toMutableMap()

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

            val springRotations = simulator.update(worldMatrices, DELTA_TIME)
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

        // Group primitives by texture to avoid buffer interleaving
        val allPrimitives = model.meshes.flatMap { it.primitives }
        val grouped = allPrimitives.groupBy { resolveTexture(state, it.imageIndex) }

        for ((texture, primitives) in grouped) {
            val renderType = RenderType.entityCutoutNoCull(texture)
            val vertexConsumer = bufferSource.getBuffer(renderType)
            val isQuadMode = renderType.mode() == com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS
            for (primitive in primitives) {
                drawPrimitive(primitive, vertexConsumer, pose, packedLight, skinningMatrices, isQuadMode)
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
    private fun convertToNodeOverrides(model: VrmModel, bonePoseMap: BonePoseMap): Map<Int, Matrix4f> {
        val overrides = mutableMapOf<Int, Matrix4f>()
        for ((bone, pose) in bonePoseMap) {
            val boneNode = model.humanoid.humanBones[bone] ?: continue
            val nodeIndex = boneNode.nodeIndex
            val node = model.skeleton.nodes.getOrNull(nodeIndex) ?: continue
            // Apply pose rotation in parent space (before rest rotation):
            // finalLocal = T_rest * T_pose * R_pose * R_rest * S_rest * S_pose
            val matrix = Matrix4f()
                .translate(node.translation)
                .translate(pose.translation)
                .rotate(pose.rotation)
                .rotate(node.rotation)
                .scale(node.scale)
                .scale(pose.scale)
            overrides[nodeIndex] = matrix
        }
        return overrides
    }

    /**
     * Estimates a uniform scale factor so the model is approximately
     * [TARGET_HEIGHT] blocks tall, based on the hips bone Y position.
     */
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

        // Reusable arrays for per-vertex joint/weight data
        val vertJoints = if (hasSkinning) IntArray(4) else null
        val vertWeights = if (hasSkinning) FloatArray(4) else null

        // Process triangles: indices are triplets (i0, i1, i2)
        val triCount = indices.size / 3
        for (tri in 0 until triCount) {
            val baseIdx = tri * 3
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

                vertexConsumer
                    .addVertex(pose, px, py, pz)
                    .setColor(255, 255, 255, 255)
                    .setUv(u, vCoord)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, nx, ny, nz)
            }
        }
    }
}
