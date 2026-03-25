# VRM 0.x Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** VRM 0.x ファイルを読み込み時に VRM 1.0 内部表現に変換し、既存の v1 パスに合流させる

**Architecture:** `VrmV0Converter` が v0 の JSON 構造を v1 相当の `JsonObject` に変換する。`VrmParser` は拡張キーを見て v0/v1 を判定し、v0 なら変換後に既存パーサーに渡す。メッシュ・テクスチャ・スケルトン等の glTF コア部分は v0/v1 共通のため変換不要。

**Tech Stack:** Kotlin, Gson (JsonObject 操作), JglTF

---

## File Structure

| ファイル | 役割 |
|---------|------|
| **Create:** `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmV0Converter.kt` | v0 JSON → v1 JSON 変換ロジック全体 |
| **Modify:** `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmParser.kt` | v0/v1 分岐追加（拡張キー判定） |
| **Modify:** `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmExtensionParser.kt` | SpringBone の v0 変換結果を受け取る軽微な調整（必要に応じて） |
| **Create:** `common/src/test/kotlin/net/narazaka/vrmmod/vrm/VrmV0ConverterTest.kt` | 変換ロジックの単体テスト |

---

## Task 1: VrmV0Converter の骨格 — Humanoid 変換

v0 humanoid（配列形式 + 親指リネーム）→ v1 humanoid（オブジェクト形式）

**Files:**
- Create: `common/src/test/kotlin/net/narazaka/vrmmod/vrm/VrmV0ConverterTest.kt`
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmV0Converter.kt`

- [ ] **Step 1: Write failing test for humanoid conversion**

```kotlin
package net.narazaka.vrmmod.vrm

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VrmV0ConverterTest {

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :common:test --tests "*VrmV0ConverterTest*" -i`
Expected: FAIL — `VrmV0Converter` not found

- [ ] **Step 3: Implement VrmV0Converter with humanoid conversion**

```kotlin
package net.narazaka.vrmmod.vrm

import com.google.gson.JsonObject
import com.google.gson.JsonArray

/**
 * Converts VRM 0.x JSON structures to VRM 1.0 format.
 *
 * VRM 0.x stores everything under a single "VRM" extension key.
 * This converter transforms each subsection (humanoid, expressions,
 * springBone, meta, firstPerson, lookAt) to match VRM 1.0's
 * VRMC_vrm / VRMC_springBone structure, so the existing v1 parser
 * can process them without modification.
 */
object VrmV0Converter {

    /** v0 thumb bone name -> v1 thumb bone name */
    private val thumbBoneNameMap = mapOf(
        "leftThumbProximal" to "leftThumbMetacarpal",
        "leftThumbIntermediate" to "leftThumbProximal",
        "rightThumbProximal" to "rightThumbMetacarpal",
        "rightThumbIntermediate" to "rightThumbProximal",
    )

    /**
     * Converts v0 humanoid (array of {bone, node}) to
     * v1 humanoid (object {boneName: {node: N}}).
     */
    fun convertHumanoid(v0Json: JsonObject): JsonObject {
        val result = JsonObject()
        val v0Humanoid = v0Json.getAsJsonObject("humanoid") ?: return result
        val v0Bones = v0Humanoid.getAsJsonArray("humanBones") ?: return result

        val v1Bones = JsonObject()
        for (entry in v0Bones) {
            val obj = entry.asJsonObject
            val boneName = obj.get("bone")?.asString ?: continue
            val nodeIndex = obj.get("node")?.asInt ?: continue

            val v1Name = thumbBoneNameMap[boneName] ?: boneName
            val boneObj = JsonObject()
            boneObj.addProperty("node", nodeIndex)
            v1Bones.add(v1Name, boneObj)
        }

        val v1Humanoid = JsonObject()
        v1Humanoid.add("humanBones", v1Bones)
        result.add("humanoid", v1Humanoid)
        return result
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :common:test --tests "*VrmV0ConverterTest*" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```
git add common/src/main/kotlin/.../VrmV0Converter.kt common/src/test/kotlin/.../VrmV0ConverterTest.kt
git commit -m "feat(v0): add VrmV0Converter with humanoid conversion"
```

---

## Task 2: Expression (BlendShape) 変換

v0 `blendShapeMaster.blendShapeGroups` → v1 `expressions.preset` / `expressions.custom`

変換ポイント:
- プリセット名マッピング (joy→happy, a→aa, etc.)
- weight: 0-100 → 0-1 (×0.01)
- morphTargetBinds の `mesh` (glTF mesh index) → `node` (glTF node index)。meshToNodeMap で変換
- v0 には override/isBinary がない → デフォルト値

**Files:**
- Modify: `common/src/test/kotlin/net/narazaka/vrmmod/vrm/VrmV0ConverterTest.kt`
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmV0Converter.kt`

- [ ] **Step 1: Write failing tests for expression conversion**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement convertExpressions**

```kotlin
/** v0 preset name -> v1 preset name */
private val presetNameMap = mapOf(
    "a" to "aa",
    "e" to "ee",
    "i" to "ih",
    "o" to "oh",
    "u" to "ou",
    "joy" to "happy",
    "angry" to "angry",
    "sorrow" to "sad",
    "fun" to "relaxed",
    "blink" to "blink",
    "blink_l" to "blinkLeft",
    "blink_r" to "blinkRight",
    "lookup" to "lookUp",
    "lookdown" to "lookDown",
    "lookleft" to "lookLeft",
    "lookright" to "lookRight",
    "neutral" to "neutral",
)

/**
 * @param meshToNodeMap glTF mesh index -> node index (needed because v0 binds
 *   reference mesh indices, but v1 references node indices)
 */
fun convertExpressions(v0Json: JsonObject, meshToNodeMap: Map<Int, Int> = emptyMap()): JsonObject {
    val result = JsonObject()
    val blendShapeMaster = v0Json.getAsJsonObject("blendShapeMaster") ?: return result
    val groups = blendShapeMaster.getAsJsonArray("blendShapeGroups") ?: return result

    val preset = JsonObject()
    val custom = JsonObject()

    for (element in groups) {
        val group = element.asJsonObject
        val v0PresetName = group.get("presetName")?.asString
        val groupName = group.get("name")?.asString ?: continue

        val v1Name = v0PresetName?.let { presetNameMap[it] }

        val expr = JsonObject()

        // Convert isBinary
        val isBinary = group.get("isBinary")?.asBoolean ?: false
        if (isBinary) expr.addProperty("isBinary", true)

        // Convert binds -> morphTargetBinds
        val binds = group.getAsJsonArray("binds")
        if (binds != null && binds.size() > 0) {
            val morphTargetBinds = JsonArray()
            for (bind in binds) {
                val b = bind.asJsonObject
                val meshIdx = b.get("mesh")?.asInt ?: continue
                val morphIdx = b.get("index")?.asInt ?: continue
                val weight = (b.get("weight")?.asFloat ?: 100f) * 0.01f

                // v0 uses mesh index, v1 uses node index
                val nodeIdx = meshToNodeMap[meshIdx] ?: meshIdx

                val v1Bind = JsonObject()
                v1Bind.addProperty("node", nodeIdx)
                v1Bind.addProperty("index", morphIdx)
                v1Bind.addProperty("weight", weight)
                morphTargetBinds.add(v1Bind)
            }
            expr.add("morphTargetBinds", morphTargetBinds)
        }

        if (v1Name != null) {
            preset.add(v1Name, expr)
        } else {
            custom.add(groupName, expr)
        }
    }

    val expressions = JsonObject()
    expressions.add("preset", preset)
    if (custom.size() > 0) {
        expressions.add("custom", custom)
    }
    result.add("expressions", expressions)
    return result
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add expression/blendShape conversion"
```

---

## Task 3: Meta 変換

v0 meta → v1 meta のフィールドマッピング

**Files:**
- Modify: `VrmV0ConverterTest.kt`
- Modify: `VrmV0Converter.kt`

- [ ] **Step 1: Write failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement convertMeta**

```kotlin
fun convertMeta(v0Json: JsonObject): JsonObject {
    val result = JsonObject()
    val v0Meta = v0Json.getAsJsonObject("meta") ?: return result

    val v1Meta = JsonObject()
    v1Meta.addProperty("name", v0Meta.get("title")?.asString ?: "")
    v1Meta.addProperty("version", v0Meta.get("version")?.asString ?: "")

    val authors = JsonArray()
    v0Meta.get("author")?.asString?.let { authors.add(it) }
    v1Meta.add("authors", authors)

    v1Meta.addProperty("copyrightInformation", v0Meta.get("contactInformation")?.asString ?: "")
    v1Meta.addProperty("licenseUrl", v0Meta.get("otherLicenseUrl")?.asString ?: "")

    result.add("meta", v1Meta)
    return result
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add meta conversion"
```

---

## Task 4: FirstPerson 変換

PascalCase フラグ → camelCase タイプ、`firstPersonBone` 廃止

**Files:**
- Modify: `VrmV0ConverterTest.kt`
- Modify: `VrmV0Converter.kt`

- [ ] **Step 1: Write failing test**

```kotlin
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
```

- [ ] **Step 2-3: Implement**

```kotlin
private val firstPersonFlagMap = mapOf(
    "Auto" to "auto",
    "FirstPersonOnly" to "firstPersonOnly",
    "ThirdPersonOnly" to "thirdPersonOnly",
    "Both" to "both",
)

/**
 * @param meshToNodeMap glTF mesh index -> node index (v0 uses mesh indices)
 */
fun convertFirstPerson(v0Json: JsonObject, meshToNodeMap: Map<Int, Int> = emptyMap()): JsonObject {
    val result = JsonObject()
    val v0Fp = v0Json.getAsJsonObject("firstPerson") ?: return result

    val v1Fp = JsonObject()
    val v0Annotations = v0Fp.getAsJsonArray("meshAnnotations")
    if (v0Annotations != null) {
        val v1Annotations = JsonArray()
        for (element in v0Annotations) {
            val ann = element.asJsonObject
            val v1Ann = JsonObject()
            // v0 uses "mesh" (mesh index), v1 uses "node" (node index)
            val meshIdx = ann.get("mesh")?.asInt ?: 0
            v1Ann.addProperty("node", meshToNodeMap[meshIdx] ?: meshIdx)
            val flag = ann.get("firstPersonFlag")?.asString ?: "Auto"
            v1Ann.addProperty("type", firstPersonFlagMap[flag] ?: "auto")
            v1Annotations.add(v1Ann)
        }
        v1Fp.add("meshAnnotations", v1Annotations)
    }

    result.add("firstPerson", v1Fp)
    return result
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add firstPerson conversion"
```

---

## Task 5: LookAt 変換

v0 の `firstPerson.firstPersonBoneOffset` → v1 `lookAt.offsetFromHeadBone`

**Files:**
- Modify: `VrmV0ConverterTest.kt`
- Modify: `VrmV0Converter.kt`

- [ ] **Step 1: Write failing test**

```kotlin
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
```

- [ ] **Step 2-3: Implement**

```kotlin
fun convertLookAt(v0Json: JsonObject): JsonObject {
    val result = JsonObject()
    val v0Fp = v0Json.getAsJsonObject("firstPerson") ?: return result

    val v1LookAt = JsonObject()
    val offset = v0Fp.getAsJsonObject("firstPersonBoneOffset")
    if (offset != null) {
        val arr = JsonArray()
        arr.add(offset.get("x")?.asFloat ?: 0f)
        arr.add(offset.get("y")?.asFloat ?: 0.06f)
        arr.add(offset.get("z")?.asFloat ?: 0f)
        v1LookAt.add("offsetFromHeadBone", arr)
    }

    result.add("lookAt", v1LookAt)
    return result
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add lookAt offset conversion"
```

---

## Task 6: SpringBone 変換（最大の山）

v0 `secondaryAnimation.boneGroups` → v1 `VRMC_springBone` 形式。
ルートボーンからノードツリーをたどってジョイントチェーンを構築する必要がある。

ノードツリー情報は glTF の nodes 配列から取得。VrmParser が持つ `GltfModel` を使う。

**Files:**
- Modify: `VrmV0ConverterTest.kt`
- Modify: `VrmV0Converter.kt`

- [ ] **Step 1: Write failing test for spring bone chain building**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement convertSpringBone**

```kotlin
/**
 * Converts v0 secondaryAnimation to v1 VRMC_springBone format.
 *
 * @param nodeChildren map of node index -> list of child node indices (from glTF)
 */
fun convertSpringBone(v0Json: JsonObject, nodeChildren: Map<Int, List<Int>>): JsonObject {
    val result = JsonObject()
    val v0Anim = v0Json.getAsJsonObject("secondaryAnimation") ?: return result

    // Convert collider groups -> individual colliders + collider groups
    val v0ColliderGroups = v0Anim.getAsJsonArray("colliderGroups")
    val v1Colliders = JsonArray()
    val v1ColliderGroups = JsonArray()
    var colliderIndex = 0

    if (v0ColliderGroups != null) {
        for (element in v0ColliderGroups) {
            val v0Group = element.asJsonObject
            val nodeIdx = v0Group.get("node")?.asInt ?: continue
            val v0Colliders = v0Group.getAsJsonArray("colliders") ?: continue

            val indices = JsonArray()
            for (col in v0Colliders) {
                val colObj = col.asJsonObject
                val v1Collider = JsonObject()
                v1Collider.addProperty("node", nodeIdx)

                val shape = JsonObject()
                val sphere = JsonObject()
                val offset = colObj.getAsJsonObject("offset")
                if (offset != null) {
                    val offsetArr = JsonArray()
                    offsetArr.add(offset.get("x")?.asFloat ?: 0f)
                    offsetArr.add(offset.get("y")?.asFloat ?: 0f)
                    offsetArr.add(offset.get("z")?.asFloat ?: 0f)
                    sphere.add("offset", offsetArr)
                }
                sphere.addProperty("radius", colObj.get("radius")?.asFloat ?: 0f)
                shape.add("sphere", sphere)
                v1Collider.add("shape", shape)

                v1Colliders.add(v1Collider)
                indices.add(colliderIndex)
                colliderIndex++
            }

            val group = JsonObject()
            group.add("colliders", indices)
            v1ColliderGroups.add(group)
        }
    }

    // Convert bone groups -> springs
    val v0BoneGroups = v0Anim.getAsJsonArray("boneGroups")
    val v1Springs = JsonArray()

    if (v0BoneGroups != null) {
        for (element in v0BoneGroups) {
            val group = element.asJsonObject
            val stiffness = group.get("stiffiness")?.asFloat
                ?: group.get("stiffness")?.asFloat ?: 1f
            val gravityPower = group.get("gravityPower")?.asFloat ?: 0f
            val gravityDir = group.getAsJsonObject("gravityDir")
            val dragForce = group.get("dragForce")?.asFloat ?: 0.5f
            val hitRadius = group.get("hitRadius")?.asFloat ?: 0f
            val center = group.get("center")?.asInt ?: -1
            val rootBones = group.getAsJsonArray("bones") ?: continue
            val colliderGroupIndices = group.getAsJsonArray("colliderGroups")

            for (rootBoneEl in rootBones) {
                val rootBone = rootBoneEl.asInt
                val chains = buildJointChains(rootBone, nodeChildren)

                for (chain in chains) {
                    val v1Spring = JsonObject()
                    v1Spring.addProperty("name", group.get("comment")?.asString ?: "")
                    if (center >= 0) {
                        v1Spring.addProperty("center", center)
                    }

                    val joints = JsonArray()
                    for (nodeIdx in chain) {
                        val joint = JsonObject()
                        joint.addProperty("node", nodeIdx)
                        joint.addProperty("hitRadius", hitRadius)
                        joint.addProperty("stiffness", stiffness)
                        joint.addProperty("gravityPower", gravityPower)
                        if (gravityDir != null) {
                            val dir = JsonObject()
                            dir.addProperty("x", gravityDir.get("x")?.asFloat ?: 0f)
                            dir.addProperty("y", gravityDir.get("y")?.asFloat ?: -1f)
                            dir.addProperty("z", gravityDir.get("z")?.asFloat ?: 0f)
                            joint.add("gravityDir", dir)
                        }
                        joint.addProperty("dragForce", dragForce)
                        joints.add(joint)
                    }
                    v1Spring.add("joints", joints)

                    if (colliderGroupIndices != null && colliderGroupIndices.size() > 0) {
                        v1Spring.add("colliderGroups", colliderGroupIndices.deepCopy())
                    }

                    v1Springs.add(v1Spring)
                }
            }
        }
    }

    result.add("colliders", v1Colliders)
    result.add("colliderGroups", v1ColliderGroups)
    result.add("springs", v1Springs)
    return result
}

/**
 * Enumerates all root-to-leaf paths from a root node.
 * v0 SpringBone affects the root bone and ALL descendants.
 * v1 springs are strictly linear chains, so each root-to-leaf path
 * becomes a separate Spring.
 *
 * Example: root=0, 0->[1,4], 1->[2], 4->[5]
 * Returns: [[0,1,2], [0,4,5]]
 */
private fun buildJointChains(rootNode: Int, nodeChildren: Map<Int, List<Int>>): List<List<Int>> {
    val results = mutableListOf<List<Int>>()

    fun dfs(node: Int, path: MutableList<Int>) {
        path.add(node)
        val children = nodeChildren[node] ?: emptyList()
        if (children.isEmpty()) {
            // Leaf node: record the path
            results.add(path.toList())
        } else {
            for (child in children) {
                dfs(child, path)
            }
        }
        path.removeAt(path.size - 1)
    }

    dfs(rootNode, mutableListOf())
    return results
}
```

注: v0 SpringBone では1つの boneGroup に複数 root bones を指定でき、各 root の子孫全体がスプリング対象。v1 は線形チェーンしか表現できないため、分岐がある場合はルート→リーフの各パスを個別の Spring として生成する。

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add springBone conversion with chain building"
```

---

## Task 7: 全変換の統合 — convertAll メソッド

個別の変換メソッドを統合し、v0 の `VRM` 拡張全体を v1 相当の `VRMC_vrm` + `VRMC_springBone` JSON に変換する。

**Files:**
- Modify: `VrmV0ConverterTest.kt`
- Modify: `VrmV0Converter.kt`

- [ ] **Step 1: Write failing test**

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement convertAll**

```kotlin
data class V0ConversionResult(
    val vrmcVrm: JsonObject,
    val vrmcSpringBone: JsonObject,
)

/**
 * Converts the entire v0 VRM extension to v1 format.
 *
 * @param v0Json the "VRM" extension root object
 * @param nodeChildren node index -> child indices (from glTF nodes)
 * @param meshToNodeMap glTF mesh index -> node index (for expression/firstPerson bind conversion)
 * @return pair of (VRMC_vrm JSON, VRMC_springBone JSON)
 */
fun convertAll(
    v0Json: JsonObject,
    nodeChildren: Map<Int, List<Int>>,
    meshToNodeMap: Map<Int, Int> = emptyMap(),
): V0ConversionResult {
    val vrmcVrm = JsonObject()

    // Merge each converted section into the VRMC_vrm object
    val meta = convertMeta(v0Json)
    meta.getAsJsonObject("meta")?.let { vrmcVrm.add("meta", it) }

    val humanoid = convertHumanoid(v0Json)
    humanoid.getAsJsonObject("humanoid")?.let { vrmcVrm.add("humanoid", it) }

    val expressions = convertExpressions(v0Json, meshToNodeMap)
    expressions.getAsJsonObject("expressions")?.let { vrmcVrm.add("expressions", it) }

    val firstPerson = convertFirstPerson(v0Json, meshToNodeMap)
    firstPerson.getAsJsonObject("firstPerson")?.let { vrmcVrm.add("firstPerson", it) }

    val lookAt = convertLookAt(v0Json)
    lookAt.getAsJsonObject("lookAt")?.let { vrmcVrm.add("lookAt", it) }

    // SpringBone is a separate extension in v1
    val vrmcSpringBone = convertSpringBone(v0Json, nodeChildren)

    return V0ConversionResult(vrmcVrm, vrmcSpringBone)
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```
git commit -m "feat(v0): add convertAll to unify v0->v1 conversion"
```

---

## Task 8: VrmParser に v0 分岐を追加

`VrmParser.parse()` で拡張キーを判定し、v0 なら `VrmV0Converter` 経由で v1 パスに合流させる。

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmParser.kt`

- [ ] **Step 1: Modify VrmParser.parse() to detect v0 and convert**

`VrmParser.parse()` の拡張キー取得部分（L54-56）を以下に変更:

```kotlin
// Detect VRM version and parse extensions
val extensions = gltf.extensions
val isV1 = extensions?.containsKey("VRMC_vrm") == true
val isV0 = !isV1 && extensions?.containsKey("VRM") == true

val vrmJson: JsonObject?
val springBoneExtension: Any?

if (isV0) {
    // VRM 0.x: convert to v1 format
    val v0Extension = extensions!!["VRM"]
    val v0Json = VrmExtensionParser.toJsonObject(v0Extension)

    // Build maps from glTF model
    val nodeChildren = buildNodeChildrenMap(model)
    val meshToNodeMap = buildMeshToNodeMap(model)

    val converted = VrmV0Converter.convertAll(v0Json, nodeChildren, meshToNodeMap)
    vrmJson = converted.vrmcVrm
    springBoneExtension = converted.vrmcSpringBone
} else {
    // VRM 1.0: use directly
    val vrmExtension = extensions?.get("VRMC_vrm")
    vrmJson = vrmExtension?.let { VrmExtensionParser.toJsonObject(it) }
    springBoneExtension = extensions?.get("VRMC_springBone")
}
```

Also add helper method:

```kotlin
/**
 * Builds a map of node index -> list of child node indices from the GltfModel.
 */
private fun buildNodeChildrenMap(model: GltfModel): Map<Int, List<Int>> {
    val nodeModels = model.nodeModels
    val result = mutableMapOf<Int, List<Int>>()
    for ((idx, node) in nodeModels.withIndex()) {
        result[idx] = node.children.map { child -> nodeModels.indexOf(child) }
    }
    return result
}
```

And update the SpringBone parsing to handle both cases. The v0 path returns a `JsonObject` directly, while the v1 path returns a raw extension map. Modify the springBone parsing:

```kotlin
// Parse VRMC_springBone extension
val springBone = if (springBoneExtension is JsonObject) {
    // v0 converted: already a JsonObject, parse via VrmExtensionParser
    VrmExtensionParser.parseSpringBone(springBoneExtension)
} else {
    VrmExtensionParser.parseSpringBone(springBoneExtension)
}
```

Note: `VrmExtensionParser.parseSpringBone` calls `toJsonObject()` on its argument, which uses `Gson().toJsonTree()`. For a `JsonObject` input, this should work transparently since Gson serializes it back to the same structure. However, we should verify this works in the integration step.

- [ ] **Step 2: Build and run all tests**

Run: `./gradlew :common:test`
Expected: PASS (no regressions)

- [ ] **Step 3: Build full project**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git commit -m "feat(v0): integrate VrmV0Converter into VrmParser for v0/v1 auto-detection"
```

---

## Task 9: VrmExtensionParser.parseSpringBone の JsonObject 直接受け取り対応

v0 変換後は `JsonObject` が直接渡されるが、既存の `parseSpringBone` は `Any?` を受けて `toJsonObject()` で変換する。`JsonObject` が渡された場合は変換をスキップする。

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmExtensionParser.kt`

- [ ] **Step 1: Update parseSpringBone to handle JsonObject directly**

```kotlin
fun parseSpringBone(extensionMap: Any?): VrmSpringBone {
    if (extensionMap == null) return VrmSpringBone()
    val json = if (extensionMap is JsonObject) extensionMap else toJsonObject(extensionMap)
    // ... rest unchanged
}
```

- [ ] **Step 2: Build and test**

Run: `./gradlew :common:test && ./gradlew build`
Expected: PASS

- [ ] **Step 3: Commit**

```
git commit -m "fix: allow parseSpringBone to accept JsonObject directly for v0 path"
```

---

## Task 10: meshToNodeMap ヘルパーを VrmParser に追加

`convertAll` に渡す `meshToNodeMap` (glTF mesh index → node index) を VrmParser に追加する。
expression と firstPerson の変換で mesh→node 逆引きに使用される。

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmParser.kt`

- [ ] **Step 1: Add buildMeshToNodeMap helper**

```kotlin
/**
 * Builds a reverse mapping from glTF mesh index to the first node index that references it.
 * Used for v0 conversion where expressions/firstPerson use mesh indices.
 */
private fun buildMeshToNodeMap(model: GltfModel): Map<Int, Int> {
    val result = mutableMapOf<Int, Int>()
    for ((nodeIdx, nodeModel) in model.nodeModels.withIndex()) {
        val meshModels = nodeModel.meshModels
        if (meshModels.isNotEmpty()) {
            val meshIdx = model.meshModels.indexOf(meshModels[0])
            if (meshIdx >= 0 && meshIdx !in result) {
                result[meshIdx] = nodeIdx
            }
        }
    }
    return result
}
```

- [ ] **Step 2: Build and test**

Run: `./gradlew :common:test && ./gradlew build`
Expected: PASS

- [ ] **Step 3: Commit**

```
git commit -m "feat(v0): add buildMeshToNodeMap helper for v0 mesh->node resolution"
```

---

## Task 11: 統合テスト — 実際の VRM 0.x ファイルで検証

実際の VRM 0.x ファイルがある場合のスモークテスト。ファイルがなければスキップ。

**Files:**
- Modify: `VrmV0ConverterTest.kt`

- [ ] **Step 1: Add integration test**

```kotlin
@Test
fun `parse real v0 VRM file if available`() {
    // Try common test data paths
    val testFile = java.io.File("../testdata/test-avatar-v0.vrm")
    if (!testFile.exists()) {
        println("v0 test VRM file not found, skipping integration test")
        return
    }
    val model = VrmParser.parse(java.io.FileInputStream(testFile))
    assertTrue(model.humanoid.humanBones.isNotEmpty(), "Should have humanoid bones")
    assertTrue(model.meshes.isNotEmpty(), "Should have meshes")
    println("v0 VRM parsed: ${model.meta.name}, bones=${model.humanoid.humanBones.size}, meshes=${model.meshes.size}")
}
```

- [ ] **Step 2: Run and verify**

- [ ] **Step 3: Commit**

```
git commit -m "test(v0): add integration test for real v0 VRM files"
```

---

## Task 12: CURRENT_STATUS.md 更新

VRM 0.x 対応を完成済みに移動。

**Files:**
- Modify: `docs/CURRENT_STATUS.md`

- [ ] **Step 1: Update status document**

低優先の「VRM 0.x 対応」を完成済みに移動し、変換の概要を記載。

- [ ] **Step 2: Commit**

```
git commit -m "docs: update CURRENT_STATUS.md for VRM 0.x support"
```
