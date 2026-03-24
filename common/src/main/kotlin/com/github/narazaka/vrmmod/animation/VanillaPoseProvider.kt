package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import kotlin.math.cos
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

        // VRM head: Y-axis = yaw (left-right, negated for VRM coordinate), X-axis = pitch
        poses[HumanBone.HEAD] = BonePose(
            rotation = Quaternionf().rotateY(-yawRad).rotateX(pitchRad),
        )
    }

    // ---- Legs (walking) ----

    private fun applyLegs(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Vanilla: cos(limbSwing * 0.6662) * 1.4 * limbSwingAmount
        val swing = cos(ctx.limbSwing * 0.6662f) * 1.4f * ctx.limbSwingAmount

        // Right leg swings forward when left goes back
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(swing),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-swing),
        )
    }

    // ---- Arms (idle rest + walking swing) ----

    private fun applyArms(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // VRM T-pose has arms horizontal. Rotate down ~75 degrees to a natural rest pose.
        val restAngle = Math.toRadians(75.0).toFloat()

        // Arms swing opposite to legs, smaller amplitude
        val swing = cos(ctx.limbSwing * 0.6662f + Math.PI.toFloat()) * 0.8f * ctx.limbSwingAmount

        // Arms: first apply walk swing (X rotation in shoulder space),
        // then rotate Z to bring arm down. Order matters: X swing first, then Z rest.
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(restAngle).rotateX(-swing),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateZ(-restAngle).rotateX(swing),
        )
    }

    // ---- Attack swing ----

    private fun applySwingAttack(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Swing the right arm down (negative X rotation)
        val existing = poses[HumanBone.RIGHT_UPPER_ARM]
        val baseRot = existing?.rotation ?: Quaternionf()
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf(baseRot).rotateX(-1.2f),
        )
    }

    // ---- Sneaking ----

    private fun applySneaking(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Lean spine forward ~25 degrees
        val leanRad = Math.toRadians(25.0).toFloat()
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
        // Bend knees slightly for crouching
        val kneeBend = Math.toRadians(30.0).toFloat()
        val hipBend = Math.toRadians(20.0).toFloat()

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
        // Rotate hips to horizontal (~80 degrees forward pitch)
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

        // Head compensates for hip pitch to look forward
        applyHead(poses, ctx)
        val headPose = poses[HumanBone.HEAD]!!
        poses[HumanBone.HEAD] = headPose.copy(
            rotation = Quaternionf(headPose.rotation).rotateX(-hipPitch),
        )
    }

    // ---- Elytra ----

    private fun applyElytra(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
        // Hips pitched forward
        val hipPitch = Math.toRadians(70.0).toFloat()
        poses[HumanBone.HIPS] = BonePose(
            rotation = Quaternionf().rotateX(hipPitch),
        )

        // Arms spread (rotated up/outward in Z)
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
        // Legs spread and bent forward
        val legSpread = Math.toRadians(30.0).toFloat()
        val legPitch = Math.toRadians(-40.0).toFloat()

        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(-legSpread),
        )
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legPitch).rotateZ(legSpread),
        )

        // Lower legs bent back
        val kneeBend = Math.toRadians(50.0).toFloat()
        poses[HumanBone.RIGHT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )
        poses[HumanBone.LEFT_LOWER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(kneeBend),
        )

        // Arms rest at sides
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(Math.toRadians(-20.0).toFloat()),
        )
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(Math.toRadians(-20.0).toFloat()),
        )
    }
}
