package com.github.narazaka.vrmmod.vrm

import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.io.GltfAssetReader
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.v2.GltfAssetV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JglTFPoCTest {

    private lateinit var model: GltfModel
    private lateinit var asset: GltfAssetV2

    private val vrmPath: Path = Path.of("../testdata/test-avatar.vrm")

    @BeforeAll
    fun setUp() {
        assertTrue(vrmPath.toFile().exists(), "Test VRM file must exist at $vrmPath")

        // Load as GltfModel (high-level API)
        val modelReader = GltfModelReader()
        model = modelReader.read(vrmPath)

        // Load as GltfAssetV2 (low-level API for raw glTF access)
        val assetReader = GltfAssetReader()
        val gltfAsset = assetReader.read(vrmPath.toUri())
        assertInstanceOf(GltfAssetV2::class.java, gltfAsset, "VRM file should be parsed as glTF v2")
        asset = gltfAsset as GltfAssetV2
    }

    @Test
    fun `meshes are accessible and non-empty`() {
        val meshModels = model.meshModels
        assertNotNull(meshModels)
        assertTrue(meshModels.isNotEmpty(), "Model should have at least one mesh")

        val firstMesh = meshModels[0]
        assertTrue(
            firstMesh.meshPrimitiveModels.isNotEmpty(),
            "First mesh should have at least one primitive"
        )
        println("Meshes: ${meshModels.size}, first mesh primitives: ${firstMesh.meshPrimitiveModels.size}")
    }

    @Test
    fun `skin joints and inverseBindMatrices are accessible`() {
        val skinModels = model.skinModels
        assertNotNull(skinModels)
        assertTrue(skinModels.isNotEmpty(), "Model should have at least one skin")

        val skin = skinModels[0]
        assertTrue(skin.joints.isNotEmpty(), "Skin should have joints")
        assertNotNull(skin.inverseBindMatrices, "Skin should have inverseBindMatrices")
        println("Skins: ${skinModels.size}, joints: ${skin.joints.size}")
    }

    @Test
    fun `nodes are accessible`() {
        val nodeModels = model.nodeModels
        assertNotNull(nodeModels)
        assertTrue(nodeModels.isNotEmpty(), "Model should have nodes")
        println("Nodes: ${nodeModels.size}")

        // Verify at least some nodes have names
        val namedNodes = nodeModels.filter { it.name != null && it.name.isNotEmpty() }
        assertTrue(namedNodes.isNotEmpty(), "Some nodes should have names")
        println("Named nodes: ${namedNodes.take(5).map { it.name }}")
    }

    @Test
    fun `textures and images are accessible`() {
        val textureModels = model.textureModels
        assertNotNull(textureModels)
        assertTrue(textureModels.isNotEmpty(), "Model should have textures")

        val imageModels = model.imageModels
        assertNotNull(imageModels)
        assertTrue(imageModels.isNotEmpty(), "Model should have images")

        // Verify image data is accessible
        val firstImage = imageModels[0]
        val imageData = firstImage.imageData
        assertNotNull(imageData, "Image data should be accessible")
        assertTrue(imageData.capacity() > 0, "Image data should be non-empty")
        println("Textures: ${textureModels.size}, Images: ${imageModels.size}, first image size: ${imageData.capacity()} bytes")
    }

    @Test
    fun `extensions field contains VRMC_vrm`() {
        val gltf = asset.gltf
        assertNotNull(gltf, "Raw GlTF object should be accessible")

        // Check extensionsUsed
        val extensionsUsed = gltf.extensionsUsed
        assertNotNull(extensionsUsed, "extensionsUsed should not be null")
        assertTrue(
            extensionsUsed.contains("VRMC_vrm"),
            "extensionsUsed should contain VRMC_vrm, got: $extensionsUsed"
        )
        println("extensionsUsed: $extensionsUsed")

        // Check extensions map
        val extensions = gltf.extensions
        assertNotNull(extensions, "extensions map should not be null")
        assertTrue(
            extensions.containsKey("VRMC_vrm"),
            "extensions should contain VRMC_vrm key, got keys: ${extensions.keys}"
        )

        val vrmExtension = extensions["VRMC_vrm"]
        assertNotNull(vrmExtension, "VRMC_vrm extension value should not be null")
        println("VRMC_vrm extension type: ${vrmExtension!!::class.java}")
        println("VRMC_vrm extension: ${vrmExtension.toString().take(200)}")

        // The extension is typically deserialized as a Map
        assertTrue(
            vrmExtension is Map<*, *>,
            "VRMC_vrm extension should be a Map, got: ${vrmExtension::class.java}"
        )

        @Suppress("UNCHECKED_CAST")
        val vrmMap = vrmExtension as Map<String, Any>
        assertTrue(vrmMap.containsKey("meta"), "VRMC_vrm should contain 'meta' key")
        assertTrue(vrmMap.containsKey("humanoid"), "VRMC_vrm should contain 'humanoid' key")
        println("VRMC_vrm keys: ${vrmMap.keys}")
    }
}
