# Multi-Version Support via Stonecutter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the VRM Mod to build for MC 1.21.4, 1.21.1, and 1.20.1 from a single branch using Stonecutter comment-based preprocessing.

**Architecture:** Migrate from Architectury multi-module (`common`/`fabric`/`neoforge`) to Stonecutter flat layout (single `src/`) with Stonecraft wrapper plugin. Version-specific code uses `//? if >=1.21.2 {` comment directives. Loader-specific code uses `//? if fabric {` / `//? if neoforge {` / `//? if forge {` directives. This preserves Architectury Loom for cross-loader compilation while adding Stonecutter for cross-version compilation.

**Tech Stack:** Stonecutter 0.8, Stonecraft 1.9.x, Architectury Loom, Kotlin 2.1.0, Gradle 8.11

---

## Phased Approach

This plan is split into 3 independent phases, each producing a working, testable build:

| Phase | Scope | Effort | Risk |
|-------|-------|--------|------|
| **Phase 1** | Stonecutter infrastructure + 1.21.4 only (no regression) | Medium | Low |
| **Phase 2** | Add MC 1.21.1 (Fabric + NeoForge) | High | Medium |
| **Phase 3** | Add MC 1.20.1 (Fabric + Forge) | Very High | High |

**Recommendation:** Implement Phase 1 and 2 first. Phase 3 (1.20.1) involves a complete networking rewrite and Forge loader support — evaluate after Phase 2 whether the user base justifies the effort.

---

## Version Compatibility Matrix

| | MC 1.21.4 | MC 1.21.1 | MC 1.20.1 |
|---|---|---|---|
| Java | 21 | 21 | 17 |
| Loaders | Fabric, NeoForge | Fabric, NeoForge | Fabric, Forge |
| Architectury API | 15.0.x | 13.0.x | 9.2.x |
| EntityRenderState | Yes (1.21.2+) | **No** | **No** |
| CustomPacketPayload | Yes | Yes | **No** |
| StreamCodec | Yes | Yes | **No** |
| blockInteractionRange() | Yes | Yes | **No** (use getPickRange) |
| TriState | Yes | Check needed | **No** |
| Direction.getApproximateNearest | Yes | **No** (getNearest) | **No** (getNearest) |
| ItemStackRenderState | Yes | **No** | **No** |
| ItemModelResolver | Yes | **No** | **No** |

---

## API Difference Details (Reference for Phase 2-3)

### Rendering Pipeline (1.21.2+ vs earlier)

**1.21.4 (EntityRenderState pattern):**
```java
// Two-phase: extractRenderState() then render()
PlayerRenderer.extractRenderState(AbstractClientPlayer, PlayerRenderState, float)
LivingEntityRenderer.render(LivingEntityRenderState, PoseStack, MultiBufferSource, int)
```

**1.21.1 / 1.20.1 (direct Entity pattern):**
```java
// Entity passed directly to render()
PlayerRenderer.render(AbstractClientPlayer, float yaw, float partialTick, PoseStack, MultiBufferSource, int)
LivingEntityRenderer.render(LivingEntity, float yaw, float partialTick, PoseStack, MultiBufferSource, int)
```

Key implication: In pre-1.21.2, the entity is available directly in `render()`, so the `VrmRenderContext` ThreadLocal pattern for passing UUID from `extractRenderState` to `render` is unnecessary.

### Networking (1.21+ vs 1.20.1)

**1.21.x (CustomPacketPayload + StreamCodec):**
```kotlin
data class ModelAnnouncePayload(...) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<ModelAnnouncePayload>(ResourceLocation.fromNamespaceAndPath(...))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, ModelAnnouncePayload> = ...
    }
}
NetworkManager.registerReceiver(NetworkManager.c2s(), TYPE, CODEC) { payload, context -> ... }
```

**1.20.1 (ResourceLocation + FriendlyByteBuf):**
```kotlin
val PACKET_ID = ResourceLocation("vrmmod", "model_announce")
NetworkManager.registerReceiver(NetworkManager.Side.S2C, PACKET_ID) { buf, context -> ... }
```

---

## Phase 1: Stonecutter Infrastructure Setup (1.21.4 Only)

### Goal
Convert from Architectury multi-module to Stonecutter flat layout. The mod continues to target ONLY MC 1.21.4 with Fabric + NeoForge. No functional changes — pure infrastructure migration.

### Target File Structure

```
vrmmod/
├── src/main/
│   ├── kotlin/net/narazaka/vrmmod/
│   │   ├── VrmMod.kt                           # unchanged
│   │   ├── platform/
│   │   │   ├── VrmModFabric.kt                  # from fabric/, wrapped in //? if fabric
│   │   │   ├── VrmModNeoForge.kt                # from neoforge/, wrapped in //? if neoforge
│   │   │   └── VrmModMenuIntegration.kt         # from fabric/, wrapped in //? if fabric
│   │   ├── animation/                           # from common/, unchanged
│   │   ├── client/                              # from common/, unchanged
│   │   ├── network/                             # from common/, unchanged
│   │   ├── physics/                             # from common/, unchanged
│   │   ├── render/                              # from common/, unchanged
│   │   ├── vrm/                                 # from common/, unchanged
│   │   └── vroidhub/                            # from common/, unchanged
│   ├── java/net/narazaka/vrmmod/mixin/          # merged from fabric+neoforge (identical)
│   │   ├── PlayerRendererMixin.java
│   │   ├── LivingEntityRendererMixin.java
│   │   ├── HandRendererMixin.java
│   │   ├── CameraMixin.java
│   │   └── GameRendererMixin.java
│   └── resources/
│       ├── fabric.mod.json                      # from fabric/ (auto-excluded for neoforge builds)
│       ├── META-INF/neoforge.mods.toml          # from neoforge/ (auto-excluded for fabric builds)
│       ├── vrmmod.accesswidener                 # from common/
│       ├── vrmmod.mixins.json                   # unified (package: net.narazaka.vrmmod.mixin)
│       └── assets/vrmmod/                       # from common/
│           ├── lang/
│           └── animations/
├── versions/
│   └── dependencies/
│       └── 1.21.4.properties
├── build.gradle.kts                             # Stonecraft + custom tasks
├── settings.gradle.kts                          # Stonecutter + Stonecraft plugin setup
├── stonecutter.gradle.kts                       # Stonecutter controller
├── gradle.properties                            # Mod metadata (mod.id, mod.name, etc.)
└── gradle/wrapper/gradle-wrapper.properties     # Gradle 8.11
```

### Task 1: Create Stonecutter/Stonecraft Build Configuration

**Files:**
- Create: `settings.gradle.kts` (replace existing)
- Create: `stonecutter.gradle.kts`
- Create: `build.gradle.kts` (replace existing)
- Create: `gradle.properties` (replace existing)
- Create: `versions/dependencies/1.21.4.properties`

- [ ] **Step 1: Back up current build files**

```bash
git stash  # or commit current state
```

- [ ] **Step 2: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.fabricmc.net/")
        maven("https://maven.architectury.dev")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net")
        maven("https://thedarkcolour.github.io/KotlinForForge/")
        maven("https://maven.shedaniel.me/")
        maven("https://maven.terraformersmc.com/")
    }
}

plugins {
    id("gg.meza.stonecraft") version "1.9.+"
    id("dev.kikugie.stonecutter") version "0.8.+"
}

stonecutter {
    centralScript = "build.gradle.kts"
    kotlinController = true

    shared {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) vers("$version-$loader", version)
        }

        mc("1.21.4", "fabric", "neoforge")

        vcsVersion = "1.21.4-fabric"
    }

    create(rootProject)
}

rootProject.name = "vrmmod"
```

- [ ] **Step 3: Write `stonecutter.gradle.kts`**

```kotlin
plugins {
    id("dev.kikugie.stonecutter")
    id("gg.meza.stonecraft")
}

stonecutter active "1.21.4-fabric" /* [SC] DO NOT EDIT */
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048M
org.gradle.parallel=false

# Mod Properties
mod.id=vrmmod
mod.name=VRM Mod
mod.version=0.1.0
mod.group=net.narazaka.vrmmod
mod.description=VRM avatar mod for Minecraft
```

- [ ] **Step 5: Write `versions/dependencies/1.21.4.properties`**

```properties
minecraft_version=1.21.4
architectury_api_version=15.0.3
fabric_loader_version=0.16.10
fabric_version=0.114.0+1.21.4
fabric_language_kotlin_version=1.13.0+kotlin.2.1.0
neoforge_version=21.4.156
kotlin_for_forge_version=5.7.0
mod_menu_version=13.0.3
```

- [ ] **Step 6: Write `build.gradle.kts`**

```kotlin
import gg.meza.stonecraft.mod
import java.util.Base64
import java.security.SecureRandom

plugins {
    id("gg.meza.stonecraft")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "2.1.0"
}

// Stonecraft handles: architectury loom, java version, minecraft deps, mappings, etc.
modSettings {
    clientOptions {
        fov = 90
        guiScale = 3
        narrator = false
    }
}

val loader = mod.loader // "fabric", "neoforge", or "forge"

// ---- Dependencies ----

repositories {
    maven("https://maven.shedaniel.me/")
    maven("https://maven.terraformersmc.com/")
    maven("https://thedarkcolour.github.io/KotlinForForge/")
    mavenCentral()
}

val shadowBundle: Configuration by configurations.creating

dependencies {
    modApi("dev.architectury:architectury-${loader}:${mod.prop("architectury_api_version")}")

    // JglTF for glTF/VRM parsing - must be bundled
    implementation("de.javagl:jgltf-model:2.0.4")
    shadowBundle("de.javagl:jgltf-model:2.0.4")

    //? if fabric {
    modImplementation("net.fabricmc:fabric-language-kotlin:${mod.prop("fabric_language_kotlin_version")}")
    modImplementation("com.terraformersmc:modmenu:${mod.prop("mod_menu_version")}")
    //?} else if neoforge {
    /*implementation("thedarkcolour:kotlinforforge-neoforge:${mod.prop("kotlin_for_forge_version")}") {
        exclude(group = "net.neoforged.fancymodloader", module = "loader")
    }*/
    //?}

    // JOML for tests
    testImplementation("org.joml:joml:1.10.8")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
}

// ---- Shadow JAR (bundle JglTF) ----

tasks.shadowJar {
    configurations = listOf(shadowBundle)
    archiveClassifier.set("dev-shadow")
    // Exclude transitive deps already provided by MC/loader
    exclude("com/fasterxml/**")
    exclude("com/google/gson/**")
    exclude("META-INF/maven/com.fasterxml*/**")
    exclude("META-INF/services/com.fasterxml*")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
}

tasks.remapJar {
    injectAccessWidener.set(true)
    inputFile.set(tasks.shadowJar.get().archiveFile)
    dependsOn(tasks.shadowJar)
    archiveClassifier.set(null as String?)
}

// ---- VRoid Hub Secrets Generation ----

val generateVRoidHubSecrets by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/sources/vroidhub/net/narazaka/vrmmod/vroidhub")
    val jsonFile = rootProject.file("vrmmod-vroidhub-secrets.json")
    if (jsonFile.exists()) inputs.file(jsonFile)
    inputs.property("envClientId", System.getenv("VROIDHUB_CLIENT_ID") ?: "")
    inputs.property("envClientSecret", System.getenv("VROIDHUB_CLIENT_SECRET") ?: "")
    outputs.dir(outputDir)

    doLast {
        var clientId = System.getenv("VROIDHUB_CLIENT_ID") ?: ""
        var clientSecret = System.getenv("VROIDHUB_CLIENT_SECRET") ?: ""

        if (clientId.isEmpty() && clientSecret.isEmpty() && jsonFile.exists()) {
            try {
                val json = groovy.json.JsonSlurper().parseText(jsonFile.readText()) as Map<*, *>
                clientId = json["clientId"]?.toString() ?: ""
                clientSecret = json["clientSecret"]?.toString() ?: ""
            } catch (e: Exception) {
                logger.warn("Failed to read vrmmod-vroidhub-secrets.json: ${e.message}")
            }
        }

        val random = SecureRandom()
        val xorKeyBytes = ByteArray(32).also { random.nextBytes(it) }
        val xorKey = Base64.getEncoder().encodeToString(xorKeyBytes)

        fun xorEncode(input: String, key: String): String {
            if (input.isEmpty()) return ""
            val keyBytes = key.toByteArray(Charsets.UTF_8)
            val encoded = input.toByteArray(Charsets.UTF_8).mapIndexed { i, b ->
                (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }.toByteArray()
            return Base64.getEncoder().encodeToString(encoded)
        }

        val encodedId = xorEncode(clientId, xorKey)
        val encodedSecret = xorEncode(clientSecret, xorKey)

        val splitA = random.nextInt(xorKey.length / 3) + 1
        val splitB = splitA + random.nextInt(xorKey.length / 3) + 1
        val keyPart1 = xorKey.substring(0, splitA)
        val keyPart2 = xorKey.substring(splitA, splitB)
        val keyPart3 = xorKey.substring(splitB)

        fun randomName(): String {
            val chars = "abcdefghijklmnopqrstuvwxyz"
            val prefix = chars[random.nextInt(chars.length)]
            val hex = ByteArray(4).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            return "$prefix$hex"
        }
        val nameId = randomName()
        val nameSecret = randomName()
        val nameK1 = randomName()
        val nameK2 = randomName()
        val nameK3 = randomName()
        val nameDecode = randomName()

        val dir = outputDir.get().asFile
        dir.mkdirs()
        dir.resolve("VRoidHubSecrets.kt").writeText(
            """
            |package net.narazaka.vrmmod.vroidhub
            |
            |import java.util.Base64
            |
            |object VRoidHubSecrets {
            |    private const val $nameId = "$encodedId"
            |    private const val $nameSecret = "$encodedSecret"
            |    private val $nameK1 = "$keyPart1"
            |    private val $nameK2 = "$keyPart2"
            |    private val $nameK3 = "$keyPart3"
            |
            |    private fun $nameDecode(encoded: String): String {
            |        if (encoded.isEmpty()) return ""
            |        val key = ($nameK1 + $nameK2 + $nameK3).toByteArray(Charsets.UTF_8)
            |        val decoded = Base64.getDecoder().decode(encoded).mapIndexed { i, b ->
            |            (b.toInt() xor key[i % key.size].toInt()).toByte()
            |        }.toByteArray()
            |        return String(decoded, Charsets.UTF_8)
            |    }
            |
            |    val defaultClientId: String get() = $nameDecode($nameId)
            |    val defaultClientSecret: String get() = $nameDecode($nameSecret)
            |
            |    fun defaultConfig(): VRoidHubConfig = VRoidHubConfig(
            |        clientId = defaultClientId,
            |        clientSecret = defaultClientSecret,
            |    )
            |}
            """.trimMargin()
        )
    }
}

sourceSets.main {
    java.srcDir(generateVRoidHubSecrets.map { layout.buildDirectory.dir("generated/sources/vroidhub") })
}

tasks.named("compileKotlin") {
    dependsOn(generateVRoidHubSecrets)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 7: Verify settings are syntactically correct**

Run: `./gradlew tasks --dry-run` (or similar quick validation)
Expected: No syntax errors in build files

- [ ] **Step 8: Commit build configuration**

```bash
git add settings.gradle.kts stonecutter.gradle.kts build.gradle.kts gradle.properties versions/
git commit -m "build: add Stonecutter/Stonecraft build configuration for multi-version support"
```

### Task 2: Migrate Source Files to Flat Layout

**Files:**
- Move: `common/src/main/kotlin/**` → `src/main/kotlin/`
- Move: `common/src/main/resources/**` → `src/main/resources/`
- Move: `fabric/src/main/java/**/mixin/*.java` → `src/main/java/net/narazaka/vrmmod/mixin/`
- Move: `fabric/src/main/kotlin/**/VrmModFabric.kt` → `src/main/kotlin/net/narazaka/vrmmod/platform/`
- Move: `fabric/src/main/kotlin/**/VrmModMenuIntegration.kt` → `src/main/kotlin/net/narazaka/vrmmod/platform/`
- Move: `neoforge/src/main/kotlin/**/VrmModNeoForge.kt` → `src/main/kotlin/net/narazaka/vrmmod/platform/`
- Move: `fabric/src/main/resources/fabric.mod.json` → `src/main/resources/`
- Move: `neoforge/src/main/resources/META-INF/neoforge.mods.toml` → `src/main/resources/META-INF/`
- Create: `src/main/resources/vrmmod.mixins.json` (unified)
- Delete: `common/`, `fabric/`, `neoforge/` directories

- [ ] **Step 1: Create target directory structure**

```bash
mkdir -p src/main/kotlin/net/narazaka/vrmmod/platform
mkdir -p src/main/java/net/narazaka/vrmmod/mixin
mkdir -p src/main/resources/META-INF
mkdir -p src/main/resources/assets
```

- [ ] **Step 2: Move common Kotlin sources**

Move all directories from `common/src/main/kotlin/net/narazaka/vrmmod/` to `src/main/kotlin/net/narazaka/vrmmod/`:
- `animation/`, `client/`, `network/`, `physics/`, `render/`, `vrm/`, `vroidhub/`
- `VrmMod.kt`

- [ ] **Step 3: Move common resources**

Move from `common/src/main/resources/`:
- `vrmmod.accesswidener` → `src/main/resources/`
- `assets/vrmmod/` → `src/main/resources/assets/vrmmod/`

- [ ] **Step 4: Move and refactor Mixin Java files**

Copy the 5 Mixin files from `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/` to `src/main/java/net/narazaka/vrmmod/mixin/`. Update the package declaration in each file:

```java
// Old:
package net.narazaka.vrmmod.fabric.mixin;
// New:
package net.narazaka.vrmmod.mixin;
```

Files: `PlayerRendererMixin.java`, `LivingEntityRendererMixin.java`, `HandRendererMixin.java`, `CameraMixin.java`, `GameRendererMixin.java`

(NeoForge mixins are identical — we use only one copy.)

- [ ] **Step 5: Move platform entry points and update packages**

Move Fabric entry points to `src/main/kotlin/net/narazaka/vrmmod/platform/`:

**VrmModFabric.kt** — Update package and wrap in Stonecutter conditional:
```kotlin
//? if fabric {
package net.narazaka.vrmmod.platform

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents

class VrmModFabric : ClientModInitializer {
    override fun onInitializeClient() {
        VrmMod.init()
        VrmModClient.init()

        WorldRenderEvents.AFTER_ENTITIES.register { context ->
            val poseStack = context.matrixStack() ?: return@register
            val consumers = context.consumers() ?: return@register
            VrmFirstPersonRenderer.renderFirstPerson(
                poseStack,
                consumers,
                context.tickCounter().getGameTimeDeltaPartialTick(false),
            )
            if (consumers is net.minecraft.client.renderer.MultiBufferSource.BufferSource) {
                consumers.endBatch()
            }
        }
    }
}
//?}
```

**VrmModNeoForge.kt** — Update package and wrap:
```kotlin
//? if neoforge {
package net.narazaka.vrmmod.platform

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmModScreen
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.Mod
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.client.gui.IConfigScreenFactory
import net.neoforged.neoforge.common.NeoForge

@Mod(VrmMod.MOD_ID)
class VrmModNeoForge(container: ModContainer) {
    init {
        VrmMod.init()
        if (FMLEnvironment.dist.isClient) {
            VrmModClient.init()
            container.registerExtensionPoint(
                IConfigScreenFactory::class.java,
                IConfigScreenFactory { _, parent -> VrmModScreen(parent) },
            )

            NeoForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
                if (event.stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val bufferSource = mc.renderBuffers().bufferSource()
                    VrmFirstPersonRenderer.renderFirstPerson(
                        event.poseStack,
                        bufferSource,
                        event.partialTick.getGameTimeDeltaPartialTick(false),
                    )
                    bufferSource.endBatch()
                }
            }
        }
    }
}
//?}
```

**VrmModMenuIntegration.kt** — Update package and wrap:
```kotlin
//? if fabric {
package net.narazaka.vrmmod.platform

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.narazaka.vrmmod.client.VrmModScreen

class VrmModMenuIntegration : ModMenuApi {
    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        return ConfigScreenFactory { parent -> VrmModScreen(parent) }
    }
}
//?}
```

- [ ] **Step 6: Create unified mixin config**

`src/main/resources/vrmmod.mixins.json`:
```json
{
  "package": "net.narazaka.vrmmod.mixin",
  "minVersion": "0.8",
  "compatibilityLevel": "JAVA_21",
  "client": [
    "PlayerRendererMixin",
    "LivingEntityRendererMixin",
    "HandRendererMixin",
    "CameraMixin",
    "GameRendererMixin"
  ]
}
```

- [ ] **Step 7: Update mod metadata for Stonecraft variable substitution**

`src/main/resources/fabric.mod.json`:
```json
{
  "schemaVersion": 1,
  "id": "${id}",
  "version": "${version}",
  "name": "${name}",
  "description": "${description}",
  "authors": ["Narazaka"],
  "license": "MIT",
  "environment": "client",
  "entrypoints": {
    "client": [
      { "adapter": "kotlin", "value": "net.narazaka.vrmmod.platform.VrmModFabric" }
    ],
    "modmenu": [
      { "adapter": "kotlin", "value": "net.narazaka.vrmmod.platform.VrmModMenuIntegration" }
    ]
  },
  "accessWidener": "vrmmod.accesswidener",
  "mixins": ["vrmmod.mixins.json"],
  "depends": {
    "fabricloader": ">=0.16.0",
    "fabric-api": "*",
    "fabric-language-kotlin": ">=1.13.0+kotlin.2.1.0",
    "minecraft": "~1.21.4",
    "architectury": ">=15.0.0"
  }
}
```

`src/main/resources/META-INF/neoforge.mods.toml`:
```toml
modLoader = "kotlinforforge"
loaderVersion = "[5,)"
license = "MIT"

[[mixins]]
config = "vrmmod.mixins.json"

[[mods]]
modId = "${id}"
version = "${version}"
displayName = "${name}"
description = "${description}"
authors = "Narazaka"

[[dependencies.vrmmod]]
modId = "neoforge"
type = "required"
versionRange = "[21.4,)"
side = "CLIENT"

[[dependencies.vrmmod]]
modId = "minecraft"
type = "required"
versionRange = "[1.21.4,1.21.5)"
side = "CLIENT"

[[dependencies.vrmmod]]
modId = "architectury"
type = "required"
versionRange = "[15.0.0,)"
side = "CLIENT"
```

- [ ] **Step 8: Delete old module directories**

Remove: `common/`, `fabric/`, `neoforge/`

- [ ] **Step 9: Remove old `common/build.gradle.kts`, `fabric/build.gradle.kts`, `neoforge/build.gradle.kts`**

These are no longer needed — the single root `build.gradle.kts` handles everything.

- [ ] **Step 10: Commit source migration**

```bash
git add -A
git commit -m "refactor: migrate to Stonecutter flat layout (single src/)"
```

### Task 3: Build Verification — Stonecraft 判断ポイント

> **重要: ここでユーザーに判断を仰ぐこと。**
> Task 1-2 完了後、最初のビルドを試みる。Shadow JAR チェーンや VRoidHub secrets 生成タスクが Stonecraft の内部管理と衝突する可能性がある。
> **ビルドが通らない場合、修正を試みるのではなく、結果をユーザーに報告して方針を判断してもらう。**
> 選択肢:
> - (A) Stonecraft のまま問題を解決して続行
> - (B) Stonecraft を外して raw Stonecutter + 手書き Architectury Loom 設定に切り替え
> - (C) その他（ユーザー判断）

- [ ] **Step 1: Run Fabric build**

```bash
./gradlew "1.21.4-fabric:build"
```
Expected: Successful build producing a JAR in `versions/1.21.4-fabric/build/libs/`

**ビルド失敗時**: エラー内容を記録し、ユーザーに報告。自己判断で修正を重ねない。

- [ ] **Step 2: Run NeoForge build**

```bash
./gradlew "1.21.4-neoforge:build"
```
Expected: Successful build producing a JAR

- [ ] **Step 3: Run tests**

```bash
./gradlew "1.21.4-fabric:test"
```
Expected: All existing tests pass

- [ ] **Step 4: ユーザーに結果を報告**

ビルド結果（成功/失敗、エラー内容）をユーザーに提示し、続行の可否を確認する。
全ビルド成功の場合のみ、Step 5 以降に進む。

- [ ] **Step 5: Run chiseled build (all variants)**

```bash
./gradlew chiseledBuild
```
Expected: Both Fabric and NeoForge JARs built successfully

- [ ] **Step 6: Manual smoke test — launch Fabric client**

```bash
./gradlew "1.21.4-fabric:runClient"
```
Expected: Game launches, VRM mod loads, model renders correctly

- [ ] **Step 7: Manual smoke test — launch NeoForge client**

```bash
./gradlew "1.21.4-neoforge:runClient"
```
Expected: Game launches, VRM mod loads, model renders correctly

- [ ] **Step 8: Fix any issues and commit**

```bash
git add -A
git commit -m "fix: resolve build issues from Stonecutter migration"
```

### Task 4: Troubleshooting Checklist (Reference)

Common issues during Stonecutter migration:

1. **"Cannot resolve symbol" in IDE** — Run `./gradlew "1.21.4-fabric:genSources"` to generate decompiled MC sources for the active version.

2. **Stonecutter preprocessor not activating** — Ensure `stonecutter.gradle.kts` exists and has the `stonecutter active "1.21.4-fabric"` line.

3. **Mixin not found** — Verify the `vrmmod.mixins.json` `package` field matches the actual Java package (`net.narazaka.vrmmod.mixin`).

4. **Access widener not applied** — Stonecraft auto-detects `{mod.id}.accesswidener` in `src/main/resources/`. Ensure the file is named `vrmmod.accesswidener`.

5. **Shadow JAR missing JglTF** — Verify the `shadowBundle` configuration is used in `tasks.shadowJar.configurations`.

6. **Platform-specific code compiled for wrong loader** — Ensure Stonecutter conditionals use the correct syntax (`//? if fabric {` with `{` on the same line).

7. **Kotlin compilation fails with "unresolved reference"** — Stonecutter comments out inactive code blocks. Check that `//? if neoforge {` blocks use `/* ... */` wrapping for the inactive state.

---

## Phase 2: Add MC 1.21.1 Support

### Goal
Add 1.21.1 as a second target version. The major challenge is that MC 1.21.1 does NOT have the `EntityRenderState` system introduced in 1.21.2.

### Task 5: Add 1.21.1 Version Configuration

**Files:**
- Modify: `settings.gradle.kts`
- Create: `versions/dependencies/1.21.1.properties`

- [ ] **Step 1: Add 1.21.1 to `settings.gradle.kts`**

In the `shared {}` block, add:
```kotlin
mc("1.21.1", "fabric", "neoforge")
mc("1.21.4", "fabric", "neoforge")
```

- [ ] **Step 2: Create `versions/dependencies/1.21.1.properties`**

```properties
minecraft_version=1.21.1
architectury_api_version=13.0.8
fabric_loader_version=0.16.10
fabric_version=0.107.0+1.21.1
fabric_language_kotlin_version=1.13.0+kotlin.2.1.0
neoforge_version=21.1.77
kotlin_for_forge_version=5.6.0
mod_menu_version=11.0.3
```

**NOTE:** These dependency versions must be verified against the actual releases available on Maven. Check:
- Architectury API: https://modrinth.com/mod/architectury-api/versions?g=1.21.1
- Fabric API: https://modrinth.com/mod/fabric-api/versions?g=1.21.1
- NeoForge: https://projects.neoforged.net/neoforged/neoforge (1.21.1 releases)
- Kotlin for Forge: https://modrinth.com/mod/kotlin-for-forge/versions?g=1.21.1
- Mod Menu: https://modrinth.com/mod/modmenu/versions?g=1.21.1

- [ ] **Step 3: Commit version config**

```bash
git add settings.gradle.kts versions/dependencies/1.21.1.properties
git commit -m "build: add MC 1.21.1 version configuration"
```

### Task 6: Add Stonecutter Constants for Version Features

**Files:**
- Modify: `build.gradle.kts`

- [ ] **Step 1: Add version feature constants**

Add after the `modSettings {}` block in `build.gradle.kts`:

```kotlin
// Stonecutter version constants for conditional compilation
stonecutter {
    // EntityRenderState was introduced in MC 1.21.2
    consts["HAS_RENDER_STATE"] = eval(stonecutter.current.version, ">=1.21.2")
    // blockInteractionRange() added in 1.20.5
    consts["HAS_INTERACTION_RANGE"] = eval(stonecutter.current.version, ">=1.20.5")
    // CustomPacketPayload + StreamCodec (1.20.5+)
    consts["HAS_CUSTOM_PAYLOAD"] = eval(stonecutter.current.version, ">=1.20.5")
    // Direction.getApproximateNearest renamed in 1.21.4
    consts["HAS_APPROXIMATE_NEAREST"] = eval(stonecutter.current.version, ">=1.21.4")
    // TriState in TextureStateShard (1.21.4+, verify)
    consts["HAS_TRISTATE"] = eval(stonecutter.current.version, ">=1.21.4")
    // ItemStackRenderState + ItemModelResolver (1.21.2+)
    consts["HAS_ITEM_RENDER_STATE"] = eval(stonecutter.current.version, ">=1.21.2")
}
```

- [ ] **Step 2: Commit**

```bash
git add build.gradle.kts
git commit -m "build: add Stonecutter version feature constants"
```

### Task 7: Version-Conditional Mixin — PlayerRendererMixin

**Files:**
- Modify: `src/main/java/net/narazaka/vrmmod/mixin/PlayerRendererMixin.java`

The entire Mixin body changes because `extractRenderState` doesn't exist before 1.21.2. In pre-1.21.2, we capture the player entity and data directly from `render()`.

- [ ] **Step 1: Add version conditionals to PlayerRendererMixin**

```java
package net.narazaka.vrmmod.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;

//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
//?}

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    //? if HAS_RENDER_STATE {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        capturePlayerData(player, partialTick);
    }
    //?} else {
    /*
    @Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, float entityYaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight, CallbackInfo ci) {
        capturePlayerData(player, partialTick);
    }
    *///?}

    private static void capturePlayerData(AbstractClientPlayer player, float partialTick) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
        Vec3 pos = player.getPosition(partialTick);
        VrmRenderContext.ENTITY_X.set((float) pos.x);
        VrmRenderContext.ENTITY_Y.set((float) pos.y);
        VrmRenderContext.ENTITY_Z.set((float) pos.z);
        VrmRenderContext.ON_GROUND.set(player.onGround());
        VrmRenderContext.HURT_TIME.set((float) player.hurtTime);

        List<String> tags = new ArrayList<>();
        player.getMainHandItem().getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                tags.add(loc.getPath());
            } else {
                tags.add(loc.toString());
            }
        });
        VrmRenderContext.MAIN_HAND_ITEM_TAGS.set(tags);

        List<String> offTags = new ArrayList<>();
        player.getOffhandItem().getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                offTags.add(loc.getPath());
            } else {
                offTags.add(loc.toString());
            }
        });
        VrmRenderContext.OFF_HAND_ITEM_TAGS.set(offTags);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/narazaka/vrmmod/mixin/PlayerRendererMixin.java
git commit -m "feat: version-conditional PlayerRendererMixin for pre-1.21.2"
```

### Task 8: Version-Conditional Mixin — LivingEntityRendererMixin

**Files:**
- Modify: `src/main/java/net/narazaka/vrmmod/mixin/LivingEntityRendererMixin.java`
- Modify: `src/main/kotlin/net/narazaka/vrmmod/render/MixinHelper.kt`
- Modify: `src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt`

- [ ] **Step 1: Update VrmRenderContext with PLAYER_ENTITY for pre-1.21.2**

In pre-1.21.2, we need to pass the entity reference from `render()` to `MixinHelper`. Add to `VrmRenderContext.kt`:

```kotlin
package net.narazaka.vrmmod.render

import java.util.UUID
//? if !HAS_RENDER_STATE {
/*import net.minecraft.client.player.AbstractClientPlayer*/
//?}

object VrmRenderContext {
    @JvmField
    val CURRENT_PLAYER_UUID: ThreadLocal<UUID> = ThreadLocal()

    //? if !HAS_RENDER_STATE {
    /*@JvmField
    val PLAYER_ENTITY: ThreadLocal<AbstractClientPlayer?> = ThreadLocal()*/
    //?}

    @JvmField
    val ENTITY_X: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ENTITY_Y: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ENTITY_Z: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ON_GROUND: ThreadLocal<Boolean> = ThreadLocal.withInitial { true }
    @JvmField
    val HURT_TIME: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val MAIN_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
    @JvmField
    val OFF_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
}
```

- [ ] **Step 2: Update MixinHelper with version conditionals**

```kotlin
package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.PoseContext
//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.PlayerRenderState
//?}
import net.minecraft.world.InteractionHand

object MixinHelper {

    //? if HAS_RENDER_STATE {
    @JvmStatic
    fun buildPoseContext(renderState: PlayerRenderState): PoseContext {
        return PoseContext(
            partialTick = 0f,
            limbSwing = renderState.walkAnimationPos,
            limbSwingAmount = renderState.walkAnimationSpeed,
            speedValue = renderState.speedValue,
            isSneaking = renderState.isCrouching,
            isSwimming = renderState.isVisuallySwimming,
            swimAmount = renderState.swimAmount,
            isFallFlying = renderState.isFallFlying,
            isRiding = renderState.isPassenger,
            isInWater = renderState.isInWater,
            isOnGround = VrmRenderContext.ON_GROUND.get(),
            isSwinging = renderState.swinging,
            attackTime = renderState.attackTime,
            isUsingItem = renderState.isUsingItem,
            ticksUsingItem = renderState.ticksUsingItem,
            isAutoSpinAttack = renderState.isAutoSpinAttack,
            deathTime = renderState.deathTime,
            headYaw = renderState.yRot,
            headPitch = renderState.xRot,
            bodyYaw = renderState.bodyRot,
            entityX = VrmRenderContext.ENTITY_X.get(),
            entityY = VrmRenderContext.ENTITY_Y.get(),
            entityZ = VrmRenderContext.ENTITY_Z.get(),
            mainHandItemTags = VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
            offHandItemTags = VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
            isOffHandSwing = renderState.attackArm != renderState.mainArm,
            isOffHandUse = renderState.useItemHand != InteractionHand.MAIN_HAND,
            isLeftHanded = renderState.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT,
            hurtTime = VrmRenderContext.HURT_TIME.get(),
        )
    }
    //?} else {
    /*@JvmStatic
    fun buildPoseContextFromEntity(player: net.minecraft.client.player.AbstractClientPlayer,
                                    entityYaw: Float, partialTick: Float): PoseContext {
        val bodyRot = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick
        val headYaw = player.yRotO + (player.yRot - player.yRotO) * partialTick
        val headPitch = player.xRotO + (player.xRot - player.xRotO) * partialTick
        return PoseContext(
            partialTick = partialTick,
            limbSwing = player.walkAnimation.position(partialTick),
            limbSwingAmount = player.walkAnimation.speed(partialTick),
            speedValue = if (player.isSprinting) 1.0f else player.walkAnimation.speed(partialTick),
            isSneaking = player.isCrouching,
            isSwimming = player.isSwimming,
            swimAmount = player.getSwimAmount(partialTick),
            isFallFlying = player.isFallFlying,
            isRiding = player.isPassenger,
            isInWater = player.isInWater,
            isOnGround = player.onGround(),
            isSwinging = player.swinging,
            attackTime = player.getAttackAnim(partialTick),
            isUsingItem = player.isUsingItem,
            ticksUsingItem = player.ticksUsingItem,
            isAutoSpinAttack = player.isAutoSpinAttack,
            deathTime = player.deathTime.toFloat(),
            headYaw = headYaw - bodyRot,
            headPitch = headPitch,
            bodyYaw = bodyRot,
            entityX = VrmRenderContext.ENTITY_X.get(),
            entityY = VrmRenderContext.ENTITY_Y.get(),
            entityZ = VrmRenderContext.ENTITY_Z.get(),
            mainHandItemTags = VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
            offHandItemTags = VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
            isOffHandSwing = player.swingingArm != InteractionHand.MAIN_HAND,
            isOffHandUse = player.usedItemHand != InteractionHand.MAIN_HAND,
            isLeftHanded = player.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT,
            hurtTime = player.hurtTime.toFloat(),
        )
    }*/
    //?}

    @JvmStatic
    fun filterHitResult(
        hit: net.minecraft.world.phys.HitResult,
        origin: net.minecraft.world.phys.Vec3,
        range: Double,
    ): net.minecraft.world.phys.HitResult {
        val loc = hit.location
        if (!loc.closerThan(origin, range)) {
            //? if HAS_APPROXIMATE_NEAREST {
            val dir = net.minecraft.core.Direction.getApproximateNearest(
                loc.x - origin.x, loc.y - origin.y, loc.z - origin.z,
            )
            //?} else {
            /*val dir = net.minecraft.core.Direction.getNearest(
                (loc.x - origin.x).toFloat(), (loc.y - origin.y).toFloat(), (loc.z - origin.z).toFloat(),
            )*/
            //?}
            return net.minecraft.world.phys.BlockHitResult.miss(
                loc, dir, net.minecraft.core.BlockPos.containing(loc),
            )
        }
        return hit
    }
}
```

- [ ] **Step 3: Update LivingEntityRendererMixin**

```java
package net.narazaka.vrmmod.mixin;

import net.narazaka.vrmmod.animation.PoseContext;
import net.narazaka.vrmmod.render.VrmPlayerManager;
import net.narazaka.vrmmod.render.VrmRenderer;
import net.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;
import net.narazaka.vrmmod.render.MixinHelper;

import java.util.UUID;

//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
//?} else {
/*import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.AbstractClientPlayer;*/
//?}

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    //? if HAS_RENDER_STATE {
    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            LivingEntityRenderState livingRenderState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(livingRenderState instanceof PlayerRenderState renderState)) return;

        UUID uuid = VrmRenderContext.CURRENT_PLAYER_UUID.get();
        if (uuid == null) return;

        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = MixinHelper.buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        if (state.getAnimationConfig().getHeldItemThirdPerson()) {
            VrmRenderer.INSTANCE.renderHeldItems(
                    state,
                    renderState.rightHandItem,
                    renderState.leftHandItem,
                    poseStack,
                    bufferSource,
                    packedLight,
                    (float) Math.toRadians(renderState.bodyRot),
                    state.getAnimationConfig()
            );
        }
        VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void vrmmod$clearContext(
            LivingEntityRenderState livingRenderState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (livingRenderState instanceof PlayerRenderState) {
            VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        }
    }
    //?} else {
    /*
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();
        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = MixinHelper.buildPoseContextFromEntity(player, entityYaw, partialTick);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        // TODO: held item rendering for pre-1.21.2 (ItemStack-based, not ItemStackRenderState)
        VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void vrmmod$clearContext(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (entity instanceof AbstractClientPlayer) {
            VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        }
    }
    */
    //?}
}
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/net/narazaka/vrmmod/mixin/LivingEntityRendererMixin.java \
        src/main/kotlin/net/narazaka/vrmmod/render/MixinHelper.kt \
        src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt
git commit -m "feat: version-conditional LivingEntityRendererMixin for pre-1.21.2"
```

### Task 9: Version-Conditional GameRendererMixin

**Files:**
- Modify: `src/main/java/net/narazaka/vrmmod/mixin/GameRendererMixin.java`

- [ ] **Step 1: Add version conditionals for blockInteractionRange**

The key change: `EntitySelector.CAN_BE_PICKED` may not exist in 1.21.1 (verify during implementation). The `blockInteractionRange()` / `entityInteractionRange()` methods exist in 1.21.1 (added in 1.20.5). The `Direction.getApproximateNearest` vs `Direction.getNearest` change is in MixinHelper, not here.

For 1.21.1 vs 1.21.4, the main difference in GameRendererMixin is the `EntitySelector.CAN_BE_PICKED` predicate. Verify during implementation whether this exists in 1.21.1 or uses a lambda.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/net/narazaka/vrmmod/mixin/GameRendererMixin.java
git commit -m "feat: version-conditional GameRendererMixin"
```

### Task 10: Version-Conditional VrmRenderType

**Files:**
- Modify: `src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderType.kt`

- [ ] **Step 1: Handle TriState availability**

`TriState` may not exist in 1.21.1. The `TextureStateShard` constructor in older versions takes `(ResourceLocation, boolean blur, boolean mipmap)` instead of `(ResourceLocation, TriState, boolean)`. Verify during implementation and add conditional:

```kotlin
//? if HAS_TRISTATE {
import net.minecraft.util.TriState
//?}

// In the builder:
//? if HAS_TRISTATE {
.setTextureState(RenderStateShard.TextureStateShard(texture, TriState.FALSE, false))
//?} else {
/*.setTextureState(RenderStateShard.TextureStateShard(texture, false, false))*/
//?}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderType.kt
git commit -m "feat: version-conditional VrmRenderType for TriState compatibility"
```

### Task 11: Version-Conditional VrmFirstPersonRenderer (Held Items)

**Files:**
- Modify: `src/main/kotlin/net/narazaka/vrmmod/render/VrmFirstPersonRenderer.kt`

- [ ] **Step 1: Handle ItemStackRenderState / ItemModelResolver availability**

In pre-1.21.2, `ItemStackRenderState` and `ItemModelResolver` don't exist. Item rendering uses `ItemRenderer.renderStatic(ItemStack, ...)` directly.

```kotlin
//? if HAS_ITEM_RENDER_STATE {
import net.minecraft.client.renderer.item.ItemStackRenderState
//?}

// Field declarations:
//? if HAS_ITEM_RENDER_STATE {
private val rightHandItemState = ItemStackRenderState()
private val leftHandItemState = ItemStackRenderState()
//?}

// In renderFirstPerson(), held item rendering:
//? if HAS_ITEM_RENDER_STATE {
if (state.animationConfig.heldItemFirstPerson) {
    val itemModelResolver = mc.itemModelResolver
    rightHandItemState.clear()
    leftHandItemState.clear()
    itemModelResolver.updateForLiving(rightHandItemState, player.getItemHeldByArm(HumanoidArm.RIGHT), ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false, player)
    itemModelResolver.updateForLiving(leftHandItemState, player.getItemHeldByArm(HumanoidArm.LEFT), ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, player)
    val bodyYawRad = Math.toRadians(bodyRot.toDouble()).toFloat()
    VrmRenderer.renderHeldItems(state, rightHandItemState, leftHandItemState, poseStack, bufferSource, packedLight, bodyYawRad, state.animationConfig)
}
//?} else {
/*if (state.animationConfig.heldItemFirstPerson) {
    // TODO: Implement ItemStack-based held item rendering for pre-1.21.2
    // Use mc.itemRenderer.renderStatic(itemStack, displayContext, light, overlay, poseStack, bufferSource, level, seed)
}*/
//?}
```

- [ ] **Step 2: Also handle VrmRenderer.renderHeldItems signature**

`VrmRenderer.renderHeldItems` currently takes `ItemStackRenderState` parameters. For pre-1.21.2, it needs to accept `ItemStack` instead. This requires a version-conditional overload or refactored interface.

**Design decision needed**: Either:
- (A) Create a `VrmHeldItemRenderer` abstraction with version-conditional implementations
- (B) Use Stonecutter conditionals directly in `VrmRenderer.renderHeldItems`

Recommend (B) for simplicity — add a second version of `renderHeldItems` that takes `ItemStack`.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/net/narazaka/vrmmod/render/VrmFirstPersonRenderer.kt
git commit -m "feat: version-conditional held item rendering for pre-1.21.2"
```

### Task 12: Update Mod Metadata for 1.21.1

**Files:**
- Modify: `src/main/resources/fabric.mod.json`
- Modify: `src/main/resources/META-INF/neoforge.mods.toml`

- [ ] **Step 1: Use version variables in metadata**

For `fabric.mod.json`, update the `minecraft` dependency to use Stonecraft variables:
```json
"minecraft": "~${minecraftVersion}"
```

For `neoforge.mods.toml`, the version ranges need Stonecutter conditionals or Stonecraft variable substitution. Check Stonecraft documentation for how `${minecraftVersion}` is substituted into TOML files.

- [ ] **Step 2: Verify Architectury version range adapts per MC version**

The `architectury` dependency version range should match the Architectury API version for each MC version (v15 for 1.21.4, v13 for 1.21.1).

- [ ] **Step 3: Commit**

### Task 13: Build and Test 1.21.1

- [ ] **Step 1: Build 1.21.1 Fabric**

```bash
./gradlew "1.21.1-fabric:build"
```

- [ ] **Step 2: Build 1.21.1 NeoForge**

```bash
./gradlew "1.21.1-neoforge:build"
```

- [ ] **Step 3: Fix compilation errors**

Expect compilation errors from MC API differences not yet caught. Common issues:
- Method signatures changed between 1.21.1 and 1.21.4
- Missing imports for version-specific classes
- Field names or types that changed

Fix each error with appropriate Stonecutter conditionals.

- [ ] **Step 4: Run chiseled build for all variants**

```bash
./gradlew chiseledBuild
```

- [ ] **Step 5: Smoke test 1.21.1 Fabric client**

```bash
./gradlew "1.21.1-fabric:runClient"
```

- [ ] **Step 6: Smoke test 1.21.4 Fabric client (regression check)**

```bash
./gradlew "1.21.4-fabric:runClient"
```

- [ ] **Step 7: Commit all fixes**

```bash
git add -A
git commit -m "feat: MC 1.21.1 support (Fabric + NeoForge)"
```

---

## Phase 3: Add MC 1.20.1 Support (Deferred)

### Goal
Add 1.20.1 as a third target version. This is the most complex phase due to:
1. **Forge** (not NeoForge) as the mod loader
2. **Networking API completely different** (no CustomPacketPayload/StreamCodec)
3. **Java 17** target
4. **Architectury API v9** (different NetworkManager API)

### Task 14: Add 1.20.1 Version Configuration

**Files:**
- Modify: `settings.gradle.kts`
- Create: `versions/dependencies/1.20.1.properties`

- [ ] **Step 1: Add to settings.gradle.kts**

```kotlin
mc("1.20.1", "fabric", "forge")  // Forge, NOT NeoForge
mc("1.21.1", "fabric", "neoforge")
mc("1.21.4", "fabric", "neoforge")
```

- [ ] **Step 2: Create 1.20.1 dependencies**

```properties
minecraft_version=1.20.1
architectury_api_version=9.2.14
fabric_loader_version=0.16.10
fabric_version=0.92.2+1.20.1
fabric_language_kotlin_version=1.12.3+kotlin.2.0.21
forge_version=1.20.1-47.3.0
kotlin_for_forge_version=4.11.0
mod_menu_version=7.2.2
```

### Task 15: Add Forge Entry Point

**Files:**
- Create: `src/main/kotlin/net/narazaka/vrmmod/platform/VrmModForge.kt`

- [ ] **Step 1: Create Forge entry point**

```kotlin
//? if forge {
package net.narazaka.vrmmod.platform

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.client.VrmModClient
import net.narazaka.vrmmod.render.VrmFirstPersonRenderer
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.loading.FMLEnvironment

@Mod(VrmMod.MOD_ID)
class VrmModForge {
    init {
        VrmMod.init()
        if (FMLEnvironment.dist.isClient) {
            VrmModClient.init()

            MinecraftForge.EVENT_BUS.addListener<RenderLevelStageEvent> { event ->
                if (event.stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                    val mc = net.minecraft.client.Minecraft.getInstance()
                    val bufferSource = mc.renderBuffers().bufferSource()
                    VrmFirstPersonRenderer.renderFirstPerson(
                        event.poseStack,
                        bufferSource,
                        event.partialTick,  // Note: 1.20.1 Forge uses float directly
                    )
                    bufferSource.endBatch()
                }
            }
        }
    }
}
//?}
```

### Task 16: Rewrite Networking for 1.20.1

**Files:**
- Modify: `src/main/kotlin/net/narazaka/vrmmod/network/ModelAnnouncePayload.kt`
- Modify: `src/main/kotlin/net/narazaka/vrmmod/network/PlayerModelPayload.kt`
- Modify: `src/main/kotlin/net/narazaka/vrmmod/network/VrmModNetwork.kt`

This is the largest single task. In 1.20.1:
- No `CustomPacketPayload` interface
- No `StreamCodec`
- No `RegistryFriendlyByteBuf` (use `FriendlyByteBuf`)
- Architectury v9 uses `NetworkManager.registerReceiver(Side, ResourceLocation, handler)` pattern

- [ ] **Step 1: Version-conditional payload classes**

The payload classes need to be completely restructured for 1.20.1. The 1.21+ version implements `CustomPacketPayload`; the 1.20.1 version is a plain data class with manual serialization to `FriendlyByteBuf`.

- [ ] **Step 2: Version-conditional network registration**

```kotlin
//? if HAS_CUSTOM_PAYLOAD {
NetworkManager.registerReceiver(NetworkManager.c2s(), ModelAnnouncePayload.TYPE, ModelAnnouncePayload.CODEC) { ... }
//?} else {
/*NetworkManager.registerReceiver(NetworkManager.Side.C2S, ModelAnnouncePayload.PACKET_ID) { buf, context -> ... }*/
//?}
```

### Task 17: Handle Java 17 Target

Stonecraft automatically sets Java 17 for MC < 1.20.6. Verify that no Java 21 language features are used in common code:
- Sealed interfaces (OK in Java 17)
- Pattern matching in `instanceof` (OK in Java 17)
- Records (OK in Java 17)
- Text blocks (OK in Java 17)
- No Java 21-only features used

### Task 18: Add Forge Mod Metadata

**Files:**
- Create: `src/main/resources/META-INF/mods.toml`

Forge 1.20.1 uses `mods.toml` (not `neoforge.mods.toml`). Stonecraft auto-excludes wrong metadata files per loader.

### Task 19: Build and Test 1.20.1

Same pattern as Task 13 but for 1.20.1 Fabric + Forge variants.

---

## Phase 4 (Optional): CI/CD Multi-Version Publishing

### Task 20: GitHub Actions Workflow

**Files:**
- Create: `.github/workflows/publish.yml`

- [ ] **Step 1: Create multi-version publish workflow**

```yaml
name: Publish
on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Build all versions
        run: ./gradlew chiseledBuildAndCollect

      - name: Upload artifacts
        uses: actions/upload-artifact@v4
        with:
          name: mod-jars
          path: build/libs/*.jar

  publish:
    needs: build
    runs-on: ubuntu-latest
    if: startsWith(github.ref, 'refs/tags/v')
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21

      - name: Publish to Modrinth + CurseForge
        run: ./gradlew chiseledPublishMods
        env:
          MODRINTH_TOKEN: ${{ secrets.MODRINTH_TOKEN }}
          MODRINTH_ID: ${{ secrets.MODRINTH_ID }}
          CURSEFORGE_TOKEN: ${{ secrets.CURSEFORGE_TOKEN }}
          CURSEFORGE_ID: ${{ secrets.CURSEFORGE_ID }}
          DO_PUBLISH: false  # set to false for dry run
```

---

## Risk Assessment

| Risk | Impact | Mitigation |
|------|--------|------------|
| MC API differences not caught by constants | Build failure on specific version | Iterative: build each version, fix errors one by one |
| Stonecraft plugin incompatibility with custom tasks | Cannot build | **Task 3 で一旦停止しユーザーに判断を仰ぐ。** Stonecraft 続行 or raw Stonecutter 切替をユーザーが決定 |
| 1.20.1 networking rewrite introduces bugs | Multiplayer broken on 1.20.1 | Thorough testing with 2-client LAN setup |
| Access widener fields renamed between versions | Runtime crash | Verify each field exists via decompiled source for each version |
| Mixin target descriptor mismatch | Mixin fails to apply | Use `@Mixin(value=..., remap=true)` and verify descriptors per version |

## Implementation Order Recommendation

1. **Phase 1 first** — Get Stonecutter working with 1.21.4 only. This is the foundation.
2. **Phase 2 next** — 1.21.1 is the closest version and validates the multi-version pattern.
3. **Phase 3 evaluate** — After Phase 2, assess whether 1.20.1 user demand justifies the networking rewrite.
4. **Phase 4 in parallel** — CI/CD can be set up alongside Phase 2.
