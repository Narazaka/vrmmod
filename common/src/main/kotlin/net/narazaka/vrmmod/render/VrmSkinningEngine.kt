package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * CPU-based skeletal skinning engine for VRM models.
 *
 * Given a [VrmSkeleton] and per-node local-transform overrides, this object
 * computes world matrices, skinning matrices, and transforms vertices / normals.
 */
object VrmSkinningEngine {

    /**
     * Computes the world (model-space) matrix for every node in the skeleton.
     *
     * For each node the local matrix is taken from [overrides] when present,
     * otherwise it is built from the node's rest-pose translation / rotation / scale.
     * The local matrix is then multiplied by the parent's world matrix.
     *
     * @return a list whose size equals [VrmSkeleton.nodes], one world matrix per node
     */
    fun computeWorldMatrices(
        skeleton: VrmSkeleton,
        overrides: Map<Int, Matrix4f> = emptyMap(),
    ): List<Matrix4f> {
        val nodes = skeleton.nodes
        val worldMatrices = ArrayList<Matrix4f>(nodes.size)
        // Initialise with identity so we can write in any order
        for (i in nodes.indices) {
            worldMatrices.add(Matrix4f())
        }

        // Build parent lookup (index -> parent index, -1 for root)
        val parentOf = IntArray(nodes.size) { -1 }
        for (i in nodes.indices) {
            for (child in nodes[i].childIndices) {
                if (child in nodes.indices) {
                    parentOf[child] = i
                }
            }
        }

        // Topological traversal (BFS from roots)
        val visited = BooleanArray(nodes.size)
        val queue = ArrayDeque<Int>()
        for (root in skeleton.rootNodeIndices) {
            queue.addLast(root)
        }
        // Also enqueue any orphan nodes not covered by rootNodeIndices
        for (i in nodes.indices) {
            if (parentOf[i] == -1 && i !in skeleton.rootNodeIndices) {
                queue.addLast(i)
            }
        }

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            if (visited[idx]) continue
            visited[idx] = true

            val local = overrides[idx] ?: buildLocalMatrix(nodes[idx])

            val parent = parentOf[idx]
            if (parent >= 0 && visited[parent]) {
                worldMatrices[idx].set(worldMatrices[parent]).mul(local)
            } else {
                worldMatrices[idx].set(local)
            }

            for (child in nodes[idx].childIndices) {
                queue.addLast(child)
            }
        }

        return worldMatrices
    }

    /**
     * Computes skinning matrices for a specific skin.
     *
     * `skinningMatrix[j] = worldMatrix[ skin.jointNodeIndices[j] ] * skin.inverseBindMatrices[j]`
     *
     * @param skinIndex index into [VrmSkeleton.skins]. If out of range, returns empty list.
     * @return one [Matrix4f] per joint (same order as the skin's jointNodeIndices)
     */
    fun computeSkinningMatrices(
        skeleton: VrmSkeleton,
        overrides: Map<Int, Matrix4f> = emptyMap(),
        skinIndex: Int = 0,
    ): List<Matrix4f> {
        val skin = skeleton.skins.getOrNull(skinIndex) ?: return emptyList()
        val worldMatrices = computeWorldMatrices(skeleton, overrides)
        val joints = skin.jointNodeIndices
        val ibms = skin.inverseBindMatrices

        return List(joints.size) { j ->
            val nodeIdx = joints[j]
            Matrix4f(worldMatrices[nodeIdx]).mul(ibms[j])
        }
    }

    /**
     * Computes skinning matrices using pre-computed world matrices.
     */
    fun computeSkinningMatrices(
        skeleton: VrmSkeleton,
        worldMatrices: List<Matrix4f>,
        skinIndex: Int = 0,
    ): List<Matrix4f> {
        val skin = skeleton.skins.getOrNull(skinIndex) ?: return emptyList()
        val joints = skin.jointNodeIndices
        val ibms = skin.inverseBindMatrices

        return List(joints.size) { j ->
            val nodeIdx = joints[j]
            Matrix4f(worldMatrices[nodeIdx]).mul(ibms[j])
        }
    }

    /**
     * Transforms a vertex position using linear blend skinning (up to 4 bones).
     *
     * @param position   the rest-pose vertex position
     * @param joints     joint indices for this vertex (4 entries, referencing into [skinningMatrices])
     * @param weights    joint weights for this vertex (4 entries, summing to ~1)
     * @param skinningMatrices the per-joint skinning matrices
     * @return the skinned position
     */
    fun skinVertex(
        position: Vector3f,
        joints: IntArray,
        weights: FloatArray,
        skinningMatrices: List<Matrix4f>,
    ): Vector3f {
        val result = Vector3f(0f, 0f, 0f)
        val tmp = Vector4f()

        for (i in 0 until minOf(4, joints.size, weights.size)) {
            val w = weights[i]
            if (w == 0f) continue
            val jointIdx = joints[i]
            if (jointIdx !in skinningMatrices.indices) continue

            tmp.set(position.x, position.y, position.z, 1f)
            skinningMatrices[jointIdx].transform(tmp)
            result.add(tmp.x * w, tmp.y * w, tmp.z * w)
        }

        return result
    }

    /**
     * Transforms a vertex normal using linear blend skinning (up to 4 bones).
     *
     * @param normal   the rest-pose vertex normal
     * @param joints   joint indices for this vertex
     * @param weights  joint weights for this vertex
     * @param skinningMatrices the per-joint skinning matrices
     * @return the skinned and re-normalised normal
     */
    fun skinNormal(
        normal: Vector3f,
        joints: IntArray,
        weights: FloatArray,
        skinningMatrices: List<Matrix4f>,
    ): Vector3f {
        val result = Vector3f(0f, 0f, 0f)
        val tmp = Vector3f()

        for (i in 0 until minOf(4, joints.size, weights.size)) {
            val w = weights[i]
            if (w == 0f) continue
            val jointIdx = joints[i]
            if (jointIdx !in skinningMatrices.indices) continue

            tmp.set(normal)
            // Transform as direction (ignore translation)
            skinningMatrices[jointIdx].transformDirection(tmp)
            result.add(tmp.x * w, tmp.y * w, tmp.z * w)
        }

        val len = result.length()
        if (len > 1e-6f) {
            result.div(len)
        } else {
            result.set(0f, 1f, 0f)
        }
        return result
    }

    /**
     * Builds a local transform matrix from a node's TRS properties.
     */
    private fun buildLocalMatrix(node: net.narazaka.vrmmod.vrm.VrmNode): Matrix4f {
        return Matrix4f()
            .translate(node.translation)
            .rotate(node.rotation)
            .scale(node.scale)
    }
}
