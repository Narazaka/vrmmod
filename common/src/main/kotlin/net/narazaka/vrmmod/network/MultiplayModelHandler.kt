package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import net.narazaka.vrmmod.vroidhub.*
import net.minecraft.client.Minecraft
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object MultiplayModelHandler {
    private var loginNotificationShown = false
    private val downloadingPlayers = ConcurrentHashMap.newKeySet<UUID>()

    fun handlePlayerModel(payload: PlayerModelPayload) {
        val mc = Minecraft.getInstance()
        val localPlayerUuid = mc.player?.uuid

        // Don't handle our own model
        if (payload.playerUUID == localPlayerUuid) return

        if (payload.vroidHubModelId == null) {
            // Player cleared model or disconnected
            VrmPlayerManager.unload(payload.playerUUID)
            downloadingPlayers.remove(payload.playerUUID)
            return
        }

        // Check if already loaded with same model
        val existingState = VrmPlayerManager.get(payload.playerUUID)
        if (existingState != null) {
            VrmPlayerManager.unload(payload.playerUUID)
        }

        // Check VRoid Hub login status
        val configDir = mc.gameDirectory.resolve("config")
        val vroidConfig = VRoidHubConfig.load(configDir.toPath())
        if (!vroidConfig.isAvailable) {
            showLoginNotificationOnce()
            return
        }

        var token = VRoidHubAuth.loadToken(configDir.toPath())
        if (token == null) {
            showLoginNotificationOnce()
            return
        }

        if (downloadingPlayers.contains(payload.playerUUID)) return
        downloadingPlayers.add(payload.playerUUID)

        val licenseId = payload.multiplayLicenseId
        val modelId = payload.vroidHubModelId
        val scale = payload.scale
        val normalMode = payload.normalMode

        CompletableFuture.supplyAsync {
            // Refresh token if expired
            if (token!!.isExpired) {
                val refreshResult = VRoidHubAuth.refreshToken(vroidConfig, token!!.refreshToken)
                refreshResult.onSuccess { newToken ->
                    VRoidHubAuth.saveToken(configDir.toPath(), newToken)
                    token = VRoidHubAuth.loadToken(configDir.toPath())
                }.onFailure { e ->
                    VrmMod.logger.error("VRoid Hub token refresh failed for multiplay download", e)
                    return@supplyAsync null
                }
            }
            val accessToken = token!!.accessToken

            // Check cache first
            val gameDir = mc.gameDirectory.toPath()
            val cached = VRoidHubModelCache.getCachedModelAnyVersion(gameDir, modelId)
            if (cached != null) {
                VrmMod.logger.info("Loading multiplayer VRM from cache for model {}", modelId)
                return@supplyAsync cached
            }

            // Download using multiplay license
            if (licenseId != null) {
                val downloadUrl = VRoidHubApi.getDownloadUrl(accessToken, licenseId).getOrElse { e ->
                    VrmMod.logger.warn("Multiplay license download failed for model {}: {}", modelId, e.message)
                    return@supplyAsync null
                }
                val vrmBytes = VRoidHubApi.downloadVrm(downloadUrl).getOrElse { e ->
                    VrmMod.logger.error("Failed to download VRM for model {}", modelId, e)
                    return@supplyAsync null
                }
                val file = VRoidHubModelCache.cacheModel(gameDir, modelId, "", vrmBytes)
                VrmMod.logger.info("Multiplayer VRM cached: {}", file.absolutePath)
                return@supplyAsync file
            }

            VrmMod.logger.warn("No multiplay license available for model {}", modelId)
            null
        }.thenAccept { file ->
            downloadingPlayers.remove(payload.playerUUID)
            if (file != null) {
                mc.execute {
                    val animationConfig = AnimationConfig.load(configDir).copy(normalMode = normalMode)
                    VrmPlayerManager.loadLocal(
                        payload.playerUUID, file, null, animationConfig, true,
                    ) { state ->
                        VrmMod.logger.info(
                            "Applying multiplay scale {} (local was {}) for player {}",
                            scale, state.cachedScale, payload.playerUUID,
                        )
                        state.cachedScale = scale
                    }
                }
            } else {
                VrmMod.logger.warn("Could not load VRM for player {}", payload.playerUUID)
            }
        }.exceptionally { e ->
            downloadingPlayers.remove(payload.playerUUID)
            VrmMod.logger.error("Multiplay model download failed", e)
            null
        }
    }

    private fun showLoginNotificationOnce() {
        if (loginNotificationShown) return
        loginNotificationShown = true
        VrmMod.logger.info("Other players have VRM models but VRoid Hub is not logged in")
        Minecraft.getInstance().execute {
            Minecraft.getInstance().gui.chat.addMessage(
                net.minecraft.network.chat.Component.literal("[VRM Mod] Other players have VRM avatars. Log in to VRoid Hub in VRM Mod settings to see them.")
            )
        }
    }

    fun reset() {
        loginNotificationShown = false
        downloadingPlayers.clear()
    }
}
