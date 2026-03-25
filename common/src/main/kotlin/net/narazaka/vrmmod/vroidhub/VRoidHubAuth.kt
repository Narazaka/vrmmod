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
