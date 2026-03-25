package com.github.narazaka.vrmmod.neoforge

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.client.VrmConfigScreen
import com.github.narazaka.vrmmod.client.VrmModClient
import com.github.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge

@Mod(VrmMod.MOD_ID)
class VrmModNeoForge(container: ModContainer) {
    init {
        VrmMod.init()
        if (FMLEnvironment.dist.isClient) {
            VrmModClient.init()
            container.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> VrmConfigScreen.create(parent) },
            )

            // Hook for first-person VRM rendering
            NeoForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
                if (event.stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                    val bufferSource = net.minecraft.client.Minecraft.getInstance().renderBuffers().bufferSource()
                    VrmFirstPersonRenderer.renderFirstPerson(
                        event.poseStack,
                        bufferSource,
                        event.partialTick.gameTimeDeltaTicks,
                    )
                    // Flush the buffer so our VRM geometry is actually drawn
                    bufferSource.endLastBatch()
                }
            }
        }
    }
}
