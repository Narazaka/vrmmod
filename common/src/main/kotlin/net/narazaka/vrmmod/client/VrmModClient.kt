package net.narazaka.vrmmod.client

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import net.narazaka.vrmmod.vroidhub.*
import com.mojang.blaze3d.platform.InputConstants
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.event.events.client.ClientTickEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Client-side initialization for the VRM mod.
 *
 * Registers keybindings and event listeners for VRM model loading/unloading.
 */
object VrmModClient {

    /** Current config, accessible for first-person mode checks. */
    var currentConfig: VrmModConfig = VrmModConfig()

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

        // Open VRM Mod screen when key is pressed
        ClientTickEvent.CLIENT_POST.register {
            while (VRM_KEY.consumeClick()) {
                Minecraft.getInstance().setScreen(VrmModScreen(null))
            }
        }

        // On world join: load VRM model from config if configured
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { player ->
            val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
            val config = VrmModConfig.load(configDir)
            currentConfig = config

            val animDir = if (config.useVrmaAnimation) config.animationDir?.let { File(it) } else null

            // Load animation config: prefer animationDir/vrmmod-animations.json, fall back to config dir
            val animationConfig = if (animDir != null) {
                val animDirConfig = File(animDir, "vrmmod-animations.json")
                if (animDirConfig.exists()) {
                    VrmMod.logger.info("Loading animation config from animation dir: {}", animDirConfig.absolutePath)
                    AnimationConfig.load(animDir)
                } else {
                    AnimationConfig.load(configDir)
                }
            } else {
                AnimationConfig.load(configDir)
            }

            when (config.modelSource) {
                ModelSource.LOCAL -> {
                    val file = config.resolveModelFile()
                    if (file != null && file.exists()) {
                        VrmMod.logger.info("Loading local VRM model: {}", file.absolutePath)
                        VrmPlayerManager.loadLocal(player.uuid, file, animDir, animationConfig, config.useVrmaAnimation)
                    } else if (file != null) {
                        VrmMod.logger.warn("Configured VRM model file not found: {}", file.absolutePath)
                    }
                }
                ModelSource.VROID_HUB -> {
                    val modelId = config.vroidHubModelId
                    if (modelId != null) {
                        loadVRoidHubModel(player.uuid, modelId, configDir, animDir, animationConfig, config.useVrmaAnimation)
                    }
                }
            }
        }

        // On world leave: unload all VRM models
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
            VrmMod.logger.info("Unloading all VRM models")
            VrmPlayerManager.clear()
        }
    }

    /**
     * Called from VRoidHubScreen after model selection to trigger download and load.
     */
    fun loadVRoidHubModelFromScreen(uuid: java.util.UUID, versionId: String = "") {
        val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
        val config = VrmModConfig.load(configDir)
        val modelId = config.vroidHubModelId ?: return
        val animDir = if (config.useVrmaAnimation) config.animationDir?.let { File(it) } else null
        val animationConfig = AnimationConfig.load(configDir)

        // Unload existing model first
        VrmPlayerManager.unload(uuid)

        loadVRoidHubModel(uuid, modelId, configDir, animDir, animationConfig, config.useVrmaAnimation, versionId)
    }

    private fun loadVRoidHubModel(
        uuid: java.util.UUID,
        modelId: String,
        configDir: File,
        animDir: File?,
        animationConfig: AnimationConfig,
        useVrmaAnimation: Boolean = true,
        knownVersionId: String = "",
    ) {
        val vroidConfig = VRoidHubConfig.load(configDir.toPath())
        if (!vroidConfig.isAvailable) return

        CompletableFuture.supplyAsync {
            // Ensure valid token
            var token = VRoidHubAuth.loadToken(configDir.toPath()) ?: return@supplyAsync null
            if (token.isExpired) {
                val refreshResult = VRoidHubAuth.refreshToken(vroidConfig, token.refreshToken)
                refreshResult.onSuccess { newToken ->
                    VRoidHubAuth.saveToken(configDir.toPath(), newToken)
                    token = VRoidHubAuth.loadToken(configDir.toPath())!!
                }.onFailure { e ->
                    VrmMod.logger.error("VRoid Hub token refresh failed", e)
                    return@supplyAsync null
                }
            }

            // Check cache: use knownVersionId if provided, otherwise try API cache,
            // otherwise use any cached version (no API calls needed).
            val gameDir = Minecraft.getInstance().gameDirectory.toPath()
            val versionId = knownVersionId.ifEmpty {
                VRoidHubModelCache.loadApiCache(gameDir)?.findVersionId(modelId) ?: ""
            }
            val cached = if (versionId.isNotEmpty()) {
                VRoidHubModelCache.getCachedModel(gameDir, modelId, versionId)
            } else {
                VRoidHubModelCache.getCachedModelAnyVersion(gameDir, modelId)
            }
            if (cached != null) {
                VrmMod.logger.info("Loading VRoid Hub model from cache: {}", cached.absolutePath)
                return@supplyAsync cached
            }

            // Download
            VrmMod.logger.info("Downloading VRoid Hub model: {}", modelId)
            val license = VRoidHubApi.postDownloadLicense(token.accessToken, modelId).getOrElse { e ->
                VrmMod.logger.error("Failed to get download license", e)
                return@supplyAsync null
            }

            val downloadUrl = VRoidHubApi.getDownloadUrl(token.accessToken, license.id).getOrElse { e ->
                VrmMod.logger.error("Failed to get download URL", e)
                return@supplyAsync null
            }

            val vrmBytes = VRoidHubApi.downloadVrm(downloadUrl).getOrElse { e ->
                VrmMod.logger.error("Failed to download VRM", e)
                return@supplyAsync null
            }

            val file = VRoidHubModelCache.cacheModel(gameDir, modelId, versionId, vrmBytes)
            VrmMod.logger.info("VRoid Hub model cached: {}", file.absolutePath)
            file
        }.thenAccept { file ->
            if (file != null) {
                VrmMod.logger.info("VRoid Hub model ready, loading: {}", file.absolutePath)
                Minecraft.getInstance().execute {
                    VrmPlayerManager.loadLocal(uuid, file, animDir, animationConfig, useVrmaAnimation)
                }
            } else {
                VrmMod.logger.error("VRoid Hub model download returned null")
            }
        }.exceptionally { e ->
            VrmMod.logger.error("VRoid Hub model load failed with exception", e)
            null
        }
    }
}
