package net.narazaka.vrmmod.client

import net.narazaka.vrmmod.VrmMod
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

/**
 * Configuration for the VRM mod.
 *
 * Loaded from `config/vrmmod.json`. If the file does not exist,
 * default values are used.
 */
/**
 * First-person view mode.
 * - "vanilla": MC default hands, no VRM in first person
 * - "vrm_mc_camera": VRM body with head removed, MC default camera position
 * - "vrm_vrm_camera": VRM body with head removed, camera at VRM eye height (future)
 */
enum class FirstPersonMode {
    VANILLA,
    VRM_MC_CAMERA,
    VRM_VRM_CAMERA,
}

data class VrmModConfig(
    val localModelPath: String? = null,
    /** Directory containing .vrma animation files. Null to use built-in procedural animation. */
    val animationDir: String? = null,
    /** Set to false to use procedural (VanillaPoseProvider) animation instead of vrma files. */
    val useVrmaAnimation: Boolean = true,
    /** First-person view mode. */
    val firstPersonMode: FirstPersonMode = FirstPersonMode.VRM_MC_CAMERA,
    /** Selected VRoid Hub model ID. Null means no VRoid Hub model selected. */
    val vroidHubModelId: String? = null,
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

        /**
         * Saves the given configuration to `vrmmod.json` in the given config directory.
         *
         * @param configDir the Minecraft config directory
         * @param config the configuration to save
         */
        fun save(configDir: File, config: VrmModConfig) {
            val configFile = File(configDir, "vrmmod.json")
            try {
                configFile.parentFile.mkdirs()
                configFile.writeText(gson.toJson(config))
                VrmMod.logger.info("Saved config to {}", configFile.absolutePath)
            } catch (e: Exception) {
                VrmMod.logger.error("Failed to save config to {}", configFile.absolutePath, e)
            }
        }
    }
}
