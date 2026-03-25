# VRoid Hub Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** VRoid Hub API を使って、ユーザーが VRoid Hub 上のアバターを Minecraft mod で利用できるようにする

**Architecture:** 外部ファイル注入による認証情報管理 + OAuth 2.0 PKCE + oob リダイレクト。Cloth Config 設定画面にログインボタンを追加し、モデル選択はカスタム Minecraft Screen で実装。ダウンロード済みモデルはローカルキャッシュに保存。

**Tech Stack:** Kotlin, Gson, java.net.http.HttpClient, Minecraft Screen API, Cloth Config API

**Spec:** `docs/superpowers/specs/2026-03-26-vroid-hub-integration-design.md`

---

## File Structure

| ファイル | 責務 |
|---------|------|
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubConfig.kt` | 認証情報ファイル読み込み (clientId, clientSecret) |
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubAuth.kt` | OAuth フロー (PKCE, トークン交換, リフレッシュ, 永続化) |
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubApi.kt` | HTTP API クライアント |
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubModelCache.kt` | ダウンロードキャッシュ管理 |
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubModels.kt` | API レスポンスのデータクラス |
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/client/VRoidHubModelSelectScreen.kt` | モデル選択カスタム Screen |
| **Modify:** `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmConfigScreen.kt` | VRoid Hub ログイン/選択 UI 追加 |
| **Modify:** `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModClient.kt` | VRoid Hub モデルの自動ロード |
| **Modify:** `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModConfig.kt` | 選択中モデル ID の保存 |
| **Create:** `common/src/test/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubAuthTest.kt` | PKCE 生成テスト |

---

## Task 1: VRoidHubConfig — 認証情報ファイル読み込み

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubConfig.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.narazaka.vrmmod.vroidhub

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Path

data class VRoidHubConfig(
    val clientId: String = "",
    val clientSecret: String = "",
) {
    val isAvailable: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: Path): VRoidHubConfig {
            val file = configDir.resolve("vrmmod-vroidhub.json").toFile()
            if (!file.exists()) return VRoidHubConfig()
            return try {
                gson.fromJson(file.readText(), VRoidHubConfig::class.java) ?: VRoidHubConfig()
            } catch (e: Exception) {
                net.narazaka.vrmmod.VrmMod.logger.warn("Failed to load VRoid Hub config", e)
                VRoidHubConfig()
            }
        }
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew :common:compileKotlin`

- [ ] **Step 3: コミット**

---

## Task 2: VRoidHubModels — API レスポンスデータクラス

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubModels.kt`

- [ ] **Step 1: 実装**

API レスポンスの最小限のデータクラス。Hearts API / Account API / Download License API のレスポンスをパースするのに必要な分だけ定義。

```kotlin
package net.narazaka.vrmmod.vroidhub

data class VRoidHubResponse<T>(
    val data: T? = null,
    val error: VRoidHubError? = null,
)

data class VRoidHubError(
    val code: String? = null,
    val message: String? = null,
)

data class CharacterModel(
    val id: String = "",
    val name: String? = null,
    val is_downloadable: Boolean = false,
    val character: CharacterInfo? = null,
    val license: CharacterModelLicense? = null,
    val latest_character_model_version: CharacterModelVersion? = null,
    val portrait_image: PortraitImage? = null,
)

data class CharacterInfo(
    val name: String = "",
    val user: UserInfo? = null,
)

data class UserInfo(
    val id: String = "",
    val name: String = "",
)

data class CharacterModelLicense(
    val characterization_allowed_user: String = "default",
    val violent_expression: String = "default",
    val sexual_expression: String = "default",
    val corporate_commercial_use: String = "default",
    val personal_commercial_use: String = "default",
    val modification: String = "default",
    val redistribution: String = "default",
    val credit: String = "default",
)

data class CharacterModelVersion(
    val id: String = "",
    val spec_version: String? = null,
)

data class PortraitImage(
    val sq150: ImageInfo? = null,
    val sq300: ImageInfo? = null,
)

data class ImageInfo(
    val url: String = "",
)

data class DownloadLicense(
    val id: String = "",
    val character_model_id: String = "",
)

data class TokenResponse(
    val access_token: String = "",
    val token_type: String = "",
    val expires_in: Int = 0,
    val refresh_token: String = "",
)

data class AccountInfo(
    val user_detail: UserDetail? = null,
)

data class UserDetail(
    val user: UserInfo? = null,
)
```

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 3: VRoidHubAuth — OAuth フロー

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubAuth.kt`
- Create: `common/src/test/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubAuthTest.kt`

- [ ] **Step 1: PKCE テスト作成**

```kotlin
package net.narazaka.vrmmod.vroidhub

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VRoidHubAuthTest {
    @Test
    fun `code verifier has valid length and characters`() {
        val verifier = VRoidHubAuth.generateCodeVerifier()
        assertTrue(verifier.length in 43..128)
        assertTrue(verifier.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~" })
    }

    @Test
    fun `code challenge is base64url of sha256`() {
        val verifier = "test_verifier_string_that_is_long_enough_for_pkce"
        val challenge = VRoidHubAuth.generateCodeChallenge(verifier)
        assertFalse(challenge.contains('+'))
        assertFalse(challenge.contains('/'))
        assertFalse(challenge.contains('='))
        assertTrue(challenge.isNotEmpty())
    }

    @Test
    fun `authorize URL contains required parameters`() {
        val config = VRoidHubConfig(clientId = "test_id", clientSecret = "test_secret")
        val (url, _) = VRoidHubAuth.buildAuthorizeUrl(config)
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=test_id"))
        assertTrue(url.contains("redirect_uri=urn"))
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state="))
    }
}
```

- [ ] **Step 2: テスト失敗確認**

- [ ] **Step 3: VRoidHubAuth 実装**

```kotlin
package net.narazaka.vrmmod.vroidhub

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

data class AuthSession(
    val codeVerifier: String,
    val state: String,
)

data class SavedToken(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresAt: Long = 0,
) {
    val isExpired: Boolean get() = System.currentTimeMillis() / 1000 >= expiresAt
}

object VRoidHubAuth {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private const val REDIRECT_URI = "urn:ietf:wg:oauth:2.0:oob"
    private const val BASE_URL = "https://hub.vroid.com"

    fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
            .replace("[^A-Za-z0-9\\-._~]".toRegex(), "")
            .take(96)
    }

    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun buildAuthorizeUrl(config: VRoidHubConfig): Pair<String, AuthSession> {
        val verifier = generateCodeVerifier()
        val challenge = generateCodeChallenge(verifier)
        val state = generateCodeVerifier().take(32)

        val url = "$BASE_URL/oauth/authorize" +
            "?response_type=code" +
            "&client_id=${enc(config.clientId)}" +
            "&redirect_uri=${enc(REDIRECT_URI)}" +
            "&scope=default" +
            "&state=${enc(state)}" +
            "&code_challenge=${enc(challenge)}" +
            "&code_challenge_method=S256"

        return url to AuthSession(codeVerifier = verifier, state = state)
    }

    fun exchangeToken(config: VRoidHubConfig, code: String, session: AuthSession): Result<TokenResponse> {
        return postToken(mapOf(
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
            "redirect_uri" to REDIRECT_URI,
            "grant_type" to "authorization_code",
            "code" to code,
            "code_verifier" to session.codeVerifier,
        ))
    }

    fun refreshToken(config: VRoidHubConfig, refreshToken: String): Result<TokenResponse> {
        return postToken(mapOf(
            "client_id" to config.clientId,
            "client_secret" to config.clientSecret,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
        ))
    }

    fun revokeToken(config: VRoidHubConfig, accessToken: String): Result<Unit> {
        return try {
            val body = mapOf(
                "client_id" to config.clientId,
                "client_secret" to config.clientSecret,
                "token" to accessToken,
            )
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/oauth/revoke"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Api-Version", "11")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(body)))
                .build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun saveToken(configDir: Path, token: TokenResponse) {
        val saved = SavedToken(
            accessToken = token.access_token,
            refreshToken = token.refresh_token,
            expiresAt = System.currentTimeMillis() / 1000 + token.expires_in,
        )
        val file = configDir.resolve("vrmmod-vroidhub-token.json").toFile()
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(saved))
    }

    fun loadToken(configDir: Path): SavedToken? {
        val file = configDir.resolve("vrmmod-vroidhub-token.json").toFile()
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), SavedToken::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteToken(configDir: Path) {
        configDir.resolve("vrmmod-vroidhub-token.json").toFile().delete()
    }

    private fun postToken(params: Map<String, String>): Result<TokenResponse> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/oauth/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Api-Version", "11")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(params)))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                Result.success(gson.fromJson(response.body(), TokenResponse::class.java))
            } else {
                val error = try { gson.fromJson(response.body(), VRoidHubError::class.java) } catch (_: Exception) { null }
                Result.failure(RuntimeException("Token request failed: ${response.statusCode()} ${error?.message ?: response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun formEncode(params: Map<String, String>): String =
        params.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }

    private fun enc(s: String): String = URLEncoder.encode(s, Charsets.UTF_8)
}
```

- [ ] **Step 4: テスト成功確認**

- [ ] **Step 5: コミット**

---

## Task 4: VRoidHubApi — API クライアント

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubApi.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.narazaka.vrmmod.vroidhub

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

object VRoidHubApi {
    private val gson = Gson()
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
    private const val BASE_URL = "https://hub.vroid.com"

    fun getAccount(accessToken: String): Result<AccountInfo> {
        return get("/api/account", accessToken).map {
            val type = object : TypeToken<VRoidHubResponse<AccountInfo>>() {}.type
            val resp: VRoidHubResponse<AccountInfo> = gson.fromJson(it, type)
            resp.data ?: throw RuntimeException("No data in response")
        }
    }

    fun getHearts(accessToken: String, count: Int = 100): Result<List<CharacterModel>> {
        return get("/api/hearts?count=$count", accessToken).map {
            val type = object : TypeToken<VRoidHubResponse<List<CharacterModel>>>() {}.type
            val resp: VRoidHubResponse<List<CharacterModel>> = gson.fromJson(it, type)
            resp.data ?: emptyList()
        }
    }

    fun postDownloadLicense(accessToken: String, characterModelId: String): Result<DownloadLicense> {
        val body = gson.toJson(mapOf("character_model_id" to characterModelId))
        return post("/api/download_licenses", accessToken, body).map {
            val type = object : TypeToken<VRoidHubResponse<DownloadLicense>>() {}.type
            val resp: VRoidHubResponse<DownloadLicense> = gson.fromJson(it, type)
            resp.data ?: throw RuntimeException("No data in response")
        }
    }

    fun getDownloadUrl(accessToken: String, licenseId: String): Result<String> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL/api/download_licenses/$licenseId/download"))
                .header("Authorization", "Bearer $accessToken")
                .header("X-Api-Version", "11")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 302) {
                val location = response.headers().firstValue("Location")
                    .orElseThrow { RuntimeException("No Location header in 302 response") }
                Result.success(location)
            } else {
                Result.failure(RuntimeException("Expected 302, got ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun downloadVrm(url: String): Result<ByteArray> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() == 200) {
                Result.success(response.body())
            } else {
                Result.failure(RuntimeException("Download failed: ${response.statusCode()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun get(path: String, accessToken: String): Result<String> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL$path"))
                .header("Authorization", "Bearer $accessToken")
                .header("X-Api-Version", "11")
                .GET()
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                Result.success(response.body())
            } else {
                Result.failure(RuntimeException("API error: ${response.statusCode()} ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun post(path: String, accessToken: String, body: String): Result<String> {
        return try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$BASE_URL$path"))
                .header("Authorization", "Bearer $accessToken")
                .header("X-Api-Version", "11")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()
            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                Result.success(response.body())
            } else {
                Result.failure(RuntimeException("API error: ${response.statusCode()} ${response.body()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 5: VRoidHubModelCache — ダウンロードキャッシュ

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vroidhub/VRoidHubModelCache.kt`

- [ ] **Step 1: 実装**

```kotlin
package net.narazaka.vrmmod.vroidhub

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.Path

data class CacheEntry(
    val versionId: String = "",
    val filePath: String = "",
    val downloadedAt: String = "",
)

data class CacheMetadata(
    val models: MutableMap<String, CacheEntry> = mutableMapOf(),
)

object VRoidHubModelCache {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getCacheDir(gameDir: Path): File {
        val dir = gameDir.resolve("cache/vrmmod/vroidhub").toFile()
        dir.mkdirs()
        return dir
    }

    fun getCachedModel(gameDir: Path, modelId: String, versionId: String): File? {
        val metadata = loadMetadata(gameDir)
        val entry = metadata.models[modelId] ?: return null
        if (entry.versionId != versionId) return null
        val file = File(entry.filePath)
        return if (file.exists()) file else null
    }

    fun cacheModel(gameDir: Path, modelId: String, versionId: String, data: ByteArray): File {
        val cacheDir = getCacheDir(gameDir)
        val file = File(cacheDir, "${modelId}_${versionId}.vrm")
        file.writeBytes(data)

        val metadata = loadMetadata(gameDir)
        // Remove old version if exists
        metadata.models[modelId]?.let { old ->
            if (old.filePath != file.absolutePath) File(old.filePath).delete()
        }
        metadata.models[modelId] = CacheEntry(
            versionId = versionId,
            filePath = file.absolutePath,
            downloadedAt = java.time.Instant.now().toString(),
        )
        saveMetadata(gameDir, metadata)
        return file
    }

    private fun loadMetadata(gameDir: Path): CacheMetadata {
        val file = getCacheDir(gameDir).resolve("cache.json")
        if (!file.exists()) return CacheMetadata()
        return try {
            gson.fromJson(file.readText(), CacheMetadata::class.java) ?: CacheMetadata()
        } catch (e: Exception) {
            CacheMetadata()
        }
    }

    private fun saveMetadata(gameDir: Path, metadata: CacheMetadata) {
        val file = getCacheDir(gameDir).resolve("cache.json")
        file.writeText(gson.toJson(metadata))
    }
}
```

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 6: VrmModConfig — 選択モデル ID の保存

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModConfig.kt`

- [ ] **Step 1: VrmModConfig に vroidHubModelId フィールドを追加**

```kotlin
data class VrmModConfig(
    val localModelPath: String? = null,
    val animationDir: String? = null,
    val useVrmaAnimation: Boolean = true,
    val firstPersonMode: FirstPersonMode = FirstPersonMode.VRM_MC_CAMERA,
    val vroidHubModelId: String? = null,  // 追加
)
```

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 7: VRoidHubModelSelectScreen — モデル選択カスタム Screen

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/client/VRoidHubModelSelectScreen.kt`

- [ ] **Step 1: 実装**

Minecraft の `Screen` API でモデル選択画面を実装。左側にスクロールリスト、右側にライセンス詳細、下部にアクションボタン。

主要クラス:
- `VRoidHubModelSelectScreen(parent: Screen, models: List<CharacterModel>, onSelect: (CharacterModel) -> Unit)`
- 内部クラス `ModelListWidget extends ObjectSelectionList` — モデル一覧のスクロールリスト

ライセンス表示: `CharacterModelLicense` の各フィールドを日本語/英語でテキスト描画。

ボタン: 「このモデルを使用する（ライセンスに同意）」は選択中モデルがある場合のみ有効化。

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 8: VrmConfigScreen — VRoid Hub UI 統合

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmConfigScreen.kt`

- [ ] **Step 1: VRoid Hub セクションを追加**

`VRoidHubConfig.isAvailable` の場合のみ表示:

未ログイン時:
- 「VRoid Hub にログイン」ボタン → ブラウザ起動 + code テキストフィールド + 「認証」ボタン

ログイン済み時:
- ユーザー名テキスト
- 「VRoid Hub モデルを選択」ボタン → `VRoidHubModelSelectScreen` を開く
- 選択中モデル名テキスト
- 「ログアウト」ボタン

- [ ] **Step 2: ビルド確認**

- [ ] **Step 3: コミット**

---

## Task 9: VrmModClient — VRoid Hub モデルの自動ロード

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModClient.kt`

- [ ] **Step 1: ワールド参加時に VRoid Hub モデルをロード**

`CLIENT_PLAYER_JOIN` イベントで:
1. `localModelPath` が設定されていればそちらを優先（既存動作）
2. `localModelPath` がなく `vroidHubModelId` があれば:
   - キャッシュにあればキャッシュからロード
   - なければ VRoid Hub API でダウンロード → キャッシュ → ロード
3. トークンの自動リフレッシュ

- [ ] **Step 2: ビルド & 動作確認**

- [ ] **Step 3: コミット**

---

## Task 10: 統合テスト & ドキュメント

- [ ] **Step 1: README に VRoid Hub 連携の使い方を追記**

- [ ] **Step 2: CURRENT_STATUS.md を更新**

- [ ] **Step 3: フルビルド & 動作確認**

- [ ] **Step 4: コミット**
