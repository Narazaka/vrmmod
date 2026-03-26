package net.narazaka.vrmmod.fabric

import net.narazaka.vrmmod.client.VRoidHubScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class VrmModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> VRoidHubScreen(parent) }
    }
}
