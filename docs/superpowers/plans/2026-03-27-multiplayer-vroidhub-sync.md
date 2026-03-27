# マルチプレイ VRoid Hub モデル同期 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** サーバー経由で各プレイヤーの VRoid Hub モデルIDとスケール値を共有し、マルチプレイ用ダウンロードライセンスで他プレイヤーのVRMアバターを表示する。

**Architecture:** Architectury NetworkManager の CustomPacketPayload でパケット定義。クライアントがログイン/モデル変更時に C2S パケットでモデル情報をサーバーへ送信、サーバーが S2C パケットで全クライアントにブロードキャスト。受信側は VRoid Hub API のマルチプレイ用ライセンスでモデルをダウンロードし既存の VrmPlayerManager でロード。

**Tech Stack:** Kotlin, Architectury API 15.0.3 (NetworkManager), MC 1.21.4 CustomPacketPayload/StreamCodec, VRoid Hub API

---

## ファイル構成

| ファイル | 操作 | 責務 |
|---------|------|------|
| `common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModNetwork.kt` | 新規 | パケット登録・ハンドラー（共通） |
| `common/src/main/kotlin/net/narazaka/vrmmod/network/ModelAnnouncePayload.kt` | 新規 | C2S パケット定義 |
| `common/src/main/kotlin/net/narazaka/vrmmod/network/PlayerModelPayload.kt` | 新規 | S2C パケット定義 |
| `common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModServer.kt` | 新規 | サーバー側プレイヤーモデル管理 |
| `common/src/main/kotlin/net/narazaka/vrmmod/network/MultiplayModelHandler.kt` | 新規 | クライアント側受信処理（他プレイヤーのモデルダウンロード＆ロード） |
| `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubApi.kt` | 修正 | マルチプレイ用ライセンス発行メソッド追加 |
| `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModClient.kt` | 修正 | ログイン時/モデル変更時にモデルアナウンス送信 |
| `common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt` | 修正 | ネットワーク初期化呼び出し追加 |

---

### Task 1: VRoid Hub API マルチプレイ用ライセンス発行

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubApi.kt`

- [ ] **Step 1: `postDownloadLicenseMultiplay` メソッドを追加**

`VRoidHubApi.kt` の `postDownloadLicense` メソッド（45行目）の直後に追加:

```kotlin
fun postDownloadLicenseMultiplay(accessToken: String, characterModelId: String): Result<DownloadLicense> {
    val body = gson.toJson(mapOf("character_model_id" to characterModelId))
    return post("/api/download_licenses/multiplay", accessToken, body).map {
        val type = object : TypeToken<VRoidHubResponse<DownloadLicense>>() {}.type
        val resp: VRoidHubResponse<DownloadLicense> = gson.fromJson(it, type)
        resp.data ?: throw RuntimeException("No data in response")
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubApi.kt
git commit -m "feat: add VRoid Hub multiplay download license API method"
```

---

### Task 2: パケット定義（ModelAnnouncePayload / PlayerModelPayload）

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/network/ModelAnnouncePayload.kt`
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/network/PlayerModelPayload.kt`

- [ ] **Step 1: ModelAnnouncePayload を作成**

```kotlin
package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation

data class ModelAnnouncePayload(
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<ModelAnnouncePayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<ModelAnnouncePayload>(
            ResourceLocation.fromNamespaceAndPath(VrmMod.MOD_ID, "model_announce")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelAnnouncePayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, ModelAnnouncePayload> {
                override fun decode(buf: RegistryFriendlyByteBuf): ModelAnnouncePayload {
                    val hasModelId = buf.readBoolean()
                    val vroidHubModelId = if (hasModelId) buf.readUtf() else null
                    val hasLicenseId = buf.readBoolean()
                    val multiplayLicenseId = if (hasLicenseId) buf.readUtf() else null
                    val scale = buf.readFloat()
                    return ModelAnnouncePayload(vroidHubModelId, multiplayLicenseId, scale)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, payload: ModelAnnouncePayload) {
                    buf.writeBoolean(payload.vroidHubModelId != null)
                    payload.vroidHubModelId?.let { buf.writeUtf(it) }
                    buf.writeBoolean(payload.multiplayLicenseId != null)
                    payload.multiplayLicenseId?.let { buf.writeUtf(it) }
                    buf.writeFloat(payload.scale)
                }
            }
    }
}
```

- [ ] **Step 2: PlayerModelPayload を作成**

```kotlin
package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import java.util.UUID

data class PlayerModelPayload(
    val playerUUID: UUID,
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PlayerModelPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerModelPayload>(
            ResourceLocation.fromNamespaceAndPath(VrmMod.MOD_ID, "player_model")
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlayerModelPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, PlayerModelPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf): PlayerModelPayload {
                    val uuid = buf.readUUID()
                    val hasModelId = buf.readBoolean()
                    val vroidHubModelId = if (hasModelId) buf.readUtf() else null
                    val hasLicenseId = buf.readBoolean()
                    val multiplayLicenseId = if (hasLicenseId) buf.readUtf() else null
                    val scale = buf.readFloat()
                    return PlayerModelPayload(uuid, vroidHubModelId, multiplayLicenseId, scale)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, payload: PlayerModelPayload) {
                    buf.writeUUID(payload.playerUUID)
                    buf.writeBoolean(payload.vroidHubModelId != null)
                    payload.vroidHubModelId?.let { buf.writeUtf(it) }
                    buf.writeBoolean(payload.multiplayLicenseId != null)
                    payload.multiplayLicenseId?.let { buf.writeUtf(it) }
                    buf.writeFloat(payload.scale)
                }
            }
    }
}
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/network/
git commit -m "feat: add ModelAnnounce and PlayerModel packet payloads"
```

---

### Task 3: サーバー側プレイヤーモデル管理

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModServer.kt`

- [ ] **Step 1: VrmModServer を作成**

```kotlin
package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import dev.architectury.networking.NetworkManager
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VrmModServer {
    data class PlayerModelInfo(
        val vroidHubModelId: String,
        val multiplayLicenseId: String?,
        val scale: Float,
    )

    private val playerModels = ConcurrentHashMap<UUID, PlayerModelInfo>()

    fun handleModelAnnounce(player: ServerPlayer, payload: ModelAnnouncePayload) {
        val uuid = player.uuid

        if (payload.vroidHubModelId == null) {
            // Model cleared
            playerModels.remove(uuid)
            VrmMod.logger.info("Player {} cleared VRM model", player.gameProfile.name)
        } else {
            val info = PlayerModelInfo(payload.vroidHubModelId, payload.multiplayLicenseId, payload.scale)
            playerModels[uuid] = info
            VrmMod.logger.info("Player {} announced VRM model: {}", player.gameProfile.name, payload.vroidHubModelId)
        }

        // Broadcast to all other players
        val broadcastPayload = PlayerModelPayload(
            playerUUID = uuid,
            vroidHubModelId = payload.vroidHubModelId,
            multiplayLicenseId = payload.multiplayLicenseId,
            scale = payload.scale,
        )
        for (otherPlayer in player.server.playerList.players) {
            if (otherPlayer.uuid != uuid) {
                NetworkManager.sendToPlayer(otherPlayer, broadcastPayload)
            }
        }

        // Send existing players' models to the newly connected player
        for ((existingUuid, info) in playerModels) {
            if (existingUuid != uuid) {
                val existingPayload = PlayerModelPayload(
                    playerUUID = existingUuid,
                    vroidHubModelId = info.vroidHubModelId,
                    multiplayLicenseId = info.multiplayLicenseId,
                    scale = info.scale,
                )
                NetworkManager.sendToPlayer(player, existingPayload)
            }
        }
    }

    fun handlePlayerDisconnect(playerUUID: UUID, server: net.minecraft.server.MinecraftServer) {
        val removed = playerModels.remove(playerUUID)
        if (removed != null) {
            VrmMod.logger.info("Player {} disconnected, broadcasting model removal", playerUUID)
            val clearPayload = PlayerModelPayload(
                playerUUID = playerUUID,
                vroidHubModelId = null,
                multiplayLicenseId = null,
                scale = 1.0f,
            )
            for (player in server.playerList.players) {
                NetworkManager.sendToPlayer(player, clearPayload)
            }
        }
    }

    fun clear() {
        playerModels.clear()
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModServer.kt
git commit -m "feat: add server-side player model management"
```

---

### Task 4: クライアント側受信処理

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/network/MultiplayModelHandler.kt`

- [ ] **Step 1: MultiplayModelHandler を作成**

```kotlin
package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import net.narazaka.vrmmod.vroidhub.*
import net.minecraft.client.Minecraft
import java.io.File
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
            // Already loaded - unload first for model change
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
                    val animationConfig = AnimationConfig.load(configDir)
                    VrmPlayerManager.loadLocal(
                        payload.playerUUID, file, null, animationConfig, true,
                    )
                    // Override cachedScale with the value from the model owner
                    val state = VrmPlayerManager.get(payload.playerUUID)
                    if (state != null && scale != state.cachedScale) {
                        VrmMod.logger.info(
                            "Applying multiplay scale {} (local was {}) for player {}",
                            scale, state.cachedScale, payload.playerUUID,
                        )
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
            val player = Minecraft.getInstance().player ?: return@execute
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("vrmmod.multiplayer.login_required")
            )
        }
    }

    fun reset() {
        loginNotificationShown = false
        downloadingPlayers.clear()
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/network/MultiplayModelHandler.kt
git commit -m "feat: add client-side multiplay model download handler"
```

---

### Task 5: ネットワーク登録・統合

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModNetwork.kt`
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt`

- [ ] **Step 1: VrmModNetwork を作成**

```kotlin
package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import dev.architectury.networking.NetworkManager
import net.minecraft.server.level.ServerPlayer

object VrmModNetwork {
    fun register() {
        VrmMod.logger.info("Registering VRM mod network packets")

        // Register S2C payload type (client needs to know how to decode)
        NetworkManager.registerS2CPayloadType(
            PlayerModelPayload.TYPE,
            PlayerModelPayload.CODEC,
        )

        // Register C2S receiver (server handles ModelAnnounce)
        NetworkManager.registerReceiver(
            NetworkManager.c2s(),
            ModelAnnouncePayload.TYPE,
            ModelAnnouncePayload.CODEC,
        ) { payload, context ->
            context.queue {
                val player = context.player
                if (player is ServerPlayer) {
                    VrmModServer.handleModelAnnounce(player, payload)
                }
            }
        }

        // Register S2C receiver (client handles PlayerModel)
        NetworkManager.registerReceiver(
            NetworkManager.s2c(),
            PlayerModelPayload.TYPE,
            PlayerModelPayload.CODEC,
        ) { payload, context ->
            context.queue {
                MultiplayModelHandler.handlePlayerModel(payload)
            }
        }
    }
}
```

- [ ] **Step 2: VrmMod.init() にネットワーク登録を追加**

`common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt` を修正:

```kotlin
package net.narazaka.vrmmod

import net.narazaka.vrmmod.network.VrmModNetwork
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object VrmMod {
    const val MOD_ID = "vrmmod"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        VrmModNetwork.register()
    }
}
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/network/VrmModNetwork.kt common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt
git commit -m "feat: register network packets in common init"
```

---

### Task 6: クライアント側モデルアナウンス送信

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModClient.kt`

- [ ] **Step 1: モデルアナウンス送信メソッドを追加**

`VrmModClient.kt` の末尾（`loadVRoidHubModel` の後）に追加:

```kotlin
/**
 * Sends the current player's VRM model info to the server for multiplayer sync.
 */
private fun announceModel(uuid: UUID, modelId: String?, scale: Float) {
    if (modelId == null) {
        // Clear model
        try {
            dev.architectury.networking.NetworkManager.sendToServer(
                net.narazaka.vrmmod.network.ModelAnnouncePayload(null, null, 1.0f)
            )
        } catch (e: Exception) {
            VrmMod.logger.debug("Could not send model clear (server may not have mod): {}", e.message)
        }
        return
    }

    val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
    val vroidConfig = VRoidHubConfig.load(configDir.toPath())
    if (!vroidConfig.isAvailable) return

    CompletableFuture.supplyAsync {
        // Get multiplay license for sharing
        val token = VRoidHubAuth.loadToken(configDir.toPath()) ?: return@supplyAsync null
        val licenseResult = VRoidHubApi.postDownloadLicenseMultiplay(token.accessToken, modelId)
        licenseResult.getOrElse { e ->
            VrmMod.logger.warn("Failed to get multiplay license (server may not support it): {}", e.message)
            null
        }
    }.thenAccept { license ->
        try {
            dev.architectury.networking.NetworkManager.sendToServer(
                net.narazaka.vrmmod.network.ModelAnnouncePayload(
                    vroidHubModelId = modelId,
                    multiplayLicenseId = license?.id,
                    scale = scale,
                )
            )
            VrmMod.logger.info("Announced VRM model to server: {} (license: {})", modelId, license?.id != null)
        } catch (e: Exception) {
            VrmMod.logger.debug("Could not send model announce (server may not have mod): {}", e.message)
        }
    }
}
```

- [ ] **Step 2: import 追加**

`VrmModClient.kt` の import セクションに追加:

```kotlin
import java.util.UUID
```

- [ ] **Step 3: ログイン時の VRoid Hub モデルロード完了後にアナウンス**

`loadVRoidHubModel` メソッドの `thenAccept` ブロック内（177行目付近）を修正。
`VrmPlayerManager.loadLocal(...)` の直後にコールバックでアナウンスを発行する。

既存コード（173-181行）:
```kotlin
}.thenAccept { file ->
    if (file != null) {
        VrmMod.logger.info("VRoid Hub model ready, loading: {}", file.absolutePath)
        Minecraft.getInstance().execute {
            VrmPlayerManager.loadLocal(uuid, file, animDir, animationConfig, useVrmaAnimation)
        }
    } else {
        VrmMod.logger.error("VRoid Hub model download returned null")
    }
```

修正後:
```kotlin
}.thenAccept { file ->
    if (file != null) {
        VrmMod.logger.info("VRoid Hub model ready, loading: {}", file.absolutePath)
        Minecraft.getInstance().execute {
            VrmPlayerManager.loadLocal(uuid, file, animDir, animationConfig, useVrmaAnimation)
            // Announce model to server for multiplayer sync
            val state = VrmPlayerManager.get(uuid)
            if (state != null) {
                announceModel(uuid, modelId, state.cachedScale)
            }
        }
    } else {
        VrmMod.logger.error("VRoid Hub model download returned null")
    }
```

- [ ] **Step 4: ワールド退出時に MultiplayModelHandler をリセット**

`CLIENT_PLAYER_QUIT` ハンドラー（89-92行）を修正:

既存:
```kotlin
ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
    VrmMod.logger.info("Unloading all VRM models")
    VrmPlayerManager.clear()
}
```

修正後:
```kotlin
ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { _ ->
    VrmMod.logger.info("Unloading all VRM models")
    VrmPlayerManager.clear()
    net.narazaka.vrmmod.network.MultiplayModelHandler.reset()
}
```

- [ ] **Step 5: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModClient.kt
git commit -m "feat: announce VRM model to server on login and model change"
```

---

### Task 7: サーバー側プレイヤー切断処理

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt`

- [ ] **Step 1: Architectury のサーバーイベントでプレイヤー切断を検知**

`VrmMod.kt` を修正してサーバー側のプレイヤー切断イベントを登録:

```kotlin
package net.narazaka.vrmmod

import net.narazaka.vrmmod.network.VrmModNetwork
import net.narazaka.vrmmod.network.VrmModServer
import dev.architectury.event.events.common.PlayerEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object VrmMod {
    const val MOD_ID = "vrmmod"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        VrmModNetwork.register()

        // Handle player disconnect on server side
        PlayerEvent.PLAYER_QUIT.register { player ->
            val server = player.server ?: return@register
            VrmModServer.handlePlayerDisconnect(player.uuid, server)
        }
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/VrmMod.kt
git commit -m "feat: handle player disconnect for multiplayer model cleanup"
```

---

### Task 8: cachedScale のオーバーライド対応

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmState.kt`
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/network/MultiplayModelHandler.kt`

- [ ] **Step 1: VrmState の cachedScale を var に変更**

`VrmState.kt` の27行目:

既存:
```kotlin
val cachedScale: Float = 0.9f,
```

修正後:
```kotlin
var cachedScale: Float = 0.9f,
```

- [ ] **Step 2: MultiplayModelHandler でスケールオーバーライドを適用**

`MultiplayModelHandler.kt` の `thenAccept` ブロック内のスケール適用部分を修正。

既存の「Override cachedScale」コメント周辺:
```kotlin
// Override cachedScale with the value from the model owner
val state = VrmPlayerManager.get(payload.playerUUID)
if (state != null && scale != state.cachedScale) {
    VrmMod.logger.info(
        "Applying multiplay scale {} (local was {}) for player {}",
        scale, state.cachedScale, payload.playerUUID,
    )
}
```

修正後:
```kotlin
// Override cachedScale with the value from the model owner
val state = VrmPlayerManager.get(payload.playerUUID)
if (state != null) {
    VrmMod.logger.info(
        "Applying multiplay scale {} (local was {}) for player {}",
        scale, state.cachedScale, payload.playerUUID,
    )
    state.cachedScale = scale
}
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: コミット**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/render/VrmState.kt common/src/main/kotlin/net/narazaka/vrmmod/network/MultiplayModelHandler.kt
git commit -m "feat: allow cachedScale override for multiplayer sync"
```

---

### Task 9: 翻訳キー追加

**Files:**
- Modify: `common/src/main/resources/assets/vrmmod/lang/en_us.json`（存在する場合）

- [ ] **Step 1: 翻訳ファイルの確認と更新**

`common/src/main/resources/assets/vrmmod/lang/` を確認。存在する場合は以下のキーを追加:

```json
{
  "vrmmod.multiplayer.login_required": "Other players have VRM avatars. Log in to VRoid Hub in VRM Mod settings to see them."
}
```

日本語（`ja_jp.json`）がある場合:

```json
{
  "vrmmod.multiplayer.login_required": "他のプレイヤーがVRMアバターを使用しています。VRM Mod設定でVRoid Hubにログインすると表示できます。"
}
```

翻訳ファイルが存在しない場合はこのタスクをスキップ（`Component.translatable` はキーをそのまま表示するだけ）。

- [ ] **Step 2: コミット**

```
git add common/src/main/resources/assets/vrmmod/lang/
git commit -m "feat: add multiplayer login notification translations"
```

---

### Task 10: 統合テスト（手動）

- [ ] **Step 1: フルビルド確認**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL（Fabric + NeoForge 両方）

- [ ] **Step 2: シングルプレイ動作確認**

1. Fabric クライアントを起動
2. シングルプレイワールドに参加
3. VRM モデルが従来通り表示されることを確認
4. ログに `Registering VRM mod network packets` が出力されることを確認
5. パケット送信エラーが出ないこと（サーバーにmodがあるため受信される）

- [ ] **Step 3: マルチプレイ動作確認（mod入りサーバー）**

1. Fabric サーバーを起動（mod入り）
2. 2台のクライアントで接続
3. クライアントAが VRoid Hub モデルを設定
4. クライアントBに VRM が表示されることを確認
5. クライアントAがログアウト → クライアントBで VRM が解放されることを確認

- [ ] **Step 4: グレースフルデグラデーション確認**

1. バニラサーバーを起動（mod なし）
2. mod入りクライアントで接続
3. エラーなく従来通り動作することを確認
4. ログに `Could not send model announce (server may not have mod)` 程度のdebugログが出ること

- [ ] **Step 5: コミット（最終確認後）**

```
git commit --allow-empty -m "test: verify multiplayer VRoid Hub sync integration"
```
