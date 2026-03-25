package com.github.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Test
import java.io.File
import java.io.FileInputStream

/**
 * Debug test to verify morph target parsing with sparse accessor resolution.
 * Run with: ./gradlew :common:test --tests "*MorphTargetDebugTest*" -i
 */
class MorphTargetDebugTest {

    private val vrmPath = "D:/make/3d/vrm/色見いつは_2.0_vrm1.vrm"

    @Test
    fun verifyMorphTargetParsing() {
        val file = File(vrmPath)
        if (!file.exists()) {
            println("VRM file not found: $vrmPath (skipping test)")
            return
        }

        val model = VrmParser.parse(FileInputStream(file))

        // Check blink expression
        val blink = model.expressions.find { it.name == "blink" }
        println("=== blink expression ===")
        println("blink: $blink")

        if (blink != null) {
            for (bind in blink.morphTargetBinds) {
                val mesh = model.meshes.getOrNull(bind.nodeIndex)
                println("  bind: meshIdx(resolved)=${bind.nodeIndex} morphIdx=${bind.morphTargetIndex} weight=${bind.weight}")
                println("  mesh: ${mesh?.name} primitives=${mesh?.primitives?.size}")

                if (mesh != null) {
                    for ((pi, prim) in mesh.primitives.withIndex()) {
                        val morph = prim.morphTargets.getOrNull(bind.morphTargetIndex)
                        if (morph != null) {
                            var nonZero = 0
                            for (i in morph.positionDeltas.indices) {
                                if (kotlin.math.abs(morph.positionDeltas[i]) > 0.0001f) nonZero++
                            }
                            println("    prim[$pi] target[${bind.morphTargetIndex}]: ${morph.positionDeltas.size / 3} verts, nonZero=$nonZero")
                        } else {
                            println("    prim[$pi] target[${bind.morphTargetIndex}]: NOT FOUND (targets=${prim.morphTargets.size})")
                        }
                    }
                }
            }
        }

        // Verify non-zero deltas exist (sparse accessor resolution worked)
        if (blink != null) {
            val bind = blink.morphTargetBinds[0]
            val mesh = model.meshes[bind.nodeIndex]
            var totalNonZero = 0
            for (prim in mesh.primitives) {
                val morph = prim.morphTargets.getOrNull(bind.morphTargetIndex) ?: continue
                for (v in morph.positionDeltas) {
                    if (kotlin.math.abs(v) > 0.0001f) totalNonZero++
                }
            }
            println("\n=== RESULT: blink has $totalNonZero non-zero delta components across all primitives ===")
            assert(totalNonZero > 0) { "Expected non-zero morph target deltas for blink expression" }
        }
    }
}
