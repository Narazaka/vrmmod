package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.VrmExpression
import kotlin.random.Random

/**
 * Controls VRM expression (blend shape) weights and computes
 * per-primitive morph target weights for rendering.
 *
 * Handles automatic blinking, damage reactions, and expression override
 * (blink/lookAt/mouth suppression) following three-vrm's VRMExpressionManager.
 */
class ExpressionController(
    /** Expression weights for damage reaction (expression name -> max weight). */
    private val damageExpressions: Map<String, Float> = mapOf("sad" to 1.0f),
    /** Duration of damage expression fade-out in seconds. */
    private val damageExpressionDuration: Float = 0.5f,
) {

    /** Current weight (0-1) for each expression by name. */
    private val weights = mutableMapOf<String, Float>()

    // --- Auto-blink state ---
    private var blinkTimer = 0f
    private var isBlinking = false
    private var nextBlinkTime = randomBlinkInterval()

    // --- Damage expression state ---
    private var damageTimer = 0f
    private var isDamaged = false
    private var wasHurt = false

    /**
     * Expression names belonging to each override category.
     * Matches three-vrm's VRMExpressionManager defaults.
     */
    val blinkExpressionNames = setOf("blink", "blinkLeft", "blinkRight")
    val lookAtExpressionNames = setOf("lookLeft", "lookRight", "lookUp", "lookDown")
    val mouthExpressionNames = setOf("aa", "ee", "ih", "oh", "ou")

    companion object {
        private const val BLINK_DURATION = 0.3f
        private const val BLINK_INTERVAL_MIN = 2f
        private const val BLINK_INTERVAL_MAX = 4f
        private const val BLINK_EXPRESSION_NAME = "blink"

        private fun randomBlinkInterval(): Float {
            return Random.nextFloat() * (BLINK_INTERVAL_MAX - BLINK_INTERVAL_MIN) + BLINK_INTERVAL_MIN
        }
    }

    fun setWeight(expressionName: String, weight: Float) {
        weights[expressionName] = weight.coerceIn(0f, 1f)
    }

    fun getWeight(expressionName: String): Float {
        return weights[expressionName] ?: 0f
    }

    /**
     * Updates auto-blink and reactive expressions.
     *
     * @param deltaTime elapsed time in seconds since the last update
     * @param hurtTime MC's hurt timer (>0 when recently damaged, counts down to 0)
     */
    fun update(deltaTime: Float, hurtTime: Float = 0f) {
        // --- Auto-blink ---
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
                weights[BLINK_EXPRESSION_NAME] = if (t < 0.5f) t * 2f else (1f - t) * 2f
            }
        }

        // --- Damage expression ---
        val isHurt = hurtTime > 0f
        if (isHurt && !wasHurt) {
            isDamaged = true
            damageTimer = 0f
        }
        wasHurt = isHurt

        if (isDamaged) {
            damageTimer += deltaTime
            val t = if (damageExpressionDuration > 0f) {
                (damageTimer / damageExpressionDuration).coerceIn(0f, 1f)
            } else {
                1f
            }
            // Fade from full weight to 0
            val fade = (1f - t).coerceIn(0f, 1f)
            for ((name, maxWeight) in damageExpressions) {
                weights[name] = maxWeight * fade
            }
            if (t >= 1f) {
                isDamaged = false
                for ((name, _) in damageExpressions) {
                    weights[name] = 0f
                }
            }
        }
    }

    /**
     * Computes the effective morph target weights from all active expressions,
     * applying expression override (blink/lookAt/mouth suppression).
     *
     * Follows three-vrm's VRMExpressionManager.update() algorithm:
     * 1. Set weights on each VrmExpression
     * 2. Calculate weight multipliers from override amounts
     * 3. Apply multipliers to expressions in override categories
     * 4. Accumulate morph target weights
     */
    fun computeMorphWeights(expressions: List<VrmExpression>): Map<Pair<Int, Int>, Float> {
        val exprWeights = expressions.map { expr ->
            val w = (weights[expr.name] ?: weights[expr.preset] ?: 0f).coerceIn(0f, 1f)
            expr to w
        }

        var blinkMultiplier = 1.0f
        var lookAtMultiplier = 1.0f
        var mouthMultiplier = 1.0f

        for ((expr, w) in exprWeights) {
            blinkMultiplier -= expr.overrideAmount(expr.overrideBlink, w)
            lookAtMultiplier -= expr.overrideAmount(expr.overrideLookAt, w)
            mouthMultiplier -= expr.overrideAmount(expr.overrideMouth, w)
        }

        blinkMultiplier = blinkMultiplier.coerceAtLeast(0f)
        lookAtMultiplier = lookAtMultiplier.coerceAtLeast(0f)
        mouthMultiplier = mouthMultiplier.coerceAtLeast(0f)

        val result = mutableMapOf<Pair<Int, Int>, Float>()

        for ((expr, w) in exprWeights) {
            var multiplier = 1.0f
            if (expr.name in blinkExpressionNames) multiplier *= blinkMultiplier
            if (expr.name in lookAtExpressionNames) multiplier *= lookAtMultiplier
            if (expr.name in mouthExpressionNames) multiplier *= mouthMultiplier

            var actualWeight = expr.outputWeight(w) * multiplier
            if (expr.isBinary && actualWeight < 1.0f) actualWeight = 0.0f
            if (actualWeight <= 0f) continue

            for (bind in expr.morphTargetBinds) {
                if (bind.nodeIndex < 0) continue
                val key = Pair(bind.nodeIndex, bind.morphTargetIndex)
                result[key] = (result[key] ?: 0f) + bind.weight * actualWeight
            }
        }

        return result
    }
}
