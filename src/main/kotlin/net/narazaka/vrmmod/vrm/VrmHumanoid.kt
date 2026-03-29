package net.narazaka.vrmmod.vrm

/**
 * VRM 1.0 humanoid bone mapping.
 */
data class VrmHumanoid(
    val humanBones: Map<HumanBone, HumanBoneNode> = emptyMap(),
)

data class HumanBoneNode(
    val nodeIndex: Int,
)

/**
 * VRM 1.0 humanoid bone names.
 * See: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md
 */
enum class HumanBone(val vrmName: String) {
    HIPS("hips"),
    SPINE("spine"),
    CHEST("chest"),
    UPPER_CHEST("upperChest"),
    NECK("neck"),
    HEAD("head"),

    LEFT_EYE("leftEye"),
    RIGHT_EYE("rightEye"),
    JAW("jaw"),

    LEFT_UPPER_LEG("leftUpperLeg"),
    LEFT_LOWER_LEG("leftLowerLeg"),
    LEFT_FOOT("leftFoot"),
    LEFT_TOES("leftToes"),

    RIGHT_UPPER_LEG("rightUpperLeg"),
    RIGHT_LOWER_LEG("rightLowerLeg"),
    RIGHT_FOOT("rightFoot"),
    RIGHT_TOES("rightToes"),

    LEFT_SHOULDER("leftShoulder"),
    LEFT_UPPER_ARM("leftUpperArm"),
    LEFT_LOWER_ARM("leftLowerArm"),
    LEFT_HAND("leftHand"),

    RIGHT_SHOULDER("rightShoulder"),
    RIGHT_UPPER_ARM("rightUpperArm"),
    RIGHT_LOWER_ARM("rightLowerArm"),
    RIGHT_HAND("rightHand"),

    // Left hand fingers
    LEFT_THUMB_METACARPAL("leftThumbMetacarpal"),
    LEFT_THUMB_PROXIMAL("leftThumbProximal"),
    LEFT_THUMB_DISTAL("leftThumbDistal"),
    LEFT_INDEX_PROXIMAL("leftIndexProximal"),
    LEFT_INDEX_INTERMEDIATE("leftIndexIntermediate"),
    LEFT_INDEX_DISTAL("leftIndexDistal"),
    LEFT_MIDDLE_PROXIMAL("leftMiddleProximal"),
    LEFT_MIDDLE_INTERMEDIATE("leftMiddleIntermediate"),
    LEFT_MIDDLE_DISTAL("leftMiddleDistal"),
    LEFT_RING_PROXIMAL("leftRingProximal"),
    LEFT_RING_INTERMEDIATE("leftRingIntermediate"),
    LEFT_RING_DISTAL("leftRingDistal"),
    LEFT_LITTLE_PROXIMAL("leftLittleProximal"),
    LEFT_LITTLE_INTERMEDIATE("leftLittleIntermediate"),
    LEFT_LITTLE_DISTAL("leftLittleDistal"),

    // Right hand fingers
    RIGHT_THUMB_METACARPAL("rightThumbMetacarpal"),
    RIGHT_THUMB_PROXIMAL("rightThumbProximal"),
    RIGHT_THUMB_DISTAL("rightThumbDistal"),
    RIGHT_INDEX_PROXIMAL("rightIndexProximal"),
    RIGHT_INDEX_INTERMEDIATE("rightIndexIntermediate"),
    RIGHT_INDEX_DISTAL("rightIndexDistal"),
    RIGHT_MIDDLE_PROXIMAL("rightMiddleProximal"),
    RIGHT_MIDDLE_INTERMEDIATE("rightMiddleIntermediate"),
    RIGHT_MIDDLE_DISTAL("rightMiddleDistal"),
    RIGHT_RING_PROXIMAL("rightRingProximal"),
    RIGHT_RING_INTERMEDIATE("rightRingIntermediate"),
    RIGHT_RING_DISTAL("rightRingDistal"),
    RIGHT_LITTLE_PROXIMAL("rightLittleProximal"),
    RIGHT_LITTLE_INTERMEDIATE("rightLittleIntermediate"),
    RIGHT_LITTLE_DISTAL("rightLittleDistal"),
    ;

    /** Returns the opposite-side bone for left/right pairs, or null for center bones. */
    fun mirrorPair(): HumanBone? = MIRROR_PAIRS[this]

    companion object {
        private val byVrmName = entries.associateBy { it.vrmName }

        /**
         * Maps camelCase VRM JSON key to enum value.
         * @return the matching [HumanBone] or null if not found
         */
        fun fromVrmName(name: String): HumanBone? = byVrmName[name]

        private val MIRROR_PAIRS: Map<HumanBone, HumanBone> = buildMap {
            fun pair(a: HumanBone, b: HumanBone) { put(a, b); put(b, a) }
            pair(LEFT_EYE, RIGHT_EYE)
            pair(LEFT_UPPER_LEG, RIGHT_UPPER_LEG)
            pair(LEFT_LOWER_LEG, RIGHT_LOWER_LEG)
            pair(LEFT_FOOT, RIGHT_FOOT)
            pair(LEFT_TOES, RIGHT_TOES)
            pair(LEFT_SHOULDER, RIGHT_SHOULDER)
            pair(LEFT_UPPER_ARM, RIGHT_UPPER_ARM)
            pair(LEFT_LOWER_ARM, RIGHT_LOWER_ARM)
            pair(LEFT_HAND, RIGHT_HAND)
            pair(LEFT_THUMB_METACARPAL, RIGHT_THUMB_METACARPAL)
            pair(LEFT_THUMB_PROXIMAL, RIGHT_THUMB_PROXIMAL)
            pair(LEFT_THUMB_DISTAL, RIGHT_THUMB_DISTAL)
            pair(LEFT_INDEX_PROXIMAL, RIGHT_INDEX_PROXIMAL)
            pair(LEFT_INDEX_INTERMEDIATE, RIGHT_INDEX_INTERMEDIATE)
            pair(LEFT_INDEX_DISTAL, RIGHT_INDEX_DISTAL)
            pair(LEFT_MIDDLE_PROXIMAL, RIGHT_MIDDLE_PROXIMAL)
            pair(LEFT_MIDDLE_INTERMEDIATE, RIGHT_MIDDLE_INTERMEDIATE)
            pair(LEFT_MIDDLE_DISTAL, RIGHT_MIDDLE_DISTAL)
            pair(LEFT_RING_PROXIMAL, RIGHT_RING_PROXIMAL)
            pair(LEFT_RING_INTERMEDIATE, RIGHT_RING_INTERMEDIATE)
            pair(LEFT_RING_DISTAL, RIGHT_RING_DISTAL)
            pair(LEFT_LITTLE_PROXIMAL, RIGHT_LITTLE_PROXIMAL)
            pair(LEFT_LITTLE_INTERMEDIATE, RIGHT_LITTLE_INTERMEDIATE)
            pair(LEFT_LITTLE_DISTAL, RIGHT_LITTLE_DISTAL)
        }
    }
}
