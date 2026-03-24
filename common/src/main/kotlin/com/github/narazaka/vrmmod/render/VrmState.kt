package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmModel
import net.minecraft.resources.ResourceLocation

/**
 * Holds a loaded VRM model and its registered texture locations,
 * ready for rendering.
 */
data class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
)
