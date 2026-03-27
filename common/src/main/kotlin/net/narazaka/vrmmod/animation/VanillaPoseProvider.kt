package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.HumanBone
import net.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Maps Minecraft player state to VRM humanoid bone poses.
 *
 * The animation logic mirrors vanilla [PlayerModel.setupAnim] behaviour,
 * producing rotation values that drive VRM bones through the [PoseProvider] interface.
 */
class VanillaPoseProvider : PoseProvider {

    companion object {
        private val RAD_15 = Math.toRadians(15.0).toFloat()
        private val RAD_20 = Math.toRadians(20.0).toFloat()
        private val RAD_25 = Math.toRadians(25.0).toFloat()
        private val RAD_30 = Math.toRadians(30.0).toFloat()
        private val RAD_35 = Math.toRadians(35.0).toFloat()
        private val RAD_40 = Math.toRadians(40.0).toFloat()
        private val RAD_45 = Math.toRadians(45.0).toFloat()
        private val RAD_50 = Math.toRadians(50.0).toFloat()
        private val RAD_70 = Math.toRadians(70.0).toFloat()
        private val RAD_75 = Math.toRadians(75.0).toFloat()
        private val RAD_80 = Math.toRadians(80.0).toFloat()
    }

    override fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap {
        val poses = mutableMapOf<HumanBone, BonePose>()

        applyHead(poses, context)

        when {
            context.isFallFlying -> applyElytra(poses, context)
            context.isSwimming -> applySwimming(poses, context)
            context.isRiding -> applyRiding(poses, context)
            else -> {
                applyLegs(poses, context)
                applyArms(poses, context)
                if (context.isSneaking) applySneaking(poses, context)
            }
        }

        if (context.isSwinging) applySwingAttack(poses, context)

        return poses
    }

    // ---- Head ----

    private fun applyHead(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val yawRad = Math.toRadians(ctx.headYaw.toDouble()).toFloat()
        val pitchRad = Math.toRadians(ctx.headPitch.toDouble()).toFloat()

        poses[HumanBone.HEAD] = BonePose(
            rotation = Quaternionf().rotateY(yawRad).rotateX(pitchRad),
        )
    }

    // ---- Legs (walking) ----

    private fun applyLegs(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Larger amplitude when sprinting
        val amplitude = if (ctx.isSprinting) 0.8f else 0.5f
        val swing = cos(ctx.limbSwing * 0.6662f) * amplitude * ctx.limbSwingAmount

        // Upper legs swing forward/back
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(swing),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-swing),
        )

        // Lower legs: bend knee when leg is forward
        val kneeMultiplier = if (ctx.isSprinting) 0.8f else 0.5f
        val rightKnee = max(0f, swing) * kneeMultiplier
        val leftKnee = max(0f, -swing) * kneeMultiplier
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(rightKnee),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(leftKnee),
        )
    }

    // ---- Arms (idle rest + walking swing) ----

    private fun applyArms(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // VRM T-pose has arms horizontal. Rotate down ~75 degrees to a natural rest pose.
        val restAngle = RAD_75

        // Arms swing opposite to legs; larger amplitude when sprinting
        val armAmplitude = if (ctx.isSprinting) 0.5f else 0.3f
        val swing = cos(ctx.limbSwing * 0.6662f + Math.PI.toFloat()) * armAmplitude * ctx.limbSwingAmount

        // Parent space: X = forward/back swing, Z = arm hang down
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(-swing).rotateZ(restAngle),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(swing).rotateZ(-restAngle),
        )

        // Lower arms: natural bend at elbow, more when sprinting
        val elbowBend = if (ctx.isSprinting) RAD_35 else RAD_15
        poses[HumanBone.RIGHT_LOWER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(elbowBend),
        )
        poses[HumanBone.LEFT_LOWER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(elbowBend),
        )
    }

    // ---- Attack swing ----

    private fun applySwingAttack(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Determine which arm is swinging:
        // isOffHandSwing XOR isLeftHanded → true means left arm swings
        val isLeftArm = ctx.isOffHandSwing != ctx.isLeftHanded
        val upperArm = if (isLeftArm) HumanBone.LEFT_UPPER_ARM else HumanBone.RIGHT_UPPER_ARM
        val lowerArm = if (isLeftArm) HumanBone.LEFT_LOWER_ARM else HumanBone.RIGHT_LOWER_ARM

        val existing = poses[upperArm]
        val baseRot = existing?.rotation ?: Quaternionf()
        poses[upperArm] = BonePose(
            rotation = Quaternionf(baseRot).rotateX(-1.2f),
        )
        poses[lowerArm] = BonePose(
            rotation = Quaternionf().rotateX(RAD_45),
        )
    }

    // ---- Sneaking ----

    private fun applySneaking(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Lean spine forward
        val leanRad = RAD_15
        poses[HumanBone.SPINE] = BonePose(
            rotation = Quaternionf().rotateX(leanRad),
        )

        // Compensate head so it stays looking forward
        val headPose = poses[HumanBone.HEAD]
        if (headPose != null) {
            poses[HumanBone.HEAD] = headPose.copy(
                rotation = Quaternionf(headPose.rotation).rotateX(-leanRad),
            )
        }

        // Crouching posture: upper leg forward (negative X), lower leg bends at knee.
        val upperLegForward = -RAD_20
        val lowerLegBack = RAD_25

        val existingRightLeg = poses[HumanBone.RIGHT_UPPER_LEG]
        val existingLeftLeg = poses[HumanBone.LEFT_UPPER_LEG]
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf(existingRightLeg?.rotation ?: Quaternionf()).rotateX(upperLegForward),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf(existingLeftLeg?.rotation ?: Quaternionf()).rotateX(upperLegForward),
        )
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(lowerLegBack),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(lowerLegBack),
        )
    }

    // ---- Swimming ----

    private fun applySwimming(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val hipPitch = RAD_80
        poses[HumanBone.HIPS] = BonePose(
            rotation = Quaternionf().rotateX(hipPitch),
        )

        // Leg kick cycle
        val kick = sin(ctx.limbSwing * 0.6662f) * 0.5f * ctx.limbSwingAmount
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kick),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-kick),
        )

        // Arms forward stroke
        val armStroke = sin(ctx.limbSwing * 0.6662f) * 0.6f * ctx.limbSwingAmount
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(-armStroke - 0.5f),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(armStroke - 0.5f),
        )

        // Head compensates for hip pitch
        applyHead(poses, ctx)
        val headPose = poses[HumanBone.HEAD]!!
        poses[HumanBone.HEAD] = headPose.copy(
            rotation = Quaternionf(headPose.rotation).rotateX(-hipPitch),
        )
    }

    // ---- Elytra ----

    private fun applyElytra(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val hipPitch = RAD_70
        poses[HumanBone.HIPS] = BonePose(
            rotation = Quaternionf().rotateX(hipPitch),
        )

        // Arms spread
        val armSpread = RAD_30
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(-armSpread),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(armSpread),
        )

        // Legs slightly back
        val legBack = RAD_15
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-legBack),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-legBack),
        )

        // Head compensates for hip pitch
        applyHead(poses, ctx)
        val headPose = poses[HumanBone.HEAD]!!
        poses[HumanBone.HEAD] = headPose.copy(
            rotation = Quaternionf(headPose.rotation).rotateX(-hipPitch),
        )
    }

    // ---- Riding ----

    private fun applyRiding(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val legSpread = RAD_30
        val legPitch = -RAD_40

        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(-legSpread),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(legSpread),
        )

        val kneeBend = RAD_50
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )

        // Arms resting at sides
        val restAngle = RAD_75
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(restAngle),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(-restAngle),
        )
    }
}
