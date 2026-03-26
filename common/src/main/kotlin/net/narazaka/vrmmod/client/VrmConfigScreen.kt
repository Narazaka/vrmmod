package net.narazaka.vrmmod.client

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import net.narazaka.vrmmod.vroidhub.*
import me.shedaniel.clothconfig2.api.ConfigBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

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
        var newVroidHubModelId = config.vroidHubModelId

        // VRoid Hub state
        val vroidHubConfig = VRoidHubConfig.load(configDir.toPath())
        val savedToken = if (vroidHubConfig.isAvailable) VRoidHubAuth.loadToken(configDir.toPath()) else null

        val builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("VRM Mod Settings"))
            .setSavingRunnable {
                val newConfig = VrmModConfig(
                    localModelPath = newModelPath.ifBlank { null },
                    animationDir = newAnimDir.ifBlank { null },
                    useVrmaAnimation = newUseVrma,
                    firstPersonMode = newFirstPersonMode,
                    vroidHubModelId = newVroidHubModelId,
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

        // VRoid Hub section (only if credentials are configured)
        if (vroidHubConfig.isAvailable) {
            val vroidHub = builder.getOrCreateCategory(Component.literal("VRoid Hub"))

            if (savedToken != null && !savedToken.isExpired) {
                // Logged in state
                vroidHub.addEntry(
                    entryBuilder.startTextDescription(
                        Component.literal("Logged in to VRoid Hub")
                    ).build()
                )

                // Select model button (opens custom screen)
                vroidHub.addEntry(
                    entryBuilder.startTextDescription(
                        Component.literal("Selected model: ${newVroidHubModelId ?: "(none)"}")
                    ).build()
                )

                // Note: actual model selection button needs to be triggered from within the screen.
                // For now, show the model ID field for direct input.
                vroidHub.addEntry(
                    entryBuilder.startStrField(Component.literal("VRoid Hub Model ID"), newVroidHubModelId ?: "")
                        .setDefaultValue("")
                        .setTooltip(Component.literal("Model ID from VRoid Hub (select via model browser)"))
                        .setSaveConsumer { newVroidHubModelId = it.ifBlank { null } }
                        .build()
                )

                vroidHub.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Logout"), false)
                        .setDefaultValue(false)
                        .setTooltip(Component.literal("Toggle ON and save to logout from VRoid Hub"))
                        .setSaveConsumer { if (it) {
                            VRoidHubAuth.revokeToken(vroidHubConfig, savedToken.accessToken)
                            VRoidHubAuth.deleteToken(configDir.toPath())
                        }}
                        .build()
                )
            } else {
                // Not logged in
                // Pre-generate auth session (PKCE) for this screen instance
                val (authorizeUrl, authSession) = VRoidHubAuth.buildAuthorizeUrl(vroidHubConfig)

                vroidHub.addEntry(
                    entryBuilder.startTextDescription(
                        Component.literal("1. Click the Login button to open VRoid Hub in your browser")
                    ).build()
                )
                vroidHub.addEntry(
                    entryBuilder.startTextDescription(
                        Component.literal("2. Authorize the application, then copy the code shown")
                    ).build()
                )
                vroidHub.addEntry(
                    entryBuilder.startTextDescription(
                        Component.literal("3. Paste the code below and click Save")
                    ).build()
                )

                // Login button — opens system browser
                vroidHub.addEntry(
                    entryBuilder.startBooleanToggle(Component.literal("Login (open browser)"), false)
                        .setDefaultValue(false)
                        .setTooltip(Component.literal("Toggle ON to open VRoid Hub login page in your browser"))
                        .setSaveConsumer { if (it) {
                            try {
                                net.minecraft.Util.getPlatform().openUri(authorizeUrl)
                                VrmMod.logger.info("Opened VRoid Hub login page in browser")
                            } catch (e: Exception) {
                                VrmMod.logger.error("Failed to open browser. URL: {}", authorizeUrl, e)
                            }
                        }}
                        .build()
                )

                var authCode = ""
                vroidHub.addEntry(
                    entryBuilder.startStrField(Component.literal("Authorization Code"), "")
                        .setDefaultValue("")
                        .setTooltip(Component.literal("Paste the authorization code from VRoid Hub"))
                        .setSaveConsumer { authCode = it }
                        .build()
                )

                // On save: exchange code for token
                val originalSaveRunnable = builder.savingRunnable
                builder.setSavingRunnable {
                    if (authCode.isNotBlank()) {
                        CompletableFuture.supplyAsync {
                            VRoidHubAuth.exchangeToken(vroidHubConfig, authCode, authSession)
                        }.thenAccept { result ->
                            result.onSuccess { token ->
                                VRoidHubAuth.saveToken(configDir.toPath(), token)
                                VrmMod.logger.info("VRoid Hub login successful")
                            }.onFailure { e ->
                                VrmMod.logger.error("VRoid Hub login failed", e)
                            }
                        }
                    }
                    originalSaveRunnable?.run()
                }
            }
        }

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
