package com.github.narazaka.vrmmod.vrm

import org.joml.Vector3f

/**
 * Parsed VRMC_materials_mtoon data for a single glTF material.
 */
data class VrmMtoonMaterial(
    val materialIndex: Int,
    val shadeColorFactor: Vector3f = Vector3f(0f, 0f, 0f),
    val shadingShiftFactor: Float = 0f,
    val shadingToonyFactor: Float = 0.9f,
)
