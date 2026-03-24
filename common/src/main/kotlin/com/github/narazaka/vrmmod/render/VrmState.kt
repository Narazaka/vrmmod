package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.animation.PoseProvider
import com.github.narazaka.vrmmod.animation.VanillaPoseProvider
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
)
