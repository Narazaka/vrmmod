# VRM Mod MVP Implementation Plan (Phases 1-3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** VRM 1.0 モデルをMinecraft内でプレイヤーアバターとしてアニメーション付きで表示する（P0: MVP）

**Architecture:** Architectury APIによるFabric/NeoForge両対応mod。JglTFでglTF基本データをパース、Gson でVRM拡張をパース、CPUスキニングで `VertexConsumer` に頂点を書き込み描画。Mixin で `PlayerRenderer` を差し替え。

**Tech Stack:** Kotlin + Java (Mixin), Architectury API, JglTF, Gradle, Minecraft 1.21.x, LWJGL/OpenGL

**Spec:** `docs/superpowers/specs/2026-03-24-vrm-minecraft-mod-design.md`

---

## File Structure

### Phase 1: Project Setup + VRM Parser

```
vrmmod/
├── settings.gradle.kts                          # Architectury multi-project settings
├── build.gradle.kts                             # Root build config
├── gradle.properties                            # Version constants (MC, Fabric, NeoForge, Architectury)
├── common/
│   ├── build.gradle.kts                         # Common module build (JglTF dep, Kotlin)
│   └── src/
│       ├── main/kotlin/com/github/narazaka/vrmmod/
│       │   ├── VrmMod.kt                        # Mod entrypoint (common init)
│       │   └── vrm/
│       │       ├── VrmModel.kt                  # Top-level VRM model data class
│       │       ├── VrmMeta.kt                   # Meta info (title, author, license)
│       │       ├── VrmHumanoid.kt               # Humanoid bone mapping + HumanBone enum
│       │       ├── VrmMesh.kt                   # Mesh vertex data
│       │       ├── VrmSkeleton.kt               # Node tree + bone hierarchy
│       │       ├── VrmTexture.kt                # Texture binary data
│       │       ├── VrmExpression.kt             # Expression definitions (stub for Phase 1)
│       │       ├── VrmSpringBone.kt             # SpringBone definitions (stub for Phase 1)
│       │       ├── VrmParser.kt                 # Orchestrates JglTF + extension parsing
│       │       └── VrmExtensionParser.kt        # Parses VRMC_vrm etc. from JSON extensions
│       └── test/kotlin/com/github/narazaka/vrmmod/vrm/
│           ├── VrmParserTest.kt                 # Parser integration test with sample .vrm
│           └── VrmExtensionParserTest.kt        # Extension JSON parsing unit tests
├── fabric/
│   ├── build.gradle.kts                         # Fabric module build
│   └── src/main/
│       ├── kotlin/com/github/narazaka/vrmmod/fabric/
│       │   └── VrmModFabric.kt                  # Fabric entrypoint
│       └── resources/
│           ├── fabric.mod.json                  # Fabric mod metadata
│           └── vrmmod.mixins.json               # Mixin config (empty for Phase 1)
├── neoforge/
│   ├── build.gradle.kts                         # NeoForge module build
│   └── src/main/
│       ├── kotlin/com/github/narazaka/vrmmod/neoforge/
│       │   └── VrmModNeoForge.kt                # NeoForge entrypoint
│       └── resources/
│           └── META-INF/
│               ├── neoforge.mods.toml           # NeoForge mod metadata
│               └── vrmmod.mixins.json           # Mixin config (empty for Phase 1)
└── testdata/
    └── test-avatar.vrm                          # Sample VRM 1.0 file for testing
```

### Phase 2: Static Rendering (T-Pose)

```
common/src/main/kotlin/com/github/narazaka/vrmmod/
├── render/
│   ├── VrmRenderer.kt              # Core VRM rendering logic (mesh → VertexConsumer)
│   ├── VrmTextureManager.kt        # DynamicTexture registration/cleanup
│   └── VrmPlayerManager.kt         # Maps player UUID → loaded VrmModel state
├── client/
│   └── VrmModClient.kt             # Client-side init (register key, load model on join)
fabric/src/main/
├── java/com/github/narazaka/vrmmod/fabric/mixin/
│   └── PlayerRendererMixin.java     # Fabric Mixin for PlayerRenderer
├── resources/
│   └── vrmmod.mixins.json          # Updated with Mixin class
neoforge/src/main/
├── java/com/github/narazaka/vrmmod/neoforge/mixin/
│   └── PlayerRendererMixin.java     # NeoForge Mixin for PlayerRenderer
├── resources/META-INF/
│   └── vrmmod.mixins.json          # Updated with Mixin class
```

### Phase 3: Bone Animation

```
common/src/main/kotlin/com/github/narazaka/vrmmod/
├── animation/
│   ├── PoseProvider.kt              # Interface + BonePose, BonePoseMap, PoseContext
│   ├── VanillaPoseProvider.kt       # Maps MC player state → VRM humanoid bone poses
│   └── HumanBone.kt                # (moved from vrm/ if needed, or re-export)
├── render/
│   ├── VrmRenderer.kt              # Updated: CPU skinning with bone matrices
│   └── VrmSkinningEngine.kt        # Bone matrix computation + vertex skinning
```

---

## Task 1: Architectury プロジェクト初期構築

**概要:** Architectury テンプレートに基づいて Gradle multi-project を構築し、Kotlin 対応とビルド通過を確認する。

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `common/build.gradle.kts`
- Create: `fabric/build.gradle.kts`
- Create: `neoforge/build.gradle.kts`
- Create: `fabric/src/main/resources/fabric.mod.json`
- Create: `neoforge/src/main/resources/META-INF/neoforge.mods.toml`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/VrmMod.kt`
- Create: `fabric/src/main/kotlin/com/github/narazaka/vrmmod/fabric/VrmModFabric.kt`
- Create: `neoforge/src/main/kotlin/com/github/narazaka/vrmmod/neoforge/VrmModNeoForge.kt`

**重要な注意:** Architectury のビルド構成は複雑で、バージョン依存が多い。最新の Architectury テンプレートジェネレーター (https://generate.architectury.dev/) の出力を参考にすること。以下は概念的な構成であり、実際のバージョン番号やAPI名は生成時の最新版に合わせる必要がある。

- [ ] **Step 1: Gradle Wrapper を初期化**

```bash
cd x:/make/devel/vrmmod
gradle wrapper --gradle-version 8.10
```

Gradle がインストールされていない場合は先にインストールする。

- [ ] **Step 2: gradle.properties を作成**

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4G
kotlin.code.style=official

# Mod
mod_id=vrmmod
mod_version=0.1.0
mod_group=com.github.narazaka.vrmmod
mod_name=VRM Mod
mod_description=VRM avatar mod for Minecraft
mod_author=narazaka

# Platforms
enabled_platforms=fabric,neoforge

# Minecraft & Dependencies (バージョンは実装時に最新を確認)
minecraft_version=1.21.4
architectury_api_version=15.0.1
fabric_loader_version=0.16.10
fabric_api_version=0.112.0+1.21.4
fabric_language_kotlin_version=1.13.0+kotlin.2.1.0
neoforge_version=21.4.0-beta
kotlin_for_forge_version=5.7.0

# Kotlin
kotlin_version=2.1.0
```

- [ ] **Step 3: settings.gradle.kts を作成**

```kotlin
pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
        gradlePluginPortal()
    }
}

include("common", "fabric", "neoforge")

rootProject.name = "vrmmod"
```

- [ ] **Step 4: ルート build.gradle.kts を作成**

```kotlin
plugins {
    id("architectury-plugin") version "3.4-SNAPSHOT"
    id("dev.architectury.loom") version "1.9-SNAPSHOT" apply false
    kotlin("jvm") version property("kotlin_version").toString() apply false
}

architectury {
    minecraft = property("minecraft_version").toString()
}

allprojects {
    group = property("mod_group").toString()
    version = property("mod_version").toString()
}

subprojects {
    apply(plugin = "dev.architectury.loom")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        maven("https://maven.architectury.dev/")
        maven("https://maven.neoforged.net/releases/")
    }

    dependencies {
        "minecraft"("com.mojang:minecraft:${property("minecraft_version")}")
        "mappings"(loom.officialMojangMappings())
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "21"
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
```

注意: `loom` は `dev.architectury.loom` プラグインが提供するエクステンション。正確な構文は Architectury テンプレートの最新版に合わせること。

- [ ] **Step 5: common/build.gradle.kts を作成**

```kotlin
architectury {
    common(property("enabled_platforms").toString().split(","))
}

dependencies {
    modImplementation("dev.architectury:architectury:${property("architectury_api_version")}")

    // JglTF for glTF/VRM parsing
    implementation("de.javagl:jgltf-model:2.0.4")

    // JOML for tests (Minecraft includes it at runtime via LWJGL, but tests need it explicitly)
    testImplementation("org.joml:joml:1.10.8")
}
```

- [ ] **Step 6: fabric/build.gradle.kts を作成**

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    fabric()
}

configurations {
    create("common")
    create("shadowCommon")
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
}

dependencies {
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${property("fabric_language_kotlin_version")}")
    modImplementation("dev.architectury:architectury-fabric:${property("architectury_api_version")}")

    "common"(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    "shadowCommon"(project(path = ":common", configuration = "transformProductionFabric")) { isTransitive = false }
}
```

- [ ] **Step 7: neoforge/build.gradle.kts を作成**

Fabric と類似だが NeoForge 固有の設定が必要。`KotlinForForge` を使って Kotlin をサポートする。

```kotlin
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

architectury {
    platformSetupLoomIde()
    neoForge()
}

configurations {
    create("common")
    create("shadowCommon")
    compileClasspath.get().extendsFrom(configurations["common"])
    runtimeClasspath.get().extendsFrom(configurations["common"])
}

dependencies {
    neoForge("net.neoforged:neoforge:${property("neoforge_version")}")
    modImplementation("dev.architectury:architectury-neoforge:${property("architectury_api_version")}")
    implementation("thedarkcolour:kotlinforforge-neoforge:${property("kotlin_for_forge_version")}")

    "common"(project(path = ":common", configuration = "namedElements")) { isTransitive = false }
    "shadowCommon"(project(path = ":common", configuration = "transformProductionNeoForge")) { isTransitive = false }
}
```

- [ ] **Step 8: fabric.mod.json を作成**

```json
{
  "schemaVersion": 1,
  "id": "vrmmod",
  "version": "${version}",
  "name": "VRM Mod",
  "description": "VRM avatar mod for Minecraft",
  "authors": ["narazaka"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": [
      {
        "adapter": "kotlin",
        "value": "com.github.narazaka.vrmmod.fabric.VrmModFabric"
      }
    ]
  },
  "mixins": ["vrmmod.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.0",
    "minecraft": "~1.21.4",
    "architectury": ">=15.0.0",
    "fabric-language-kotlin": ">=1.13.0"
  }
}
```

- [ ] **Step 9: neoforge.mods.toml を作成**

NeoForge 向けの mod メタデータ。

- [ ] **Step 10: Mixin 設定ファイルを作成（空）**

`fabric/src/main/resources/vrmmod.mixins.json`:
```json
{
  "required": true,
  "package": "com.github.narazaka.vrmmod.fabric.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [],
  "injectors": {
    "defaultRequire": 1
  }
}
```

NeoForge 側も同様。

- [ ] **Step 11: 最小限のエントリポイントクラスを作成**

`common/src/main/kotlin/com/github/narazaka/vrmmod/VrmMod.kt`:
```kotlin
package com.github.narazaka.vrmmod

object VrmMod {
    const val MOD_ID = "vrmmod"

    fun init() {
        // Common initialization
    }
}
```

`fabric/src/main/kotlin/com/github/narazaka/vrmmod/fabric/VrmModFabric.kt`:
```kotlin
package com.github.narazaka.vrmmod.fabric

import com.github.narazaka.vrmmod.VrmMod
import net.fabricmc.api.ClientModInitializer

object VrmModFabric : ClientModInitializer {
    override fun onInitializeClient() {
        VrmMod.init()
    }
}
```

NeoForge 側も同様。

- [ ] **Step 12: ビルドが通ることを確認**

```bash
cd x:/make/devel/vrmmod
./gradlew build
```

Expected: BUILD SUCCESSFUL（警告は許容）

- [ ] **Step 13: Commit**

```bash
git add -A
git commit -m "feat: initialize Architectury project with Fabric/NeoForge support"
```

---

## Task 2: テスト用VRMファイルの準備

**概要:** テストに使用するVRM 1.0サンプルファイルを用意する。

**Files:**
- Create: `testdata/test-avatar.vrm`
- Create: `testdata/README.md`

- [ ] **Step 1: テスト用VRMファイルを入手**

VRoid Studio で最小限のVRM 1.0ファイルをエクスポートするか、CC0/パブリックドメインのVRM 1.0サンプルをダウンロードする。

候補:
- VRoid Studio のデフォルトアバターをVRM 1.0で出力
- VRM公式のサンプルモデル（https://github.com/vrm-c/vrm-specification のサンプル）

- [ ] **Step 2: testdata/ に配置し README を作成**

`testdata/README.md`:
```markdown
# Test Data

## test-avatar.vrm
VRM 1.0 format test avatar.
Source: [入手元を記載]
License: [ライセンスを記載]
```

- [ ] **Step 3: .gitignore に .vrm の大容量ファイルを除外するか、Git LFS を設定**

```bash
# .vrm ファイルが大きい場合
git lfs track "*.vrm"
git add .gitattributes testdata/
git commit -m "chore: add test VRM file"
```

または小さいファイルならそのまま commit。

---

## Task 3: VRM 内部データモデル定義

**概要:** VRM 1.0 のデータを表現する Kotlin data class 群を定義する。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmModel.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmMeta.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmHumanoid.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmMesh.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmSkeleton.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmTexture.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmExpression.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmSpringBone.kt`

- [ ] **Step 1: HumanBone enum を定義**

`VrmHumanoid.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

import org.joml.Matrix4f

/**
 * VRM 1.0 Humanoid bone names.
 * See: https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md
 */
enum class HumanBone {
    // Torso
    HIPS, SPINE, CHEST, UPPER_CHEST, NECK,
    // Head
    HEAD,
    // Left Arm
    LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_LOWER_ARM, LEFT_HAND,
    // Right Arm
    RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM, RIGHT_HAND,
    // Left Leg
    LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT, LEFT_TOES,
    // Right Leg
    RIGHT_UPPER_LEG, RIGHT_LOWER_LEG, RIGHT_FOOT, RIGHT_TOES,
    // Left Fingers (optional)
    LEFT_THUMB_METACARPAL, LEFT_THUMB_PROXIMAL, LEFT_THUMB_DISTAL,
    LEFT_INDEX_PROXIMAL, LEFT_INDEX_INTERMEDIATE, LEFT_INDEX_DISTAL,
    LEFT_MIDDLE_PROXIMAL, LEFT_MIDDLE_INTERMEDIATE, LEFT_MIDDLE_DISTAL,
    LEFT_RING_PROXIMAL, LEFT_RING_INTERMEDIATE, LEFT_RING_DISTAL,
    LEFT_LITTLE_PROXIMAL, LEFT_LITTLE_INTERMEDIATE, LEFT_LITTLE_DISTAL,
    // Right Fingers (optional)
    RIGHT_THUMB_METACARPAL, RIGHT_THUMB_PROXIMAL, RIGHT_THUMB_DISTAL,
    RIGHT_INDEX_PROXIMAL, RIGHT_INDEX_INTERMEDIATE, RIGHT_INDEX_DISTAL,
    RIGHT_MIDDLE_PROXIMAL, RIGHT_MIDDLE_INTERMEDIATE, RIGHT_MIDDLE_DISTAL,
    RIGHT_RING_PROXIMAL, RIGHT_RING_INTERMEDIATE, RIGHT_RING_DISTAL,
    RIGHT_LITTLE_PROXIMAL, RIGHT_LITTLE_INTERMEDIATE, RIGHT_LITTLE_DISTAL,
    // Eyes (optional)
    LEFT_EYE, RIGHT_EYE, JAW;

    companion object {
        /** VRM 1.0 JSON key (camelCase) → enum */
        fun fromVrmName(name: String): HumanBone? = entries.find {
            it.name.lowercase().replace("_", "") == name.lowercase().replace("_", "")
        }
    }
}

data class VrmHumanoid(
    /** Maps HumanBone → node index in the glTF node array */
    val boneToNodeIndex: Map<HumanBone, Int>
)
```

- [ ] **Step 2: メッシュ・スケルトン・テクスチャのデータクラスを定義**

`VrmMesh.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

import java.nio.FloatBuffer
import java.nio.IntBuffer

data class VrmMesh(
    val name: String,
    val primitives: List<VrmPrimitive>,
)

data class VrmPrimitive(
    val positions: FloatBuffer,    // x,y,z × vertexCount
    val normals: FloatBuffer?,     // x,y,z × vertexCount
    val texCoords: FloatBuffer?,   // u,v × vertexCount
    val joints: IntBuffer?,        // j0,j1,j2,j3 × vertexCount (bone indices)
    val weights: FloatBuffer?,     // w0,w1,w2,w3 × vertexCount
    val indices: IntBuffer,        // triangle indices
    val vertexCount: Int,
    val materialIndex: Int,
)
```

`VrmSkeleton.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

data class VrmNode(
    val index: Int,
    val name: String,
    val translation: Vector3f,
    val rotation: Quaternionf,
    val scale: Vector3f,
    val childIndices: List<Int>,
)

data class VrmSkeleton(
    val nodes: List<VrmNode>,
    val rootNodeIndices: List<Int>,
    val inverseBindMatrices: List<Matrix4f>,   // per joint
    val jointNodeIndices: List<Int>,           // skin.joints → node index
)
```

`VrmTexture.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

data class VrmTexture(
    val index: Int,
    val name: String?,
    val imageData: ByteArray,      // PNG/JPEG bytes
    val mimeType: String,
)
```

`VrmMeta.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

data class VrmMeta(
    val name: String,
    val version: String?,
    val authors: List<String>,
    val copyrightInformation: String?,
    val licenseUrl: String?,
)
```

- [ ] **Step 3: Expression / SpringBone のスタブ定義**

`VrmExpression.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

data class VrmExpression(
    val name: String,
    val preset: String?,       // "happy", "angry", "blink", etc.
    val morphTargetBinds: List<MorphTargetBind>,
)

data class MorphTargetBind(
    val meshIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float,
)
```

`VrmSpringBone.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

// Stub for Phase 1. Full implementation in Phase 4.
data class VrmSpringBoneData(
    val springs: List<Spring>,
    val colliders: List<Collider>,
) {
    data class Spring(val jointNodeIndices: List<Int>)
    data class Collider(val nodeIndex: Int)
}
```

- [ ] **Step 4: VrmModel トップレベルクラス**

`VrmModel.kt`:
```kotlin
package com.github.narazaka.vrmmod.vrm

data class VrmModel(
    val meta: VrmMeta,
    val humanoid: VrmHumanoid,
    val meshes: List<VrmMesh>,
    val skeleton: VrmSkeleton,
    val textures: List<VrmTexture>,
    val expressions: List<VrmExpression>,
    val springBones: VrmSpringBoneData?,
)
```

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/
git commit -m "feat: define VRM 1.0 internal data model classes"
```

---

## Task 4: JglTF PoC — glTF基本データのパース

**概要:** JglTFで `.vrm` ファイルを読み込み、メッシュ・スキン・テクスチャ・ノードが取得できることを検証する。また、`extensions` フィールドの生JSONにアクセスできることを確認する（VRM拡張パースの前提条件）。

**Files:**
- Create: `common/src/test/kotlin/com/github/narazaka/vrmmod/vrm/JglTFPoCTest.kt`

- [ ] **Step 1: テストクラスを作成**

```kotlin
package com.github.narazaka.vrmmod.vrm

import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.io.GltfModelReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

/**
 * PoC: JglTF が .vrm (GLB) を読め、extensions にアクセスできることを検証
 */
class JglTFPoCTest {

    private fun loadTestModel(): GltfModel {
        val file = File("../../testdata/test-avatar.vrm")
        assertTrue(file.exists(), "Test VRM file not found at ${file.absolutePath}")
        return GltfModelReader().read(file.toURI())
    }

    @Test
    fun `VRM file can be loaded as glTF model`() {
        val model = loadTestModel()
        assertNotNull(model)
    }

    @Test
    fun `mesh data is accessible`() {
        val model = loadTestModel()
        assertTrue(model.meshModels.isNotEmpty(), "No meshes found")
        val firstMesh = model.meshModels[0]
        assertTrue(firstMesh.meshPrimitiveModels.isNotEmpty(), "No primitives found")
    }

    @Test
    fun `skin data is accessible`() {
        val model = loadTestModel()
        assertTrue(model.skinModels.isNotEmpty(), "No skins found")
        val skin = model.skinModels[0]
        assertTrue(skin.joints.isNotEmpty(), "No joints found")
        assertNotNull(skin.inverseBindMatrix, "No inverseBindMatrices")
    }

    @Test
    fun `node tree is accessible`() {
        val model = loadTestModel()
        assertTrue(model.nodeModels.isNotEmpty(), "No nodes found")
    }

    @Test
    fun `texture and image data is accessible`() {
        val model = loadTestModel()
        assertTrue(model.textureModels.isNotEmpty(), "No textures found")
        assertTrue(model.imageModels.isNotEmpty(), "No images found")
        val firstImage = model.imageModels[0]
        assertNotNull(firstImage.imageData, "Image data is null")
    }

    @Test
    fun `glTF extensions field is accessible for VRM data`() {
        // JglTF の GltfModel から生の glTF JSON (extensions) にアクセスできるか検証
        // GltfModelReader は GltfModel を返すが、生の Gltf (POJO) へのアクセスが必要
        // jgltf-impl-v2 の GlTF クラスを使う
        val file = File("../../testdata/test-avatar.vrm")
        val reader = de.javagl.jgltf.model.io.v2.GltfReaderV2()
        val asset = file.inputStream().use { reader.readBinary(it) }
        val gltf = asset.gltf
        val extensions = gltf.extensions
        assertNotNull(extensions, "glTF extensions is null — VRM data not accessible")
        assertTrue(extensions.containsKey("VRMC_vrm"), "VRMC_vrm extension not found")
    }
}
```

- [ ] **Step 2: common/build.gradle.kts にテスト依存を追加**

```kotlin
dependencies {
    // ... existing
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("de.javagl:jgltf-impl-v2:2.0.4")  // For raw glTF POJO access
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: テストを実行**

```bash
./gradlew :common:test
```

Expected: 全テスト PASS。特に `glTF extensions field is accessible` が PASS であることが重要（FAIL の場合は JglTF の代替アプローチを検討）。

- [ ] **Step 4: Commit**

```bash
git add common/src/test/ common/build.gradle.kts
git commit -m "feat: PoC - verify JglTF can parse VRM and access extensions"
```

---

## Task 5: VRM拡張JSONパーサー

**概要:** glTF の `extensions` フィールドから VRM 1.0 固有データ（VRMC_vrm: メタ、ヒューマノイド、Expression）をパースする。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmExtensionParser.kt`
- Create: `common/src/test/kotlin/com/github/narazaka/vrmmod/vrm/VrmExtensionParserTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package com.github.narazaka.vrmmod.vrm

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VrmExtensionParserTest {

    private val sampleVrmcVrm = """
    {
      "specVersion": "1.0",
      "meta": {
        "name": "Test Avatar",
        "version": "1.0",
        "authors": ["TestAuthor"],
        "copyrightInformation": "CC0",
        "licenseUrl": "https://creativecommons.org/publicdomain/zero/1.0/"
      },
      "humanoid": {
        "humanBones": {
          "hips": { "node": 0 },
          "spine": { "node": 1 },
          "chest": { "node": 2 },
          "head": { "node": 5 },
          "leftUpperArm": { "node": 10 },
          "rightUpperArm": { "node": 15 }
        }
      },
      "expressions": {
        "preset": {
          "happy": {
            "morphTargetBinds": [
              { "node": 30, "index": 0, "weight": 1.0 }
            ]
          }
        }
      }
    }
    """.trimIndent()

    @Test
    fun `parse meta from VRMC_vrm`() {
        val json = Gson().fromJson(sampleVrmcVrm, JsonObject::class.java)
        val meta = VrmExtensionParser.parseMeta(json.getAsJsonObject("meta"))
        assertEquals("Test Avatar", meta.name)
        assertEquals(listOf("TestAuthor"), meta.authors)
    }

    @Test
    fun `parse humanoid bone mapping`() {
        val json = Gson().fromJson(sampleVrmcVrm, JsonObject::class.java)
        val humanoid = VrmExtensionParser.parseHumanoid(json.getAsJsonObject("humanoid"))
        assertEquals(0, humanoid.boneToNodeIndex[HumanBone.HIPS])
        assertEquals(5, humanoid.boneToNodeIndex[HumanBone.HEAD])
        assertEquals(10, humanoid.boneToNodeIndex[HumanBone.LEFT_UPPER_ARM])
    }

    @Test
    fun `parse expressions`() {
        val json = Gson().fromJson(sampleVrmcVrm, JsonObject::class.java)
        val expressions = VrmExtensionParser.parseExpressions(json.getAsJsonObject("expressions"))
        assertTrue(expressions.any { it.preset == "happy" })
    }
}
```

- [ ] **Step 2: テスト実行して FAIL を確認**

```bash
./gradlew :common:test --tests "*VrmExtensionParserTest*"
```

Expected: FAIL（`VrmExtensionParser` が未実装）

- [ ] **Step 3: VrmExtensionParser を実装**

```kotlin
package com.github.narazaka.vrmmod.vrm

import com.google.gson.JsonObject

object VrmExtensionParser {

    fun parseMeta(json: JsonObject): VrmMeta {
        return VrmMeta(
            name = json.get("name")?.asString ?: "Unknown",
            version = json.get("version")?.asString,
            authors = json.getAsJsonArray("authors")?.map { it.asString } ?: emptyList(),
            copyrightInformation = json.get("copyrightInformation")?.asString,
            licenseUrl = json.get("licenseUrl")?.asString,
        )
    }

    fun parseHumanoid(json: JsonObject): VrmHumanoid {
        val humanBones = json.getAsJsonObject("humanBones")
        val boneMap = mutableMapOf<HumanBone, Int>()
        for ((key, value) in humanBones.entrySet()) {
            val bone = HumanBone.fromVrmName(key)
            if (bone != null) {
                val nodeIndex = value.asJsonObject.get("node").asInt
                boneMap[bone] = nodeIndex
            }
        }
        return VrmHumanoid(boneToNodeIndex = boneMap)
    }

    fun parseExpressions(json: JsonObject?): List<VrmExpression> {
        if (json == null) return emptyList()
        val result = mutableListOf<VrmExpression>()

        val preset = json.getAsJsonObject("preset")
        preset?.entrySet()?.forEach { (name, value) ->
            val obj = value.asJsonObject
            val binds = obj.getAsJsonArray("morphTargetBinds")?.map { bind ->
                val b = bind.asJsonObject
                MorphTargetBind(
                    meshIndex = b.get("node").asInt,
                    morphTargetIndex = b.get("index").asInt,
                    weight = b.get("weight").asFloat,
                )
            } ?: emptyList()
            result.add(VrmExpression(name = name, preset = name, morphTargetBinds = binds))
        }

        return result
    }
}
```

- [ ] **Step 4: テスト実行して PASS を確認**

```bash
./gradlew :common:test --tests "*VrmExtensionParserTest*"
```

Expected: ALL PASS

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmExtensionParser.kt \
        common/src/test/kotlin/com/github/narazaka/vrmmod/vrm/VrmExtensionParserTest.kt
git commit -m "feat: VRM 1.0 extension parser for meta, humanoid, expressions"
```

---

## Task 6: VrmParser — glTF + VRM拡張の統合パーサー

**概要:** JglTF と VrmExtensionParser を組み合わせ、`.vrm` ファイルから `VrmModel` を生成する統合パーサーを実装する。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmParser.kt`
- Create: `common/src/test/kotlin/com/github/narazaka/vrmmod/vrm/VrmParserTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package com.github.narazaka.vrmmod.vrm

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.io.File

class VrmParserTest {

    @Test
    fun `parse VRM file into VrmModel`() {
        val file = File("../../testdata/test-avatar.vrm")
        assertTrue(file.exists(), "Test VRM file not found")

        val model = VrmParser.parse(file.inputStream())

        // Meta
        assertTrue(model.meta.name.isNotEmpty(), "Meta name is empty")

        // Humanoid
        assertNotNull(model.humanoid.boneToNodeIndex[HumanBone.HIPS], "HIPS bone not found")
        assertNotNull(model.humanoid.boneToNodeIndex[HumanBone.HEAD], "HEAD bone not found")

        // Meshes
        assertTrue(model.meshes.isNotEmpty(), "No meshes")
        val firstPrim = model.meshes[0].primitives[0]
        assertTrue(firstPrim.vertexCount > 0, "Vertex count is 0")
        assertNotNull(firstPrim.positions, "Positions are null")

        // Skeleton
        assertTrue(model.skeleton.nodes.isNotEmpty(), "No skeleton nodes")
        assertTrue(model.skeleton.jointNodeIndices.isNotEmpty(), "No joints")

        // Textures
        assertTrue(model.textures.isNotEmpty(), "No textures")
        assertTrue(model.textures[0].imageData.isNotEmpty(), "Texture data is empty")
    }
}
```

- [ ] **Step 2: テスト実行して FAIL を確認**

```bash
./gradlew :common:test --tests "*VrmParserTest*"
```

- [ ] **Step 3: VrmParser を実装**

```kotlin
package com.github.narazaka.vrmmod.vrm

import com.google.gson.Gson
import com.google.gson.JsonObject
import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.FloatBuffer
import java.nio.IntBuffer

object VrmParser {

    fun parse(inputStream: InputStream): VrmModel {
        val bytes = inputStream.readAllBytes()

        // 1. Parse with JglTF for mesh/skin/node/texture data
        val gltfModel = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))
        // Need to also read full model for resolved data
        val model = GltfModelReader().read(ByteArrayInputStream(bytes))

        // 2. Parse raw glTF for extensions
        val reader = GltfReaderV2()
        val asset = reader.readBinary(ByteArrayInputStream(bytes))
        val gltf = asset.gltf
        val extensions = gltf.extensions
            ?: throw IllegalArgumentException("No extensions found - not a valid VRM file")

        val vrmcVrm = Gson().toJsonTree(extensions["VRMC_vrm"]).asJsonObject

        // Parse VRM extensions
        val meta = VrmExtensionParser.parseMeta(vrmcVrm.getAsJsonObject("meta"))
        val humanoid = VrmExtensionParser.parseHumanoid(vrmcVrm.getAsJsonObject("humanoid"))
        val expressions = VrmExtensionParser.parseExpressions(vrmcVrm.getAsJsonObject("expressions"))

        // Parse meshes
        val meshes = parseMeshes(model)

        // Parse skeleton
        val skeleton = parseSkeleton(model)

        // Parse textures
        val textures = parseTextures(model)

        // SpringBone (stub - parse in Phase 4)
        val springBones = parseSpringBoneStub(extensions)

        return VrmModel(
            meta = meta,
            humanoid = humanoid,
            meshes = meshes,
            skeleton = skeleton,
            textures = textures,
            expressions = expressions,
            springBones = springBones,
        )
    }

    private fun parseMeshes(model: GltfModel): List<VrmMesh> {
        return model.meshModels.mapIndexed { meshIdx, meshModel ->
            val primitives = meshModel.meshPrimitiveModels.map { prim ->
                val posAccessor = prim.attributes["POSITION"]
                val normAccessor = prim.attributes["NORMAL"]
                val uvAccessor = prim.attributes["TEXCOORD_0"]
                val jointsAccessor = prim.attributes["JOINTS_0"]
                val weightsAccessor = prim.attributes["WEIGHTS_0"]
                val indicesAccessor = prim.indices

                val vertexCount = posAccessor?.count ?: 0

                VrmPrimitive(
                    positions = posAccessor?.accessorData?.let { extractFloatBuffer(it) }
                        ?: FloatBuffer.allocate(0),
                    normals = normAccessor?.accessorData?.let { extractFloatBuffer(it) },
                    texCoords = uvAccessor?.accessorData?.let { extractFloatBuffer(it) },
                    joints = jointsAccessor?.accessorData?.let { extractIntBuffer(it) },
                    weights = weightsAccessor?.accessorData?.let { extractFloatBuffer(it) },
                    indices = indicesAccessor?.accessorData?.let { extractIntBuffer(it) }
                        ?: IntBuffer.allocate(0),
                    vertexCount = vertexCount,
                    materialIndex = prim.materialModel?.let { model.materialModels.indexOf(it) } ?: 0,
                )
            }
            VrmMesh(name = meshModel.name ?: "mesh_$meshIdx", primitives = primitives)
        }
    }

    private fun parseSkeleton(model: GltfModel): VrmSkeleton {
        val nodes = model.nodeModels.mapIndexed { idx, nodeModel ->
            VrmNode(
                index = idx,
                name = nodeModel.name ?: "node_$idx",
                translation = nodeModel.translation?.let { Vector3f(it[0], it[1], it[2]) }
                    ?: Vector3f(),
                rotation = nodeModel.rotation?.let { Quaternionf(it[0], it[1], it[2], it[3]) }
                    ?: Quaternionf(),
                scale = nodeModel.scale?.let { Vector3f(it[0], it[1], it[2]) }
                    ?: Vector3f(1f, 1f, 1f),
                childIndices = nodeModel.children.map { child ->
                    model.nodeModels.indexOf(child)
                },
            )
        }

        val skin = model.skinModels.firstOrNull()
        val jointNodeIndices = skin?.joints?.map { model.nodeModels.indexOf(it) } ?: emptyList()
        val ibm = parseInverseBindMatrices(skin)

        return VrmSkeleton(
            nodes = nodes,
            rootNodeIndices = model.sceneModels.firstOrNull()?.nodeModels
                ?.map { model.nodeModels.indexOf(it) } ?: emptyList(),
            inverseBindMatrices = ibm,
            jointNodeIndices = jointNodeIndices,
        )
    }

    private fun parseInverseBindMatrices(skin: de.javagl.jgltf.model.SkinModel?): List<Matrix4f> {
        if (skin == null) return emptyList()
        val ibmAccessor = skin.inverseBindMatrix ?: return emptyList()
        // inverseBindMatrix is a flat float array, 16 floats per matrix
        val data = ibmAccessor // AccessorFloatData
        val matrices = mutableListOf<Matrix4f>()
        val jointCount = skin.joints.size
        for (i in 0 until jointCount) {
            val m = Matrix4f()
            // JglTF stores in column-major order matching glTF spec
            val floats = FloatArray(16)
            for (j in 0 until 16) {
                floats[j] = data[i * 16 + j]
            }
            m.set(floats)
            matrices.add(m)
        }
        return matrices
    }

    private fun parseTextures(model: GltfModel): List<VrmTexture> {
        return model.imageModels.mapIndexed { idx, imageModel ->
            VrmTexture(
                index = idx,
                name = imageModel.name,
                imageData = imageModel.imageData.array(),
                mimeType = imageModel.mimeType ?: "image/png",
            )
        }
    }

    private fun parseSpringBoneStub(extensions: Map<String, Any>): VrmSpringBoneData? {
        // Stub - returns null if no spring bone data, actual parsing in Phase 4
        return if (extensions.containsKey("VRMC_springBone")) {
            VrmSpringBoneData(springs = emptyList(), colliders = emptyList())
        } else null
    }

    // Helper: extract float data from accessor (implementation depends on JglTF API)
    private fun extractFloatBuffer(data: Any): FloatBuffer {
        // JglTF AccessorData implementation varies - adapt based on PoC findings
        TODO("Implement based on JglTF API — see PoC test results")
    }

    private fun extractIntBuffer(data: Any): IntBuffer {
        TODO("Implement based on JglTF API — see PoC test results")
    }
}
```

**注意:**
- `extractFloatBuffer` / `extractIntBuffer` の実装は JglTF の具体的な `AccessorData` 型に依存する。Task 4 の PoC テスト結果に基づいて実装する。JglTF は `AccessorFloatData`, `AccessorIntData` 等のクラスを提供しており、`get(elementIndex, componentIndex)` メソッドでアクセスする。
- `parseInverseBindMatrices`: `SkinModel.getInverseBindMatrix()` は `AccessorModel` を返す。`AccessorModel.getAccessorData()` で `AccessorFloatData` を取得し、`get(jointIndex * 16 + component)` ではなく `get(jointIndex, component)` でアクセスする形に修正する必要がある。
- `GltfModelReader().readWithoutReferences()` は JglTF 2.0.4 に存在しない可能性がある。`read(URI)` または `read(InputStream)` を使うこと。
- 上記のコードはスケルトンであり、JglTF の API に合わせて調整が必要。

- [ ] **Step 4: テスト実行して PASS を確認**

```bash
./gradlew :common:test --tests "*VrmParserTest*"
```

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/vrm/VrmParser.kt \
        common/src/test/kotlin/com/github/narazaka/vrmmod/vrm/VrmParserTest.kt
git commit -m "feat: integrated VRM parser combining JglTF and extension parser"
```

---

## Task 7: テクスチャ管理 — DynamicTexture 登録

**概要:** VrmTexture のバイナリデータを Minecraft の `DynamicTexture` / `TextureManager` に登録し、`ResourceLocation` を返す仕組みを実装する。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmTextureManager.kt`

- [ ] **Step 1: VrmTextureManager を実装**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.vrm.VrmTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VrmTextureManager {
    private val registeredTextures = ConcurrentHashMap<String, ResourceLocation>()

    /**
     * Register VRM textures for a player and return the ResourceLocations.
     * Must be called on the render thread.
     */
    fun registerTextures(playerUUID: UUID, textures: List<VrmTexture>): List<ResourceLocation> {
        val textureManager = Minecraft.getInstance().textureManager
        return textures.mapIndexed { idx, vrmTex ->
            val key = "${playerUUID}_$idx"
            registeredTextures.getOrPut(key) {
                val nativeImage = NativeImage.read(ByteArrayInputStream(vrmTex.imageData))
                val dynamicTexture = DynamicTexture(nativeImage)
                val location = ResourceLocation.fromNamespaceAndPath(
                    VrmMod.MOD_ID, "vrm_tex/${playerUUID}/$idx"
                )
                textureManager.register(location, dynamicTexture)
                location
            }
        }
    }

    /**
     * Unregister all textures for a player.
     */
    fun unregisterTextures(playerUUID: UUID) {
        val textureManager = Minecraft.getInstance().textureManager
        val keysToRemove = registeredTextures.keys.filter { it.startsWith("${playerUUID}_") }
        keysToRemove.forEach { key ->
            registeredTextures.remove(key)?.let { location ->
                textureManager.release(location)
            }
        }
    }

    fun clear() {
        val textureManager = Minecraft.getInstance().textureManager
        registeredTextures.values.forEach { textureManager.release(it) }
        registeredTextures.clear()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmTextureManager.kt
git commit -m "feat: VRM texture manager for DynamicTexture registration"
```

テクスチャ管理はMinecraftランタイムに依存するため、ユニットテストではなくゲーム内テスト（Task 9）で検証する。

---

## Task 8: VrmPlayerManager — プレイヤーとモデルの紐付け

**概要:** プレイヤー UUID と読み込み済み VrmModel を管理し、レンダラーに提供するマネージャー。非同期読み込みとフォールバックを扱う。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmPlayerManager.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmState.kt`

- [ ] **Step 1: VrmState を定義**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmModel
import net.minecraft.resources.ResourceLocation

data class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
)
```

- [ ] **Step 2: VrmPlayerManager を実装**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmParser
import net.minecraft.client.player.AbstractClientPlayer
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object VrmPlayerManager {
    private val states = ConcurrentHashMap<UUID, VrmState>()
    private val loading = ConcurrentHashMap<UUID, CompletableFuture<*>>()

    fun get(player: AbstractClientPlayer): VrmState? {
        return states[player.uuid]
    }

    /**
     * Load a VRM model for the local player from a local file.
     * Parsing is done async; texture registration is scheduled on the render thread.
     */
    fun loadLocal(playerUUID: UUID, file: File) {
        if (loading.containsKey(playerUUID)) return // already loading

        val future = CompletableFuture.supplyAsync {
            VrmParser.parse(file.inputStream())
        }.thenAcceptAsync({ model ->
            // This runs on the main/render thread via Minecraft.getInstance().execute()
            val texLocations = VrmTextureManager.registerTextures(playerUUID, model.textures)
            states[playerUUID] = VrmState(model = model, textureLocations = texLocations)
            loading.remove(playerUUID)
        }, { runnable ->
            net.minecraft.client.Minecraft.getInstance().execute(runnable)
        }).exceptionally { e ->
            // Log error and fallback to vanilla skin
            com.github.narazaka.vrmmod.VrmMod.logger.error("Failed to load VRM model", e)
            loading.remove(playerUUID)
            null
        }

        loading[playerUUID] = future
    }

    fun unload(playerUUID: UUID) {
        states.remove(playerUUID)
        VrmTextureManager.unregisterTextures(playerUUID)
        loading.remove(playerUUID)?.cancel(false)
    }

    fun clear() {
        states.clear()
        loading.values.forEach { it.cancel(false) }
        loading.clear()
        VrmTextureManager.clear()
    }
}
```

- [ ] **Step 3: VrmMod に logger を追加**

```kotlin
// VrmMod.kt に追加
import org.slf4j.LoggerFactory

object VrmMod {
    const val MOD_ID = "vrmmod"
    val logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        logger.info("VRM Mod initialized")
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmState.kt \
        common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmPlayerManager.kt \
        common/src/main/kotlin/com/github/narazaka/vrmmod/VrmMod.kt
git commit -m "feat: VRM player manager with async loading and fallback"
```

---

## Task 9: VrmRenderer — Tポーズの静的メッシュ描画

**概要:** VrmModel のメッシュデータを `VertexConsumer` に書き込み、Tポーズ（スキニングなし）で描画するレンダラーを実装する。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmRenderer.kt`

- [ ] **Step 1: VrmRenderer を実装**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmPrimitive
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import org.joml.Matrix3f
import org.joml.Matrix4f

object VrmRenderer {

    /**
     * Render a VRM model at the player's position.
     * Phase 2: T-pose only (no skinning).
     */
    fun render(
        state: VrmState,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
    ) {
        val model = state.model
        poseStack.pushPose()

        // Scale VRM to match Minecraft player height (~1.8 blocks)
        // VRM models are typically in meters, MC players are ~1.8m
        // Adjust based on hips bone position if needed
        applyModelScale(poseStack, model)

        for ((meshIdx, mesh) in model.meshes.withIndex()) {
            for (primitive in mesh.primitives) {
                val textureLocation = resolveTexture(state, primitive.materialIndex)
                val renderType = RenderType.entityCutoutNoCull(textureLocation)
                val consumer = bufferSource.getBuffer(renderType)

                drawPrimitive(
                    primitive = primitive,
                    poseStack = poseStack,
                    consumer = consumer,
                    packedLight = packedLight,
                    packedOverlay = 0, // OverlayTexture.NO_OVERLAY
                )
            }
        }

        poseStack.popPose()
    }

    private fun applyModelScale(poseStack: PoseStack, model: com.github.narazaka.vrmmod.vrm.VrmModel) {
        // glTF/VRM 1.0 coordinate system: right-hand, Y-up, +Z forward (toward viewer)
        // Minecraft coordinate system: Y-up, +Z south (away from viewer in default camera)
        // Need to flip Z axis for correct facing direction
        poseStack.scale(1.0f, 1.0f, -1.0f)

        // Scale VRM to match Minecraft player height (~1.8 blocks)
        // VRM models are in meters. Estimate model height from hips position.
        val hipsNodeIndex = model.humanoid.boneToNodeIndex[
            com.github.narazaka.vrmmod.vrm.HumanBone.HIPS
        ]
        val hipsY = if (hipsNodeIndex != null) {
            model.skeleton.nodes[hipsNodeIndex].translation.y
        } else 1.0f

        // Approximate total height as ~2x hips height (rough heuristic)
        val estimatedHeight = hipsY * 2.0f
        val targetHeight = 1.8f // MC player height in blocks
        val scale = if (estimatedHeight > 0.1f) targetHeight / estimatedHeight else 1.0f
        poseStack.scale(scale, scale, scale)

        // Offset so model stands on the ground (Y=0 at feet)
        poseStack.translate(0.0, (-hipsY + hipsY).toDouble(), 0.0)
    }

    private fun resolveTexture(state: VrmState, materialIndex: Int): ResourceLocation {
        // For now, use first texture or a fallback
        return if (state.textureLocations.isNotEmpty()) {
            state.textureLocations[materialIndex.coerceIn(0, state.textureLocations.lastIndex)]
        } else {
            // Fallback to missing texture
            ResourceLocation.withDefaultNamespace("textures/misc/unknown_pack.png")
        }
    }

    private fun drawPrimitive(
        primitive: VrmPrimitive,
        poseStack: PoseStack,
        consumer: VertexConsumer,
        packedLight: Int,
        packedOverlay: Int,
    ) {
        val pose = poseStack.last()
        val positionMatrix: Matrix4f = pose.pose()
        val normalMatrix: Matrix3f = pose.normal()

        val positions = primitive.positions
        val normals = primitive.normals
        val texCoords = primitive.texCoords
        val indices = primitive.indices

        positions.rewind()
        normals?.rewind()
        texCoords?.rewind()
        indices.rewind()

        // Build vertex data per index
        while (indices.hasRemaining()) {
            val idx = indices.get()

            val px = positions.get(idx * 3)
            val py = positions.get(idx * 3 + 1)
            val pz = positions.get(idx * 3 + 2)

            val nx = normals?.get(idx * 3) ?: 0f
            val ny = normals?.get(idx * 3 + 1) ?: 1f
            val nz = normals?.get(idx * 3 + 2) ?: 0f

            val u = texCoords?.get(idx * 2) ?: 0f
            val v = texCoords?.get(idx * 2 + 1) ?: 0f

            consumer.addVertex(positionMatrix, px, py, pz)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(packedOverlay)
                .setLight(packedLight)
                .setNormal(normalMatrix, nx, ny, nz)
        }
    }
}
```

**注意:** `VertexConsumer` の正確なメソッドチェーンは Minecraft バージョンによって異なる。1.21.x の Mojang mapping に合わせて調整すること。上記はコンセプトコード。

- [ ] **Step 2: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmRenderer.kt
git commit -m "feat: VRM renderer for static T-pose mesh drawing"
```

---

## Task 10: Mixin — PlayerRenderer の差し替え

**概要:** Fabric / NeoForge それぞれの Mixin を Java で記述し、VRM 設定済みプレイヤーの描画を差し替える。

**Files:**
- Create: `fabric/src/main/java/com/github/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java`
- Modify: `fabric/src/main/resources/vrmmod.mixins.json`
- Create: `neoforge/src/main/java/com/github/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java`
- Create or Modify: `neoforge/src/main/resources/META-INF/vrmmod.mixins.json`

- [ ] **Step 1: Fabric Mixin を作成**

```java
package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderer;
import com.github.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            AbstractClientPlayer player,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        // Skip VRM rendering in first-person view (spec: "バニラ一人称ではVRMモデルは描画しない")
        if (net.minecraft.client.Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;

        VrmState state = VrmPlayerManager.INSTANCE.get(player);
        if (state == null) return;
        VrmRenderer.INSTANCE.render(state, player, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        ci.cancel();
    }
}
```

**注意:**
- `render` メソッドのシグネチャは Minecraft バージョンと mapping によって異なる。実装時に `PlayerRenderer.render()` の正確なディスクリプタを確認すること。method 文字列にはMojang mappingベースの名前を使う（Loom が自動remap）。
- **1.21.2+ のレンダリングリファクタリング**: Minecraft 1.21.2 以降で `EntityRenderState` ベースにリファクタリングされた可能性がある。ターゲットバージョンの `PlayerRenderer` クラスを実際に確認し、`render` のシグネチャが変わっている場合は適宜調整すること。
- `getCameraType().isFirstPerson()` のメソッド名もバージョンにより異なる場合がある。

- [ ] **Step 2: vrmmod.mixins.json を更新（Fabric）**

```json
{
  "required": true,
  "package": "com.github.narazaka.vrmmod.fabric.mixin",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "PlayerRendererMixin"
  ],
  "injectors": {
    "defaultRequire": 1
  }
}
```

- [ ] **Step 3: NeoForge 側も同様に作成**

NeoForge の Mixin クラスはパッケージが `com.github.narazaka.vrmmod.neoforge.mixin` になる以外は Fabric 版と同一。NeoForge の render メソッドシグネチャが異なる場合は調整する。

- [ ] **Step 4: Commit**

```bash
git add fabric/src/main/java/ fabric/src/main/resources/vrmmod.mixins.json \
        neoforge/src/main/java/ neoforge/src/main/resources/
git commit -m "feat: PlayerRenderer Mixin for VRM avatar rendering"
```

---

## Task 11: クライアント初期化 — モデルロードとキーバインド

**概要:** クライアント起動時にキーバインドを登録し、ローカル VRM ファイルの読み込みをトリガーする仕組みを実装する。最小限のテスト用：config ファイルのパスから VRM を読み込む。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/client/VrmModClient.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/client/VrmModConfig.kt`

- [ ] **Step 1: VrmModConfig を実装（最小限）**

```kotlin
package com.github.narazaka.vrmmod.client

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

data class VrmModConfig(
    val localModelPath: String? = null,
) {
    companion object {
        private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        fun load(configDir: File): VrmModConfig {
            val configFile = File(configDir, "vrmmod.json")
            return if (configFile.exists()) {
                gson.fromJson(configFile.readText(), VrmModConfig::class.java)
            } else {
                val default = VrmModConfig()
                configFile.parentFile.mkdirs()
                configFile.writeText(gson.toJson(default))
                default
            }
        }
    }
}
```

- [ ] **Step 2: VrmModClient を実装**

```kotlin
package com.github.narazaka.vrmmod.client

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.render.VrmPlayerManager
import dev.architectury.event.events.client.ClientPlayerEvent
import dev.architectury.registry.client.keymappings.KeyMappingRegistry
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import java.io.File

object VrmModClient {
    private val KEY_VRM_SETTINGS = KeyMapping(
        "key.vrmmod.settings",
        InputConstants.Type.KEYSYM,
        InputConstants.KEY_V,
        "category.vrmmod"
    )

    fun init() {
        KeyMappingRegistry.register(KEY_VRM_SETTINGS)

        // On world join, load local VRM if configured
        ClientPlayerEvent.CLIENT_PLAYER_JOIN.register { player ->
            val configDir = File(Minecraft.getInstance().gameDirectory, "config")
            val config = VrmModConfig.load(configDir)
            if (config.localModelPath != null) {
                val vrmFile = File(config.localModelPath)
                if (vrmFile.exists()) {
                    VrmMod.logger.info("Loading VRM model: ${vrmFile.absolutePath}")
                    VrmPlayerManager.loadLocal(player.uuid, vrmFile)
                } else {
                    VrmMod.logger.warn("VRM file not found: ${config.localModelPath}")
                }
            }
        }

        // On world leave, cleanup
        ClientPlayerEvent.CLIENT_PLAYER_QUIT.register { player ->
            if (player != null) {
                VrmPlayerManager.unload(player.uuid)
            }
        }
    }
}
```

- [ ] **Step 3: VrmMod.init() から VrmModClient.init() を呼ぶ**

```kotlin
// VrmMod.kt
fun init() {
    logger.info("VRM Mod initialized")
    client.VrmModClient.init()
}
```

注意: `init()` はクライアントサイドのみで呼ばれることを確認（`fabric.mod.json` の `environment: "client"` で保証）。

- [ ] **Step 4: ゲーム内テスト**

1. `config/vrmmod.json` に `{"localModelPath": "path/to/test-avatar.vrm"}` を設定
2. Fabric 開発環境で Minecraft を起動: `./gradlew :fabric:runClient`
3. ワールドに入り、三人称視点（F5）に切り替え
4. VRM モデルが T ポーズで表示されることを確認

Expected: VRM メッシュがプレイヤー位置に T ポーズで表示される。テクスチャが正しく貼られている。

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/client/
git commit -m "feat: client init with config loading and VRM model auto-load on join"
```

---

## Task 12: PoseProvider インターフェースとボーン行列計算

**概要:** アニメーションシステムの基盤を実装する。PoseProvider インターフェース、BonePose/PoseContext データクラス、ボーン行列（ローカル→ワールド）の計算エンジン。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/animation/PoseProvider.kt`
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmSkinningEngine.kt`
- Create: `common/src/test/kotlin/com/github/narazaka/vrmmod/render/VrmSkinningEngineTest.kt`

- [ ] **Step 1: PoseProvider インターフェースを定義**

```kotlin
package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f

data class BonePose(
    val translation: Vector3f = Vector3f(),
    val rotation: Quaternionf = Quaternionf(),
    val scale: Vector3f = Vector3f(1f, 1f, 1f),
)

typealias BonePoseMap = Map<HumanBone, BonePose>

data class PoseContext(
    val partialTick: Float,
    val limbSwing: Float,
    val limbSwingAmount: Float,
    val isSwinging: Boolean,
    val isSneaking: Boolean,
    val isSprinting: Boolean,
    val isSwimming: Boolean,
    val isFallFlying: Boolean,
    val isRiding: Boolean,
    val headYaw: Float,
    val headPitch: Float,
)

interface PoseProvider {
    fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap
}
```

- [ ] **Step 2: スキニングエンジンのテストを書く**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmNode
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VrmSkinningEngineTest {

    private fun createSimpleSkeleton(): VrmSkeleton {
        // Simple 2-bone chain: root(0) → child(1)
        val root = VrmNode(0, "root", Vector3f(0f, 0f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(1))
        val child = VrmNode(1, "child", Vector3f(0f, 1f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList())
        return VrmSkeleton(
            nodes = listOf(root, child),
            rootNodeIndices = listOf(0),
            inverseBindMatrices = listOf(Matrix4f(), Matrix4f().translate(0f, -1f, 0f)),
            jointNodeIndices = listOf(0, 1),
        )
    }

    @Test
    fun `compute world matrices for rest pose`() {
        val skeleton = createSimpleSkeleton()
        val worldMatrices = VrmSkinningEngine.computeWorldMatrices(skeleton, emptyMap())

        // Root should be at identity
        val rootPos = Vector3f()
        worldMatrices[0].getTranslation(rootPos)
        assertEquals(0f, rootPos.x, 0.001f)
        assertEquals(0f, rootPos.y, 0.001f)

        // Child should be at (0, 1, 0)
        val childPos = Vector3f()
        worldMatrices[1].getTranslation(childPos)
        assertEquals(0f, childPos.x, 0.001f)
        assertEquals(1f, childPos.y, 0.001f)
    }

    @Test
    fun `compute skinning matrices`() {
        val skeleton = createSimpleSkeleton()
        val skinningMatrices = VrmSkinningEngine.computeSkinningMatrices(skeleton, emptyMap())

        // Skinning matrix = worldMatrix * inverseBindMatrix
        // For root: identity * identity = identity
        // For child: translate(0,1,0) * translate(0,-1,0) = identity
        assertEquals(2, skinningMatrices.size)
    }

    @Test
    fun `skin a vertex with single bone weight`() {
        val skeleton = createSimpleSkeleton()
        val skinningMatrices = VrmSkinningEngine.computeSkinningMatrices(skeleton, emptyMap())

        val position = Vector3f(0f, 0f, 0f) // vertex at origin
        val joints = intArrayOf(1, 0, 0, 0)
        val weights = floatArrayOf(1f, 0f, 0f, 0f)

        val result = VrmSkinningEngine.skinVertex(position, joints, weights, skinningMatrices)
        // Bone 1 (child) skinning matrix should be identity in rest pose
        assertEquals(0f, result.x, 0.001f)
        assertEquals(0f, result.y, 0.001f)
        assertEquals(0f, result.z, 0.001f)
    }
}
```

- [ ] **Step 3: テスト実行して FAIL を確認**

```bash
./gradlew :common:test --tests "*VrmSkinningEngineTest*"
```

- [ ] **Step 4: VrmSkinningEngine を実装**

```kotlin
package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

object VrmSkinningEngine {

    /**
     * Compute world-space transform for each node in the skeleton.
     * @param overrides Map of nodeIndex → local transform override (from animation)
     */
    fun computeWorldMatrices(
        skeleton: VrmSkeleton,
        overrides: Map<Int, Matrix4f>,
    ): List<Matrix4f> {
        val worldMatrices = Array(skeleton.nodes.size) { Matrix4f() }

        fun traverse(nodeIndex: Int, parentWorld: Matrix4f) {
            val node = skeleton.nodes[nodeIndex]
            val localMatrix = overrides[nodeIndex] ?: run {
                Matrix4f()
                    .translate(node.translation)
                    .rotate(node.rotation)
                    .scale(node.scale)
            }
            worldMatrices[nodeIndex] = Matrix4f(parentWorld).mul(localMatrix)

            for (childIndex in node.childIndices) {
                traverse(childIndex, worldMatrices[nodeIndex])
            }
        }

        for (rootIndex in skeleton.rootNodeIndices) {
            traverse(rootIndex, Matrix4f())
        }

        return worldMatrices.toList()
    }

    /**
     * Compute skinning matrices: worldMatrix[joint] * inverseBindMatrix[joint]
     */
    fun computeSkinningMatrices(
        skeleton: VrmSkeleton,
        overrides: Map<Int, Matrix4f>,
    ): List<Matrix4f> {
        val worldMatrices = computeWorldMatrices(skeleton, overrides)
        return skeleton.jointNodeIndices.mapIndexed { jointIdx, nodeIdx ->
            Matrix4f(worldMatrices[nodeIdx]).mul(skeleton.inverseBindMatrices[jointIdx])
        }
    }

    /**
     * Apply skinning to a single vertex position.
     */
    fun skinVertex(
        position: Vector3f,
        joints: IntArray,    // 4 joint indices
        weights: FloatArray, // 4 weights
        skinningMatrices: List<Matrix4f>,
    ): Vector3f {
        val result = Vector3f()
        val temp = Vector3f()

        for (i in 0 until 4) {
            if (weights[i] <= 0f) continue
            val jointIdx = joints[i]
            if (jointIdx < 0 || jointIdx >= skinningMatrices.size) continue

            temp.set(position)
            skinningMatrices[jointIdx].transformPosition(temp)
            result.add(temp.x * weights[i], temp.y * weights[i], temp.z * weights[i])
        }

        return result
    }

    /**
     * Apply skinning to a normal vector.
     */
    fun skinNormal(
        normal: Vector3f,
        joints: IntArray,
        weights: FloatArray,
        skinningMatrices: List<Matrix4f>,
    ): Vector3f {
        val result = Vector3f()
        val temp = Vector3f()

        for (i in 0 until 4) {
            if (weights[i] <= 0f) continue
            val jointIdx = joints[i]
            if (jointIdx < 0 || jointIdx >= skinningMatrices.size) continue

            temp.set(normal)
            skinningMatrices[jointIdx].transformDirection(temp)
            result.add(temp.x * weights[i], temp.y * weights[i], temp.z * weights[i])
        }

        return result.normalize()
    }
}
```

- [ ] **Step 5: テスト実行して PASS を確認**

```bash
./gradlew :common:test --tests "*VrmSkinningEngineTest*"
```

Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/animation/PoseProvider.kt \
        common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmSkinningEngine.kt \
        common/src/test/kotlin/com/github/narazaka/vrmmod/render/VrmSkinningEngineTest.kt
git commit -m "feat: PoseProvider interface and CPU skinning engine with bone matrices"
```

---

## Task 13: VanillaPoseProvider — Minecraft アクションの VRM ボーンマッピング

**概要:** Minecraft のプレイヤー状態（歩行、攻撃、スニーク等）を VRM ヒューマノイドボーンの回転にマッピングする。バニラの `PlayerModel.setupAnim()` を参考に実装。

**Files:**
- Create: `common/src/main/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProvider.kt`
- Create: `common/src/test/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProviderTest.kt`

- [ ] **Step 1: テストを書く**

```kotlin
package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.*
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VanillaPoseProviderTest {

    private fun createTestSkeleton(): VrmSkeleton {
        // Minimal humanoid skeleton for testing
        val nodes = listOf(
            VrmNode(0, "hips", Vector3f(0f, 1f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(1, 4, 7, 10)),
            VrmNode(1, "spine", Vector3f(0f, 0.2f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(2)),
            VrmNode(2, "chest", Vector3f(0f, 0.2f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(3)),
            VrmNode(3, "head", Vector3f(0f, 0.3f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
            VrmNode(4, "leftUpperArm", Vector3f(-0.2f, 0.3f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(5)),
            VrmNode(5, "leftLowerArm", Vector3f(0f, -0.3f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
            VrmNode(6, "rightUpperArm", Vector3f(0.2f, 0.3f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
            VrmNode(7, "leftUpperLeg", Vector3f(-0.1f, 0f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), listOf(8)),
            VrmNode(8, "leftLowerLeg", Vector3f(0f, -0.4f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
            VrmNode(9, "rightUpperLeg", Vector3f(0.1f, 0f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
            VrmNode(10, "rightLowerLeg", Vector3f(0f, -0.4f, 0f), Quaternionf(), Vector3f(1f, 1f, 1f), emptyList()),
        )
        return VrmSkeleton(
            nodes = nodes,
            rootNodeIndices = listOf(0),
            inverseBindMatrices = nodes.map { Matrix4f() },
            jointNodeIndices = nodes.indices.toList(),
        )
    }

    private fun createTestHumanoid(): VrmHumanoid {
        return VrmHumanoid(mapOf(
            HumanBone.HIPS to 0,
            HumanBone.SPINE to 1,
            HumanBone.CHEST to 2,
            HumanBone.HEAD to 3,
            HumanBone.LEFT_UPPER_ARM to 4,
            HumanBone.LEFT_LOWER_ARM to 5,
            HumanBone.RIGHT_UPPER_ARM to 6,
            HumanBone.LEFT_UPPER_LEG to 7,
            HumanBone.LEFT_LOWER_LEG to 8,
            HumanBone.RIGHT_UPPER_LEG to 9,
            HumanBone.RIGHT_LOWER_LEG to 10,
        ))
    }

    @Test
    fun `idle pose returns non-empty map`() {
        val provider = VanillaPoseProvider()
        val context = PoseContext(
            partialTick = 0f, limbSwing = 0f, limbSwingAmount = 0f,
            isSwinging = false, isSneaking = false, isSprinting = false,
            isSwimming = false, isFallFlying = false, isRiding = false,
            headYaw = 0f, headPitch = 0f,
        )
        val pose = provider.computePose(createTestSkeleton(), context)
        assertTrue(pose.isNotEmpty())
    }

    @Test
    fun `walking pose has leg rotation`() {
        val provider = VanillaPoseProvider()
        val context = PoseContext(
            partialTick = 0f, limbSwing = 1.0f, limbSwingAmount = 0.8f,
            isSwinging = false, isSneaking = false, isSprinting = false,
            isSwimming = false, isFallFlying = false, isRiding = false,
            headYaw = 0f, headPitch = 0f,
        )
        val pose = provider.computePose(createTestSkeleton(), context)
        val leftLeg = pose[HumanBone.LEFT_UPPER_LEG]
        val rightLeg = pose[HumanBone.RIGHT_UPPER_LEG]

        // Legs should have opposite rotations during walk
        assertNotNull(leftLeg)
        assertNotNull(rightLeg)
        // X rotation (pitch) should be non-zero during walk
        assertNotEquals(0f, leftLeg!!.rotation.x, 0.001f)
    }

    @Test
    fun `head follows headYaw and headPitch`() {
        val provider = VanillaPoseProvider()
        val context = PoseContext(
            partialTick = 0f, limbSwing = 0f, limbSwingAmount = 0f,
            isSwinging = false, isSneaking = false, isSprinting = false,
            isSwimming = false, isFallFlying = false, isRiding = false,
            headYaw = 45f, headPitch = -20f,
        )
        val pose = provider.computePose(createTestSkeleton(), context)
        val head = pose[HumanBone.HEAD]
        assertNotNull(head)
        // Head should have Y rotation for yaw
        assertNotEquals(0f, head!!.rotation.y, 0.001f)
    }
}
```

- [ ] **Step 2: テスト実行して FAIL を確認**

```bash
./gradlew :common:test --tests "*VanillaPoseProviderTest*"
```

- [ ] **Step 3: VanillaPoseProvider を実装**

```kotlin
package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/**
 * Maps Minecraft player state to VRM humanoid bone poses.
 * Based on vanilla PlayerModel.setupAnim() logic.
 */
class VanillaPoseProvider : PoseProvider {

    override fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap {
        val poses = mutableMapOf<HumanBone, BonePose>()

        // Head rotation (yaw/pitch)
        val headYawRad = Math.toRadians(context.headYaw.toDouble()).toFloat()
        val headPitchRad = Math.toRadians(context.headPitch.toDouble()).toFloat()
        poses[HumanBone.HEAD] = BonePose(
            rotation = Quaternionf().rotateY(-headYawRad).rotateX(-headPitchRad)
        )

        // Leg swing (walking)
        val swing = context.limbSwing
        val swingAmount = context.limbSwingAmount
        val legAngle = cos(swing * 0.6662f) * 1.4f * swingAmount
        poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(-legAngle)
        )
        poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
            rotation = Quaternionf().rotateX(legAngle)
        )

        // Arm swing (opposite to legs)
        val armAngle = cos(swing * 0.6662f + Math.PI.toFloat()) * 0.8f * swingAmount
        poses[HumanBone.LEFT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(-armAngle)
        )
        poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
            rotation = Quaternionf().rotateX(armAngle)
        )

        // Attack animation
        if (context.isSwinging) {
            poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(
                rotation = Quaternionf().rotateX(-1.8f) // swing arm down
            )
        }

        // Sneaking
        if (context.isSneaking) {
            poses[HumanBone.SPINE] = BonePose(
                rotation = Quaternionf().rotateX(0.5f) // lean forward
            )
            poses[HumanBone.HEAD] = BonePose(
                rotation = Quaternionf().rotateY(-headYawRad).rotateX(-headPitchRad - 0.3f)
            )
        }

        return poses
    }
}
```

これは初期実装であり、水泳・エリトラ・騎乗のアニメーションは後で追加する。歩行・頭・攻撃・スニークの4つのコアアニメーションをまずカバー。

- [ ] **Step 4: テスト実行して PASS を確認**

```bash
./gradlew :common:test --tests "*VanillaPoseProviderTest*"
```

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProvider.kt \
        common/src/test/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProviderTest.kt
git commit -m "feat: VanillaPoseProvider maps MC player state to VRM bone poses"
```

---

## Task 14: VrmRenderer にスキニング統合

**概要:** VrmRenderer を更新し、PoseProvider + VrmSkinningEngine による CPU スキニングを統合する。T ポーズから動的ポーズへの移行。

**Files:**
- Modify: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmRenderer.kt`
- Modify: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmState.kt`
- Modify: `common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmPlayerManager.kt`

- [ ] **Step 1: VrmState に PoseProvider を追加**

```kotlin
// VrmState.kt
data class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
    val poseProvider: PoseProvider = VanillaPoseProvider(),
)
```

- [ ] **Step 2: buildPoseContext と convertToNodeOverrides ヘルパーを実装**

```kotlin
// VrmRenderer.kt に追加

private fun buildPoseContext(player: AbstractClientPlayer, partialTick: Float): PoseContext {
    return PoseContext(
        partialTick = partialTick,
        limbSwing = player.walkAnimation.position(partialTick),
        limbSwingAmount = player.walkAnimation.speed(partialTick),
        isSwinging = player.swinging,
        isSneaking = player.isCrouching,
        isSprinting = player.isSprinting,
        isSwimming = player.isSwimming,
        isFallFlying = player.isFallFlying,
        isRiding = player.isPassenger,
        headYaw = net.minecraft.util.Mth.rotLerp(partialTick, player.yHeadRotO, player.yHeadRot) - net.minecraft.util.Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot),
        headPitch = net.minecraft.util.Mth.lerp(partialTick, player.xRotO, player.xRot),
    )
}

/**
 * Convert BonePoseMap (HumanBone → BonePose) to node-index overrides (nodeIndex → Matrix4f)
 * for the skinning engine.
 */
private fun convertToNodeOverrides(
    model: VrmModel,
    bonePoseMap: BonePoseMap,
): Map<Int, Matrix4f> {
    val overrides = mutableMapOf<Int, Matrix4f>()
    for ((bone, pose) in bonePoseMap) {
        val nodeIndex = model.humanoid.boneToNodeIndex[bone] ?: continue
        val node = model.skeleton.nodes[nodeIndex]
        // Start from the node's rest pose, then apply animation pose on top
        val matrix = Matrix4f()
            .translate(node.translation)
            .translate(pose.translation)
            .rotate(node.rotation)
            .rotate(pose.rotation)
            .scale(node.scale)
            .scale(pose.scale)
        overrides[nodeIndex] = matrix
    }
    return overrides
}
```

注意: `player.walkAnimation` は Minecraft 1.20+ の API。バージョンによっては `player.walkDist` / `player.walkDistO` を使う旧方式の場合もある。ターゲットバージョンに合わせて確認すること。

- [ ] **Step 3: VrmRenderer にスキニング描画を統合**

`VrmRenderer.render()` を更新:

```kotlin
fun render(
    state: VrmState,
    player: AbstractClientPlayer,   // 追加: PoseContext を構築するため
    entityYaw: Float,
    partialTick: Float,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource,
    packedLight: Int,
) {
    val model = state.model
    poseStack.pushPose()
    applyModelScale(poseStack, model)

    // 1. Compute pose
    val poseContext = buildPoseContext(player, partialTick)
    val bonePoseMap = state.poseProvider.computePose(model.skeleton, poseContext)

    // 2. Convert BonePoseMap to node-level transform overrides
    val nodeOverrides = convertToNodeOverrides(model, bonePoseMap)

    // 3. Compute skinning matrices
    val skinningMatrices = VrmSkinningEngine.computeSkinningMatrices(model.skeleton, nodeOverrides)

    // 4. Draw each mesh with skinning
    for (mesh in model.meshes) {
        for (primitive in mesh.primitives) {
            val textureLocation = resolveTexture(state, primitive.materialIndex)
            val renderType = RenderType.entityCutoutNoCull(textureLocation)
            val consumer = bufferSource.getBuffer(renderType)

            drawSkinnedPrimitive(primitive, skinningMatrices, poseStack, consumer, packedLight, 0)
        }
    }

    poseStack.popPose()
}

private fun drawSkinnedPrimitive(
    primitive: VrmPrimitive,
    skinningMatrices: List<Matrix4f>,
    poseStack: PoseStack,
    consumer: VertexConsumer,
    packedLight: Int,
    packedOverlay: Int,
) {
    val pose = poseStack.last()
    val posMatrix = pose.pose()
    val normMatrix = pose.normal()

    val positions = primitive.positions
    val normals = primitive.normals
    val texCoords = primitive.texCoords
    val joints = primitive.joints
    val weights = primitive.weights
    val indices = primitive.indices

    positions.rewind()
    normals?.rewind()
    texCoords?.rewind()
    joints?.rewind()
    weights?.rewind()
    indices.rewind()

    while (indices.hasRemaining()) {
        val idx = indices.get()

        var px = positions.get(idx * 3)
        var py = positions.get(idx * 3 + 1)
        var pz = positions.get(idx * 3 + 2)

        var nx = normals?.get(idx * 3) ?: 0f
        var ny = normals?.get(idx * 3 + 1) ?: 1f
        var nz = normals?.get(idx * 3 + 2) ?: 0f

        // Apply skinning if bone data is available
        if (joints != null && weights != null) {
            val j = intArrayOf(
                joints.get(idx * 4), joints.get(idx * 4 + 1),
                joints.get(idx * 4 + 2), joints.get(idx * 4 + 3)
            )
            val w = floatArrayOf(
                weights.get(idx * 4), weights.get(idx * 4 + 1),
                weights.get(idx * 4 + 2), weights.get(idx * 4 + 3)
            )

            val skinnedPos = VrmSkinningEngine.skinVertex(Vector3f(px, py, pz), j, w, skinningMatrices)
            px = skinnedPos.x; py = skinnedPos.y; pz = skinnedPos.z

            val skinnedNorm = VrmSkinningEngine.skinNormal(Vector3f(nx, ny, nz), j, w, skinningMatrices)
            nx = skinnedNorm.x; ny = skinnedNorm.y; nz = skinnedNorm.z
        }

        val u = texCoords?.get(idx * 2) ?: 0f
        val v = texCoords?.get(idx * 2 + 1) ?: 0f

        consumer.addVertex(posMatrix, px, py, pz)
            .setColor(255, 255, 255, 255)
            .setUv(u, v)
            .setOverlay(packedOverlay)
            .setLight(packedLight)
            .setNormal(normMatrix, nx, ny, nz)
    }
}
```

- [ ] **Step 3: Mixin から追加パラメータを渡すように更新**

PlayerRendererMixin を更新して `player`, `entityYaw`, `partialTick` を `VrmRenderer.render()` に渡す。

- [ ] **Step 4: ゲーム内テスト**

1. `./gradlew :fabric:runClient`
2. ワールド参加、三人称視点（F5）に切り替え
3. 歩行時に脚が動き、攻撃時に腕が振れることを確認
4. 頭がマウスの向きに追従することを確認

Expected: VRM モデルが歩行・攻撃・スニークのアニメーション付きで表示される。

- [ ] **Step 5: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmRenderer.kt \
        common/src/main/kotlin/com/github/narazaka/vrmmod/render/VrmState.kt \
        fabric/src/main/java/com/github/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java \
        neoforge/src/main/java/com/github/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java
git commit -m "feat: integrate CPU skinning with PoseProvider into VRM renderer"
```

---

## Task 15: 水泳・エリトラ・騎乗アニメーション追加

**概要:** VanillaPoseProvider に残りの主要アニメーション（水泳、エリトラ飛行、騎乗）を追加する。

**Files:**
- Modify: `common/src/main/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProvider.kt`
- Modify: `common/src/test/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProviderTest.kt`

- [ ] **Step 1: テストを追加**

```kotlin
@Test
fun `swimming pose rotates body horizontally`() {
    val provider = VanillaPoseProvider()
    val context = PoseContext(
        partialTick = 0f, limbSwing = 0f, limbSwingAmount = 0f,
        isSwinging = false, isSneaking = false, isSprinting = false,
        isSwimming = true, isFallFlying = false, isRiding = false,
        headYaw = 0f, headPitch = 0f,
    )
    val pose = provider.computePose(createTestSkeleton(), context)
    val hips = pose[HumanBone.HIPS]
    assertNotNull(hips)
    // Hips should be rotated to horizontal
    assertNotEquals(0f, hips!!.rotation.x, 0.001f)
}

@Test
fun `riding pose opens legs`() {
    val provider = VanillaPoseProvider()
    val context = PoseContext(
        partialTick = 0f, limbSwing = 0f, limbSwingAmount = 0f,
        isSwinging = false, isSneaking = false, isSprinting = false,
        isSwimming = false, isFallFlying = false, isRiding = true,
        headYaw = 0f, headPitch = 0f,
    )
    val pose = provider.computePose(createTestSkeleton(), context)
    val leftLeg = pose[HumanBone.LEFT_UPPER_LEG]
    assertNotNull(leftLeg)
    // Legs should have Z rotation for spreading
    assertNotEquals(0f, leftLeg!!.rotation.z, 0.001f)
}
```

- [ ] **Step 2: テスト実行して FAIL を確認**

- [ ] **Step 3: VanillaPoseProvider に水泳・エリトラ・騎乗を実装**

`computePose()` 内に条件分岐を追加:

```kotlin
// Swimming
if (context.isSwimming) {
    poses[HumanBone.HIPS] = BonePose(
        rotation = Quaternionf().rotateX(-Math.toRadians(90.0).toFloat())
    )
    // Legs kick
    val kickAngle = cos(swing * 0.6662f) * 0.5f * swingAmount
    poses[HumanBone.LEFT_UPPER_LEG] = BonePose(rotation = Quaternionf().rotateX(-kickAngle))
    poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(rotation = Quaternionf().rotateX(kickAngle))
}

// Elytra
if (context.isFallFlying) {
    poses[HumanBone.HIPS] = BonePose(
        rotation = Quaternionf().rotateX(-Math.toRadians(70.0).toFloat())
    )
    // Arms back
    poses[HumanBone.LEFT_UPPER_ARM] = BonePose(rotation = Quaternionf().rotateZ(Math.toRadians(90.0).toFloat()))
    poses[HumanBone.RIGHT_UPPER_ARM] = BonePose(rotation = Quaternionf().rotateZ(-Math.toRadians(90.0).toFloat()))
}

// Riding
if (context.isRiding) {
    val legSpread = Math.toRadians(30.0).toFloat()
    poses[HumanBone.LEFT_UPPER_LEG] = BonePose(
        rotation = Quaternionf().rotateX(-Math.toRadians(70.0).toFloat()).rotateZ(-legSpread)
    )
    poses[HumanBone.RIGHT_UPPER_LEG] = BonePose(
        rotation = Quaternionf().rotateX(-Math.toRadians(70.0).toFloat()).rotateZ(legSpread)
    )
}
```

- [ ] **Step 4: テスト実行して PASS を確認**

```bash
./gradlew :common:test --tests "*VanillaPoseProviderTest*"
```

- [ ] **Step 5: ゲーム内で水泳・エリトラ・騎乗を確認**

- [ ] **Step 6: Commit**

```bash
git add common/src/main/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProvider.kt \
        common/src/test/kotlin/com/github/narazaka/vrmmod/animation/VanillaPoseProviderTest.kt
git commit -m "feat: add swimming, elytra, and riding animations to VanillaPoseProvider"
```

---

## Summary

| Task | 内容 | 種別 |
|------|------|------|
| 1 | Architectury プロジェクト初期構築 | Setup |
| 2 | テスト用VRMファイルの準備 | Setup |
| 3 | VRM 内部データモデル定義 | Parser |
| 4 | JglTF PoC（glTF + extensions アクセス検証） | Parser / PoC |
| 5 | VRM拡張JSONパーサー | Parser |
| 6 | VrmParser 統合パーサー | Parser |
| 7 | テクスチャ管理（DynamicTexture） | Rendering |
| 8 | VrmPlayerManager（プレイヤー管理） | Rendering |
| 9 | VrmRenderer（Tポーズ静的描画） | Rendering |
| 10 | Mixin（PlayerRenderer差し替え） | Rendering |
| 11 | クライアント初期化（config + キーバインド） | Integration |
| 12 | PoseProvider + スキニングエンジン | Animation |
| 13 | VanillaPoseProvider（歩行・攻撃・スニーク） | Animation |
| 14 | VrmRenderer にスキニング統合 | Integration |
| 15 | 水泳・エリトラ・騎乗アニメーション追加 | Animation |

### MVP で対応しない項目（後続フェーズ）

以下は仕様書に記載されているが、MVP スコープ外としたもの:

- **名札位置調整**: VRM モデルの head ボーン高さに合わせた名札オフセット。スケーリングが正しければ大きくずれないため P2 以降で対応。
- **エラーケーステスト**: 不正 VRM ファイル、テクスチャ欠損モデル、ボーン欠損モデルの処理。MVP ではクラッシュしないことを手動確認し、体系的なテストは後続で追加。

**MVP完了後の次のステップ:** Phase 4 (SpringBone) 以降の計画を別途策定する。
