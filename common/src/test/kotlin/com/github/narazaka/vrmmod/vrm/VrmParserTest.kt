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
    fun `primitives have valid imageIndex`() {
        val allPrimitives = vrmModel.meshes.flatMap { it.primitives }
        assertTrue(allPrimitives.isNotEmpty(), "Should have at least one primitive")

        val textureCount = vrmModel.textures.size
        val primsWithImage = allPrimitives.filter { it.imageIndex >= 0 }
        assertTrue(primsWithImage.isNotEmpty(), "At least one primitive should have a valid imageIndex")

        for (prim in primsWithImage) {
            assertTrue(
                prim.imageIndex < textureCount,
                "imageIndex ${prim.imageIndex} should be < texture count $textureCount"
            )
        }
        println("Primitives with valid imageIndex: ${primsWithImage.size}/${allPrimitives.size}, texture count: $textureCount")
    }

    @Test
    fun `indices are within vertex range`() {
        for ((meshIdx, mesh) in vrmModel.meshes.withIndex()) {
            for ((primIdx, prim) in mesh.primitives.withIndex()) {
                if (prim.indices.isEmpty() || prim.positions.isEmpty()) continue
                val maxIndex = prim.indices.max()
                val vertexCount = prim.vertexCount
                assertTrue(
                    maxIndex < vertexCount,
                    "Mesh $meshIdx prim $primIdx: max index $maxIndex >= vertexCount $vertexCount"
                )
                assertTrue(prim.indices.min() >= 0, "Mesh $meshIdx prim $primIdx: negative index")
                // Check positions array size matches vertexCount * 3
                assertEquals(
                    vertexCount * 3, prim.positions.size,
                    "Mesh $meshIdx prim $primIdx: positions size ${prim.positions.size} != vertexCount*3 ${vertexCount * 3}"
                )
                // indices count should be multiple of 3 (triangles)
                assertEquals(
                    0, prim.indices.size % 3,
                    "Mesh $meshIdx prim $primIdx: indices count ${prim.indices.size} not multiple of 3"
                )
                println("Mesh $meshIdx prim $primIdx: ${prim.vertexCount} verts, ${prim.indices.size / 3} tris, maxIdx=$maxIndex, joints=${prim.joints.size / 4} weights=${prim.weights.size / 4}")
            }
        }
    }

    @Test
    fun `index ranges per primitive show shared vs separate vertex buffers`() {
        for ((meshIdx, mesh) in vrmModel.meshes.withIndex()) {
            println("=== Mesh $meshIdx: ${mesh.name}, ${mesh.primitives.size} primitives ===")
            for ((primIdx, prim) in mesh.primitives.withIndex()) {
                if (prim.indices.isEmpty()) {
                    println("  prim $primIdx: no indices")
                    continue
                }
                val minIdx = prim.indices.min()
                val maxIdx = prim.indices.max()
                println("  prim $primIdx: vertexCount=${prim.vertexCount}, indexCount=${prim.indices.size}, " +
                    "indexRange=[$minIdx..$maxIdx], imageIndex=${prim.imageIndex}")
            }
            // Check if multiple primitives share vertex data
            val vertexCounts = mesh.primitives.map { it.vertexCount }.distinct()
            if (vertexCounts.size == 1 && mesh.primitives.size > 1) {
                println("  NOTE: All ${mesh.primitives.size} primitives share vertexCount=${vertexCounts[0]} -- likely shared vertex buffer")
                // Check if index ranges overlap
                val ranges = mesh.primitives.filter { it.indices.isNotEmpty() }.map { it.indices.min()..it.indices.max() }
                for (i in ranges.indices) {
                    for (j in i + 1 until ranges.size) {
                        val overlap = ranges[i].first <= ranges[j].last && ranges[j].first <= ranges[i].last
                        if (overlap) {
                            println("  WARNING: prim $i range ${ranges[i]} overlaps with prim $j range ${ranges[j]}")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `dump hips children to check spine vs leg hierarchy`() {
        val skeleton = vrmModel.skeleton
        val humanoid = vrmModel.humanoid
        val hipsNode = humanoid.humanBones[HumanBone.HIPS]!!
        val hipsIdx = hipsNode.nodeIndex
        val hips = skeleton.nodes[hipsIdx]
        println("HIPS node $hipsIdx '${hips.name}' children: ${hips.childIndices}")
        for (childIdx in hips.childIndices) {
            val child = skeleton.nodes[childIdx]
            println("  child $childIdx '${child.name}' children: ${child.childIndices}")
        }
        // Find SPINE's parent
        val spineNode = humanoid.humanBones[HumanBone.SPINE]!!
        val spineIdx = spineNode.nodeIndex
        val spineParent = skeleton.nodes.indexOfFirst { it.childIndices.contains(spineIdx) }
        println("SPINE node $spineIdx parent: $spineParent (${if (spineParent >= 0) skeleton.nodes[spineParent].name else "none"})")
        // Find UPPER_LEG's parent
        val rightLegNode = humanoid.humanBones[HumanBone.RIGHT_UPPER_LEG]!!
        val rightLegParent = skeleton.nodes.indexOfFirst { it.childIndices.contains(rightLegNode.nodeIndex) }
        println("RIGHT_UPPER_LEG node ${rightLegNode.nodeIndex} parent: $rightLegParent (${if (rightLegParent >= 0) skeleton.nodes[rightLegParent].name else "none"})")
    }

    @Test
    fun `dump humanoid bone rest rotations`() {
        val skeleton = vrmModel.skeleton
        val humanoid = vrmModel.humanoid
        for ((bone, boneNode) in humanoid.humanBones) {
            val node = skeleton.nodes[boneNode.nodeIndex]
            val r = node.rotation
            val isIdentity = r.x == 0f && r.y == 0f && r.z == 0f && r.w == 1f
            if (!isIdentity) {
                println("BONE ${bone.vrmName} (node ${boneNode.nodeIndex} '${node.name}'): rotation=(${r.x}, ${r.y}, ${r.z}, ${r.w})")
            }
        }
        // Also dump HEAD and its parents
        val headNode = humanoid.humanBones[HumanBone.HEAD]
        if (headNode != null) {
            println("--- HEAD chain ---")
            var nodeIdx = headNode.nodeIndex
            while (nodeIdx >= 0) {
                val node = skeleton.nodes[nodeIdx]
                val r = node.rotation
                println("  node $nodeIdx '${node.name}': rot=(${r.x}, ${r.y}, ${r.z}, ${r.w}), trans=(${node.translation.x}, ${node.translation.y}, ${node.translation.z})")
                // Find parent
                val parent = skeleton.nodes.indexOfFirst { it.childIndices.contains(nodeIdx) }
                nodeIdx = parent
            }
        }
    }

    @Test
    fun `springbone nodes are in joint list`() {
        val skeleton = vrmModel.skeleton
        val jointSet = skeleton.jointNodeIndices.toSet()
        val springNodes = vrmModel.springBone.springs.flatMap { it.joints.map { j -> j.nodeIndex } }.toSet()
        val inJoints = springNodes.filter { it in jointSet }
        val notInJoints = springNodes.filter { it !in jointSet }
        println("SpringBone nodes total: ${springNodes.size}")
        println("In joint list: ${inJoints.size} (${inJoints.sorted().take(10)}...)")
        println("NOT in joint list: ${notInJoints.size} (${notInJoints.sorted().take(10)}...)")
        println("Joint list size: ${skeleton.jointNodeIndices.size}")
    }

    @Test
    fun `expressions are parsed`() {
        // Expressions may or may not be present in the test VRM
        println("Expressions: ${vrmModel.expressions.size} (${vrmModel.expressions.map { it.name }})")
    }
}
