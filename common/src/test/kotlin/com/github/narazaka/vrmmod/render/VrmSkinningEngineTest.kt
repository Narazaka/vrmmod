package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmNode
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VrmSkinningEngineTest {

    private fun assertVec3Near(expected: Vector3f, actual: Vector3f, eps: Float = 1e-4f) {
        assertTrue(
            abs(expected.x - actual.x) < eps &&
                abs(expected.y - actual.y) < eps &&
                abs(expected.z - actual.z) < eps,
            "Expected $expected but got $actual",
        )
    }

    // ---- computeWorldMatrices ----

    @Test
    fun `computeWorldMatrices single root node`() {
        val skeleton = VrmSkeleton(
            nodes = listOf(
                VrmNode(name = "root", translation = Vector3f(1f, 2f, 3f)),
            ),
            rootNodeIndices = listOf(0),
        )

        val world = VrmSkinningEngine.computeWorldMatrices(skeleton)
        assertEquals(1, world.size)

        val pos = Vector3f()
        world[0].getTranslation(pos)
        assertVec3Near(Vector3f(1f, 2f, 3f), pos)
    }

    @Test
    fun `computeWorldMatrices 2-bone chain`() {
        // root at (0,1,0), child at local (0,1,0) => world (0,2,0)
        val skeleton = VrmSkeleton(
            nodes = listOf(
                VrmNode(name = "root", translation = Vector3f(0f, 1f, 0f), childIndices = listOf(1)),
                VrmNode(name = "child", translation = Vector3f(0f, 1f, 0f)),
            ),
            rootNodeIndices = listOf(0),
        )

        val world = VrmSkinningEngine.computeWorldMatrices(skeleton)
        assertEquals(2, world.size)

        val rootPos = Vector3f()
        world[0].getTranslation(rootPos)
        assertVec3Near(Vector3f(0f, 1f, 0f), rootPos)

        val childPos = Vector3f()
        world[1].getTranslation(childPos)
        assertVec3Near(Vector3f(0f, 2f, 0f), childPos)
    }

    @Test
    fun `computeWorldMatrices with override`() {
        val skeleton = VrmSkeleton(
            nodes = listOf(
                VrmNode(name = "root", translation = Vector3f(0f, 0f, 0f), childIndices = listOf(1)),
                VrmNode(name = "child", translation = Vector3f(0f, 1f, 0f)),
            ),
            rootNodeIndices = listOf(0),
        )

        // Override child to translate (5, 0, 0) instead
        val overrides = mapOf(1 to Matrix4f().translate(5f, 0f, 0f))
        val world = VrmSkinningEngine.computeWorldMatrices(skeleton, overrides)

        val childPos = Vector3f()
        world[1].getTranslation(childPos)
        assertVec3Near(Vector3f(5f, 0f, 0f), childPos)
    }

    // ---- computeSkinningMatrices ----

    @Test
    fun `computeSkinningMatrices identity bind`() {
        // Single node, single joint, identity inverse bind matrix
        val skeleton = VrmSkeleton(
            nodes = listOf(
                VrmNode(name = "bone", translation = Vector3f(0f, 2f, 0f)),
            ),
            rootNodeIndices = listOf(0),
            jointNodeIndices = listOf(0),
            inverseBindMatrices = listOf(Matrix4f()), // identity
        )

        val skinning = VrmSkinningEngine.computeSkinningMatrices(skeleton)
        assertEquals(1, skinning.size)

        // Skinning matrix = world * ibm = translate(0,2,0) * identity
        val pos = Vector3f()
        skinning[0].getTranslation(pos)
        assertVec3Near(Vector3f(0f, 2f, 0f), pos)
    }

    @Test
    fun `computeSkinningMatrices with inverse bind`() {
        // Node at (0,2,0), IBM that undoes (0,2,0) => skinning should be identity
        val skeleton = VrmSkeleton(
            nodes = listOf(
                VrmNode(name = "bone", translation = Vector3f(0f, 2f, 0f)),
            ),
            rootNodeIndices = listOf(0),
            jointNodeIndices = listOf(0),
            inverseBindMatrices = listOf(Matrix4f().translate(0f, -2f, 0f)),
        )

        val skinning = VrmSkinningEngine.computeSkinningMatrices(skeleton)
        val pos = Vector3f()
        skinning[0].getTranslation(pos)
        assertVec3Near(Vector3f(0f, 0f, 0f), pos)
    }

    // ---- skinVertex ----

    @Test
    fun `skinVertex single bone weight`() {
        // Skinning matrix translates by (3,0,0)
        val matrices = listOf(Matrix4f().translate(3f, 0f, 0f))
        val result = VrmSkinningEngine.skinVertex(
            position = Vector3f(1f, 0f, 0f),
            joints = intArrayOf(0, 0, 0, 0),
            weights = floatArrayOf(1f, 0f, 0f, 0f),
            skinningMatrices = matrices,
        )
        assertVec3Near(Vector3f(4f, 0f, 0f), result)
    }

    @Test
    fun `skinVertex two bone blend`() {
        // Bone 0 translates (2,0,0), bone 1 translates (0,4,0)
        // Weight 0.5/0.5 => (1, 2, 0)
        val matrices = listOf(
            Matrix4f().translate(2f, 0f, 0f),
            Matrix4f().translate(0f, 4f, 0f),
        )
        val result = VrmSkinningEngine.skinVertex(
            position = Vector3f(0f, 0f, 0f),
            joints = intArrayOf(0, 1, 0, 0),
            weights = floatArrayOf(0.5f, 0.5f, 0f, 0f),
            skinningMatrices = matrices,
        )
        assertVec3Near(Vector3f(1f, 2f, 0f), result)
    }

    // ---- skinNormal ----

    @Test
    fun `skinNormal preserves direction with identity`() {
        val matrices = listOf(Matrix4f()) // identity
        val result = VrmSkinningEngine.skinNormal(
            normal = Vector3f(0f, 1f, 0f),
            joints = intArrayOf(0, 0, 0, 0),
            weights = floatArrayOf(1f, 0f, 0f, 0f),
            skinningMatrices = matrices,
        )
        assertVec3Near(Vector3f(0f, 1f, 0f), result)
    }

    @Test
    fun `skinNormal with rotation`() {
        // 90 degree rotation around Z: (0,1,0) -> (-1,0,0)
        val rot = Matrix4f().rotate(
            Quaternionf().rotateZ(Math.toRadians(90.0).toFloat()),
        )
        val matrices = listOf(rot)
        val result = VrmSkinningEngine.skinNormal(
            normal = Vector3f(0f, 1f, 0f),
            joints = intArrayOf(0, 0, 0, 0),
            weights = floatArrayOf(1f, 0f, 0f, 0f),
            skinningMatrices = matrices,
        )
        assertVec3Near(Vector3f(-1f, 0f, 0f), result)
    }
}
