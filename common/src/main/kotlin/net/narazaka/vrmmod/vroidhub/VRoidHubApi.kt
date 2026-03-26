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

    fun getAccountCharacterModels(accessToken: String, count: Int = 100): Result<List<CharacterModel>> {
        return get("/api/account/character_models?count=$count", accessToken).map {
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
