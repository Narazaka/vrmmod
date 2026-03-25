package net.narazaka.vrmmod.vrm

import net.narazaka.vrmmod.render.VrmSkinningEngine
import org.joml.Quaternionf

/**
 * Pre-computed data for converting normalized-space bone rotations to
 * raw bone local rotations. Follows three-vrm's VRMHumanoidRig design.
 *
 * @param parentWorldRotation The world rotation of the raw bone's direct parent node at rest.
 * @param boneRotation The raw bone's local rotation at rest (node.rotation).
 * @param parentNodeIndex The node index of the raw bone's direct parent (-1 if root).
 */
data class NormalizedBoneInfo(
    val parentWorldRotation: Quaternionf,
    val boneRotation: Quaternionf,
    val parentNodeIndex: Int = -1,
)

/**
 * Top-level container for a parsed VRM 1.0 model.
 */
data class VrmModel(
    val meta: VrmMeta = VrmMeta(name = ""),
    val humanoid: VrmHumanoid = VrmHumanoid(),
    val meshes: List<VrmMesh> = emptyList(),
    val skeleton: VrmSkeleton = VrmSkeleton(),
    val textures: List<VrmTexture> = emptyList(),
    val expressions: List<VrmExpression> = emptyList(),
    val springBone: VrmSpringBone = VrmSpringBone(),
    val mtoonMaterials: List<VrmMtoonMaterial> = emptyList(),
    /** Per-mesh first person annotation. Keys are mesh indices. */
    val firstPersonAnnotations: Map<Int, FirstPersonType> = emptyMap(),
    /** LookAt offsetFromHeadBone (VRM 1.0 spec). Used for eye/camera position. */
    val lookAtOffsetFromHeadBone: org.joml.Vector3f = org.joml.Vector3f(0f, 0.06f, 0f),
    /**
     * Per-humanoid-bone normalized rig info. Computed at load time from rest pose.
     * Used to convert VRMA animations (normalized space) to raw bone local rotations.
     * Follows three-vrm's VRMHumanoidRig._parentWorldRotations / _boneRotations.
     */
    val normalizedBoneInfo: Map<HumanBone, NormalizedBoneInfo> = emptyMap(),
) {
    companion object {
        /**
         * Computes NormalizedBoneInfo for all humanoid bones from the skeleton rest pose.
         */
        fun computeNormalizedBoneInfo(
            humanoid: VrmHumanoid,
            skeleton: VrmSkeleton,
        ): Map<HumanBone, NormalizedBoneInfo> {
            val worldMatrices = VrmSkinningEngine.computeWorldMatrices(skeleton)

            // Build parent lookup
            val parentOf = IntArray(skeleton.nodes.size) { -1 }
            for ((i, node) in skeleton.nodes.withIndex()) {
                for (child in node.childIndices) {
                    if (child in skeleton.nodes.indices) parentOf[child] = i
                }
            }

            val result = mutableMapOf<HumanBone, NormalizedBoneInfo>()
            for ((bone, boneNode) in humanoid.humanBones) {
                val nodeIndex = boneNode.nodeIndex
                val node = skeleton.nodes.getOrNull(nodeIndex) ?: continue

                // Parent's world rotation (from the direct glTF parent, not humanoid parent)
                val parentIdx = parentOf[nodeIndex]
                val parentWorldRotation = if (parentIdx >= 0) {
                    val q = Quaternionf()
                    worldMatrices[parentIdx].getNormalizedRotation(q)
                    q
                } else {
                    Quaternionf() // identity for root nodes
                }

                result[bone] = NormalizedBoneInfo(
                    parentWorldRotation = parentWorldRotation,
                    boneRotation = Quaternionf(node.rotation),
                    parentNodeIndex = parentIdx,
                )
            }
            return result
        }
    }
}

/**
 * VRM firstPerson mesh annotation type.
 */
enum class FirstPersonType {
    AUTO,
    BOTH,
    FIRST_PERSON_ONLY,
    THIRD_PERSON_ONLY,
}
