package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.animation.AnimationConfig
import com.github.narazaka.vrmmod.render.VrmPlayerManager
import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientPlayerEvent
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

    /** Key binding to toggle VRM model (V key). */
    private val VRM_KEY = KeyMapping(
        "key.${VrmMod.MOD_ID}.toggle",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_V,
        "category.${VrmMod.MOD_ID}",
    )

    fun init() {
        VrmMod.logger.info("Initializing VRM Mod client")

        // Register keybinding
        KeyMappingRegistry.register(VRM_KEY)

        // On world join: load VRM model from config if configured
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { player ->
            val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
            val config = VrmModConfig.load(configDir)

            val animationConfig = AnimationConfig.load(configDir)

            val modelPath = config.localModelPath
            if (modelPath != null) {
                val file = File(modelPath)
                if (file.exists()) {
                    val animDir = config.animationDir?.let { File(it) }
                    VrmMod.logger.info("Loading local VRM model: {}", modelPath)
                    if (animDir != null) {
                        VrmMod.logger.info("Animation directory: {}", animDir.absolutePath)
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
