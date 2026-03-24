package com.github.narazaka.vrmmod.fabric

import com.github.narazaka.vrmmod.VrmMod
import net.fabricmc.api.ClientModInitializer

class VrmModFabric : ClientModInitializer {
    override fun onInitializeClient() {
        VrmMod.init()
        VrmMod.initClient()
    }
}
