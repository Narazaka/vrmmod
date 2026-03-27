package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.animation.ExpressionController
import net.narazaka.vrmmod.animation.PoseProvider
import net.narazaka.vrmmod.animation.VanillaPoseProvider
import net.narazaka.vrmmod.physics.SpringBoneSimulator
import net.narazaka.vrmmod.vrm.VrmModel
import net.minecraft.resources.ResourceLocation

/**
 * Holds the parsed VRM model and its registered texture locations
 * for a single player.
 */
data class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
    val poseProvider: PoseProvider = VanillaPoseProvider(),
    val springBoneSimulator: SpringBoneSimulator? = null,
    val expressionController: ExpressionController = ExpressionController(),
    val animationConfig: AnimationConfig = AnimationConfig(),
    /** VRM model's eye height in MC blocks at rest pose (scaled). */
    val eyeHeight: Float = 1.62f,
) {
    /**
     * Eye position offset from entity feet in MC blocks, updated each frame.
     * Includes HEAD bone rotation effects but not walk animation translation.
     * Used by CameraMixin for VRM_VRM_CAMERA mode.
     */
    @Volatile
    var currentEyeOffset: org.joml.Vector3f = org.joml.Vector3f(0f, eyeHeight, 0f)
    @Volatile
    var rightHandMatrix: org.joml.Matrix4f? = null
    @Volatile
    var leftHandMatrix: org.joml.Matrix4f? = null
}
