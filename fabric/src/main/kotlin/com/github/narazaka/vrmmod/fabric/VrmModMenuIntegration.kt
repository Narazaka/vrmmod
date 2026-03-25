package com.github.narazaka.vrmmod.fabric

import com.github.narazaka.vrmmod.client.VrmConfigScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class VrmModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> VrmConfigScreen.create(parent) }
    }
}
