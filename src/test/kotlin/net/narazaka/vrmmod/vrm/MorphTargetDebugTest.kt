package net.narazaka.vrmmod.vrm

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

        // Dump firstPerson annotations
        println("=== firstPerson ===")
        println("  annotations: ${model.firstPersonAnnotations}")
        println("  meshes: ${model.meshes.mapIndexed { i, m -> "$i:${m.name}" }}")

        // Also dump raw firstPerson from glTF
        val bytes = file.readBytes()
        val rawData = de.javagl.jgltf.model.io.RawGltfDataReader.read(java.io.ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltf = de.javagl.jgltf.model.io.v2.GltfReaderV2().read(java.io.ByteArrayInputStream(jsonBytes))
        val vrmExt = gltf.extensions?.get("VRMC_vrm")
        val vrmJson = vrmExt?.let { VrmExtensionParser.toJsonObject(it) }
        val fp = vrmJson?.getAsJsonObject("firstPerson")
        println("  raw firstPerson: $fp")

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

        // Dump ALL expressions with their binds
        println("\n=== All expressions ===")
        for (expr in model.expressions) {
            val bindsStr = expr.morphTargetBinds.map { "mesh=${it.nodeIndex},idx=${it.morphTargetIndex},w=${it.weight}" }
            println("  ${expr.name} (preset=${expr.preset}): ${expr.morphTargetBinds.size} binds $bindsStr")
        }

        // Check sad specifically
        val sad = model.expressions.find { it.name == "sad" || it.preset == "sad" }
        println("\n=== sad expression ===")
        if (sad != null) {
            println("  name=${sad.name} preset=${sad.preset} binds=${sad.morphTargetBinds.size}")
            for (bind in sad.morphTargetBinds) {
                val mesh = model.meshes.getOrNull(bind.nodeIndex)
                println("  bind: meshIdx=${bind.nodeIndex} morphIdx=${bind.morphTargetIndex} weight=${bind.weight}")
                if (mesh != null) {
                    val prim0 = mesh.primitives[0]
                    val morph = prim0.morphTargets.getOrNull(bind.morphTargetIndex)
                    var nonZero = 0
                    if (morph != null) {
                        for (v in morph.positionDeltas) {
                            if (kotlin.math.abs(v) > 0.0001f) nonZero++
                        }
                    }
                    println("  prim0 morph: ${morph?.positionDeltas?.size ?: -1} components, nonZero=$nonZero")
                }
            }
        } else {
            println("  NOT FOUND")
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
