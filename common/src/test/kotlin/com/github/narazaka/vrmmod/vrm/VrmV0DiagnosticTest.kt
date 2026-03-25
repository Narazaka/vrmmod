package com.github.narazaka.vrmmod.vrm

import com.github.narazaka.vrmmod.render.VrmSkinningEngine
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import org.joml.Matrix4f
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

class VrmV0DiagnosticTest {

    @Test
    fun `check worldMatrix times IBM for iromi v0`() {
        val file = File("D:/make/3d/vrm/色見いつは_2.0.vrm")
        if (!file.exists()) { println("File not found"); return }

        val model = VrmParser.parse(FileInputStream(file))

        val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
        val joints = model.skeleton.jointNodeIndices
        val ibms = model.skeleton.inverseBindMatrices

        println("=== 色見いつは v0: worldMatrix * IBM check ===")
        println("Joints: ${joints.size}, IBMs: ${ibms.size}")

        var badCount = 0
        for (j in joints.indices) {
            val nodeIdx = joints[j]
            val wm = worldMatrices[nodeIdx]
            val ibm = ibms[j]
            val sm = Matrix4f(wm).mul(ibm)
            val d = floatArrayOf(sm.m00(), sm.m11(), sm.m22())
            val t = floatArrayOf(sm.m30(), sm.m31(), sm.m32())
            val isId = abs(d[0] - 1f) < 0.01f && abs(d[1] - 1f) < 0.01f && abs(d[2] - 1f) < 0.01f &&
                abs(t[0]) < 0.01f && abs(t[1]) < 0.01f && abs(t[2]) < 0.01f
            if (!isId) {
                badCount++
                if (badCount <= 5) {
                    val name = model.skeleton.nodes.getOrNull(nodeIdx)?.name ?: "?"
                    println("  WRONG joint[$j] '$name': diag=(${d[0]}, ${d[1]}, ${d[2]}) trans=(${t[0]}, ${t[1]}, ${t[2]})")
                }
            }
        }
        println("Non-identity: $badCount / ${joints.size}")

        // Also check raw glTF skin.skeleton
        val bytes = file.readBytes()
        val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltf = GltfReaderV2().read(ByteArrayInputStream(jsonBytes))
        val skinSkeleton = gltf.skins?.firstOrNull()?.skeleton
        println("skin.skeleton = $skinSkeleton")
    }

    @Test
    fun `check mesh-skin relationship for cynthia`() {
        val file = File("D:/make/3d/vrm/シンシア like AINA.vrm")
        if (!file.exists()) { println("File not found"); return }

        val bytes = file.readBytes()
        val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltf = GltfReaderV2().read(ByteArrayInputStream(jsonBytes))

        // Check which nodes have skins, and what their parents are
        println("=== Node-Skin-Mesh relationships ===")
        val nodes = gltf.nodes ?: emptyList()
        for ((i, node) in nodes.withIndex()) {
            if (node.skin != null || node.mesh != null) {
                // Find parent
                var parentIdx = -1
                for ((pi, pn) in nodes.withIndex()) {
                    if (pn.children?.contains(i) == true) {
                        parentIdx = pi
                        break
                    }
                }
                val parentName = if (parentIdx >= 0) nodes[parentIdx].name else "(root)"
                println("  node[$i] '${node.name}': mesh=${node.mesh} skin=${node.skin} parent=$parentIdx '$parentName'")
            }
        }

        // Skins summary
        println("\n=== Skins ===")
        for ((si, skin) in (gltf.skins ?: emptyList()).withIndex()) {
            println("  skin[$si]: skeleton=${skin.skeleton} joints=${skin.joints?.size}")
        }
    }
}
