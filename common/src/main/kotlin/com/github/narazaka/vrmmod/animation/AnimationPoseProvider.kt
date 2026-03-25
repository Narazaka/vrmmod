package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A [PoseProvider] that selects and plays animation clips based on Minecraft player state.
 *
 * Clips are selected by mapping [PoseContext] flags to animation clip names.
 * Time is tracked internally using [System.nanoTime] for frame-rate-independent playback.
 *
 * Hips translation is scaled by the ratio of the VRM model's hips height to the
 * vrma animation's rest hips height, so animations transfer correctly across
 * models of different proportions.
 */
class AnimationPoseProvider(
    private val clips: Map<String, AnimationClip>,
) : PoseProvider {

    override val isAbsoluteRotation: Boolean get() = true

    /**
     * The VRM model's hips Y position (rest pose, world space).
     * Set externally after construction so that hips translation can be
     * scaled by model/animation height ratio.
     */
    var modelHipsHeight: Float = 0f

    /** Whether to apply head tracking (look-at) on top of animation. */
    var enableHeadTracking: Boolean = true

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
        val poses = clip.sample(currentTime)

        // Scale hips translation by model/animation height ratio
        val animHipsHeight = clip.restHipsHeight
        if (animHipsHeight > 0f && modelHipsHeight > 0f) {
            val ratio = modelHipsHeight / animHipsHeight
            if (ratio != 1f) {
                val hipsPose = poses[HumanBone.HIPS]
                if (hipsPose != null) {
                    val t = hipsPose.translation
                    if (t.x != 0f || t.y != 0f || t.z != 0f) {
                        val scaledTranslation = Vector3f(t).mul(ratio)
                        return poses + (HumanBone.HIPS to hipsPose.copy(translation = scaledTranslation))
                    }
                }
            }
        }

        // Apply head tracking on top of animation
        val result = if (enableHeadTracking) {
            applyHeadTracking(poses, context)
        } else {
            poses
        }

        return result
    }

    /**
     * Blends head yaw/pitch from MC mouse look into the animation's HEAD rotation.
     * The animation provides the base head pose; we multiply the look-at rotation on top.
     */
    private fun applyHeadTracking(poses: BonePoseMap, context: PoseContext): BonePoseMap {
        val yawRad = Math.toRadians(context.headYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(context.headPitch.toDouble()).toFloat()
        if (yawRad == 0f && pitchRad == 0f) return poses

        val lookAtRot = Quaternionf().rotateY(yawRad).rotateX(pitchRad)

        val existingHead = poses[HumanBone.HEAD]
        val baseRot = existingHead?.rotation ?: Quaternionf()
        // Multiply look-at on top of animation rotation
        val combinedRot = Quaternionf(baseRot).mul(lookAtRot)

        return poses + (HumanBone.HEAD to BonePose(
            translation = existingHead?.translation ?: Vector3f(),
            rotation = combinedRot,
        ))
    }

    private fun selectClip(context: PoseContext): String {
        val name = when {
            context.isFallFlying -> "Jump_Idle"
            context.isSwimming -> "Crawling"
            context.isRiding -> "Sitting_Idle"
            !context.isOnGround -> "Jump_Idle"
            context.isSneaking -> "Sneaking"
            context.limbSwingAmount > 0.5f -> "Running_A"
            context.limbSwingAmount > 0.01f -> "Walking_A"
            else -> "Idle_A"
        }
        // Return the name if a clip exists, otherwise fall back to any available clip
        return if (clips.containsKey(name)) name else clips.keys.firstOrNull() ?: ""
    }
}
