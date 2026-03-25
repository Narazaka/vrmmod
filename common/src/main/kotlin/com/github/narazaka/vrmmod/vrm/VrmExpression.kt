package com.github.narazaka.vrmmod.vrm

/**
 * VRM 1.0 expression (blend shape group).
 */
data class VrmExpression(
    val name: String,
    val preset: String = "",
    val morphTargetBinds: List<MorphTargetBind> = emptyList(),
)

data class MorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)
