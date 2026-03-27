package net.narazaka.vrmmod.fabric

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents

class VrmModFabric : ClientModInitializer {
    override fun onInitializeClient() {
        VrmMod.init()
        VrmModClient.init()

        // Hook for first-person VRM rendering
        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val poseStack = context.matrixStack() ?: return@register
            val consumers = context.consumers() ?: return@register
            VrmFirstPersonRenderer.renderFirstPerson(
                poseStack,
                consumers,
                context.tickCounter().getGameTimeDeltaPartialTick(false),
            )
            // Flush batches so VRM geometry is committed (matches NeoForge behavior)
            if (consumers is net.minecraft.client.renderer.MultiBufferSource.BufferSource) {
                consumers.endBatch()
            }
        }
    }
}
