package com.github.narazaka.vrmmod.neoforge

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.client.VrmModClient
import net.neoforged.fml.common.Mod

@Mod(VrmMod.MOD_ID)
class VrmModNeoForge {
    init {
        VrmMod.init()
        VrmModClient.init()
    }
}
