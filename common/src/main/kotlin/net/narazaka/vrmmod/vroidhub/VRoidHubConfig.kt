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
