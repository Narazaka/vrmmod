package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.animation.ExpressionController
import com.github.narazaka.vrmmod.animation.PoseProvider
import com.github.narazaka.vrmmod.animation.VanillaPoseProvider
import com.github.narazaka.vrmmod.physics.SpringBoneSimulator
import com.github.narazaka.vrmmod.vrm.VrmModel
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
}
