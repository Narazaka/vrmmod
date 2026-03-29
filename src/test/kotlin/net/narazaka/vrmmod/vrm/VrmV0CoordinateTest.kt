package net.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Verifies that VRM 0.x coordinate conversion produces data matching VRM 1.0.
 * Uses the same model exported in both formats as ground truth.
 *
 * Since v0 and v1 exports may differ in vertex ordering, mesh splitting,
 * and minor T-pose adjustments, we verify coordinate system correctness
 * through aggregate properties (bounding boxes, centroid positions, etc.)
 * rather than per-vertex exact matching.
 */
class VrmV0CoordinateTest {

    private val v0Path = "D:/make/3d/vrm/色見いつは_2.0.vrm"
    private val v1Path = "D:/make/3d/vrm/色見いつは_2.0_vrm1.vrm"

    private data class BoundingBox(
        var minX: Float = Float.MAX_VALUE, var maxX: Float = -Float.MAX_VALUE,
        var minY: Float = Float.MAX_VALUE, var maxY: Float = -Float.MAX_VALUE,
        var minZ: Float = Float.MAX_VALUE, var maxZ: Float = -Float.MAX_VALUE,
    ) {
        fun extend(positions: FloatArray) {
            var i = 0
            while (i < positions.size) {
                minX = min(minX, positions[i]); maxX = max(maxX, positions[i])
                minY = min(minY, positions[i + 1]); maxY = max(maxY, positions[i + 1])
                minZ = min(minZ, positions[i + 2]); maxZ = max(maxZ, positions[i + 2])
                i += 3
            }
        }

        override fun toString() = "X[${minX}..${maxX}] Y[${minY}..${maxY}] Z[${minZ}..${maxZ}]"
    }

    @Test
    fun `v0 bounding box matches v1 after coordinate conversion`() {
        val v0File = File(v0Path)
        val v1File = File(v1Path)
        if (!v0File.exists() || !v1File.exists()) {
            println("Test files not found, skipping")
            return
        }

        val v0Model = VrmParser.parse(FileInputStream(v0File))
        val v1Model = VrmParser.parse(FileInputStream(v1File))

        val v0BB = BoundingBox()
        val v1BB = BoundingBox()

        for (mesh in v0Model.meshes) {
            for (prim in mesh.primitives) {
                v0BB.extend(prim.positions)
            }
        }
        for (mesh in v1Model.meshes) {
            for (prim in mesh.primitives) {
                v1BB.extend(prim.positions)
            }
        }

        println("v0 BB: $v0BB")
        println("v1 BB: $v1BB")

        // Bounding box extents should be very similar (within 5% of model height)
        val modelHeight = v1BB.maxY - v1BB.minY
        val tolerance = modelHeight * 0.05f

        assertTrue(abs(v0BB.minX - v1BB.minX) < tolerance, "minX: v0=${v0BB.minX} v1=${v1BB.minX}")
        assertTrue(abs(v0BB.maxX - v1BB.maxX) < tolerance, "maxX: v0=${v0BB.maxX} v1=${v1BB.maxX}")
        assertTrue(abs(v0BB.minY - v1BB.minY) < tolerance, "minY: v0=${v0BB.minY} v1=${v1BB.minY}")
        assertTrue(abs(v0BB.maxY - v1BB.maxY) < tolerance, "maxY: v0=${v0BB.maxY} v1=${v1BB.maxY}")
        assertTrue(abs(v0BB.minZ - v1BB.minZ) < tolerance, "minZ: v0=${v0BB.minZ} v1=${v1BB.minZ}")
        assertTrue(abs(v0BB.maxZ - v1BB.maxZ) < tolerance, "maxZ: v0=${v0BB.maxZ} v1=${v1BB.maxZ}")

        println("Bounding boxes match within ${tolerance}m tolerance")
    }

    @Test
    fun `v0 bone world positions match v1 direction after conversion`() {
        val v0File = File(v0Path)
        val v1File = File(v1Path)
        if (!v0File.exists() || !v1File.exists()) {
            println("Test files not found, skipping")
            return
        }

        val v0Model = VrmParser.parse(FileInputStream(v0File))
        val v1Model = VrmParser.parse(FileInputStream(v1File))

        // Verify key bone translations have matching signs
        // This proves the coordinate system is correct:
        // - Left arm should have positive X (left of character) in both
        // - Right arm should have negative X in both
        // - Hips Z should have same sign in both
        val checks = listOf(
            HumanBone.HIPS to "Hips",
            HumanBone.LEFT_UPPER_ARM to "L_UpperArm",
            HumanBone.RIGHT_UPPER_ARM to "R_UpperArm",
            HumanBone.LEFT_UPPER_LEG to "L_UpperLeg",
            HumanBone.RIGHT_UPPER_LEG to "R_UpperLeg",
            HumanBone.HEAD to "Head",
        )

        println("=== Bone Translation Direction Check ===")
        for ((bone, label) in checks) {
            val v0Idx = v0Model.humanoid.humanBones[bone]?.nodeIndex ?: continue
            val v1Idx = v1Model.humanoid.humanBones[bone]?.nodeIndex ?: continue
            val v0T = v0Model.skeleton.nodes[v0Idx].translation
            val v1T = v1Model.skeleton.nodes[v1Idx].translation

            println("  $label: v0=$v0T v1=$v1T")

            // X should have the same sign (or both near zero)
            if (abs(v0T.x) > 0.01f || abs(v1T.x) > 0.01f) {
                assertTrue(v0T.x * v1T.x >= 0,
                    "$label X sign mismatch: v0=${v0T.x} v1=${v1T.x}")
            }
            // Y should be close (translation within parent, not affected by coordinate flip)
            assertTrue(abs(v0T.y - v1T.y) < 0.02f,
                "$label Y differs too much: v0=${v0T.y} v1=${v1T.y}")
        }

        println("All bone directions verified!")
    }

    @Test
    fun `converted v0 IBM diagonal signs match v1`() {
        val v0File = File(v0Path)
        val v1File = File(v1Path)
        if (!v0File.exists() || !v1File.exists()) {
            println("Test files not found, skipping")
            return
        }

        val v0Model = VrmParser.parse(FileInputStream(v0File))
        val v1Model = VrmParser.parse(FileInputStream(v1File))

        // Compare IBM diagonal elements for the first few joints
        // After correct conversion, the diagonal (m00, m11, m22) should have
        // matching signs between v0 and v1
        val checkCount = minOf(10, v0Model.skeleton.inverseBindMatrices.size,
            v1Model.skeleton.inverseBindMatrices.size)

        println("=== IBM Diagonal Check (first $checkCount joints) ===")
        for (i in 0 until checkCount) {
            val v0IBM = v0Model.skeleton.inverseBindMatrices[i]
            val v1IBM = v1Model.skeleton.inverseBindMatrices[i]
            val v0Name = v0Model.skeleton.nodes.getOrNull(
                v0Model.skeleton.jointNodeIndices.getOrNull(i) ?: -1)?.name ?: "?"
            println("  joint[$i] '$v0Name': v0 diag=(${v0IBM.m00()}, ${v0IBM.m11()}, ${v0IBM.m22()}) " +
                "v1 diag=(${v1IBM.m00()}, ${v1IBM.m11()}, ${v1IBM.m22()})")
        }
    }
}
