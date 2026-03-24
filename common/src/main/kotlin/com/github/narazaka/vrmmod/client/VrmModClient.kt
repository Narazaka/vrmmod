package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.render.VrmPlayerManager
import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.io.File

/**
 * Client-side initialization: key bindings, event listeners,
 * config loading, and VRM model loading on world join.
 */
object VrmModClient {
    private val KEY_VRM_SETTINGS = KeyMapping(
        "key.vrmmod.settings",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_V,
        "category.vrmmod",
    )

    fun init() {
        KeyMappingRegistry.register(KEY_VRM_SETTINGS)

        // On world join, load local VRM if configured
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { player ->
            val configDir = File(Minecraft.getInstance().gameDirectory, "config")
            val config = VrmModConfig.load(configDir)
            if (config.localModelPath != null) {
                val vrmFile = File(config.localModelPath)
                if (vrmFile.exists()) {
                    VrmMod.logger.info("Loading VRM model: ${vrmFile.absolutePath}")
                    VrmPlayerManager.loadLocal(player.uuid, vrmFile)
                } else {
                    VrmMod.logger.warn("VRM file not found: ${config.localModelPath}")
                }
            }
        }

        // On world leave, cleanup
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { player ->
            if (player != null) {
                VrmPlayerManager.unload(player.uuid)
            }
        }

        // Key binding tick handler (settings screen placeholder)
        ClientTickEvent.CLIENT_POST.register { _ ->
            while (KEY_VRM_SETTINGS.consumeClick()) {
                VrmMod.logger.info("VRM settings key pressed (settings UI not yet implemented)")
            }
        }
    }
}
