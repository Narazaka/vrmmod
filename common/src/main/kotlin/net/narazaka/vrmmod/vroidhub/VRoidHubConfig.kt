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

        private val logger get() = try {
            net.narazaka.vrmmod.VrmMod.logger
        } catch (_: Exception) {
            org.slf4j.LoggerFactory.getLogger(VRoidHubConfig::class.java)
        }

        fun load(configDir: Path): VRoidHubConfig {
            val file = configDir.resolve("vrmmod-vroidhub-secrets.json").toFile()
            if (file.exists()) {
                try {
                    val loaded = gson.fromJson(file.readText(), VRoidHubConfig::class.java)
                    if (loaded != null && loaded.isAvailable) {
                        return loaded
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to load VRoid Hub config", e)
                }
            }
            return VRoidHubSecrets.defaultConfig()
        }
    }
}
