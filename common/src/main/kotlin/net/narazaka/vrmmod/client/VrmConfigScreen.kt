package net.narazaka.vrmmod.client

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
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
        var newFirstPersonMode = config.firstPersonMode

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("VRM Mod Settings"))
            .setSavingRunnable {
                val newConfig = VrmModConfig(
                    localModelPath = newModelPath.ifBlank { null },
                    animationDir = newAnimDir.ifBlank { null },
                    useVrmaAnimation = newUseVrma,
                    firstPersonMode = newFirstPersonMode,
                )
                VrmModClient.currentConfig = newConfig
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

        general.addEntry(
            entryBuilder.startEnumSelector(
                Component.literal("First Person Mode"),
                FirstPersonMode::class.java,
                newFirstPersonMode,
            )
                .setDefaultValue(FirstPersonMode.VRM_MC_CAMERA)
                .setTooltip(
                    Component.literal("VANILLA: MC default hands"),
                    Component.literal("VRM_MC_CAMERA: VRM body, MC camera height"),
                    Component.literal("VRM_VRM_CAMERA: VRM body, VRM eye height (future)"),
                )
                .setSaveConsumer { newFirstPersonMode = it }
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
