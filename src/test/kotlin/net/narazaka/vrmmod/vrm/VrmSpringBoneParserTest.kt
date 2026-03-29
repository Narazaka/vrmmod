package net.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class VrmSpringBoneParserTest {

    @Test
    fun `dump VRMC_springBone extension structure`() {
        val vrmFile = File("../testdata/test-avatar.vrm")
        assertTrue(vrmFile.exists(), "Test VRM file should exist at ${vrmFile.absolutePath}")

        // Read raw glTF to inspect the extension structure
        val bytes = vrmFile.readBytes()
        val rawData = de.javagl.jgltf.model.io.RawGltfDataReader.read(java.io.ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltfReader = de.javagl.jgltf.model.io.v2.GltfReaderV2()
        val gltf = gltfReader.read(java.io.ByteArrayInputStream(jsonBytes))

        val springBoneExt = gltf.extensions?.get("VRMC_springBone")
        assertNotNull(springBoneExt, "VRMC_springBone extension should exist")

        val json = VrmExtensionParser.toJsonObject(springBoneExt!!)
        println("VRMC_springBone keys: ${json.keySet()}")
        println("VRMC_springBone full: ${json}")
    }

    @Test
    fun `parseSpringBone extracts spring bone data from test VRM`() {
        val vrmFile = File("../testdata/test-avatar.vrm")
        assertTrue(vrmFile.exists(), "Test VRM file should exist at ${vrmFile.absolutePath}")

        val model = VrmParser.parse(vrmFile.inputStream())

        val springBone = model.springBone
        assertNotNull(springBone)

        println("Springs: ${springBone.springs.size}")
        println("Colliders: ${springBone.colliders.size}")
        println("ColliderGroups: ${springBone.colliderGroups.size}")

        for (spring in springBone.springs) {
            println("  Spring '${spring.name}': ${spring.joints.size} joints, colliderGroups=${spring.colliderGroupIndices}")
            for (joint in spring.joints) {
                println("    Joint node=${joint.nodeIndex}, stiffness=${joint.stiffness}, drag=${joint.dragForce}, gravity=${joint.gravityPower}")
            }
        }
    }

    @Test
    fun `parseSpringBone returns empty for null input`() {
        val result = VrmExtensionParser.parseSpringBone(null)
        assertEquals(VrmSpringBone(), result)
    }

    @Test
    fun `parseSpringBone parses sphere collider`() {
        val json = mapOf(
            "colliders" to listOf(
                mapOf(
                    "node" to 5,
                    "shape" to mapOf(
                        "sphere" to mapOf(
                            "offset" to mapOf("x" to 0.1, "y" to 0.2, "z" to 0.3),
                            "radius" to 0.05,
                        ),
                    ),
                ),
            ),
            "colliderGroups" to listOf(
                mapOf(
                    "name" to "head",
                    "colliders" to listOf(0),
                ),
            ),
            "springs" to listOf(
                mapOf(
                    "name" to "hair",
                    "joints" to listOf(
                        mapOf(
                            "node" to 10,
                            "hitRadius" to 0.02,
                            "stiffness" to 0.5,
                            "gravityPower" to 1.0,
                            "gravityDir" to mapOf("x" to 0.0, "y" to -1.0, "z" to 0.0),
                            "dragForce" to 0.4,
                        ),
                    ),
                    "colliderGroups" to listOf(0),
                ),
            ),
        )

        val result = VrmExtensionParser.parseSpringBone(json)

        assertEquals(1, result.colliders.size)
        val collider = result.colliders[0]
        assertEquals(5, collider.nodeIndex)
        assertTrue(collider.shape is ColliderShape.Sphere)
        val sphere = collider.shape as ColliderShape.Sphere
        assertEquals(0.05f, sphere.radius, 0.001f)

        assertEquals(1, result.colliderGroups.size)
        assertEquals("head", result.colliderGroups[0].name)
        assertEquals(listOf(0), result.colliderGroups[0].colliderIndices)

        assertEquals(1, result.springs.size)
        val spring = result.springs[0]
        assertEquals("hair", spring.name)
        assertEquals(1, spring.joints.size)
        assertEquals(10, spring.joints[0].nodeIndex)
        assertEquals(0.5f, spring.joints[0].stiffness, 0.001f)
        assertEquals(0.4f, spring.joints[0].dragForce, 0.001f)
    }

    @Test
    fun `parseSpringBone parses capsule collider`() {
        val json = mapOf(
            "colliders" to listOf(
                mapOf(
                    "node" to 3,
                    "shape" to mapOf(
                        "capsule" to mapOf(
                            "offset" to mapOf("x" to 0.0, "y" to 0.1, "z" to 0.0),
                            "radius" to 0.03,
                            "tail" to mapOf("x" to 0.0, "y" to 0.5, "z" to 0.0),
                        ),
                    ),
                ),
            ),
        )

        val result = VrmExtensionParser.parseSpringBone(json)

        assertEquals(1, result.colliders.size)
        val collider = result.colliders[0]
        assertTrue(collider.shape is ColliderShape.Capsule)
        val capsule = collider.shape as ColliderShape.Capsule
        assertEquals(0.03f, capsule.radius, 0.001f)
        assertEquals(0.5f, capsule.tail.y, 0.001f)
    }
}
