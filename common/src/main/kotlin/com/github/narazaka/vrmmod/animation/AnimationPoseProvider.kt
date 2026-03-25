package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A [PoseProvider] that selects and plays animation clips based on Minecraft player state.
 *
 * When the active clip changes, the previous pose is cross-faded into the
 * new clip over [transitionDuration] seconds using slerp (rotation) and
 * lerp (translation).
 */
class AnimationPoseProvider(
    private val clips: Map<String, AnimationClip>,
) : PoseProvider {

    override val isAbsoluteRotation: Boolean get() = true

    var modelHipsHeight: Float = 0f
    var enableHeadTracking: Boolean = true

    /** Duration of cross-fade between clips, in seconds. */
    var transitionDuration: Float = 0.25f

    // Current clip state
    private var currentClipName = ""
    private var currentTime = 0f
    private var lastTimeNano = 0L

    // Cross-fade state
    private var prevPose: BonePoseMap = emptyMap()
    private var transitionElapsed = 0f
    private var isTransitioning = false

    override fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap {
        val now = System.nanoTime()
        val deltaTime = if (lastTimeNano == 0L) {
            1f / 60f
        } else {
            ((now - lastTimeNano) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        }
        lastTimeNano = now

        val targetClipName = selectClip(context)

        // Detect clip change -> start cross-fade
        if (targetClipName != currentClipName) {
            // Snapshot current pose as the "from" pose for blending
            val currentClip = clips[currentClipName]
            if (currentClip != null) {
                prevPose = currentClip.sample(currentTime)
                scaleHipsTranslation(prevPose, currentClip)?.let { prevPose = it }
            }
            currentClipName = targetClipName
            currentTime = 0f
            transitionElapsed = 0f
            isTransitioning = true
        }

        currentTime += deltaTime

        // Sample current clip
        val clip = clips[currentClipName] ?: return emptyMap()
        var poses = clip.sample(currentTime)
        poses = scaleHipsTranslation(poses, clip) ?: poses

        // Cross-fade blending
        if (isTransitioning) {
            transitionElapsed += deltaTime
            val t = (transitionElapsed / transitionDuration).coerceIn(0f, 1f)

            if (t < 1f) {
                poses = blendPoses(prevPose, poses, t)
            } else {
                isTransitioning = false
                prevPose = emptyMap()
            }
        }

        // Head tracking
        if (enableHeadTracking) {
            poses = applyHeadTracking(poses, context)
        }

        return poses
    }

    /**
     * Blends two BonePoseMaps. [t]=0 returns [from], [t]=1 returns [to].
     */
    private fun blendPoses(from: BonePoseMap, to: BonePoseMap, t: Float): BonePoseMap {
        val allBones = from.keys + to.keys
        val result = mutableMapOf<HumanBone, BonePose>()

        for (bone in allBones) {
            val fromPose = from[bone]
            val toPose = to[bone]

            val blendedRotation: Quaternionf
            val blendedTranslation: Vector3f

            if (fromPose != null && toPose != null) {
                // Both present: slerp rotation, lerp translation
                blendedRotation = Quaternionf(fromPose.rotation).slerp(toPose.rotation, t)
                blendedTranslation = Vector3f(fromPose.translation).lerp(toPose.translation, t)
            } else if (toPose != null) {
                // Only target: fade in from identity
                blendedRotation = Quaternionf().slerp(toPose.rotation, t)
                blendedTranslation = Vector3f().lerp(toPose.translation, t)
            } else if (fromPose != null) {
                // Only source: fade out to identity
                blendedRotation = Quaternionf(fromPose.rotation).slerp(Quaternionf(), t)
                blendedTranslation = Vector3f(fromPose.translation).lerp(Vector3f(), t)
            } else {
                continue
            }

            result[bone] = BonePose(
                translation = blendedTranslation,
                rotation = blendedRotation,
            )
        }

        return result
    }

    private fun scaleHipsTranslation(poses: BonePoseMap, clip: AnimationClip): BonePoseMap? {
        val animHipsHeight = clip.restHipsHeight
        if (animHipsHeight <= 0f || modelHipsHeight <= 0f) return null
        val ratio = modelHipsHeight / animHipsHeight
        if (ratio == 1f) return null

        val hipsPose = poses[HumanBone.HIPS] ?: return null
        val t = hipsPose.translation
        if (t.x == 0f && t.y == 0f && t.z == 0f) return null

        val scaledTranslation = Vector3f(t).mul(ratio)
        return poses + (HumanBone.HIPS to hipsPose.copy(translation = scaledTranslation))
    }

    private fun applyHeadTracking(poses: BonePoseMap, context: PoseContext): BonePoseMap {
        val yawRad = Math.toRadians(context.headYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(context.headPitch.toDouble()).toFloat()
        if (yawRad == 0f && pitchRad == 0f) return poses

        val lookAtRot = Quaternionf().rotateY(yawRad).rotateX(pitchRad)

        val existingHead = poses[HumanBone.HEAD]
        val baseRot = existingHead?.rotation ?: Quaternionf()
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
        return if (clips.containsKey(name)) name else clips.keys.firstOrNull() ?: ""
    }
}
