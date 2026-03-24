package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmPrimitive
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

/**
 * Renders VRM models using VertexConsumer.
 * Phase 2: T-pose only (no skinning / bone animation).
 */
object VrmRenderer {

    fun render(
        state: VrmState,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        val model = state.model
        poseStack.pushPose()

        // Flip Z axis: glTF uses +Z toward viewer, MC uses +Z south (away)
        poseStack.scale(1.0f, 1.0f, -1.0f)

        for (mesh in model.meshes) {
            for (primitive in mesh.primitives) {
                val textureLocation = resolveTexture(state, primitive.materialIndex)
                val renderType = RenderType.entityCutoutNoCull(textureLocation)
                val consumer = bufferSource.getBuffer(renderType)

                drawPrimitive(primitive, poseStack, consumer, packedLight)
            }
        }

        poseStack.popPose()
    }

    private fun resolveTexture(state: VrmState, materialIndex: Int): ResourceLocation {
        return if (state.textureLocations.isNotEmpty()) {
            // coerceIn handles materialIndex == -1 by mapping to 0
            state.textureLocations[materialIndex.coerceIn(0, state.textureLocations.lastIndex)]
        } else {
            ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png")
        }
    }

    private fun drawPrimitive(
        primitive: VrmPrimitive,
        poseStack: PoseStack,
        consumer: VertexConsumer,
        packedLight: Int,
    ) {
        val pose = poseStack.last()

        val positions = primitive.positions
        val normals = primitive.normals
        val texCoords = primitive.texCoords
        val indices = primitive.indices
        val packedOverlay = OverlayTexture.NO_OVERLAY

        for (i in indices.indices) {
            val idx = indices[i]
            val p3 = idx * 3

            val px = positions[p3]
            val py = positions[p3 + 1]
            val pz = positions[p3 + 2]

            val hasNormals = normals.size > p3 + 2
            val nx = if (hasNormals) normals[p3] else 0f
            val ny = if (hasNormals) normals[p3 + 1] else 1f
            val nz = if (hasNormals) normals[p3 + 2] else 0f

            val p2 = idx * 2
            val hasUVs = texCoords.size > p2 + 1
            val u = if (hasUVs) texCoords[p2] else 0f
            val v = if (hasUVs) texCoords[p2 + 1] else 0f

            consumer.addVertex(pose, px, py, pz)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(pose, nx, ny, nz)
        }
    }
}
