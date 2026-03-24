package com.github.narazaka.vrmmod.vrm

/**
 * VRM 1.0 spring bone physics (stub).
 */
data class VrmSpringBone(
    val springs: List<Spring> = emptyList(),
    val colliders: List<Collider> = emptyList(),
) {
    data class Spring(
        val name: String = "",
        val jointNodeIndices: List<Int> = emptyList(),
        val colliderGroupIndices: List<Int> = emptyList(),
    )

    data class Collider(
        val nodeIndex: Int = -1,
        val shapeType: String = "sphere",
    )
}
