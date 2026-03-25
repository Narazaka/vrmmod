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
    val mtoonMaterials: List<VrmMtoonMaterial> = emptyList(),
    /** Per-mesh first person annotation. Keys are mesh indices. */
    val firstPersonAnnotations: Map<Int, FirstPersonType> = emptyMap(),
    /** LookAt offsetFromHeadBone (VRM 1.0 spec). Used for eye/camera position. */
    val lookAtOffsetFromHeadBone: org.joml.Vector3f = org.joml.Vector3f(0f, 0.06f, 0f),
)

/**
 * VRM firstPerson mesh annotation type.
 */
enum class FirstPersonType {
    AUTO,
    BOTH,
    FIRST_PERSON_ONLY,
    THIRD_PERSON_ONLY,
}
