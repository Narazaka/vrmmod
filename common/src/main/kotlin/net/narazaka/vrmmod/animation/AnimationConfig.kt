package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.VrmMod
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class AnimationConfig(
    val states: Map<String, StateConfig> = defaultStates(),
    val transitions: Map<String, Map<String, Float>> = defaultTransitions(),
    val headTracking: Boolean = true,
    val walkThreshold: Float = 0.01f,
    val runThreshold: Float = 0.5f,
    /** Expression weights to apply when damaged. Keys are expression names, values are max weights. */
    val damageExpression: Map<String, Float> = mapOf("sad" to 1.0f),
    /** Duration of damage expression fade-out in seconds. */
    val damageExpressionDuration: Float = 0.5f,
) {
    data class StateConfig(
        val clip: String,
        val loop: Boolean = true,
    )

    fun getTransitionDuration(from: String, to: String): Float {
        // Exact match: transitions[from][to]
        transitions[from]?.get(to)?.let { return it }
        // Wildcard from: transitions[from][*]
        transitions[from]?.get("*")?.let { return it }
        // Wildcard to: transitions[*][to]
        transitions["*"]?.get(to)?.let { return it }
        // Default: transitions[*][*]
        return transitions["*"]?.get("*") ?: 0.25f
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: File): AnimationConfig {
            val file = File(configDir, "vrmmod-animations.json")
            if (!file.exists()) {
                val default = AnimationConfig()
                try {
                    file.parentFile.mkdirs()
                    file.writeText(gson.toJson(default))
                } catch (_: Exception) {}
                return default
            }
            return try {
                Gson().fromJson(file.readText(), AnimationConfig::class.java) ?: AnimationConfig()
            } catch (e: Exception) {
                VrmMod.logger.error("Failed to load animation config", e)
                AnimationConfig()
            }
        }

        fun defaultStates(): Map<String, StateConfig> = mapOf(
            // Movement
            "idle" to StateConfig("Idle_Loop"),
            "walk" to StateConfig("Walk_Loop"),
            "walkBackward" to StateConfig("Walk_Loop"),
            "walkLeft" to StateConfig("Walk_Loop"),
            "walkRight" to StateConfig("Walk_Loop"),
            "run" to StateConfig("Sprint_Loop"),
            "jump" to StateConfig("Jump_Loop"),
            "sneak" to StateConfig("Crouch_Idle_Loop"),
            "sneakWalk" to StateConfig("Crouch_Fwd_Loop"),
            "swim" to StateConfig("Swim_Fwd_Loop"),
            "swimIdle" to StateConfig("Swim_Idle_Loop"),
            "ride" to StateConfig("Sitting_Idle_Loop"),
            "elytra" to StateConfig("Swim_Fwd_Loop"),
            // Actions
            "attack" to StateConfig("Punch_Jab", loop = false),
            "hurt" to StateConfig("Hit_Chest", loop = false),
            "useItem" to StateConfig("Interact", loop = false),
            "spinAttack" to StateConfig("Roll", loop = false),
            "death" to StateConfig("Death01", loop = false),
        )

        fun defaultTransitions(): Map<String, Map<String, Float>> = mapOf(
            "run" to mapOf("idle" to 0.1f, "walk" to 0.2f),
            "walk" to mapOf("idle" to 0.1f, "run" to 0.2f),
            "jump" to mapOf("*" to 0.1f),
            "idle" to mapOf("walk" to 0.15f, "run" to 0.15f),
            "*" to mapOf("*" to 0.25f),
        )
    }
}
