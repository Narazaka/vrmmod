package net.narazaka.vrmmod.client

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File

object VrmConfigScreen {

    fun create(parent: Screen?): Screen {
        val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
        val config = VrmModConfig.load(configDir)

        var newModelPath = config.localModelPath ?: ""
        var newAnimDir = config.animationDir ?: ""
        var newUseVrma = config.useVrmaAnimation
        var newFirstPersonMode = config.firstPersonMode

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.translatable("vrmmod.config.title"))
            .setSavingRunnable {
                val newConfig = VrmModConfig(
                    localModelPath = newModelPath.ifBlank { null },
                    animationDir = newAnimDir.ifBlank { null },
                    useVrmaAnimation = newUseVrma,
                    firstPersonMode = newFirstPersonMode,
                    vroidHubModelId = config.vroidHubModelId,
                )
                VrmModClient.currentConfig = newConfig
                VrmModConfig.save(configDir, newConfig)
                reloadModel(configDir, newConfig)
            }

        val general = builder.getOrCreateCategory(Component.translatable("vrmmod.config.category.general"))
        val entryBuilder = builder.entryBuilder()

        general.addEntry(
            entryBuilder.startStrField(Component.translatable("vrmmod.config.model_path"), newModelPath)
                .setDefaultValue("")
                .setTooltip(Component.translatable("vrmmod.config.model_path.tooltip"))
                .setSaveConsumer { newModelPath = it }
                .build()
        )

        general.addEntry(
            entryBuilder.startStrField(Component.translatable("vrmmod.config.animation_dir"), newAnimDir)
                .setDefaultValue("")
                .setTooltip(Component.translatable("vrmmod.config.animation_dir.tooltip"))
                .setSaveConsumer { newAnimDir = it }
                .build()
        )

        general.addEntry(
            entryBuilder.startBooleanToggle(Component.translatable("vrmmod.config.use_vrma"), newUseVrma)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("vrmmod.config.use_vrma.tooltip"))
                .setSaveConsumer { newUseVrma = it }
                .build()
        )

        general.addEntry(
            entryBuilder.startEnumSelector(
                Component.translatable("vrmmod.config.first_person_mode"),
                FirstPersonMode::class.java,
                newFirstPersonMode,
            )
                .setDefaultValue(FirstPersonMode.VRM_MC_CAMERA)
                .setTooltip(
                    Component.translatable("vrmmod.config.first_person_mode.vanilla"),
                    Component.translatable("vrmmod.config.first_person_mode.vrm_mc_camera"),
                    Component.translatable("vrmmod.config.first_person_mode.vrm_vrm_camera"),
                )
                .setSaveConsumer { newFirstPersonMode = it }
                .build()
        )

        return builder.build()
    }

    private fun reloadModel(configDir: File, config: VrmModConfig) {
        val player = Minecraft.getInstance().player ?: return
        val uuid = player.uuid

        VrmPlayerManager.unload(uuid)

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
