package com.github.narazaka.vrmmod.vrm

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.io.File

class VrmV0ConverterTest {

    // --- Task 1: Humanoid conversion ---

    @Test
    fun `converts v0 humanoid array to v1 object format`() {
        val v0 = JsonParser.parseString("""
        {
            "humanoid": {
                "humanBones": [
                    {"bone": "hips", "node": 0},
                    {"bone": "spine", "node": 1},
                    {"bone": "head", "node": 2}
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertHumanoid(v0)
        val bones = v1.getAsJsonObject("humanoid").getAsJsonObject("humanBones")
        assertEquals(0, bones.getAsJsonObject("hips").get("node").asInt)
        assertEquals(1, bones.getAsJsonObject("spine").get("node").asInt)
        assertEquals(2, bones.getAsJsonObject("head").get("node").asInt)
    }

    @Test
    fun `renames v0 thumb bones to v1 names`() {
        val v0 = JsonParser.parseString("""
        {
            "humanoid": {
                "humanBones": [
                    {"bone": "leftThumbProximal", "node": 10},
                    {"bone": "leftThumbIntermediate", "node": 11},
                    {"bone": "rightThumbProximal", "node": 20},
                    {"bone": "rightThumbIntermediate", "node": 21}
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertHumanoid(v0)
        val bones = v1.getAsJsonObject("humanoid").getAsJsonObject("humanBones")
        assertEquals(10, bones.getAsJsonObject("leftThumbMetacarpal").get("node").asInt)
        assertEquals(11, bones.getAsJsonObject("leftThumbProximal").get("node").asInt)
        assertEquals(20, bones.getAsJsonObject("rightThumbMetacarpal").get("node").asInt)
        assertEquals(21, bones.getAsJsonObject("rightThumbProximal").get("node").asInt)
    }

    // --- Task 2: Expression conversion ---

    @Test
    fun `converts v0 blendShapeGroups to v1 expressions`() {
        val v0 = JsonParser.parseString("""
        {
            "blendShapeMaster": {
                "blendShapeGroups": [
                    {
                        "presetName": "joy",
                        "name": "Joy",
                        "binds": [
                            {"mesh": 0, "index": 1, "weight": 100}
                        ],
                        "isBinary": false
                    },
                    {
                        "presetName": "a",
                        "name": "A",
                        "binds": [
                            {"mesh": 0, "index": 2, "weight": 50}
                        ]
                    }
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertExpressions(v0)
        val preset = v1.getAsJsonObject("expressions").getAsJsonObject("preset")

        // joy -> happy
        val happy = preset.getAsJsonObject("happy")
        assertNotNull(happy)
        val binds = happy.getAsJsonArray("morphTargetBinds")
        assertEquals(1, binds.size())
        val bind = binds[0].asJsonObject
        assertEquals(0, bind.get("node").asInt)
        assertEquals(1, bind.get("index").asInt)
        assertEquals(1.0f, bind.get("weight").asFloat, 0.01f) // 100 * 0.01

        // a -> aa
        val aa = preset.getAsJsonObject("aa")
        assertNotNull(aa)
        val aaBind = aa.getAsJsonArray("morphTargetBinds")[0].asJsonObject
        assertEquals(0.5f, aaBind.get("weight").asFloat, 0.01f) // 50 * 0.01
    }

    @Test
    fun `places non-preset expressions in custom`() {
        val v0 = JsonParser.parseString("""
        {
            "blendShapeMaster": {
                "blendShapeGroups": [
                    {
                        "presetName": "unknown",
                        "name": "MyCustom",
                        "binds": []
                    }
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertExpressions(v0)
        val custom = v1.getAsJsonObject("expressions").getAsJsonObject("custom")
        assertNotNull(custom.getAsJsonObject("MyCustom"))
    }

    // --- Task 3: Meta conversion ---

    @Test
    fun `converts v0 meta to v1 meta`() {
        val v0 = JsonParser.parseString("""
        {
            "meta": {
                "title": "TestAvatar",
                "version": "1.0",
                "author": "testuser",
                "contactInformation": "test@example.com",
                "reference": "https://example.com",
                "otherLicenseUrl": "https://example.com/license"
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertMeta(v0)
        val meta = v1.getAsJsonObject("meta")
        assertEquals("TestAvatar", meta.get("name").asString)
        assertEquals("1.0", meta.get("version").asString)
        assertEquals("testuser", meta.getAsJsonArray("authors")[0].asString)
        assertEquals("https://example.com/license", meta.get("licenseUrl").asString)
    }

    // --- Task 4: FirstPerson conversion ---

    @Test
    fun `converts v0 firstPerson mesh annotations`() {
        val v0 = JsonParser.parseString("""
        {
            "firstPerson": {
                "meshAnnotations": [
                    {"mesh": 0, "firstPersonFlag": "Auto"},
                    {"mesh": 1, "firstPersonFlag": "ThirdPersonOnly"},
                    {"mesh": 2, "firstPersonFlag": "FirstPersonOnly"},
                    {"mesh": 3, "firstPersonFlag": "Both"}
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertFirstPerson(v0)
        val annotations = v1.getAsJsonObject("firstPerson")
            .getAsJsonArray("meshAnnotations")
        assertEquals(4, annotations.size())
        assertEquals("auto", annotations[0].asJsonObject.get("type").asString)
        assertEquals("thirdPersonOnly", annotations[1].asJsonObject.get("type").asString)
        assertEquals("firstPersonOnly", annotations[2].asJsonObject.get("type").asString)
        assertEquals("both", annotations[3].asJsonObject.get("type").asString)
    }

    // --- Task 5: LookAt conversion ---

    @Test
    fun `converts v0 firstPersonBoneOffset to v1 lookAt offset`() {
        val v0 = JsonParser.parseString("""
        {
            "firstPerson": {
                "firstPersonBoneOffset": {"x": 0.0, "y": 0.06, "z": 0.0}
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertLookAt(v0)
        val lookAt = v1.getAsJsonObject("lookAt")
        val offset = lookAt.getAsJsonArray("offsetFromHeadBone")
        assertEquals(0.0f, offset[0].asFloat, 0.001f)
        assertEquals(0.06f, offset[1].asFloat, 0.001f)
        assertEquals(0.0f, offset[2].asFloat, 0.001f)
    }

    // --- Task 6: SpringBone conversion ---

    @Test
    fun `converts v0 springBone boneGroups to v1 springs with joint chains`() {
        // Node tree: 0 -> 1 -> 2 -> 3 (linear chain)
        //            0 -> 4 (branch)
        val nodeChildren = mapOf(
            0 to listOf(1, 4),
            1 to listOf(2),
            2 to listOf(3),
            3 to emptyList(),
            4 to emptyList(),
        )
        val v0 = JsonParser.parseString("""
        {
            "secondaryAnimation": {
                "boneGroups": [
                    {
                        "comment": "hair",
                        "stiffiness": 0.5,
                        "gravityPower": 0.1,
                        "gravityDir": {"x": 0, "y": -1, "z": 0},
                        "dragForce": 0.4,
                        "center": -1,
                        "hitRadius": 0.02,
                        "bones": [1],
                        "colliderGroups": [0]
                    }
                ],
                "colliderGroups": [
                    {
                        "node": 5,
                        "colliders": [
                            {"offset": {"x": 0, "y": 0, "z": 0}, "radius": 0.1}
                        ]
                    }
                ]
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertSpringBone(v0, nodeChildren)

        // Verify springs
        val springs = v1.getAsJsonArray("springs")
        assertEquals(1, springs.size())
        val spring = springs[0].asJsonObject
        val joints = spring.getAsJsonArray("joints")
        // Chain from root node 1: 1 -> 2 -> 3 (linear, 1 chain of 3 joints)
        assertEquals(3, joints.size())
        assertEquals(1, joints[0].asJsonObject.get("node").asInt)
        assertEquals(2, joints[1].asJsonObject.get("node").asInt)
        assertEquals(3, joints[2].asJsonObject.get("node").asInt)

        // Each joint has the group-level parameters
        val joint0 = joints[0].asJsonObject
        assertEquals(0.5f, joint0.get("stiffness").asFloat, 0.01f)
        assertEquals(0.1f, joint0.get("gravityPower").asFloat, 0.01f)
        assertEquals(0.4f, joint0.get("dragForce").asFloat, 0.01f)
        assertEquals(0.02f, joint0.get("hitRadius").asFloat, 0.01f)

        // Verify colliders (sphere only in v0)
        val colliders = v1.getAsJsonArray("colliders")
        assertEquals(1, colliders.size())
        val collider = colliders[0].asJsonObject
        assertEquals(5, collider.get("node").asInt)
        val shape = collider.getAsJsonObject("shape")
        assertNotNull(shape.getAsJsonObject("sphere"))

        // Verify collider groups
        val groups = v1.getAsJsonArray("colliderGroups")
        assertEquals(1, groups.size())
    }

    @Test
    fun `springBone handles multiple root bones in one group`() {
        // Node tree: 1 -> 2, 4 -> 5
        val nodeChildren = mapOf(
            1 to listOf(2),
            2 to emptyList(),
            4 to listOf(5),
            5 to emptyList(),
        )
        val v0 = JsonParser.parseString("""
        {
            "secondaryAnimation": {
                "boneGroups": [
                    {
                        "stiffiness": 1.0,
                        "gravityPower": 0,
                        "gravityDir": {"x": 0, "y": -1, "z": 0},
                        "dragForce": 0.5,
                        "center": -1,
                        "hitRadius": 0,
                        "bones": [1, 4],
                        "colliderGroups": []
                    }
                ],
                "colliderGroups": []
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertSpringBone(v0, nodeChildren)
        val springs = v1.getAsJsonArray("springs")
        // Each root bone becomes a separate spring
        assertEquals(2, springs.size())
        assertEquals(1, springs[0].asJsonObject.getAsJsonArray("joints")[0].asJsonObject.get("node").asInt)
        assertEquals(4, springs[1].asJsonObject.getAsJsonArray("joints")[0].asJsonObject.get("node").asInt)
    }

    @Test
    fun `springBone generates separate chains for branching children`() {
        // Node tree: 0 -> [1, 4], 1 -> [2], 2 -> [], 4 -> [5], 5 -> []
        val nodeChildren = mapOf(
            0 to listOf(1, 4),
            1 to listOf(2),
            2 to emptyList(),
            4 to listOf(5),
            5 to emptyList(),
        )
        val v0 = JsonParser.parseString("""
        {
            "secondaryAnimation": {
                "boneGroups": [
                    {
                        "stiffiness": 1.0,
                        "gravityPower": 0,
                        "gravityDir": {"x": 0, "y": -1, "z": 0},
                        "dragForce": 0.5,
                        "center": -1,
                        "hitRadius": 0,
                        "bones": [0],
                        "colliderGroups": []
                    }
                ],
                "colliderGroups": []
            }
        }
        """).asJsonObject
        val v1 = VrmV0Converter.convertSpringBone(v0, nodeChildren)
        val springs = v1.getAsJsonArray("springs")
        // Root 0 branches into two paths: 0->1->2 and 0->4->5
        assertEquals(2, springs.size())

        val chain0 = springs[0].asJsonObject.getAsJsonArray("joints")
        assertEquals(3, chain0.size())
        assertEquals(0, chain0[0].asJsonObject.get("node").asInt)
        assertEquals(1, chain0[1].asJsonObject.get("node").asInt)
        assertEquals(2, chain0[2].asJsonObject.get("node").asInt)

        val chain1 = springs[1].asJsonObject.getAsJsonArray("joints")
        assertEquals(3, chain1.size())
        assertEquals(0, chain1[0].asJsonObject.get("node").asInt)
        assertEquals(4, chain1[1].asJsonObject.get("node").asInt)
        assertEquals(5, chain1[2].asJsonObject.get("node").asInt)
    }

    // --- Task 7: convertAll integration ---

    @Test
    fun `convertAll produces VRMC_vrm and VRMC_springBone`() {
        val v0 = JsonParser.parseString("""
        {
            "meta": {"title": "Test"},
            "humanoid": {
                "humanBones": [{"bone": "hips", "node": 0}]
            },
            "blendShapeMaster": {
                "blendShapeGroups": []
            },
            "firstPerson": {
                "meshAnnotations": [],
                "firstPersonBoneOffset": {"x": 0, "y": 0.06, "z": 0}
            },
            "secondaryAnimation": {
                "boneGroups": [],
                "colliderGroups": []
            }
        }
        """).asJsonObject
        val nodeChildren = mapOf(0 to emptyList<Int>())
        val (vrmcVrm, vrmcSpringBone) = VrmV0Converter.convertAll(v0, nodeChildren)

        assertNotNull(vrmcVrm.getAsJsonObject("meta"))
        assertNotNull(vrmcVrm.getAsJsonObject("humanoid"))
        assertNotNull(vrmcVrm.getAsJsonObject("expressions"))
        assertNotNull(vrmcVrm.getAsJsonObject("firstPerson"))
        assertNotNull(vrmcVrm.getAsJsonObject("lookAt"))
        assertNotNull(vrmcSpringBone)
    }

    // --- Integration test: compare v0 and v1 parsing of the same model ---

    @Test
    fun `v0 and v1 of same model produce comparable results`() {
        val v0File = File("D:/make/3d/vrm/色見いつは_2.0.vrm")
        val v1File = File("D:/make/3d/vrm/色見いつは_2.0_vrm1.vrm")

        if (!v0File.exists()) {
            println("SKIP: VRM 0.x file not found at ${v0File.absolutePath}")
            return
        }
        if (!v1File.exists()) {
            println("SKIP: VRM 1.0 file not found at ${v1File.absolutePath}")
            return
        }

        val v0Model = v0File.inputStream().use { VrmParser.parse(it) }
        val v1Model = v1File.inputStream().use { VrmParser.parse(it) }

        // --- humanoid bones exist ---
        assertTrue(v0Model.humanoid.humanBones.isNotEmpty(), "v0 should have humanoid bones")
        assertTrue(v1Model.humanoid.humanBones.isNotEmpty(), "v1 should have humanoid bones")
        println("Humanoid bones: v0=${v0Model.humanoid.humanBones.size}, v1=${v1Model.humanoid.humanBones.size}")
        // bone counts should be roughly equal (allow small difference for optional bones)
        val boneDiff = kotlin.math.abs(v0Model.humanoid.humanBones.size - v1Model.humanoid.humanBones.size)
        assertTrue(boneDiff <= 5, "Humanoid bone count difference ($boneDiff) should be <= 5")

        // --- meshes exist ---
        assertTrue(v0Model.meshes.isNotEmpty(), "v0 should have meshes")
        assertTrue(v1Model.meshes.isNotEmpty(), "v1 should have meshes")
        println("Meshes: v0=${v0Model.meshes.size}, v1=${v1Model.meshes.size}")
        // mesh counts should be roughly equal
        val meshDiff = kotlin.math.abs(v0Model.meshes.size - v1Model.meshes.size)
        assertTrue(meshDiff <= 3, "Mesh count difference ($meshDiff) should be <= 3")

        // --- expressions exist ---
        assertTrue(v0Model.expressions.isNotEmpty(), "v0 should have expressions")
        assertTrue(v1Model.expressions.isNotEmpty(), "v1 should have expressions")
        println("Expressions: v0=${v0Model.expressions.size} (${v0Model.expressions.map { it.name }}), v1=${v1Model.expressions.size} (${v1Model.expressions.map { it.name }})")
        // expression counts should be roughly equal
        val exprDiff = kotlin.math.abs(v0Model.expressions.size - v1Model.expressions.size)
        assertTrue(exprDiff <= 5, "Expression count difference ($exprDiff) should be <= 5")

        println("Integration test PASSED: v0 and v1 models are comparable")
    }
}
