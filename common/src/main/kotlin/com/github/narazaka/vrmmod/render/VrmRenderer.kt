package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmPrimitive
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

/**
 * Renders a VRM model in T-pose (no skinning) using Minecraft's
 * VertexConsumer rendering pipeline.
 */
object VrmRenderer {

    /** Target height in blocks for the rendered model. */
    private const val TARGET_HEIGHT = 1.8f

    /** Fallback scale if hips position cannot be determined. */
    private const val DEFAULT_SCALE = 0.9f

    /**
     * Renders the VRM model described by [state] at the current PoseStack position.
     *
     * The model is drawn as a static T-pose with no bone skinning applied.
     *
     * @param state the VRM state containing model data and texture locations
     * @param poseStack the current matrix stack
     * @param bufferSource the buffer source to obtain VertexConsumers from
     * @param packedLight the packed light value for lighting
     */
    fun render(
        state: VrmState,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        val model = state.model

        poseStack.pushPose()

        // glTF uses right-handed coordinates; Minecraft uses left-handed.
        // Flip Z axis to convert.
        poseStack.scale(1f, 1f, -1f)

        // Scale model to approximately player height (~1.8 blocks).
        val scale = estimateScale(state)
        poseStack.scale(scale, scale, scale)

        val pose = poseStack.last()

        for (mesh in model.meshes) {
            for (primitive in mesh.primitives) {
                val texture = resolveTexture(state, primitive.materialIndex)
                val vertexConsumer = bufferSource.getBuffer(
                    RenderType.entityCutoutNoCull(texture),
                )

                drawPrimitive(primitive, vertexConsumer, pose, packedLight)
            }
        }

        poseStack.popPose()
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
                    // Hips are roughly at half the model height.
                    // Estimate full height as hipsY * 2, then scale to TARGET_HEIGHT.
                    return TARGET_HEIGHT / (hipsY * 2f)
                }
            }
        }
        return DEFAULT_SCALE
    }

    /**
     * Resolves the texture ResourceLocation for a given material index.
     * Falls back to the first texture or a missing texture placeholder.
     */
    private fun resolveTexture(state: VrmState, materialIndex: Int): ResourceLocation {
        val locations = state.textureLocations
        if (locations.isEmpty()) {
            // Return a dummy location; the render will show missing texture
            return ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png")
        }
        // Use materialIndex if valid, otherwise fall back to first texture
        return if (materialIndex in locations.indices) {
            locations[materialIndex]
        } else {
            locations[0]
        }
    }

    /**
     * Draws a single primitive's indexed triangles through the VertexConsumer.
     */
    private fun drawPrimitive(
        primitive: VrmPrimitive,
        vertexConsumer: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        packedLight: Int,
    ) {
        val positions = primitive.positions
        val normals = primitive.normals
        val texCoords = primitive.texCoords
        val indices = primitive.indices
        val hasNormals = normals.size >= positions.size
        val hasUVs = texCoords.size >= (primitive.vertexCount * 2)

        for (index in indices) {
            val px = positions[index * 3]
            val py = positions[index * 3 + 1]
            val pz = positions[index * 3 + 2]

            val nx: Float
            val ny: Float
            val nz: Float
            if (hasNormals) {
                nx = normals[index * 3]
                ny = normals[index * 3 + 1]
                nz = normals[index * 3 + 2]
            } else {
                nx = 0f
                ny = 1f
                nz = 0f
            }

            val u: Float
            val v: Float
            if (hasUVs) {
                u = texCoords[index * 2]
                v = texCoords[index * 2 + 1]
            } else {
                u = 0f
                v = 0f
            }

            vertexConsumer
                .addVertex(pose, px, py, pz)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz)
        }
    }
}
