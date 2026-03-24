package com.github.narazaka.vrmmod.fabric

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.client.VrmModClient
import net.fabricmc.api.ClientModInitializer

class VrmModFabric : ClientModInitializer {
    override fun onInitializeClient() {
        VrmMod.init()
        VrmModClient.init()
    }
}
