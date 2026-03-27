package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.VrmMod
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class AnimationConfig(
    val states: Map<String, StateConfig> = defaultStates(),
    val transitions: Map<String, Map<String, Float>> = defaultTransitions(),
    val weaponTags: Set<String> = defaultWeaponTags(),
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
        val mirror: Boolean = false,
    )

    fun resolveStateConfig(stateName: String): StateConfig? {
        var key = stateName
        while (true) {
            states[key]?.let { return it }
            val dot = key.lastIndexOf('.')
            if (dot < 0) break
            key = key.substring(0, dot)
        }
        return null
    }

    fun getTransitionDuration(from: String, to: String): Float {
        var f = from
        while (true) {
            val fromMap = transitions[f]
            if (fromMap != null) {
                var t = to
                while (true) {
                    fromMap[t]?.let { return it }
                    val dot = t.lastIndexOf('.')
                    if (dot < 0) break
                    t = t.substring(0, dot)
                }
                fromMap["*"]?.let { return it }
            }
            val dot = f.lastIndexOf('.')
            if (dot < 0) break
            f = f.substring(0, dot)
        }
        transitions["*"]?.get(to)?.let { return it }
        transitions["*"]?.get("*")?.let { return it }
        return 0.25f
    }

    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: File): AnimationConfig {
            val file = File(configDir, "vrmmod-animations.json")
            val loaded = if (file.exists()) {
                try {
                    Gson().fromJson(file.readText(), AnimationConfig::class.java)
                } catch (e: Exception) {
                    VrmMod.logger.error("Failed to load animation config", e)
                    null
                }
            } else null

            // Merge: default states provide a base, user config overrides
            val merged = if (loaded != null) {
                loaded.copy(
                    states = defaultStates() + loaded.states,
                    transitions = defaultTransitions() + loaded.transitions,
                    weaponTags = defaultWeaponTags() + loaded.weaponTags,
                )
            } else {
                AnimationConfig()
            }

            // Write back so new default states appear in the file
            try {
                file.parentFile.mkdirs()
                file.writeText(gson.toJson(merged))
            } catch (_: Exception) {}

            return merged
        }

        fun defaultStates(): Map<String, StateConfig> = mapOf(
            // Movement
            "move.idle" to StateConfig("Idle_Loop", mirror = true),
            "move.walk" to StateConfig("Walk_Loop"),
            "move.walk.backward" to StateConfig("Walk_Loop"),
            "move.walk.left" to StateConfig("Walk_Loop"),
            "move.walk.right" to StateConfig("Walk_Loop"),
            "move.sprint" to StateConfig("Sprint_Loop"),
            "move.jump" to StateConfig("Jump_Loop"),
            "move.sneak" to StateConfig("Crouch_Idle_Loop"),
            "move.sneak.idle" to StateConfig("Crouch_Idle_Loop"),
            "move.sneak.walk" to StateConfig("Crouch_Fwd_Loop"),
            "move.swim" to StateConfig("Swim_Fwd_Loop"),
            "move.swim.idle" to StateConfig("Swim_Idle_Loop"),
            "move.ride" to StateConfig("Sitting_Idle_Loop"),
            "move.elytra" to StateConfig("Swim_Fwd_Loop"),
            // Actions
            "action.swing" to StateConfig("Punch_Jab", loop = false, mirror = true),
            "action.swing.mainHand.weapon" to StateConfig("Sword_Attack", loop = false, mirror = true),
            "action.swing.mainHand.item" to StateConfig("Interact", loop = false, mirror = true),
            "action.swing.offHand.weapon" to StateConfig("Sword_Attack", loop = false),
            "action.swing.offHand.item" to StateConfig("Interact", loop = false),
            "action.useItem" to StateConfig("Interact", loop = false),
            "action.useItem.mainHand" to StateConfig("Interact", loop = false),
            "action.useItem.offHand" to StateConfig("Interact", loop = false),
            "action.hurt" to StateConfig("Hit_Chest", loop = false),
            "action.spinAttack" to StateConfig("Roll", loop = false),
            "action.death" to StateConfig("Death01", loop = false),
        )

        fun defaultWeaponTags(): Set<String> = setOf(
            "swords", "axes", "pickaxes", "shovels", "hoes"
        )

        fun defaultTransitions(): Map<String, Map<String, Float>> = mapOf(
            "move.sprint" to mapOf("move.idle" to 0.1f, "move.walk" to 0.2f),
            "move.walk" to mapOf("move.idle" to 0.1f, "move.sprint" to 0.2f),
            "move.jump" to mapOf("*" to 0.1f),
            "move.idle" to mapOf("move.walk" to 0.15f, "move.sprint" to 0.15f),
            "*" to mapOf("*" to 0.25f),
        )
    }
}
