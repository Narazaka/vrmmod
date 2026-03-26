package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.HumanBone
import net.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A [PoseProvider] that selects and plays animation clips based on Minecraft player state.
 *
 * When the active clip changes, the previous pose is cross-faded into the
 * new clip over a configurable duration using slerp (rotation) and
 * lerp (translation).
 */
class AnimationPoseProvider(
    private val clips: Map<String, AnimationClip>,
    private val config: AnimationConfig = AnimationConfig(),
) : PoseProvider {

    override val isAbsoluteRotation: Boolean get() = true

    var modelHipsHeight: Float = 0f
    var enableHeadTracking: Boolean = config.headTracking

    // Current clip state
    private var currentClipName = ""
    private var currentStateName = ""
    private var currentTime = 0f
    private var lastTimeNano = 0L

    // Movement direction tracking
    private var prevEntityX = 0f
    private var prevEntityZ = 0f
    private var moveDirInitialized = false

    // Attack state
    private var wasSwinging = false

    // Cross-fade state
    private var prevPose: BonePoseMap = emptyMap()
    private var transitionElapsed = 0f
    private var isTransitioning = false
    private var activeTransitionDuration = 0.25f

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
            val prevStateName = currentStateName
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
            activeTransitionDuration = config.getTransitionDuration(prevStateName, currentStateName)
        }

        currentTime += deltaTime

        // Sample current clip
        val clip = clips[currentClipName] ?: return emptyMap()
        var poses = clip.sample(currentTime)
        poses = scaleHipsTranslation(poses, clip) ?: poses

        // Cross-fade blending
        if (isTransitioning) {
            transitionElapsed += deltaTime
            val t = (transitionElapsed / activeTransitionDuration).coerceIn(0f, 1f)

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

    /**
     * Determines the movement direction relative to body facing.
     * Returns "forward", "backward", "left", or "right".
     */
    private fun getMovementDirection(context: PoseContext): String {
        if (!moveDirInitialized) {
            prevEntityX = context.entityX
            prevEntityZ = context.entityZ
            moveDirInitialized = true
            return "forward"
        }

        val dx = context.entityX - prevEntityX
        val dz = context.entityZ - prevEntityZ
        prevEntityX = context.entityX
        prevEntityZ = context.entityZ

        if (dx * dx + dz * dz < 0.0001f) return "forward"

        // Movement angle in world space (radians)
        val moveAngle = kotlin.math.atan2(-dx.toDouble(), dz.toDouble()).toFloat()
        // Body facing angle (degrees -> radians)
        val bodyAngle = Math.toRadians(context.bodyYaw.toDouble()).toFloat()
        // Relative angle: how much the movement deviates from body facing
        var relAngle = moveAngle - bodyAngle
        // Normalize to -PI..PI
        while (relAngle > Math.PI) relAngle -= (2 * Math.PI).toFloat()
        while (relAngle < -Math.PI) relAngle += (2 * Math.PI).toFloat()

        val deg = Math.toDegrees(relAngle.toDouble()).toFloat()
        return when {
            deg > 135f || deg < -135f -> "backward"
            deg > 45f -> "left"
            deg < -45f -> "right"
            else -> "forward"
        }
    }

    private var wasHurt = false

    private fun selectClip(context: PoseContext): String {
        // Death takes absolute priority
        if (context.deathTime > 0f) {
            return tryState("death") ?: selectMovementClip(context)
        }

        // Spin attack (trident riptide) takes priority
        if (context.isAutoSpinAttack) {
            return tryState("spinAttack") ?: selectMovementClip(context)
        }

        // Hurt reaction — triggered on rising edge of hurtTime
        val isHurt = context.hurtTime > 0f
        if (isHurt && !wasHurt) {
            wasHurt = true
            val clip = tryState("hurt")
            if (clip != null) return clip
        }
        if (!isHurt) wasHurt = false

        // Attack swing (left-click) — triggered on rising edge
        if (context.isSwinging && !wasSwinging) {
            wasSwinging = true
            val clip = tryState("attack")
            if (clip != null) return clip
        }
        if (!context.isSwinging) wasSwinging = false

        // If currently in a one-shot action animation, keep playing until it finishes
        if (currentStateName in setOf("attack", "hurt", "useItem", "spinAttack", "death")) {
            val actionClip = clips[currentClipName]
            if (actionClip != null && currentTime < actionClip.duration) {
                return currentClipName
            }
        }

        // Item use (right-click hold: eating, bow, shield, etc.)
        if (context.isUsingItem) {
            return tryState("useItem") ?: selectMovementClip(context)
        }

        return selectMovementClip(context)
    }

    /** Try to activate a state, returning the clip name if available, null otherwise. */
    private fun tryState(stateName: String): String? {
        val clipName = config.states[stateName]?.clip ?: return null
        if (clipName.isBlank() || !clips.containsKey(clipName)) return null
        currentStateName = stateName
        return clipName
    }

    private fun selectMovementClip(context: PoseContext): String {
        val moveDir = getMovementDirection(context)
        val isMoving = context.limbSwingAmount > config.walkThreshold

        val stateName = when {
            context.isFallFlying -> "elytra"
            context.isSwimming && isMoving -> "swim"
            context.isSwimming -> "swimIdle"
            context.isRiding -> "ride"
            !context.isOnGround -> "jump"
            context.isSneaking && isMoving -> "sneakWalk"
            context.isSneaking -> "sneak"
            context.limbSwingAmount > config.runThreshold -> "run"
            isMoving && moveDir == "backward" -> "walkBackward"
            isMoving && moveDir == "left" -> "walkLeft"
            isMoving && moveDir == "right" -> "walkRight"
            isMoving -> "walk"
            else -> "idle"
        }
        currentStateName = stateName
        val clipName = config.states[stateName]?.clip
        if (clipName != null && clips.containsKey(clipName)) return clipName

        // Fallback: directional walk -> walk, directional run -> run
        if (stateName in listOf("walkBackward", "walkLeft", "walkRight")) {
            val walkClip = config.states["walk"]?.clip
            if (walkClip != null && clips.containsKey(walkClip)) return walkClip
        }

        return clips.keys.firstOrNull() ?: ""
    }
}
