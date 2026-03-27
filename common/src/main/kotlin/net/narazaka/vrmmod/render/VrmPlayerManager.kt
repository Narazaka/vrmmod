package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.animation.AnimationPoseProvider
import net.narazaka.vrmmod.animation.ExpressionController
import net.narazaka.vrmmod.animation.VrmaParser
import net.narazaka.vrmmod.physics.SpringBoneSimulator
import net.narazaka.vrmmod.vrm.VrmParser
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
    fun loadLocal(playerUUID: UUID, file: File, animationDir: File? = null, animationConfig: AnimationConfig = AnimationConfig(), useVrmaAnimation: Boolean = true) {
        // Skip if already loaded or currently loading
        if (states.containsKey(playerUUID) || loading.containsKey(playerUUID)) {
            return
        }

        val future = CompletableFuture.supplyAsync {
            VrmMod.logger.info("Parsing VRM file for player {}: {}", playerUUID, file.name)
            val model = file.inputStream().use { VrmParser.parse(it) }

            // Load animation clips from directory or bundled resources
            val clips = if (!useVrmaAnimation) {
                emptyMap()
            } else if (animationDir != null && animationDir.isDirectory) {
                loadAnimationClips(animationDir)
            } else {
                loadBundledAnimationClips()
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
                    // Compute rest-pose world matrices once
                    val restPoseWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)

                    val poseProvider = if (clips.isNotEmpty()) {
                        VrmMod.logger.info(
                            "Using animation pose provider with {} clips: {}",
                            clips.size,
                            clips.keys.joinToString(),
                        )
                        AnimationPoseProvider(clips, animationConfig).also { provider ->
                            // Set model hips height for translation scaling
                            val hipsBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HIPS]
                            if (hipsBoneNode != null) {
                                val hipsNode = model.skeleton.nodes.getOrNull(hipsBoneNode.nodeIndex)
                                if (hipsNode != null) {
                                    val hipsWorldPos = org.joml.Vector3f()
                                    restPoseWorldMatrices[hipsBoneNode.nodeIndex].getTranslation(hipsWorldPos)
                                    provider.modelHipsHeight = hipsWorldPos.y
                                }
                            }
                        }
                    } else {
                        net.narazaka.vrmmod.animation.VanillaPoseProvider()
                    }
                    val expressionCtrl = ExpressionController(
                        damageExpressions = animationConfig.damageExpression,
                        damageExpressionDuration = animationConfig.damageExpressionDuration,
                    )
                    // Compute scale from world-space hips Y
                    var cachedScale = run {
                        val hipsNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HIPS]
                        if (hipsNode != null && hipsNode.nodeIndex in model.skeleton.nodes.indices) {
                            val hipsWorldPos = org.joml.Vector3f()
                            restPoseWorldMatrices[hipsNode.nodeIndex].getTranslation(hipsWorldPos)
                            if (hipsWorldPos.y > 0f) 1.8f / (hipsWorldPos.y * 2f) else 0.9f
                        } else 0.9f
                    }

                    // Match MC eye height: adjust scale so VRM eye height equals MC's 1.62
                    if (animationConfig.matchMcEyeHeight) {
                        val baseEyeHeight = computeEyeHeight(model, restPoseWorldMatrices, cachedScale)
                        if (baseEyeHeight > 0f) {
                            cachedScale *= 1.62f / baseEyeHeight
                        }
                    }

                    // Apply user avatar scale multiplier
                    cachedScale *= animationConfig.avatarScale

                    // Compute VRM eye height in MC blocks (with final scale)
                    val eyeHeight = computeEyeHeight(model, restPoseWorldMatrices, cachedScale)

                    val state = VrmState(
                        model = model,
                        textureLocations = textureLocations,
                        poseProvider = poseProvider,
                        springBoneSimulator = simulator,
                        expressionController = expressionCtrl,
                        animationConfig = animationConfig,
                        eyeHeight = eyeHeight,
                        restPoseWorldMatrices = restPoseWorldMatrices,
                        cachedScale = cachedScale,
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
    private fun loadAnimationClips(dir: File): Map<String, net.narazaka.vrmmod.animation.AnimationClip> {
        val clips = mutableMapOf<String, net.narazaka.vrmmod.animation.AnimationClip>()
        val vrmaFiles = dir.listFiles { f -> f.extension == "vrma" } ?: return clips

        for (vrmaFile in vrmaFiles) {
            try {
                VrmMod.logger.info("Loading animation file: {}", vrmaFile.name)
                val parsed = vrmaFile.inputStream().use { VrmaParser.parse(it) }
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
     * Computes the VRM model's eye height in MC blocks.
     *
     * Per three-vrm's VRMLookAt.getLookAtWorldPosition():
     *   eyeWorldPos = headBone.worldMatrix * lookAt.offsetFromHeadBone
     *
     * The result is scaled by the same factor as VrmRenderer to convert
     * from VRM model space (meters) to MC blocks.
     */
    private fun computeEyeHeight(
        model: net.narazaka.vrmmod.vrm.VrmModel,
        restPoseWorldMatrices: List<org.joml.Matrix4f>,
        scale: Float,
    ): Float {
        val headBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD]
            ?: return 1.62f

        val headWorldMatrix = restPoseWorldMatrices[headBoneNode.nodeIndex]

        val offset = model.lookAtOffsetFromHeadBone
        val eyePos = org.joml.Vector3f(offset)
        headWorldMatrix.transformPosition(eyePos)

        val eyeHeight = eyePos.y * scale
        VrmMod.logger.info(
            "VRM eye height: {} blocks (eye model Y={}, offset={}, scale={})",
            eyeHeight, eyePos.y, offset, scale,
        )
        return eyeHeight
    }

    /** Bundled animation resource files. */
    private val BUNDLED_ANIMATIONS = listOf("UAL1_Standard.vrma")

    /**
     * Loads animation clips from bundled mod resources.
     */
    private fun loadBundledAnimationClips(): Map<String, net.narazaka.vrmmod.animation.AnimationClip> {
        val clips = mutableMapOf<String, net.narazaka.vrmmod.animation.AnimationClip>()
        for (filename in BUNDLED_ANIMATIONS) {
            val resourcePath = "/assets/${VrmMod.MOD_ID}/animations/$filename"
            try {
                val stream = VrmPlayerManager::class.java.getResourceAsStream(resourcePath)
                if (stream != null) {
                    VrmMod.logger.info("Loading bundled animation: {}", filename)
                    stream.use {
                        val parsed = VrmaParser.parse(it)
                        for (clip in parsed) {
                            clips[clip.name] = clip
                            VrmMod.logger.info("  Loaded clip '{}' (duration={}s, {} bones)", clip.name, clip.duration, clip.tracks.size)
                        }
                    }
                } else {
                    VrmMod.logger.warn("Bundled animation not found: {}", resourcePath)
                }
            } catch (e: Exception) {
                VrmMod.logger.error("Failed to parse bundled animation: {}", filename, e)
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
