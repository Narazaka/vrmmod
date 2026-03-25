package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.animation.AnimationConfig
import com.github.narazaka.vrmmod.render.VrmPlayerManager
import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.io.File

/**
 * Client-side initialization for the VRM mod.
 *
 * Registers keybindings and event listeners for VRM model loading/unloading.
 */
object VrmModClient {

    /** Key binding to open VRM config screen (unbound by default). */
    private val VRM_KEY = KeyMapping(
        "key.${VrmMod.MOD_ID}.config",
        InputConstants.Type.KEYSYM,
        InputConstants.UNKNOWN.value,
        "category.${VrmMod.MOD_ID}",
    )

    fun init() {
        VrmMod.logger.info("Initializing VRM Mod client")

        // Register keybinding
        KeyMappingRegistry.register(VRM_KEY)

        // Open config screen when V is pressed
        ClientTickEvent.CLIENT_POST.register {
            while (VRM_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(VrmConfigScreen.create(null))
            }
        }

        // On world join: load VRM model from config if configured
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { player ->
            val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
            val config = VrmModConfig.load(configDir)

            val animationConfig = AnimationConfig.load(configDir)

            val modelPath = config.localModelPath
            if (modelPath != null) {
                val file = File(modelPath)
                if (file.exists()) {
                    val animDir = if (config.useVrmaAnimation) config.animationDir?.let { File(it) } else null
                    VrmMod.logger.info("Loading local VRM model: {}", modelPath)
                    if (animDir != null) {
                        VrmMod.logger.info("Animation directory: {}", animDir.absolutePath)
                    } else if (!config.useVrmaAnimation) {
                        VrmMod.logger.info("VRMA animation disabled, using procedural animation")
                    }
                    VrmPlayerManager.loadLocal(player.uuid, file, animDir, animationConfig)
                } else {
                    VrmMod.logger.warn("Configured VRM model file not found: {}", modelPath)
                }
            }
        }

        // On world leave: unload all VRM models
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
            VrmMod.logger.info("Unloading all VRM models")
            VrmPlayerManager.clear()
        }
    }
}
