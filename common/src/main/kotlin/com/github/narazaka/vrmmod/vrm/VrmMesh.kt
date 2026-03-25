package com.github.narazaka.vrmmod.vrm

/**
 * A mesh consisting of one or more primitives.
 */
data class VrmMesh(
    val name: String = "",
    val primitives: List<VrmPrimitive> = emptyList(),
    /** Index into VrmSkeleton.skins. -1 means unskinned. */
    val skinIndex: Int = -1,
)

/**
 * A single draw-call unit within a mesh.
 */
/**
 * glTF alphaMode for the material.
 */
enum class AlphaMode {
    OPAQUE, MASK, BLEND
}

data class VrmPrimitive(
    val positions: FloatArray,
    val normals: FloatArray = floatArrayOf(),
    val texCoords: FloatArray = floatArrayOf(),
    val joints: IntArray = intArrayOf(),
    val weights: FloatArray = floatArrayOf(),
    val indices: IntArray = intArrayOf(),
    val vertexCount: Int,
    val materialIndex: Int = -1,
    /** Index into VrmModel.textures (resolved from material -> baseColorTexture -> image). */
    val imageIndex: Int = -1,
    val alphaMode: AlphaMode = AlphaMode.OPAQUE,
    val morphTargets: List<VrmMorphTarget> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VrmPrimitive) return false
        return positions.contentEquals(other.positions) &&
            normals.contentEquals(other.normals) &&
            texCoords.contentEquals(other.texCoords) &&
            joints.contentEquals(other.joints) &&
            weights.contentEquals(other.weights) &&
            indices.contentEquals(other.indices) &&
            vertexCount == other.vertexCount &&
            materialIndex == other.materialIndex &&
            imageIndex == other.imageIndex &&
            morphTargets == other.morphTargets
    }

    override fun hashCode(): Int {
        var result = positions.contentHashCode()
        result = 31 * result + normals.contentHashCode()
        result = 31 * result + indices.contentHashCode()
        result = 31 * result + vertexCount
        return result
    }
}

/**
 * A morph target (blend shape) delta.
 */
data class VrmMorphTarget(
    val positionDeltas: FloatArray = floatArrayOf(),
    val normalDeltas: FloatArray = floatArrayOf(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VrmMorphTarget) return false
        return positionDeltas.contentEquals(other.positionDeltas) &&
            normalDeltas.contentEquals(other.normalDeltas)
    }

    override fun hashCode(): Int {
        var result = positionDeltas.contentHashCode()
        result = 31 * result + normalDeltas.contentHashCode()
        return result
    }
}
