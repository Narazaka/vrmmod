package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
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
        // Vanilla MC uses 1.4 but MC's box model has short legs.
        // VRM has realistic proportions so a smaller amplitude looks natural.
        val swing = cos(ctx.limbSwing * 0.6662f) * 0.5f * ctx.limbSwingAmount

        // Upper legs swing forward/back
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(swing),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-swing),
        )

        // Lower legs: bend knee when leg is forward (positive X rotation = forward)
        // When upper leg swings forward, lower leg bends back slightly (natural walk)
        val rightKnee = max(0f, swing) * 0.5f
        val leftKnee = max(0f, -swing) * 0.5f
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
        val restAngle = Math.toRadians(75.0).toFloat()

        // Arms swing opposite to legs, smaller amplitude than legs
        val swing = cos(ctx.limbSwing * 0.6662f + Math.PI.toFloat()) * 0.3f * ctx.limbSwingAmount

        // Parent space: X = forward/back swing, Z = arm hang down
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(-swing).rotateZ(restAngle),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(swing).rotateZ(-restAngle),
        )

        // Lower arms: slight natural bend at elbow (~15 degrees)
        val elbowBend = Math.toRadians(15.0).toFloat()
        poses[HumanBone.RIGHT_LOWER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(elbowBend),
        )
        poses[HumanBone.LEFT_LOWER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(elbowBend),
        )
    }

    // ---- Attack swing ----

    private fun applySwingAttack(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val existing = poses[HumanBone.RIGHT_UPPER_ARM]
        val baseRot = existing?.rotation ?: Quaternionf()
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf(baseRot).rotateX(-1.2f),
        )
        // Bend elbow more during attack
        poses[HumanBone.RIGHT_LOWER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(Math.toRadians(45.0).toFloat()),
        )
    }

    // ---- Sneaking ----

    private fun applySneaking(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Lean spine forward
        val leanRad = Math.toRadians(20.0).toFloat()
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

        // Lower hips to reduce floating appearance
        poses[HumanBone.HIPS] = BonePose(
            translation = Vector3f(0f, -0.04f, 0f),
        )

        // Bend upper legs forward and lower legs back for crouching
        val hipBend = Math.toRadians(10.0).toFloat()
        val kneeBend = Math.toRadians(20.0).toFloat()

        val existingRightLeg = poses[HumanBone.RIGHT_UPPER_LEG]
        val existingLeftLeg = poses[HumanBone.LEFT_UPPER_LEG]
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf(existingRightLeg?.rotation ?: Quaternionf()).rotateX(hipBend),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf(existingLeftLeg?.rotation ?: Quaternionf()).rotateX(hipBend),
        )
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )
    }

    // ---- Swimming ----

    private fun applySwimming(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        val hipPitch = Math.toRadians(80.0).toFloat()
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
        val hipPitch = Math.toRadians(70.0).toFloat()
        poses[HumanBone.HIPS] = BonePose(
            rotation = Quaternionf().rotateX(hipPitch),
        )

        // Arms spread
        val armSpread = Math.toRadians(30.0).toFloat()
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(-armSpread),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(armSpread),
        )

        // Legs slightly back
        val legBack = Math.toRadians(15.0).toFloat()
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
        val legSpread = Math.toRadians(30.0).toFloat()
        val legPitch = Math.toRadians(-40.0).toFloat()

        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(-legSpread),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(legSpread),
        )

        val kneeBend = Math.toRadians(50.0).toFloat()
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )

        // Arms resting at sides
        val restAngle = Math.toRadians(75.0).toFloat()
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(restAngle),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(-restAngle),
        )
    }
}
