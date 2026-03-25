package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Configuration for the VRM mod.
 *
 * Loaded from `config/vrmmod.json`. If the file does not exist,
 * default values are used.
 */
data class VrmModConfig(
    val localModelPath: String? = null,
    /** Directory containing .vrma animation files. */
    val animationDir: String? = null,
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        /**
         * Loads the mod configuration from `vrmmod.json` in the given config directory.
         *
         * @param configDir the Minecraft config directory
         * @return the loaded configuration, or a default instance if the file does not exist
         */
        fun load(configDir: File): VrmModConfig {
            val configFile = File(configDir, "vrmmod.json")
            if (!configFile.exists()) {
                VrmMod.logger.info("No config file found at {}, creating default", configFile.absolutePath)
                val default = VrmModConfig()
                try {
                    configFile.parentFile.mkdirs()
                    configFile.writeText(gson.toJson(default))
                } catch (e: Exception) {
                    VrmMod.logger.warn("Failed to write default config", e)
                }
                return default
            }
            return try {
                val text = configFile.readText()
                gson.fromJson(text, VrmModConfig::class.java) ?: VrmModConfig()
            } catch (e: Exception) {
                VrmMod.logger.error("Failed to load config from {}", configFile.absolutePath, e)
                VrmModConfig()
            }
        }
    }
}
