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
        if (versionId.isNotEmpty() && entry.versionId != versionId) return null
        val file = File(entry.filePath)
        return if (file.exists()) file else null
    }

    /**
     * Returns the cached model file regardless of version.
     * Used when loading at world join without making API calls.
     */
    fun getCachedModelAnyVersion(gameDir: Path, modelId: String): File? {
        return getCachedModel(gameDir, modelId, "")
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
