package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.animation.AnimationPoseProvider
import com.github.narazaka.vrmmod.animation.VrmaParser
import com.github.narazaka.vrmmod.physics.SpringBoneSimulator
import com.github.narazaka.vrmmod.vrm.VrmParser
import net.minecraft.client.Minecraft
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages per-player VRM model loading, storage, and cleanup.
 *
 * VRM files are parsed asynchronously, then texture registration and
 * state creation happen on the render thread.
 */
object VrmPlayerManager {

    private val states = ConcurrentHashMap<UUID, VrmState>()
    private val loading = ConcurrentHashMap<UUID, CompletableFuture<*>>()

    /**
     * Returns the [VrmState] for the given player UUID, or null if not loaded.
     */
    fun get(playerUUID: UUID): VrmState? {
        return states[playerUUID]
    }

    /**
     * Asynchronously loads a VRM file for the given player.
     *
     * Parsing happens off-thread; texture registration and state storage
     * happen on the Minecraft render thread.
     *
     * @param playerUUID the UUID of the player
     * @param file the VRM file to load
     */
    fun loadLocal(playerUUID: UUID, file: File, animationDir: File? = null) {
        // Skip if already loaded or currently loading
        if (states.containsKey(playerUUID) || loading.containsKey(playerUUID)) {
            return
        }

        val future = CompletableFuture.supplyAsync {
            VrmMod.logger.info("Parsing VRM file for player {}: {}", playerUUID, file.name)
            val model = VrmParser.parse(file.inputStream())

            // Load animation clips from the animation directory
            val clips = if (animationDir != null && animationDir.isDirectory) {
                loadAnimationClips(animationDir)
            } else {
                emptyMap()
            }

            Pair(model, clips)
        }.thenAccept { (model, clips) ->
            // Schedule texture registration on the render thread
            Minecraft.getInstance().execute {
                try {
                    val textureLocations = VrmTextureManager.registerTextures(
                        playerUUID,
                        model.textures,
                    )
                    val simulator = if (model.springBone.springs.isNotEmpty()) {
                        SpringBoneSimulator(model.springBone, model.skeleton)
                    } else {
                        null
                    }
                    val poseProvider = if (clips.isNotEmpty()) {
                        VrmMod.logger.info(
                            "Using animation pose provider with {} clips: {}",
                            clips.size,
                            clips.keys.joinToString(),
                        )
                        AnimationPoseProvider(clips)
                    } else {
                        com.github.narazaka.vrmmod.animation.VanillaPoseProvider()
                    }
                    val state = VrmState(
                        model = model,
                        textureLocations = textureLocations,
                        poseProvider = poseProvider,
                        springBoneSimulator = simulator,
                    )
                    states[playerUUID] = state
                    VrmMod.logger.info(
                        "VRM model loaded for player {}: {} ({} textures)",
                        playerUUID,
                        model.meta.name,
                        textureLocations.size,
                    )
                } catch (e: Exception) {
                    VrmMod.logger.error("Failed to register VRM textures for player {}", playerUUID, e)
                } finally {
                    loading.remove(playerUUID)
                }
            }
        }.exceptionally { throwable ->
            VrmMod.logger.error("Failed to parse VRM file for player {}", playerUUID, throwable)
            loading.remove(playerUUID)
            null
        }

        loading[playerUUID] = future
    }

    /**
     * Loads all .vrma files from the given directory and returns a map of clip name to clip.
     */
    private fun loadAnimationClips(dir: File): Map<String, com.github.narazaka.vrmmod.animation.AnimationClip> {
        val clips = mutableMapOf<String, com.github.narazaka.vrmmod.animation.AnimationClip>()
        val vrmaFiles = dir.listFiles { f -> f.extension == "vrma" } ?: return clips

        for (vrmaFile in vrmaFiles) {
            try {
                VrmMod.logger.info("Loading animation file: {}", vrmaFile.name)
                val parsed = VrmaParser.parse(vrmaFile.inputStream())
                for (clip in parsed) {
                    clips[clip.name] = clip
                    VrmMod.logger.info("  Loaded clip '{}' (duration={}s, {} bones)", clip.name, clip.duration, clip.tracks.size)
                }
            } catch (e: Exception) {
                VrmMod.logger.error("Failed to parse animation file: {}", vrmaFile.name, e)
            }
        }
        return clips
    }

    /**
     * Unloads the VRM model and textures for the given player.
     */
    fun unload(playerUUID: UUID) {
        loading.remove(playerUUID)?.cancel(false)
        states.remove(playerUUID)
        VrmTextureManager.unregisterTextures(playerUUID)
    }

    /**
     * Unloads all VRM models and textures.
     */
    fun clear() {
        for (uuid in loading.keys.toList()) {
            loading.remove(uuid)?.cancel(false)
        }
        states.clear()
        VrmTextureManager.clear()
    }
}
