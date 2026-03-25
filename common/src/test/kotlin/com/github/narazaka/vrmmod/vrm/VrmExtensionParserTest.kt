package com.github.narazaka.vrmmod.vrm

import com.google.gson.JsonParser
import de.javagl.jgltf.model.io.GltfAssetReader
import de.javagl.jgltf.model.io.v2.GltfAssetV2
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Path

class VrmExtensionParserTest {

    @Nested
    inner class ParseMeta {
        @Test
        fun `parses meta with all fields`() {
            val json = JsonParser.parseString("""
                {
                    "meta": {
                        "name": "TestAvatar",
                        "version": "1.0",
                        "authors": ["Author1", "Author2"],
                        "copyrightInformation": "Copyright 2024",
                        "licenseUrl": "https://example.com/license"
                    }
                }
            """).asJsonObject

            val meta = VrmExtensionParser.parseMeta(json)
            assertEquals("TestAvatar", meta.name)
            assertEquals("1.0", meta.version)
            assertEquals(listOf("Author1", "Author2"), meta.authors)
            assertEquals("Copyright 2024", meta.copyrightInformation)
            assertEquals("https://example.com/license", meta.licenseUrl)
        }

        @Test
        fun `parses meta with minimal fields`() {
            val json = JsonParser.parseString("""
                {
                    "meta": {
                        "name": "Minimal"
                    }
                }
            """).asJsonObject

            val meta = VrmExtensionParser.parseMeta(json)
            assertEquals("Minimal", meta.name)
            assertEquals("", meta.version)
            assertEquals(emptyList<String>(), meta.authors)
        }

        @Test
        fun `returns default meta when meta block is missing`() {
            val json = JsonParser.parseString("{}").asJsonObject
            val meta = VrmExtensionParser.parseMeta(json)
            assertEquals("", meta.name)
        }
    }

    @Nested
    inner class ParseHumanoid {
        @Test
        fun `parses humanoid bones`() {
            val json = JsonParser.parseString("""
                {
                    "humanoid": {
                        "humanBones": {
                            "hips": {"node": 0},
                            "spine": {"node": 1},
                            "head": {"node": 5},
                            "leftUpperArm": {"node": 10},
                            "rightUpperArm": {"node": 20}
                        }
                    }
                }
            """).asJsonObject

            val humanoid = VrmExtensionParser.parseHumanoid(json)
            assertEquals(5, humanoid.humanBones.size)
            assertEquals(0, humanoid.humanBones[HumanBone.HIPS]?.nodeIndex)
            assertEquals(5, humanoid.humanBones[HumanBone.HEAD]?.nodeIndex)
            assertEquals(10, humanoid.humanBones[HumanBone.LEFT_UPPER_ARM]?.nodeIndex)
        }

        @Test
        fun `skips unknown bone names`() {
            val json = JsonParser.parseString("""
                {
                    "humanoid": {
                        "humanBones": {
                            "hips": {"node": 0},
                            "unknownBone": {"node": 99}
                        }
                    }
                }
            """).asJsonObject

            val humanoid = VrmExtensionParser.parseHumanoid(json)
            assertEquals(1, humanoid.humanBones.size)
            assertNotNull(humanoid.humanBones[HumanBone.HIPS])
        }

        @Test
        fun `returns empty humanoid when block is missing`() {
            val json = JsonParser.parseString("{}").asJsonObject
            val humanoid = VrmExtensionParser.parseHumanoid(json)
            assertTrue(humanoid.humanBones.isEmpty())
        }
    }

    @Nested
    inner class ParseExpressions {
        @Test
        fun `parses preset expressions`() {
            val json = JsonParser.parseString("""
                {
                    "expressions": {
                        "preset": {
                            "happy": {
                                "morphTargetBinds": [
                                    {"node": 0, "index": 1, "weight": 1.0}
                                ]
                            },
                            "angry": {
                                "morphTargetBinds": []
                            }
                        }
                    }
                }
            """).asJsonObject

            val expressions = VrmExtensionParser.parseExpressions(json)
            assertEquals(2, expressions.size)

            val happy = expressions.find { it.name == "happy" }
            assertNotNull(happy)
            assertEquals("happy", happy!!.preset)
            assertEquals(1, happy.morphTargetBinds.size)
            assertEquals(0, happy.morphTargetBinds[0].nodeIndex)
            assertEquals(1, happy.morphTargetBinds[0].morphTargetIndex)
            assertEquals(1.0f, happy.morphTargetBinds[0].weight)
        }

        @Test
        fun `parses custom expressions`() {
            val json = JsonParser.parseString("""
                {
                    "expressions": {
                        "custom": {
                            "myExpression": {
                                "morphTargetBinds": [
                                    {"node": 2, "index": 3, "weight": 0.5}
                                ]
                            }
                        }
                    }
                }
            """).asJsonObject

            val expressions = VrmExtensionParser.parseExpressions(json)
            assertEquals(1, expressions.size)
            assertEquals("myExpression", expressions[0].name)
            assertEquals("", expressions[0].preset)
        }

        @Test
        fun `returns empty list when expressions is null`() {
            val expressions = VrmExtensionParser.parseExpressions(null)
            assertTrue(expressions.isEmpty())
        }

        @Test
        fun `returns empty list when expressions block is missing`() {
            val json = JsonParser.parseString("{}").asJsonObject
            val expressions = VrmExtensionParser.parseExpressions(json)
            assertTrue(expressions.isEmpty())
        }
    }

    @Nested
    inner class IntegrationWithActualVrm {
        private val vrmPath: Path = Path.of("../testdata/test-avatar.vrm")

        @Test
        fun `parses VRMC_vrm extension from actual VRM file`() {
            assertTrue(vrmPath.toFile().exists(), "Test VRM file must exist at $vrmPath")

            val assetReader = GltfAssetReader()
            val gltfAsset = assetReader.read(vrmPath.toUri())
            val asset = gltfAsset as GltfAssetV2

            val extensions = asset.gltf.extensions
            assertNotNull(extensions)
            assertTrue(extensions.containsKey("VRMC_vrm"))

            val vrmExtension = extensions["VRMC_vrm"]!!
            val json = VrmExtensionParser.toJsonObject(vrmExtension)

            // Parse meta
            val meta = VrmExtensionParser.parseMeta(json)
            assertTrue(meta.name.isNotEmpty(), "Meta name should be non-empty")
            println("Meta: name=${meta.name}, authors=${meta.authors}")

            // Parse humanoid
            val humanoid = VrmExtensionParser.parseHumanoid(json)
            assertTrue(humanoid.humanBones.isNotEmpty(), "Humanoid should have bones")
            assertNotNull(humanoid.humanBones[HumanBone.HIPS], "Should have HIPS bone")
            assertNotNull(humanoid.humanBones[HumanBone.HEAD], "Should have HEAD bone")
            println("Humanoid bones: ${humanoid.humanBones.size}")

            // Parse expressions
            val expressions = VrmExtensionParser.parseExpressions(json)
            println("Expressions: ${expressions.size} (${expressions.map { it.name }})")
        }
    }
}
