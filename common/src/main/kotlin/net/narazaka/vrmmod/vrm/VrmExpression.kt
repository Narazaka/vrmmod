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
    /** Effective output weight considering isBinary. */
    val outputWeight: Float
        get() = if (isBinary) (if (_weight > 0.5f) 1.0f else 0.0f) else _weight

    /** Override amount for blink category (0.0 = no override, 1.0 = full block). */
    val overrideBlinkAmount: Float
        get() = overrideAmount(overrideBlink)

    /** Override amount for lookAt category. */
    val overrideLookAtAmount: Float
        get() = overrideAmount(overrideLookAt)

    /** Override amount for mouth category. */
    val overrideMouthAmount: Float
        get() = overrideAmount(overrideMouth)

    // Mutable weight set externally by ExpressionController
    @Transient
    private var _weight: Float = 0f

    var weight: Float
        get() = _weight
        set(value) { _weight = value.coerceIn(0f, 1f) }

    private fun overrideAmount(type: ExpressionOverrideType): Float = when (type) {
        ExpressionOverrideType.BLOCK -> if (outputWeight > 0f) 1.0f else 0.0f
        ExpressionOverrideType.BLEND -> outputWeight
        ExpressionOverrideType.NONE -> 0.0f
    }
}

data class MorphTargetBind(
    val nodeIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)
