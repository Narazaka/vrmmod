package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * A single named animation clip containing keyframe tracks per humanoid bone.
 */
data class AnimationClip(
    val name: String,
    val duration: Float, // seconds
    val tracks: Map<HumanBone, BoneTrack>,
) {
    /**
     * Samples the animation at the given [time], returning interpolated bone poses.
     * Time is wrapped (looped) within the clip duration.
     */
    fun sample(time: Float): BonePoseMap {
        if (duration <= 0f) return emptyMap()
        val t = (time % duration + duration) % duration // ensure positive wrap

        val poses = mutableMapOf<HumanBone, BonePose>()
        for ((bone, track) in tracks) {
            val rotation = sampleRotation(track.rotationKeyframes, t)
            val translation = sampleTranslation(track.translationKeyframes, t)
            if (rotation != null || translation != null) {
                poses[bone] = BonePose(
                    translation = translation ?: Vector3f(),
                    rotation = rotation ?: Quaternionf(),
                )
            }
        }
        return poses
    }

    private fun sampleRotation(keyframes: List<RotationKeyframe>, t: Float): Quaternionf? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) return Quaternionf(keyframes[0].value)

        // Binary search for the interval containing t
        val index = keyframes.binarySearchBy(t) { it.time }
        if (index >= 0) {
            // Exact match
            return Quaternionf(keyframes[index].value)
        }

        val insertionPoint = -(index + 1)
        if (insertionPoint == 0) return Quaternionf(keyframes[0].value)
        if (insertionPoint >= keyframes.size) return Quaternionf(keyframes.last().value)

        val prev = keyframes[insertionPoint - 1]
        val next = keyframes[insertionPoint]
        val alpha = (t - prev.time) / (next.time - prev.time)

        return Quaternionf(prev.value).slerp(next.value, alpha)
    }

    private fun sampleTranslation(keyframes: List<TranslationKeyframe>, t: Float): Vector3f? {
        if (keyframes.isEmpty()) return null
        if (keyframes.size == 1) return Vector3f(keyframes[0].value)

        val index = keyframes.binarySearchBy(t) { it.time }
        if (index >= 0) {
            return Vector3f(keyframes[index].value)
        }

        val insertionPoint = -(index + 1)
        if (insertionPoint == 0) return Vector3f(keyframes[0].value)
        if (insertionPoint >= keyframes.size) return Vector3f(keyframes.last().value)

        val prev = keyframes[insertionPoint - 1]
        val next = keyframes[insertionPoint]
        val alpha = (t - prev.time) / (next.time - prev.time)

        return Vector3f(prev.value).lerp(next.value, alpha)
    }
}

/**
 * Keyframe tracks for a single bone. Translation is typically only for hips.
 */
data class BoneTrack(
    val rotationKeyframes: List<RotationKeyframe> = emptyList(),
    val translationKeyframes: List<TranslationKeyframe> = emptyList(),
)

data class RotationKeyframe(val time: Float, val value: Quaternionf)
data class TranslationKeyframe(val time: Float, val value: Vector3f)

/**
 * Binary search by a comparable key extracted from elements.
 * Returns the index if found, or -(insertion point) - 1 if not found.
 */
private inline fun <T, K : Comparable<K>> List<T>.binarySearchBy(
    key: K,
    crossinline selector: (T) -> K,
): Int {
    var low = 0
    var high = size - 1
    while (low <= high) {
        val mid = (low + high) ushr 1
        val cmp = selector(this[mid]).compareTo(key)
        when {
            cmp < 0 -> low = mid + 1
            cmp > 0 -> high = mid - 1
            else -> return mid
        }
    }
    return -(low + 1)
}
