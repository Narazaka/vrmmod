# Stonecutter マルチバージョン移行 知見集

## 概要

Architectury マルチモジュール（`common`/`fabric`/`neoforge`）から Stonecutter + Stonecraft フラットレイアウト（単一 `src/`）への移行で得た知見。

## 採用したツールチェーン

| ツール | バージョン | 備考 |
|--------|------------|------|
| Gradle | **9.3.1** | Stonecutter 0.8.3 が Gradle 9 を要求 |
| Stonecutter | **0.8.3** | コメントベースプリプロセッサ |
| Stonecraft | **1.9.x** | Stonecutter + Architectury Loom ラッパー |
| Architectury Loom | **1.13.467** | Stonecraft が自動選択（Gradle 9 対応版） |
| Shadow | **com.gradleup.shadow:8.3.10** | 旧 `com.github.johnrengelman.shadow` から移行 |

## 対応バリアント

| MC | Fabric | NeoForge | Forge | Architectury API | Java |
|----|--------|----------|-------|-----------------|------|
| 1.21.4 | OK | OK | - | 15.0.3 | 21 |
| 1.21.1 | OK | OK | - | 13.0.8 | 21 |
| 1.20.1 | OK | - | OK | 9.2.14 | 17 |

## フラットレイアウトの構造

```
src/main/
├── kotlin/net/narazaka/vrmmod/
│   ├── platform/          # ローダー別エントリポイント（Stonecutter条件分岐）
│   │   ├── VrmModFabric.kt            # //? if fabric { ... //?}
│   │   ├── VrmModNeoForge.kt          # //? if neoforge { ... //?} (VCS版ではinactive)
│   │   ├── VrmModForge.kt             # //? if forge { ... //?} (VCS版ではinactive)
│   │   └── VrmModMenuIntegration.kt   # //? if fabric { ... //?}
│   ├── animation/         # 共通コード（条件分岐なし）
│   ├── client/            # GUI（ほぼ共通、一部条件分岐）
│   ├── network/           # ネットワーク（HAS_CUSTOM_PAYLOAD で大きく分岐）
│   ├── render/            # レンダリング（複数の条件分岐あり）
│   ├── vrm/               # VRM パーサー（共通）
│   └── vroidhub/          # VRoid Hub 連携（共通）
├── java/net/narazaka/vrmmod/mixin/  # 統一Mixinパッケージ
└── resources/
    ├── fabric.mod.json              # Stonecraft が NeoForge/Forge ビルド時に自動除外
    ├── META-INF/neoforge.mods.toml  # Stonecraft が Fabric/Forge ビルド時に自動除外
    ├── META-INF/mods.toml           # Forge 用。Stonecraft が Fabric/NeoForge ビルド時に自動除外
    ├── vrmmod.accesswidener
    └── vrmmod.mixins.json           # パッケージ: net.narazaka.vrmmod.mixin
versions/
└── dependencies/
    ├── 1.20.1.properties
    ├── 1.21.1.properties
    └── 1.21.4.properties
```

---

## Gradle 9 アップグレードが必須だった理由

- Stonecutter **全 0.8.x バージョン**（0.8.0〜0.8.3）が Gradle 9 を要求
- Stonecutter 0.7.11 は Gradle 8 対応だが、Stonecraft 1.9.x は Stonecutter 0.8+ を前提
- Gradle 8 のまま使う選択肢は事実上ない

### Gradle 9 に伴う変更

- **Shadow プラグイン**: `com.github.johnrengelman.shadow:8.1.1` → `com.gradleup.shadow:8.3.10`（8.3.10 は Gradle 9 バックポート版。Shadow 9.x も選択可能）
- **JUnit**: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` の追加が必要
- **Architectury Loom**: 1.10 → 1.13+（Loom も Gradle 9 対応版が必要、Stonecraft が自動選択）

### Java 17 ツールチェーン（1.20.1 ビルドに必要）

Stonecraft は MC 1.20.1 を Java 17 でビルドする。ビルドマシンに Java 17 がインストールされている必要がある。

Gradle のツールチェーン自動検出が Java 17 を見つけられない場合、`~/.gradle/gradle.properties` にパスを追加:
```properties
org.gradle.java.installations.paths=C:/Program Files/Microsoft/jdk-17.0.18.8-hotspot,C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot
```

**注意:** `org.gradle.toolchains.foojay-resolver-convention` プラグインは Gradle 9.3.1 と `IBM_SEMERU` フィールドの互換性問題があり使えなかった（`NoSuchFieldError: JvmVendorSpec.IBM_SEMERU`）。

---

## Stonecutter コメント構文の注意点

### VCS バージョンと active/inactive 状態

Stonecutter はソースコードを直接書き換えるプリプロセッサ。`stonecutter.gradle.kts` の `stonecutter active "1.21.4-fabric"` が「今ソースに反映されているバージョン」を示す。

**VCS version = `1.21.4-fabric` の場合:**
- `//? if fabric {` ブロック → コードがそのまま書かれる（active）
- `//? if neoforge {` ブロック → コードが `/* */` で囲まれる（inactive）
- `//? if forge {` ブロック → コードが `/* */` で囲まれる（inactive）

```kotlin
// ✅ 正しい（fabric がactive、neoforge/forge がinactive）
//? if fabric {
package net.narazaka.vrmmod.platform
import net.fabricmc.api.ClientModInitializer
// ... fabric code ...
//?}

//? if neoforge {
/*package net.narazaka.vrmmod.platform
import net.neoforged.fml.common.Mod
// ... neoforge code ...
*///?}

//? if forge {
/*package net.narazaka.vrmmod.platform
import net.minecraftforge.fml.common.Mod
// ... forge code ...
*///?}
```

**間違えやすいポイント:** inactive ブロックの中身を `/* */` で囲み忘れると、active ビルドでコンパイルエラーになる。

### Stonecutter API: `vers()` vs `version()`

Stonecutter 0.8 の settings.gradle.kts では `version()` を使う（`vers()` は古い API で存在しない）:

```kotlin
stonecutter {
    shared {
        fun mc(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version)  // vers() ではない
        }
    }
}
```

### Stonecutter 定数の定義

`build.gradle.kts` 内の `stonecutter {}` ブロックで `constants["NAME"] = eval(...)` を使う:
```kotlin
stonecutter {
    val mcVersion = stonecutter.current.version
    constants["HAS_RENDER_STATE"] = eval(mcVersion, ">=1.21.2")
}
```

**注意:** `consts()` や `consts(mapOf(...))` は存在しない。`constants["key"] = value` が正しい API。

---

## Stonecraft のプロパティ名

`versions/dependencies/{version}.properties` で Stonecraft が要求するプロパティ名:

| プロパティ | 用途 | 必須 |
|-----------|------|------|
| `minecraft_version` | MC バージョン | Yes |
| `loader_version` | Fabric Loader バージョン | Yes |
| `fabric_version` | Fabric API バージョン | Fabric |
| `neoforge_version` | NeoForge バージョン | NeoForge |
| `forge_version` | Forge バージョン（`1.20.1-47.3.0` 形式） | Forge |

カスタムプロパティ（`architectury_api_version` 等）は `mod.prop("key")` でアクセス。

## Stonecraft の変数置換

`fabric.mod.json`、`neoforge.mods.toml`、`mods.toml` 内で使える変数:
- `${id}` → `mod.id`（gradle.properties の `mod.id`）
- `${version}` → mod バージョン
- `${name}` → `mod.name`
- `${description}` → `mod.description`
- `${minecraftVersion}` → MC バージョン
- `${neoforgeVersion}` → NeoForge バージョン
- カスタム変数は `modSettings { variableReplacements.set(mapOf(...)) }` で追加可能

---

## NeoForge / Forge dev 環境の罠（重要）

### 1. Kotlin クラスの ClassNotFoundException

**症状:** `ClassNotFoundException: net.narazaka.vrmmod.client.VrmModClient`（Mixin 適用時）

**原因:** NeoForge/Forge は `ModuleClassLoader` を使用。dev 環境では mod のソースセットがどのモジュールに属するか明示的に登録が必要。Stonecraft はこれを自動設定しない。

**解決:**
```kotlin
if (mod.isNeoforge || mod.isForge) {
    loom {
        mods {
            register(mod.id) {
                sourceSet(sourceSets.getByName("main"))
            }
        }
    }
}
```

### 2. 外部ライブラリ（JglTF）の NoClassDefFoundError

**症状:** `NoClassDefFoundError: de/javagl/jgltf/model/io/GltfModelReader`

**原因:** `implementation()` で追加した JglTF が NeoForge/Forge のモジュールクラスローダーから見えない。`localRuntime` や `include()` では解決しない。

**解決:** `forgeRuntimeLibrary` configuration を使用:
```kotlin
if (mod.isNeoforge || mod.isForge) {
    "forgeRuntimeLibrary"("de.javagl:jgltf-model:2.0.4")
}
```

`forgeRuntimeLibrary` は NeoForge/Forge + Loom がモジュールレイヤーにライブラリを追加するための configuration で、旧構成の `developmentNeoForge` に最も近い。

**試して効果がなかった方法:**
- `localRuntime()` — NeoForge のモジュールローダーが参照しない
- `include()` (Jar-in-Jar) — dev 環境では効果なし（プロダクション JAR 向け）

### 3. Jackson 除外の条件（Shadow JAR + forgeRuntimeLibrary）

**Shadow JAR での Jackson 除外は NeoForge 1.21.4 のみ:**
```kotlin
tasks.shadowJar {
    // Jackson は NeoForge 1.21.4 だけが自前提供。他バージョンでは JglTF が必要とする
    if (mod.isNeoforge && stonecutter.eval(stonecutter.current.version, ">=1.21.4")) {
        exclude("com/fasterxml/**")
    }
}
```

**forgeRuntimeLibrary では Jackson を除外しない:**
NeoForge 1.21.1 は JglTF が必要とする Jackson クラス（`PropertyNamingStrategy` 等）を全て提供していない。除外すると `ClassNotFoundException` が発生する。

### 4. Forge 1.20.1 で Mixin が適用されない

**症状:** mod は起動するが、VRM 描画が行われず Mixin が適用されていない

**原因:** `mods.toml` に `[[mixins]]` セクションがあっても、Forge の dev 環境では Architectury Loom に明示的に mixin config を登録する必要がある。

**解決:**
```kotlin
if (mod.isForge) {
    loom {
        forge {
            mixinConfigs("vrmmod.mixins.json")
        }
    }
}
```

### 5. Forge の shadowJar と generatePackMCMetaJson の依存関係

**症状:** `Task ':1.20.1-forge:shadowJar' uses this output of task ':1.20.1-forge:generatePackMCMetaJson' without declaring an explicit dependency`

**原因:** Stonecraft が Forge 向けに `pack.mcmeta` を自動生成するが、shadow JAR タスクがそれに依存していない。

**解決:**
```kotlin
tasks.shadowJar {
    dependsOn(tasks.named("processResources"))
    tasks.findByName("generatePackMCMetaJson")?.let { dependsOn(it) }
}
```

---

## run ディレクトリの分離

バージョン＋ローダーごとに分離（設定ファイルやワールドの混在を防止）:

```kotlin
modSettings {
    runDirectory = rootProject.layout.projectDirectory.dir("run/${stonecutter.current.version}-$loader")
}
```

結果: `run/1.21.4-fabric/`, `run/1.21.1-neoforge/`, `run/1.20.1-forge/` 等

---

## Stonecraft の `--username=developer` 問題

Stonecraft は `Loom.kt` で全クライアント runConfig に `programArgs("--username=developer")` をハードコードしている。これにより認証済みアカウント名が使えず、マルチプレイテストでも全員 `developer` になる。

**解決:** `afterEvaluate` で上書き + マルチテスト用 client2 を追加:
```kotlin
afterEvaluate {
    val loom = extensions.getByType<net.fabricmc.loom.api.LoomGradleExtensionAPI>()
    loom.runConfigs.matching { it.environment == "client" }.configureEach {
        programArgs.removeIf { it.startsWith("--username") }
    }
    loom.runConfigs.create("client2") {
        client()
        programArgs("--username=Player2")
    }
}
```

---

## Mixin JSON の Java 互換レベル

1.21+ は `JAVA_21`、1.20.1 は `JAVA_17`。JSON ファイルは Stonecutter コメントが使えないため、`processResources` で置換:

```kotlin
tasks.processResources {
    filesMatching("vrmmod.mixins.json") {
        val javaVersion = if (stonecutter.eval(stonecutter.current.version, ">=1.20.6")) "JAVA_21" else "JAVA_17"
        filter { it.replace("JAVA_21", javaVersion) }
    }
}
```

---

## Stonecutter 定数一覧

```kotlin
stonecutter {
    val mcVersion = stonecutter.current.version
    constants["HAS_RENDER_STATE"] = eval(mcVersion, ">=1.21.2")        // EntityRenderState
    constants["HAS_ITEM_RENDER_STATE"] = eval(mcVersion, ">=1.21.2")   // ItemStackRenderState, ItemModelResolver
    constants["HAS_APPROXIMATE_NEAREST"] = eval(mcVersion, ">=1.21.4") // Direction.getApproximateNearest, EntitySelector.CAN_BE_PICKED
    constants["HAS_INTERACTION_RANGE"] = eval(mcVersion, ">=1.20.5")   // player.blockInteractionRange()
    constants["HAS_CUSTOM_PAYLOAD"] = eval(mcVersion, ">=1.20.5")      // CustomPacketPayload + StreamCodec
    constants["HAS_NEW_VERTEX_API"] = eval(mcVersion, ">=1.21")        // addVertex/setColor vs vertex/color/endVertex
    constants["HAS_RESOURCE_LOCATION_FACTORY"] = eval(mcVersion, ">=1.21") // fromNamespaceAndPath vs constructor
    constants["HAS_TICK_COUNTER"] = eval(mcVersion, ">=1.21")          // tickCounter vs tickDelta (Fabric)
    constants["HAS_BLOCKPOS_CONTAINING"] = eval(mcVersion, ">=1.20.2") // BlockPos.containing vs constructor
}
```

---

## 条件分岐が必要だったファイル一覧

### Phase 2: 1.21.1 対応（EntityRenderState 分岐が主）

| ファイル | 分岐内容 | 定数 |
|----------|----------|------|
| `PlayerRendererMixin.java` | `extractRenderState` vs `render` | `HAS_RENDER_STATE` |
| `LivingEntityRendererMixin.java` | RenderState vs Entity ベースの render フック | `HAS_RENDER_STATE` |
| `GameRendererMixin.java` | `EntitySelector.CAN_BE_PICKED` vs inline predicate | `HAS_APPROXIMATE_NEAREST` |
| `MixinHelper.kt` | `buildPoseContext(RenderState)` vs `buildPoseContextFromEntity(Entity)` | `HAS_RENDER_STATE` |
| `MixinHelper.kt` | `Direction.getApproximateNearest` vs `getNearest` | `HAS_APPROXIMATE_NEAREST` |
| `VrmRenderType.kt` | `TriState` vs `boolean` in TextureStateShard | `HAS_ITEM_RENDER_STATE` |
| `VrmRenderer.kt` | `ItemStackRenderState` vs `ItemStack` + `ItemRenderer.renderStatic()` | `HAS_ITEM_RENDER_STATE` |
| `VrmFirstPersonRenderer.kt` | `ItemModelResolver` vs player entity ベースのアイテム描画 | `HAS_ITEM_RENDER_STATE` |

### Phase 3: 1.20.1 対応（ネットワーク + VertexConsumer + Forge）

| ファイル | 分岐内容 | 定数 |
|----------|----------|------|
| `ModelAnnouncePayload.kt` | `CustomPacketPayload` vs プレーンデータクラス | `HAS_CUSTOM_PAYLOAD` |
| `PlayerModelPayload.kt` | 同上 | `HAS_CUSTOM_PAYLOAD` |
| `VrmModNetwork.kt` | `registerReceiver(c2s(), TYPE, CODEC)` vs `registerReceiver(Side.C2S, PACKET_ID)` | `HAS_CUSTOM_PAYLOAD` |
| `VrmModServer.kt` | `sendToPlayer(player, payload)` vs `sendToPlayer(player, PACKET_ID, buf)` | `HAS_CUSTOM_PAYLOAD` |
| `VrmModClient.kt` | `sendToServer(payload)` vs `sendToServer(PACKET_ID, buf)` | `HAS_CUSTOM_PAYLOAD` |
| `VrmRenderer.kt` | `addVertex/setColor/setNormal` vs `vertex/color/normal/endVertex` | `HAS_NEW_VERTEX_API` |
| `VrmRenderer.kt` | `poseStack.mulPose(Matrix4f)` vs 手動行列乗算 | `HAS_NEW_VERTEX_API` |
| `VrmTextureManager.kt` | `ResourceLocation.fromNamespaceAndPath` vs コンストラクタ | `HAS_RESOURCE_LOCATION_FACTORY` |
| `VrmRenderer.kt` | `ResourceLocation.withDefaultNamespace` vs コンストラクタ | `HAS_RESOURCE_LOCATION_FACTORY` |
| `VrmFirstPersonRenderer.kt` | `BlockPos.containing` vs `BlockPos(Mth.floor(...))` | `HAS_BLOCKPOS_CONTAINING` |
| `MixinHelper.kt` | 同上 | `HAS_BLOCKPOS_CONTAINING` |
| `VrmModFabric.kt` | `context.tickCounter().getGameTimeDeltaPartialTick(false)` vs `context.tickDelta()` | `HAS_TICK_COUNTER` |
| `GameRendererMixin.java` | `blockInteractionRange()` vs `gameMode.getPickRange()` | `HAS_INTERACTION_RANGE` |
| `VrmModScreen.kt` | `mouseScrolled(4 params)` vs `mouseScrolled(3 params)` | `HAS_NEW_VERTEX_API` |
| `VrmSettingsList.kt` | コンストラクタ引数5個 vs 6個 | `HAS_CUSTOM_PAYLOAD` |
| `VrmSettingsList.kt` | `getScrollbarPosition()` オーバーライド（1.20.x のみ） | `!HAS_CUSTOM_PAYLOAD` |
| `VrmModForge.kt` | Forge エントリポイント（全体が `//? if forge { }` ） | `forge` |

---

## Forge 1.20.1 固有の知見

### エントリポイント

NeoForge (`net.neoforged.*`) → Forge (`net.minecraftforge.*`) のパッケージ差異:

| 用途 | NeoForge (1.21+) | Forge (1.20.1) |
|------|-------------------|----------------|
| Mod アノテーション | `net.neoforged.fml.common.Mod` | `net.minecraftforge.fml.common.Mod` |
| イベントバス | `NeoForge.EVENT_BUS` | `MinecraftForge.EVENT_BUS` |
| 設定画面 | `IConfigScreenFactory` | `ConfigScreenHandler.ConfigScreenFactory` |
| 環境判定 | `FMLEnvironment` (neoforged) | `FMLEnvironment` (minecraftforge) |
| partialTick | `event.partialTick.getGameTimeDeltaPartialTick(false)` | `event.partialTick` (直接 float) |

### mods.toml の差異

| 項目 | NeoForge | Forge |
|------|----------|-------|
| ファイル名 | `neoforge.mods.toml` | `mods.toml` |
| ローダーバージョン | `"[5,)"` | `"[4,)"` |
| 依存必須指定 | `type = "required"` | `mandatory = true` |
| ローダー依存 | `modId = "neoforge"` | `modId = "forge"` |

### KotlinForForge

| MC | KotlinForForge | アーティファクト |
|----|----------------|----------------|
| 1.21+ | 5.x | `thedarkcolour:kotlinforforge-neoforge` |
| 1.20.1 | 4.x | `thedarkcolour:kotlinforforge` |

---

## ネットワーク API の差異（Architectury v13+ vs v9）

### 1.21+ (Architectury v13+): CustomPacketPayload パターン
```kotlin
data class Payload(...) : CustomPacketPayload {
    companion object {
        val TYPE = CustomPacketPayload.Type<Payload>(ResourceLocation.fromNamespaceAndPath(...))
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, Payload> = object : StreamCodec<...> {
            override fun decode(buf: RegistryFriendlyByteBuf) = ...
            override fun encode(buf: RegistryFriendlyByteBuf, payload: Payload) = ...
        }
    }
}
NetworkManager.registerReceiver(NetworkManager.c2s(), TYPE, CODEC) { payload, ctx -> ... }
NetworkManager.sendToServer(payload)
NetworkManager.sendToPlayer(player, payload)
```

### 1.20.1 (Architectury v9): ResourceLocation + FriendlyByteBuf パターン
```kotlin
data class Payload(...) {
    companion object {
        val PACKET_ID = ResourceLocation("vrmmod", "packet_name")
        fun decode(buf: FriendlyByteBuf) = ...
        fun encode(buf: FriendlyByteBuf, payload: Payload) = ...
    }
}
NetworkManager.registerReceiver(NetworkManager.Side.C2S, PACKET_ID) { buf, ctx ->
    val payload = Payload.decode(buf)
    ctx.queue { ... }
}
// 送信: buf を作って encode してから渡す
val buf = FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())
Payload.encode(buf, payload)
NetworkManager.sendToServer(PACKET_ID, buf)
NetworkManager.sendToPlayer(player, PACKET_ID, buf)
```

---

## VertexConsumer API の差異（1.21+ vs 1.20.x）

### 1.21+ (新 API)
```kotlin
vertexConsumer
    .addVertex(pose, px, py, pz)          // PoseStack.Pose を渡す
    .setColor(r, g, b, 255)
    .setUv(u, vCoord)
    .setOverlay(OverlayTexture.NO_OVERLAY)
    .setLight(packedLight)
    .setNormal(pose, nx, ny, nz)          // PoseStack.Pose を渡す
// endVertex() は不要（自動）
```

### 1.20.x (旧 API)
```kotlin
vertexConsumer
    .vertex(pose.pose(), px, py, pz)      // Matrix4f を渡す
    .color(r, g, b, 255)
    .uv(u, vCoord)
    .overlayCoords(OverlayTexture.NO_OVERLAY)
    .uv2(packedLight)
    .normal(pose.normal(), nx, ny, nz)    // Matrix3f を渡す
    .endVertex()                           // 必須
```

---

## GUI API の差異

**当初の予想に反して、GUI API はほぼ共通。** `GuiGraphics` は 1.20 で導入済み。

変更が必要だった箇所:

| API | 1.21+ | 1.20.1 |
|-----|-------|--------|
| `Screen.mouseScrolled` | 4引数 `(mouseX, mouseY, scrollX, scrollY)` | 3引数 `(mouseX, mouseY, scrollDelta)` |
| `ContainerObjectSelectionList` コンストラクタ | 5引数 `(mc, w, h, top, itemH)` | 6引数 `(mc, w, h, top, bottom, itemH)` |
| `getScrollbarPosition()` | メソッド削除（自動） | オーバーライド必要 |

---

## ビルドコマンド

```bash
# 個別ビルド
./gradlew "1.21.4-fabric:build"
./gradlew "1.21.4-neoforge:build"
./gradlew "1.21.1-fabric:build"
./gradlew "1.21.1-neoforge:build"
./gradlew "1.20.1-fabric:build"
./gradlew "1.20.1-forge:build"

# 全バージョン一括ビルド
./gradlew chiseledBuild

# 開発実行
./gradlew "1.21.4-fabric:runClient"
./gradlew "1.21.4-neoforge:runClient"
./gradlew "1.21.1-fabric:runClient"
./gradlew "1.21.1-neoforge:runClient"
./gradlew "1.20.1-fabric:runClient"
./gradlew "1.20.1-forge:runClient"

# マルチプレイテスト用（Player2として起動）
./gradlew "1.21.4-fabric:runClient2"

# テスト
./gradlew "1.21.4-fabric:test"
```

成果物: `versions/{version}-{loader}/build/libs/`

---

## Modrinth / CurseForge 公開

Stonecraft は `me.modmuss50.mod-publish-plugin` を内蔵。`build.gradle.kts` に `publishMods` ブロックを追加し、CI から `chiseledPublishMods` で全バリアントを公開。

### 必要な環境変数（GitHub Secrets）

| 環境変数 | 用途 |
|----------|------|
| `MODRINTH_TOKEN` | Modrinth API トークン |
| `MODRINTH_ID` | Modrinth プロジェクト ID |
| `CURSEFORGE_TOKEN` | CurseForge API トークン |
| `CURSEFORGE_ID` | CurseForge プロジェクト ID |
| `CURSEFORGE_SLUG` | CurseForge プロジェクト slug |
| `DO_PUBLISH` | `false` で実際に公開。`true` または未設定でドライラン |

### `DO_PUBLISH` の罠（重要）

Stonecraft のソースコード（`Publishing.kt`）:
```kotlin
dryRun.set(project.providers.environmentVariable("DO_PUBLISH").getOrElse("true").toBoolean())
```

`DO_PUBLISH` の値が**そのまま** `dryRun` に渡される。つまり名前と動作が逆:

| `DO_PUBLISH` | `dryRun` | 結果 |
|---|---|---|
| 未設定 | `true` | ドライラン |
| `"true"` | `true` | ドライラン |
| `"false"` | `false` | **実際に公開** |

Stonecraft のドキュメント（stonecraft.meza.gg）は「`DO_PUBLISH=true` で公開」と記載しているが、ソースコードの動作はその逆。**ソースコードが正しい。**

### リリース手順

```bash
# 1. バージョンを設定してタグを打つ
release 0.1.0          # (release.cmd) gradle.properties書き換え + commit + tag

# 2. プッシュ → CI が自動で公開
git push origin master v0.1.0
```
