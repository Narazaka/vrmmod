package com.github.narazaka.vrmmod.client

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Minimal mod configuration.
 * Stored as JSON in config/vrmmod.json.
 */
data class VrmModConfig(
    val localModelPath: String? = null,
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: File): VrmModConfig {
            val configFile = File(configDir, "vrmmod.json")
            return if (configFile.exists()) {
                try {
                    gson.fromJson(configFile.readText(), VrmModConfig::class.java)
                } catch (_: Exception) {
                    VrmModConfig()
                }
            } else {
                val default = VrmModConfig()
                configFile.parentFile.mkdirs()
                configFile.writeText(gson.toJson(default))
                default
            }
        }
    }
}
