package net.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

class FirstPersonDiagnosticTest {

    @Test
    fun `diagnose first person head detection for cecil`() {
        val file = File("D:/make/3d/vrm/cecil-pony-tail-02.vrm")
        if (!file.exists()) { println("File not found"); return }

        val model = VrmParser.parse(FileInputStream(file))

        // HEAD bone info
        val headBone = model.humanoid.humanBones[HumanBone.HEAD]
        println("=== HEAD bone ===")
        println("HEAD nodeIndex: ${headBone?.nodeIndex}")
        if (headBone != null) {
            val headNode = model.skeleton.nodes[headBone.nodeIndex]
            println("HEAD node name: '${headNode.name}'")
            println("HEAD children: ${headNode.childIndices.map { "${it}:'${model.skeleton.nodes[it].name}'" }}")
        }

        // Eye bones
        for (bone in listOf(HumanBone.LEFT_EYE, HumanBone.RIGHT_EYE)) {
            val boneNode = model.humanoid.humanBones[bone]
            if (boneNode != null) {
                val node = model.skeleton.nodes[boneNode.nodeIndex]
                println("${bone.vrmName} nodeIndex=${boneNode.nodeIndex} name='${node.name}'")
            }
        }

        // Per-skin HEAD joint indices
        println("\n=== Per-skin HEAD joint indices ===")
        for ((si, skin) in model.skeleton.skins.withIndex()) {
            val headJoints = mutableSetOf<Int>()
            for ((jointIdx, nodeIdx) in skin.jointNodeIndices.withIndex()) {
                if (isDescendant(model.skeleton, nodeIdx, headBone?.nodeIndex ?: -1)) {
                    headJoints.add(jointIdx)
                }
            }
            val jointNames = headJoints.map { ji ->
                val ni = skin.jointNodeIndices[ji]
                "$ji:node$ni:'${model.skeleton.nodes[ni].name}'"
            }
            println("skin[$si] (${skin.jointNodeIndices.size} joints): ${headJoints.size} HEAD descendants: $jointNames")
        }

        // Mesh info with firstPerson annotations
        println("\n=== Mesh info ===")
        for ((mi, mesh) in model.meshes.withIndex()) {
            val annotation = model.firstPersonAnnotations[mi]
            for ((pi, prim) in mesh.primitives.withIndex()) {
                val skinIdx = mesh.skinIndex
                val hasJoints = prim.joints.isNotEmpty()

                // Count triangles that would be skipped
                var totalTris = prim.indices.size / 3
                var skippedTris = 0
                if (hasJoints && skinIdx >= 0) {
                    val skin = model.skeleton.skins.getOrNull(skinIdx)
                    val headJoints = mutableSetOf<Int>()
                    if (skin != null) {
                        for ((ji, ni) in skin.jointNodeIndices.withIndex()) {
                            if (isDescendant(model.skeleton, ni, headBone?.nodeIndex ?: -1)) {
                                headJoints.add(ji)
                            }
                        }
                    }

                    for (tri in 0 until totalTris) {
                        val base = tri * 3
                        var skip = false
                        for (vi in 0 until 3) {
                            if (skip) break
                            val idx = prim.indices[base + vi]
                            for (i in 0 until 4) {
                                val di = idx * 4 + i
                                if (di >= prim.joints.size) break
                                val w = if (di < prim.weights.size) prim.weights[di] else 0f
                                if (w > 0f && prim.joints[di] in headJoints) {
                                    skip = true; break
                                }
                            }
                        }
                        if (skip) skippedTris++
                    }
                }

                // Check unskinned mesh node parentage
                val nodeInfo = if (!hasJoints) {
                    val nodeIdx = model.skeleton.nodes.indexOfFirst { it.meshIndex == mi }
                    val isHeadDesc = if (nodeIdx >= 0) isDescendant(model.skeleton, nodeIdx, headBone?.nodeIndex ?: -1) else false
                    " node=$nodeIdx isHeadDescendant=$isHeadDesc"
                } else ""

                println("  mesh[$mi] '${mesh.name}' prim[$pi]: skin=$skinIdx annotation=$annotation " +
                    "verts=${prim.vertexCount} tris=$totalTris skipped=$skippedTris/$totalTris" +
                    "$nodeInfo")
            }
        }
    }

    private fun isDescendant(skeleton: VrmSkeleton, nodeIndex: Int, ancestorIndex: Int): Boolean {
        if (ancestorIndex < 0) return false
        if (nodeIndex == ancestorIndex) return true
        for ((idx, node) in skeleton.nodes.withIndex()) {
            if (nodeIndex in node.childIndices) {
                return isDescendant(skeleton, idx, ancestorIndex)
            }
        }
        return false
    }
}
