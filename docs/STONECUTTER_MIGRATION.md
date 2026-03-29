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

## フラットレイアウトの構造

```
src/main/
├── kotlin/net/narazaka/vrmmod/
│   ├── platform/          # ローダー別エントリポイント（Stonecutter条件分岐）
│   │   ├── VrmModFabric.kt        # //? if fabric { ... //?}
│   │   ├── VrmModNeoForge.kt      # //? if neoforge { ... //?} (VCS版ではinactive)
│   │   └── VrmModMenuIntegration.kt
│   ├── animation/         # 共通コード（条件分岐なし）
│   ├── client/
│   ├── network/
│   ├── render/
│   ├── vrm/
│   └── vroidhub/
├── java/net/narazaka/vrmmod/mixin/  # 統一Mixinパッケージ
└── resources/
    ├── fabric.mod.json              # Stonecraft が NeoForge ビルド時に自動除外
    ├── META-INF/neoforge.mods.toml  # Stonecraft が Fabric ビルド時に自動除外
    ├── vrmmod.accesswidener
    └── vrmmod.mixins.json           # パッケージ: net.narazaka.vrmmod.mixin
versions/
└── dependencies/
    └── 1.21.4.properties            # MC/loader/API バージョン
```

## Gradle 9 アップグレードが必須だった理由

- Stonecutter **全 0.8.x バージョン**（0.8.0〜0.8.3）が Gradle 9 を要求
- Stonecutter 0.7.11 は Gradle 8 対応だが、Stonecraft 1.9.x は Stonecutter 0.8+ を前提
- Gradle 8 のまま使う選択肢は事実上ない

### Gradle 9 に伴う変更

- **Shadow プラグイン**: `com.github.johnrengelman.shadow:8.1.1` → `com.gradleup.shadow:8.3.10`（8.3.10 は Gradle 9 バックポート版。Shadow 9.x も選択可能）
- **JUnit**: `testRuntimeOnly("org.junit.platform:junit-platform-launcher")` の追加が必要
- **Architectury Loom**: 1.10 → 1.13+（Loom も Gradle 9 対応版が必要、Stonecraft が自動選択）

## Stonecutter コメント構文の注意点

### VCS バージョンと active/inactive 状態

Stonecutter はソースコードを直接書き換えるプリプロセッサ。`stonecutter.gradle.kts` の `stonecutter active "1.21.4-fabric"` が「今ソースに反映されているバージョン」を示す。

**VCS version = `1.21.4-fabric` の場合:**
- `//? if fabric {` ブロック → コードがそのまま書かれる（active）
- `//? if neoforge {` ブロック → コードが `/* */` で囲まれる（inactive）

```kotlin
// ✅ 正しい（fabric がactive、neoforge がinactive）
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
```

**間違えやすいポイント:** neoforge ブロックの中身をコメントアウトし忘れると、fabric ビルドで NeoForge の import がコンパイルエラーになる。

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

## Stonecraft のプロパティ名

`versions/dependencies/{version}.properties` で Stonecraft が要求するプロパティ名:

| プロパティ | 用途 | 必須 |
|-----------|------|------|
| `minecraft_version` | MC バージョン | Yes |
| `loader_version` | Fabric Loader バージョン | Yes |
| `fabric_version` | Fabric API バージョン | Fabric |
| `neoforge_version` | NeoForge バージョン | NeoForge |

カスタムプロパティ（`architectury_api_version` 等）は `mod.prop("key")` でアクセス。

## Stonecraft の変数置換

`fabric.mod.json` や `neoforge.mods.toml` 内で使える変数:
- `${id}` → `mod.id`（gradle.properties の `mod.id`）
- `${version}` → mod バージョン
- `${name}` → `mod.name`
- `${description}` → `mod.description`
- `${minecraftVersion}` → MC バージョン

## NeoForge dev 環境の罠（重要）

### 1. Kotlin クラスの ClassNotFoundException

**症状:** `ClassNotFoundException: net.narazaka.vrmmod.client.VrmModClient`（Mixin 適用時）

**原因:** NeoForge は `ModuleClassLoader` を使用。dev 環境では mod のソースセットがどのモジュールに属するか明示的に登録が必要。Stonecraft はこれを自動設定しない。

**解決:**
```kotlin
if (mod.isNeoforge) {
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

**原因:** `implementation()` で追加した JglTF が NeoForge のモジュールクラスローダーから見えない。`localRuntime` や `include()` では解決しない。

**解決:** `forgeRuntimeLibrary` configuration を使用:
```kotlin
if (mod.isNeoforge) {
    "forgeRuntimeLibrary"("de.javagl:jgltf-model:2.0.4") {
        exclude(group = "com.fasterxml.jackson.core")
        exclude(group = "com.fasterxml.jackson")
    }
}
```

`forgeRuntimeLibrary` は NeoForge/Loom がモジュールレイヤーにライブラリを追加するための configuration で、旧構成の `developmentNeoForge` に最も近い。

**試して効果がなかった方法:**
- `localRuntime()` — NeoForge のモジュールローダーが参照しない
- `include()` (Jar-in-Jar) — dev 環境では効果なし（プロダクション JAR 向け）

### 3. Jackson の除外（NeoForge）

NeoForge は Jackson を自身で提供する。JglTF の transitive dep として Jackson を含めるとモジュール衝突が起きる。`exclude(group = "com.fasterxml.jackson.core")` が必須。

## run ディレクトリの分離

デフォルトでは Fabric/NeoForge 両方が `run/` を共有する。設定ファイルやワールドが混在するため分離が必要:

```kotlin
modSettings {
    runDirectory = rootProject.layout.projectDirectory.dir("run/$loader")
}
```

結果: `run/fabric/`, `run/neoforge/`

## ビルドコマンド

```bash
# 個別ビルド
./gradlew "1.21.4-fabric:build"
./gradlew "1.21.4-neoforge:build"

# 全バージョン一括ビルド
./gradlew chiseledBuild

# 開発実行
./gradlew "1.21.4-fabric:runClient"
./gradlew "1.21.4-neoforge:runClient"

# テスト
./gradlew "1.21.4-fabric:test"
```

成果物: `versions/1.21.4-{loader}/build/libs/`
