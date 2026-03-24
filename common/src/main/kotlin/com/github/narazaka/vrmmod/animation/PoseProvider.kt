package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A pose applied to a single humanoid bone, expressed as
 * translation / rotation / scale deltas relative to the bone's rest pose.
 */
data class BonePose(
    val translation: Vector3f = Vector3f(),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
)

typealias BonePoseMap = Map<HumanBone, BonePose>

/**
 * Snapshot of the player's movement / action state that
 * [PoseProvider] implementations use to compute bone poses.
 */
data class PoseContext(
    val partialTick: Float,
    /** Walk cycle position (distance walked). */
    val limbSwing: Float,
    /** Walk cycle amplitude (0 = idle, 1 = full stride). */
    val limbSwingAmount: Float,
    /** True when playing the attack-swing animation. */
    val isSwinging: Boolean,
    val isSneaking: Boolean,
    val isSprinting: Boolean,
    val isSwimming: Boolean,
    /** True when using an elytra. */
    val isFallFlying: Boolean,
    val isRiding: Boolean,
    /** Head yaw relative to body, in degrees. */
    val headYaw: Float,
    /** Head pitch, in degrees. */
    val headPitch: Float,
    /** Body yaw in world space, in degrees. Used for model Y rotation. */
    val bodyYaw: Float = 0f,
)

/**
 * Computes a [BonePoseMap] for a VRM skeleton given the current animation context.
 */
interface PoseProvider {
    fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap
}
