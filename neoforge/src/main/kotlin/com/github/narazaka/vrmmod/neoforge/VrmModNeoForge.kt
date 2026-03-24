package com.github.narazaka.vrmmod.neoforge

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.client.VrmModClient
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment

@Mod(VrmMod.MOD_ID)
class VrmModNeoForge {
    init {
        VrmMod.init()
        if (FMLEnvironment.dist.isClient) {
            VrmModClient.init()
        }
    }
}
