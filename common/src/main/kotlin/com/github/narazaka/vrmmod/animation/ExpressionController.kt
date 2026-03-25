package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.VrmExpression

/**
 * Controls VRM expression (blend shape) weights and computes
 * per-primitive morph target weights for rendering.
 */
class ExpressionController {

    /** Current weight (0-1) for each expression by name. */
    private val weights = mutableMapOf<String, Float>()

    /**
     * Sets the weight for an expression, clamped to [0, 1].
     */
    fun setWeight(expressionName: String, weight: Float) {
        weights[expressionName] = weight.coerceIn(0f, 1f)
    }

    /**
     * Returns the current weight for an expression, defaulting to 0.
     */
    fun getWeight(expressionName: String): Float {
        return weights[expressionName] ?: 0f
    }

    /**
     * Computes the effective morph target weights from all active expressions.
     *
     * @param expressions the list of VRM expressions from the model
     * @return a map of (meshIndex, morphTargetIndex) to accumulated weight
     */
    fun computeMorphWeights(expressions: List<VrmExpression>): Map<Pair<Int, Int>, Float> {
        val result = mutableMapOf<Pair<Int, Int>, Float>()
        for ((name, weight) in weights) {
            if (weight <= 0f) continue
            val expression = expressions.find { it.name == name || it.preset == name } ?: continue
            for (bind in expression.morphTargetBinds) {
                val key = Pair(bind.meshIndex, bind.morphTargetIndex)
                result[key] = (result[key] ?: 0f) + bind.weight * weight
            }
        }
        return result
    }
}
