package net.narazaka.vrmmod.vrm

/**
 * Override type for expression interactions.
 * Matches VRM 1.0 spec: none / block / blend.
 */
enum class ExpressionOverrideType {
    NONE,
    BLOCK,
    BLEND;

    companion object {
        fun fromString(value: String?): ExpressionOverrideType = when (value) {
            "block" -> BLOCK
            "blend" -> BLEND
            else -> NONE
        }
    }
}

/**
 * VRM 1.0 expression (blend shape group).
 *
 * Override fields follow three-vrm's VRMExpression:
 * - overrideBlink/overrideLookAt/overrideMouth specify how this expression
 *   suppresses blink/lookAt/mouth category expressions when active.
 * - isBinary: interpret weight > 0.5 as 1.0, otherwise 0.0.
 */
data class VrmExpression(
    val name: String,
    val preset: String = "",
    val morphTargetBinds: List<MorphTargetBind> = emptyList(),
    val isBinary: Boolean = false,
    val overrideBlink: ExpressionOverrideType = ExpressionOverrideType.NONE,
    val overrideLookAt: ExpressionOverrideType = ExpressionOverrideType.NONE,
    val overrideMouth: ExpressionOverrideType = ExpressionOverrideType.NONE,
) {
    fun outputWeight(weight: Float): Float =
        if (isBinary) (if (weight > 0.5f) 1.0f else 0.0f) else weight

    fun overrideAmount(type: ExpressionOverrideType, weight: Float): Float = when (type) {
        ExpressionOverrideType.BLOCK -> if (outputWeight(weight) > 0f) 1.0f else 0.0f
        ExpressionOverrideType.BLEND -> outputWeight(weight)
        ExpressionOverrideType.NONE -> 0.0f
    }
}

data class MorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)
