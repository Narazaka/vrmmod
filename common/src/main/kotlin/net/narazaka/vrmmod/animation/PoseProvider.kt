package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.HumanBone
import net.narazaka.vrmmod.vrm.VrmSkeleton
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

    // --- Movement ---
    /** Walk cycle position (distance walked). */
    val limbSwing: Float,
    /** Walk cycle amplitude (0 = idle, 1 = full stride). */
    val limbSwingAmount: Float,
    /** Movement speed value. >0.9 typically means sprinting. */
    val speedValue: Float = 0f,
    val isSneaking: Boolean,
    val isSwimming: Boolean,
    /** Blend amount for swimming pose (0-1 transition). */
    val swimAmount: Float = 0f,
    /** True when using an elytra. */
    val isFallFlying: Boolean,
    val isRiding: Boolean,
    val isInWater: Boolean = false,
    val isOnGround: Boolean = true,

    // --- Actions ---
    /** True when the arm swing animation is playing (left-click: attack/break). */
    val isSwinging: Boolean,
    /** Arm swing animation progress (0-1). Covers all arm swing actions. */
    val attackTime: Float = 0f,
    /** True when using an item (eating, drawing bow, blocking with shield, etc.) */
    val isUsingItem: Boolean = false,
    /** Ticks spent using the current item. */
    val ticksUsingItem: Int = 0,
    /** True during trident spin attack (riptide). */
    val isAutoSpinAttack: Boolean = false,
    /** Death animation progress. >0 when dying. */
    val deathTime: Float = 0f,

    // --- Head / body ---
    /** Head yaw relative to body, in degrees. */
    val headYaw: Float,
    /** Head pitch, in degrees. */
    val headPitch: Float,
    /** Body yaw in world space, in degrees. Used for model Y rotation. */
    val bodyYaw: Float = 0f,

    // --- Position ---
    /** Entity world position (absolute, not camera-relative). */
    val entityX: Float = 0f,
    val entityY: Float = 0f,
    val entityZ: Float = 0f,

    // --- Damage ---
    /** MC hurt time (>0 when recently damaged). */
    val hurtTime: Float = 0f,
) {
    /** Convenience: true if sprinting (derived from speedValue). */
    val isSprinting: Boolean get() = speedValue > 0.9f
}

/**
 * Computes a [BonePoseMap] for a VRM skeleton given the current animation context.
 */
interface PoseProvider {
    fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap

    /** If true, rotations are absolute local rotations (replace rest rotation).
     *  If false, rotations are deltas applied before rest rotation. */
    val isAbsoluteRotation: Boolean get() = false
}
