package com.github.narazaka.vrmmod.neoforge

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.client.VrmConfigScreen
import com.github.narazaka.vrmmod.client.VrmModClient
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.gui.IConfigScreenFactory

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
        }
    }
}
