package com.github.narazaka.vrmmod.vrm

import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class VrmaAnalysisTest {

    private val vrmaDir = Path.of("../../vrm-anims/vrma")

    @Test
    fun `analyze all vrma files`() {
        val files = vrmaDir.toFile().listFiles { f -> f.extension == "vrma" } ?: return
        for (file in files.sorted()) {
            println("=== ${file.name} ===")
            try {
                val bytes = file.readBytes()

                // Read high-level model for animation data
                val model = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))

                // Read raw glTF for extensions
                val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
                val jsonBuf = rawData.jsonData
                val jsonBytes = ByteArray(jsonBuf.remaining())
                jsonBuf.get(jsonBytes)
                val gltf = GltfReaderV2().read(ByteArrayInputStream(jsonBytes))

                // Animations
                val anims = model.animationModels
                println("  Animations: ${anims.size}")
                for ((i, anim) in anims.withIndex()) {
                    println("  [$i] name='${anim.name}', channels=${anim.channels.size}")
                    // Show first few channels
                    for ((j, channel) in anim.channels.withIndex().take(3)) {
                        println("    channel[$j]: node='${channel.nodeModel?.name}', path='${channel.path}'")
                    }
                    if (anim.channels.size > 3) {
                        println("    ... and ${anim.channels.size - 3} more channels")
                    }
                }

                // Nodes
                println("  Nodes: ${model.nodeModels.size}")
                for ((i, node) in model.nodeModels.withIndex().take(10)) {
                    println("    [$i] '${node.name}'")
                }
                if (model.nodeModels.size > 10) {
                    println("    ... and ${model.nodeModels.size - 10} more nodes")
                }

                // VRMC_vrm_animation extension
                val extensions = gltf.extensions
                if (extensions != null && extensions.containsKey("VRMC_vrm_animation")) {
                    val vrmAnim = Gson().toJsonTree(extensions["VRMC_vrm_animation"]).asJsonObject
                    println("  VRMC_vrm_animation:")
                    println("    specVersion: ${vrmAnim.get("specVersion")}")

                    val humanoid = vrmAnim.getAsJsonObject("humanoid")
                    if (humanoid != null) {
                        val bones = humanoid.getAsJsonObject("humanBones")
                        println("    humanBones: ${bones?.entrySet()?.map { it.key }}")
                    }

                    val expressions = vrmAnim.getAsJsonObject("expressions")
                    if (expressions != null) {
                        val preset = expressions.getAsJsonObject("preset")
                        println("    expressions preset: ${preset?.entrySet()?.map { it.key }}")
                    }
                } else {
                    println("  No VRMC_vrm_animation extension found")
                }
            } catch (e: Exception) {
                println("  ERROR: ${e.message}")
            }
            println()
        }
    }

    @Test
    fun `detailed analysis of MovementBasic`() {
        val file = vrmaDir.resolve("Rig_Medium_MovementBasic.vrma").toFile()
        if (!file.exists()) { println("File not found"); return }

        val bytes = file.readBytes()
        val model = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))

        println("=== ${file.name} detailed ===")
        val anims = model.animationModels
        println("Animations: ${anims.size}")
        for ((i, anim) in anims.withIndex()) {
            // Get duration from samplers
            var maxTime = 0f
            for (channel in anim.channels) {
                val sampler = channel.sampler
                val inputData = sampler?.input?.accessorData
                if (inputData is de.javagl.jgltf.model.AccessorFloatData) {
                    for (k in 0 until inputData.numElements) {
                        val t = inputData.get(k, 0)
                        if (t > maxTime) maxTime = t
                    }
                }
            }
            println("[$i] name='${anim.name}', channels=${anim.channels.size}, duration=${maxTime}s")
        }
    }

    @Test
    fun `analyze all vrma files in large folder`() {
        val vrmLargeDir = Path.of("../../vrm-anims/vrma-large")
        val files = vrmLargeDir.toFile().listFiles { f -> f.extension == "vrma" } ?: return

        println("\n=== VRMA Large Folder Analysis ===")
        println("Found ${files.size} files\n")

        for (file in files.sorted()) {
            println("=== ${file.name} ===")
            try {
                val bytes = file.readBytes()
                val model = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))

                // Animations - just show names
                val anims = model.animationModels
                println("  Animation names:")
                for ((i, anim) in anims.withIndex()) {
                    println("    [$i] ${anim.name}")
                }
            } catch (e: Exception) {
                println("  ERROR: ${e.message}")
            }
            println()
        }
    }

    @Test
    fun `analyze all vrma files in quaternius folder`() {
        val quaterniusDir = Path.of("../../vrm-anims/vrma-quaternius")
        val files = quaterniusDir.toFile().listFiles { f -> f.extension == "vrma" } ?: return

        println("\n=== VRMA Quaternius Folder Analysis ===")
        println("Found ${files.size} files\n")

        for (file in files.sorted()) {
            println("=== ${file.name} ===")
            try {
                val bytes = file.readBytes()
                val model = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))

                // Animations with duration and channels
                val anims = model.animationModels
                println("  Animations:")
                for ((i, anim) in anims.withIndex()) {
                    // Calculate duration from samplers
                    var maxTime = 0f
                    for (channel in anim.channels) {
                        val sampler = channel.sampler
                        val inputData = sampler?.input?.accessorData
                        if (inputData is de.javagl.jgltf.model.AccessorFloatData) {
                            for (k in 0 until inputData.numElements) {
                                val t = inputData.get(k, 0)
                                if (t > maxTime) maxTime = t
                            }
                        }
                    }
                    println("    [$i] name='${anim.name}', duration=${maxTime}s, channels=${anim.channels.size}")
                }
            } catch (e: Exception) {
                println("  ERROR: ${e.message}")
            }
            println()
        }
    }
}
