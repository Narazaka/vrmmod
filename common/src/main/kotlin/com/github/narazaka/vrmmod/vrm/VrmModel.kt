package com.github.narazaka.vrmmod.vrm

/**
 * Top-level container for a parsed VRM 1.0 model.
 */
data class VrmModel(
    val meta: VrmMeta = VrmMeta(name = ""),
    val humanoid: VrmHumanoid = VrmHumanoid(),
    val meshes: List<VrmMesh> = emptyList(),
    val skeleton: VrmSkeleton = VrmSkeleton(),
    val textures: List<VrmTexture> = emptyList(),
    val expressions: List<VrmExpression> = emptyList(),
    val springBone: VrmSpringBone = VrmSpringBone(),
)
