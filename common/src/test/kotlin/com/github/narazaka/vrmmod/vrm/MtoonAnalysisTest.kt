package com.github.narazaka.vrmmod.vrm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.nio.file.Path

class MtoonAnalysisTest {

    private val vrmPath = Path.of("../testdata/test-avatar.vrm")
    private val gson = Gson()
    private val prettyGson = GsonBuilder().setPrettyPrinting().create()

    @Test
    fun `dump all VRMC_materials_mtoon data from test VRM`() {
        val bytes = vrmPath.toFile().readBytes()

        // Read raw glTF JSON
        val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltf = GltfReaderV2().read(ByteArrayInputStream(jsonBytes))

        // Top-level extensions used
        val extensionsUsed = gltf.extensionsUsed ?: emptyList()
        println("extensionsUsed: $extensionsUsed")

        val materials = gltf.materials ?: emptyList()
        println("Total materials: ${materials.size}\n")

        for ((i, material) in materials.withIndex()) {
            println("=== Material[$i]: ${material.name ?: "(unnamed)"} ===")

            // Base PBR info
            val pbr = material.pbrMetallicRoughness
            if (pbr != null) {
                println("  baseColorFactor: ${pbr.baseColorFactor?.toList()}")
                println("  baseColorTexture index: ${pbr.baseColorTexture?.index}")
                println("  metallicFactor: ${pbr.metallicFactor}")
                println("  roughnessFactor: ${pbr.roughnessFactor}")
            }
            println("  alphaMode: ${material.alphaMode}")
            println("  alphaCutoff: ${material.alphaCutoff}")

            // VRMC_materials_mtoon extension
            val extensions = material.extensions
            if (extensions != null && extensions.containsKey("VRMC_materials_mtoon")) {
                val mtoonRaw = extensions["VRMC_materials_mtoon"]
                val mtoon = gson.toJsonTree(mtoonRaw).asJsonObject
                println("  VRMC_materials_mtoon:")
                println("    specVersion: ${mtoon.get("specVersion")}")

                // Shade
                println("    shadeColorFactor: ${mtoon.get("shadeColorFactor")}")
                println("    shadeMultiplyTexture: ${mtoon.get("shadeMultiplyTexture")}")

                // Shading
                println("    shadingShiftFactor: ${mtoon.get("shadingShiftFactor")}")
                println("    shadingShiftTexture: ${mtoon.get("shadingShiftTexture")}")
                println("    shadingToonyFactor: ${mtoon.get("shadingToonyFactor")}")

                // GI
                println("    giEqualizationFactor: ${mtoon.get("giEqualizationFactor")}")

                // Matcap
                println("    matcapFactor: ${mtoon.get("matcapFactor")}")
                println("    matcapTexture: ${mtoon.get("matcapTexture")}")

                // Rim
                println("    parametricRimColorFactor: ${mtoon.get("parametricRimColorFactor")}")
                println("    parametricRimFresnelPowerFactor: ${mtoon.get("parametricRimFresnelPowerFactor")}")
                println("    parametricRimLiftFactor: ${mtoon.get("parametricRimLiftFactor")}")
                println("    rimMultiplyTexture: ${mtoon.get("rimMultiplyTexture")}")
                println("    rimLightingMixFactor: ${mtoon.get("rimLightingMixFactor")}")

                // Outline
                println("    outlineWidthMode: ${mtoon.get("outlineWidthMode")}")
                println("    outlineWidthFactor: ${mtoon.get("outlineWidthFactor")}")
                println("    outlineWidthMultiplyTexture: ${mtoon.get("outlineWidthMultiplyTexture")}")
                println("    outlineColorFactor: ${mtoon.get("outlineColorFactor")}")
                println("    outlineLightingMixFactor: ${mtoon.get("outlineLightingMixFactor")}")

                // UV Animation
                println("    uvAnimationMaskTexture: ${mtoon.get("uvAnimationMaskTexture")}")
                println("    uvAnimationScrollXSpeedFactor: ${mtoon.get("uvAnimationScrollXSpeedFactor")}")
                println("    uvAnimationScrollYSpeedFactor: ${mtoon.get("uvAnimationScrollYSpeedFactor")}")
                println("    uvAnimationRotationSpeedFactor: ${mtoon.get("uvAnimationRotationSpeedFactor")}")

                // Transparency
                println("    transparentWithZWrite: ${mtoon.get("transparentWithZWrite")}")
                println("    renderQueueOffsetNumber: ${mtoon.get("renderQueueOffsetNumber")}")

                // Full JSON dump
                println("    --- Full JSON ---")
                println(prettyGson.toJson(mtoon).prependIndent("    "))
            } else {
                println("  (no VRMC_materials_mtoon extension)")
            }
            println()
        }
    }
}
