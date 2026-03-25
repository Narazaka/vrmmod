package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.animation.AnimationConfig
import com.github.narazaka.vrmmod.render.VrmPlayerManager
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File

/**
 * Creates the Cloth Config settings screen for VRM Mod.
 *
 * When the screen is closed after saving, the current player's VRM model
 * is reloaded with the new settings so changes take effect immediately.
 */
object VrmConfigScreen {

    fun create(parent: Screen?): Screen {
        val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
        val config = VrmModConfig.load(configDir)

        // Mutable holders for the new values
        var newModelPath = config.localModelPath ?: ""
        var newAnimDir = config.animationDir ?: ""
        var newUseVrma = config.useVrmaAnimation

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("VRM Mod Settings"))
            .setSavingRunnable {
                val newConfig = VrmModConfig(
                    localModelPath = newModelPath.ifBlank { null },
                    animationDir = newAnimDir.ifBlank { null },
                    useVrmaAnimation = newUseVrma,
                )
                VrmModConfig.save(configDir, newConfig)
                reloadModel(configDir, newConfig)
            }

        val general = builder.getOrCreateCategory(Component.literal("General"))
        val entryBuilder = builder.entryBuilder()

        general.addEntry(
            entryBuilder.startStrField(Component.literal("VRM Model Path"), newModelPath)
                .setDefaultValue("")
                .setTooltip(Component.literal("Absolute path to the .vrm model file"))
                .setSaveConsumer { newModelPath = it }
                .build()
        )

        general.addEntry(
            entryBuilder.startStrField(Component.literal("Animation Directory"), newAnimDir)
                .setDefaultValue("")
                .setTooltip(Component.literal("Directory containing .vrma animation files"))
                .setSaveConsumer { newAnimDir = it }
                .build()
        )

        general.addEntry(
            entryBuilder.startBooleanToggle(Component.literal("Use VRMA Animation"), newUseVrma)
                .setDefaultValue(true)
                .setTooltip(Component.literal("Enable .vrma file animation (disable for procedural animation)"))
                .setSaveConsumer { newUseVrma = it }
                .build()
        )

        return builder.build()
    }

    /**
     * Unloads the current player's VRM model and reloads it with the new config.
     */
    private fun reloadModel(configDir: File, config: VrmModConfig) {
        val player = Minecraft.getInstance().player ?: return
        val uuid = player.uuid

        // Unload existing model
        VrmPlayerManager.unload(uuid)

        // Reload with new settings
        val modelPath = config.localModelPath
        if (modelPath != null) {
            val file = File(modelPath)
            if (file.exists()) {
                val animDir = if (config.useVrmaAnimation) config.animationDir?.let { File(it) } else null
                val animationConfig = AnimationConfig.load(configDir)
                VrmMod.logger.info("Reloading VRM model after config change: {}", modelPath)
                VrmPlayerManager.loadLocal(uuid, file, animDir, animationConfig)
            } else {
                VrmMod.logger.warn("Configured VRM model file not found: {}", modelPath)
            }
        }
    }
}
