package com.github.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.nio.file.Path

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VrmParserTest {

    private lateinit var vrmModel: VrmModel

    private val vrmPath: Path = Path.of("../testdata/test-avatar.vrm")

    @BeforeAll
    fun setUp() {
        assertTrue(vrmPath.toFile().exists(), "Test VRM file must exist at $vrmPath")
        vrmModel = vrmPath.toFile().inputStream().use { VrmParser.parse(it) }
    }

    @Test
    fun `meta name is non-empty`() {
        assertTrue(vrmModel.meta.name.isNotEmpty(), "Meta name should be non-empty, got: '${vrmModel.meta.name}'")
        println("Meta: name=${vrmModel.meta.name}, authors=${vrmModel.meta.authors}")
    }

    @Test
    fun `humanoid has HIPS and HEAD bones`() {
        val humanoid = vrmModel.humanoid
        assertTrue(humanoid.humanBones.isNotEmpty(), "Humanoid should have bones")
        assertNotNull(humanoid.humanBones[HumanBone.HIPS], "Should have HIPS bone")
        assertNotNull(humanoid.humanBones[HumanBone.HEAD], "Should have HEAD bone")
        println("Humanoid bones: ${humanoid.humanBones.keys.map { it.vrmName }}")
    }

    @Test
    fun `meshes are non-empty with vertex data`() {
        assertTrue(vrmModel.meshes.isNotEmpty(), "Should have at least one mesh")

        val totalPrimitives = vrmModel.meshes.sumOf { it.primitives.size }
        assertTrue(totalPrimitives > 0, "Should have at least one primitive")

        // Verify at least one primitive has vertex data
        val firstPrimWithData = vrmModel.meshes.flatMap { it.primitives }.first { it.positions.isNotEmpty() }
        assertTrue(firstPrimWithData.vertexCount > 0, "First primitive should have vertices")
        assertTrue(firstPrimWithData.positions.isNotEmpty(), "First primitive should have positions")
        println("Meshes: ${vrmModel.meshes.size}, primitives: $totalPrimitives, first prim vertices: ${firstPrimWithData.vertexCount}")
    }

    @Test
    fun `skeleton has nodes and joints`() {
        val skeleton = vrmModel.skeleton
        assertTrue(skeleton.nodes.isNotEmpty(), "Skeleton should have nodes")
        assertTrue(skeleton.jointNodeIndices.isNotEmpty(), "Skeleton should have joint indices")
        assertTrue(skeleton.inverseBindMatrices.isNotEmpty(), "Skeleton should have inverse bind matrices")
        assertEquals(
            skeleton.jointNodeIndices.size,
            skeleton.inverseBindMatrices.size,
            "Joint count should match inverse bind matrix count"
        )
        println("Nodes: ${skeleton.nodes.size}, joints: ${skeleton.jointNodeIndices.size}, rootNodes: ${skeleton.rootNodeIndices}")
    }

    @Test
    fun `textures are non-empty`() {
        assertTrue(vrmModel.textures.isNotEmpty(), "Should have at least one texture")

        val firstTexture = vrmModel.textures[0]
        assertTrue(firstTexture.imageData.isNotEmpty(), "First texture should have image data")
        println("Textures: ${vrmModel.textures.size}, first texture size: ${firstTexture.imageData.size} bytes")
    }

    @Test
    fun `expressions are parsed`() {
        // Expressions may or may not be present in the test VRM
        println("Expressions: ${vrmModel.expressions.size} (${vrmModel.expressions.map { it.name }})")
    }
}
