package net.narazaka.vrmmod.vrm

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A single skin binding: joints and their inverse bind matrices.
 * glTF allows multiple skins (e.g., body and clothing use different joint sets).
 */
data class VrmSkin(
    val jointNodeIndices: List<Int> = emptyList(),
    val inverseBindMatrices: List<Matrix4f> = emptyList(),
)

/**
 * The skeleton (node tree + skin data) of a VRM model.
 */
data class VrmSkeleton(
    val nodes: List<VrmNode> = emptyList(),
    val rootNodeIndices: List<Int> = emptyList(),
    val skins: List<VrmSkin> = emptyList(),
) {
    // Backward-compatible accessors for the primary skin (index 0)
    val jointNodeIndices: List<Int> get() = skins.firstOrNull()?.jointNodeIndices ?: emptyList()
    val inverseBindMatrices: List<Matrix4f> get() = skins.firstOrNull()?.inverseBindMatrices ?: emptyList()
}

/**
 * A single node in the glTF scene graph.
 */
data class VrmNode(
    val name: String = "",
    val translation: Vector3f = Vector3f(0f, 0f, 0f),
    val rotation: Quaternionf = Quaternionf(0f, 0f, 0f, 1f),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
    val childIndices: List<Int> = emptyList(),
    val meshIndex: Int = -1,
)
