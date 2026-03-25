package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.VrmSkeleton

/**
 * A [PoseProvider] that selects and plays animation clips based on Minecraft player state.
 *
 * Clips are selected by mapping [PoseContext] flags to animation clip names.
 * Time is tracked internally using [System.nanoTime] for frame-rate-independent playback.
 */
class AnimationPoseProvider(
    private val clips: Map<String, AnimationClip>,
) : PoseProvider {

    private var currentClipName = ""
    private var currentTime = 0f
    private var lastTimeNano = 0L

    override fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap {
        // Compute delta time from system clock
        val now = System.nanoTime()
        val deltaTime = if (lastTimeNano == 0L) {
            1f / 60f // first frame fallback
        } else {
            ((now - lastTimeNano) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        }
        lastTimeNano = now

        // Select clip based on MC state
        val targetClipName = selectClip(context)

        // Reset time if clip changed
        if (targetClipName != currentClipName) {
            currentClipName = targetClipName
            currentTime = 0f
        }

        // Advance time
        currentTime += deltaTime

        // Sample the clip
        val clip = clips[currentClipName] ?: return emptyMap()
        return clip.sample(currentTime)
    }

    private fun selectClip(context: PoseContext): String {
        val name = when {
            context.isFallFlying -> "Jump_Idle"
            context.isSwimming -> "Crawling"
            context.isRiding -> "Sitting_Idle"
            context.isSneaking -> "Sneaking"
            context.limbSwingAmount > 0.5f -> "Running_A"
            context.limbSwingAmount > 0.01f -> "Walking_A"
            else -> "Idle_A"
        }
        // Return the name if a clip exists, otherwise fall back to any available clip
        return if (clips.containsKey(name)) name else clips.keys.firstOrNull() ?: ""
    }
}
