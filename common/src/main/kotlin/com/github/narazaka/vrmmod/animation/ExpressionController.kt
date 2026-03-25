package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.VrmExpression
import kotlin.random.Random

/**
 * Controls VRM expression (blend shape) weights and computes
 * per-primitive morph target weights for rendering.
 *
 * Also handles automatic blinking animation.
 */
class ExpressionController {

    /** Current weight (0-1) for each expression by name. */
    private val weights = mutableMapOf<String, Float>()

    // --- Auto-blink state ---
    private var blinkTimer = 0f
    private var isBlinking = false
    private var nextBlinkTime = randomBlinkInterval()

    companion object {
        /** Duration of a single blink (close + open) in seconds. */
        private const val BLINK_DURATION = 0.3f
        private const val BLINK_INTERVAL_MIN = 2f
        private const val BLINK_INTERVAL_MAX = 4f
        private const val BLINK_EXPRESSION_NAME = "blink"

        private fun randomBlinkInterval(): Float {
            return Random.nextFloat() * (BLINK_INTERVAL_MAX - BLINK_INTERVAL_MIN) + BLINK_INTERVAL_MIN
        }
    }

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
     * Updates the auto-blink timer and sets the blink expression weight.
     *
     * @param deltaTime elapsed time in seconds since the last update
     */
    fun update(deltaTime: Float) {
        blinkTimer += deltaTime
        if (!isBlinking && blinkTimer >= nextBlinkTime) {
            isBlinking = true
            blinkTimer = 0f
        }
        if (isBlinking) {
            val t = blinkTimer / BLINK_DURATION
            if (t >= 1f) {
                isBlinking = false
                weights[BLINK_EXPRESSION_NAME] = 0f
                nextBlinkTime = randomBlinkInterval()
                blinkTimer = 0f
            } else {
                // Triangle wave: 0 -> 1 -> 0
                weights[BLINK_EXPRESSION_NAME] = if (t < 0.5f) t * 2f else (1f - t) * 2f
            }
        }
    }

    /**
     * Computes the effective morph target weights from all active expressions.
     *
     * @param expressions the list of VRM expressions from the model
     * @return a map of (meshIndex, morphTargetIndex) to accumulated weight.
     *   The meshIndex comes from [MorphTargetBind.nodeIndex], which has been
     *   resolved to a mesh index during parsing.
     */
    fun computeMorphWeights(expressions: List<VrmExpression>): Map<Pair<Int, Int>, Float> {
        val result = mutableMapOf<Pair<Int, Int>, Float>()
        for ((name, weight) in weights) {
            if (weight <= 0f) continue
            val expression = expressions.find { it.name == name || it.preset == name } ?: continue
            for (bind in expression.morphTargetBinds) {
                if (bind.nodeIndex < 0) continue
                val key = Pair(bind.nodeIndex, bind.morphTargetIndex)
                result[key] = (result[key] ?: 0f) + bind.weight * weight
            }
        }
        return result
    }
}
