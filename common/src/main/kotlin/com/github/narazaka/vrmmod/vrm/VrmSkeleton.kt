package com.github.narazaka.vrmmod.vrm

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * The skeleton (node tree + skin data) of a VRM model.
 */
data class VrmSkeleton(
    val nodes: List<VrmNode> = emptyList(),
    val rootNodeIndices: List<Int> = emptyList(),
    val jointNodeIndices: List<Int> = emptyList(),
    val inverseBindMatrices: List<Matrix4f> = emptyList(),
)

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
