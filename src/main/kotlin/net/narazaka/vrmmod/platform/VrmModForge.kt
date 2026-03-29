//? if forge {
/*package net.narazaka.vrmmod.platform

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmModScreen
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraftforge.client.ConfigScreenHandler
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.ModLoadingContext

@Mod(VrmMod.MOD_ID)
class VrmModForge {
    init {
        VrmMod.init()
        if (FMLEnvironment.dist.isClient) {
            VrmModClient.init()
            ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory::class.java,
            ) { ConfigScreenHandler.ConfigScreenFactory { _, parent -> VrmModScreen(parent) } }

            MinecraftForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
                if (event.stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val bufferSource = mc.renderBuffers().bufferSource()
                    VrmFirstPersonRenderer.renderFirstPerson(
                        event.poseStack,
                        bufferSource,
                        event.partialTick,
                    )
                    bufferSource.endBatch()
                }
            }
        }
    }
}
*///?}
