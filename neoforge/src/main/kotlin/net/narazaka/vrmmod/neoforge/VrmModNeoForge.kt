package net.narazaka.vrmmod.neoforge

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmConfigScreen
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
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
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val bufferSource = mc.renderBuffers().bufferSource()
                    VrmFirstPersonRenderer.renderFirstPerson(
                        event.poseStack,
                        bufferSource,
                        event.partialTick.getGameTimeDeltaPartialTick(false),
                    )
                    // Flush all batches so VRM geometry is committed to the render pipeline
                    bufferSource.endBatch()
                }
            }
        }
    }
}
