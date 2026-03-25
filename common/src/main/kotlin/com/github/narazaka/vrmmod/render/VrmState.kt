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
)
