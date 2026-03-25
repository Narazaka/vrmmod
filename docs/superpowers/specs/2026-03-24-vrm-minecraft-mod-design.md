# VRM Minecraft Mod 設計書

## 概要

Minecraft Java Edition 向けに、VRM (Virtual Reality Model) ファイルをプレイヤーアバターとして利用できるmodを開発する。VRoid Hub連携により、マルチプレイでの安全なモデル共有も将来的に実現する。

## 背景と動機

- 既存の3Dモデル持ち込みmod（MMD mod等）はVRChat由来のモデルを扱うには煩雑
- VRMはglTF 2.0ベースの標準フォーマットで、ヒューマノイドアバターに最適化されている
- VRoid Hub連携により、著作権を尊重したモデル共有が可能になる

## 要件

### 機能要件

- VRM 1.0ファイルをプレイヤーアバターとして表示する
- VRM未設定のプレイヤーにはバニラスキンをフォールバック表示
- Fabric / NeoForge 両対応（Architectury API使用）
- 対応Minecraftバージョン: 1.21.x

### 機能の優先度

| 優先度 | 機能 |
|---|---|
| P0（必須） | メッシュ + テクスチャ + ヒューマノイドボーンアニメーション |
| P1（高） | SpringBone（揺れもの物理） |
| P1（高） | Expression（表情） |
| P2（中） | 擬似MToonシェーダー（バニラRenderTypeベース、Iris互換） |
| P2（中） | コンフィグUI |
| P2（中） | マルチプレイ同期（サーバーmod併用時のみ） |
| P3（将来） | VRoid Hub 連携（OAuth + API） |
| P3（将来） | Vivecraft IK（VR一人称対応） |
| P3（将来） | LookAt（視線追従） |
| P3（将来） | VRM 0.x 対応 |
| P4（オプション） | カスタムシェーダーによるフルMToon（Iris無効時のみ） |
| P4（オプション） | Iris Shader Pack 側でのMToon対応 |
| P4（オプション） | カスタムアニメーション定義ファイル |

### NPC/エンティティ拡張

プレイヤーアバターがメインだが、低コストでNPC/エンティティにも拡張できる設計にする。VrmModelとレンダリングをプレイヤーに密結合させず、汎用的な構造とする。

## 技術スタック

| 項目 | 選定 |
|---|---|
| 言語 | Kotlin（Java 21）。ただし Mixin クラスのみ Java で記述（Mixin はバイトコード変換に依存し、Kotlin コンパイラとの相性問題があるため） |
| Mod Loader | Fabric + NeoForge（Architectury API） |
| ビルド | Gradle + Architectury Plugin + Loom / NeoGradle |
| glTFパース | JglTF (`jgltf-model`) ※フェーズ1でVRM拡張データへのアクセス可否をPoC検証する |
| VRM拡張パース | Gson（Minecraft同梱）で自前実装 |
| レンダリング | Mixin によるPlayerRenderer差し替え + CPUスキニング |

## アーキテクチャ

### プロジェクト構成

```
vrmmod/
├── common/                    # プラットフォーム共通コード
│   └── src/main/kotlin/net/narazaka/vrmmod/
│       ├── vrm/               # VRMパーサー・モデル定義
│       ├── render/            # レンダリングエンジン
│       ├── animation/         # アニメーションシステム
│       ├── physics/           # SpringBone物理
│       ├── network/           # パケット定義（抽象）
│       └── client/            # クライアントロジック（設定UI等）
├── fabric/                    # Fabric固有コード（Mixin、エントリポイント）
│   └── src/main/kotlin/
│   └── src/main/resources/
│       └── vrmmod.mixins.json
├── neoforge/                  # NeoForge固有コード
│   └── src/main/kotlin/
├── build.gradle.kts
└── settings.gradle.kts
```

### コンポーネント図

```
┌─────────────────────────────────────────────┐
│                  vrmmod                      │
│                                              │
│  ┌──────────┐    ┌────────────────────────┐  │
│  │ VRM Parser│    │  Animation System      │  │
│  │  JglTF +  │    │  ┌──────────────────┐  │  │
│  │  VRM Ext  │───→│  │ PoseProvider     │  │  │
│  └──────────┘    │  │  ├ Vanilla        │  │  │
│       │          │  │  └ Vivecraft(将来) │  │  │
│       ▼          │  ├──────────────────┤  │  │
│  ┌──────────┐    │  │ SpringBone Sim   │  │  │
│  │ VrmModel │    │  ├──────────────────┤  │  │
│  │ (内部表現)│───→│  │ ExpressionCtrl   │  │  │
│  └──────────┘    │  └────────────────────┘  │
│       │                     │                │
│       ▼                     ▼                │
│  ┌─────────────────────────────────────┐    │
│  │         VRM Renderer                 │    │
│  │  Mixin PlayerRenderer               │    │
│  │  CPU Skinning + VertexConsumer       │    │
│  │  DynamicTexture管理                  │    │
│  └─────────────────────────────────────┘    │
│                                              │
│  ┌────────────┐  ┌──────────────────┐       │
│  │ Config/UI  │  │ Network (将来)    │       │
│  │ Screen API │  │ サーバーmod時同期  │       │
│  └────────────┘  └──────────────────┘       │
│                                              │
│  ┌──────────────────────────────────────┐   │
│  │ VRoid Hub連携 (将来)                  │   │
│  │ OAuth + API Client + キャッシュ       │   │
│  └──────────────────────────────────────┘   │
└─────────────────────────────────────────────┘
```

## VRMパーサー

### 2層構造

```
.vrm (GLBバイナリ)
    ↓ JglTF
glTF標準データ (メッシュ, スキン, テクスチャ, ノード)
    ↓ VRM拡張パーサー
VrmModel (mod内部表現)
```

### JglTF で取得するデータ

- `meshes` → 頂点データ（position, normal, texcoord, joints, weights）
- `skins` → ボーン階層、inverseBindMatrices
- `nodes` → ノードツリー（親子関係、Transform）
- `textures` / `images` → テクスチャバイナリ
- `animations` → キーフレームデータ（もしあれば）

### VRM拡張パーサーで取得するデータ

glTFのJSON `extensions` フィールドから:

- `VRMC_vrm` → メタ情報、ヒューマノイドボーン定義、Expression、LookAt、FirstPerson
- `VRMC_springBone` → 揺れものの定義（Spring, Collider）
- `VRMC_materials_mtoon` → MToonシェーダーパラメータ
- `VRMC_node_constraint` → ボーンコンストレイント

### 内部モデルクラス

```kotlin
data class VrmModel(
    val meta: VrmMeta,
    val humanoid: VrmHumanoid,
    val meshes: List<VrmMesh>,
    val skeleton: VrmSkeleton,
    val textures: List<VrmTexture>,
    val expressions: List<VrmExpression>,
    val springBones: VrmSpringBone?,
    val lookAt: VrmLookAt?,
    val mtoonMaterials: List<VrmMtoonMaterial>?,
)
```

### VRM 0.x 対応の拡張ポイント

VRM 1.0 に最適化した内部表現とする。0.x 対応時に必要に応じてリファクタリング・抽象化を行う。現時点では 0.x 互換性を意識した設計は行わない。

## レンダリングエンジン

### PlayerRenderer差し替え

Mixin で `PlayerRenderer.render()` に介入し、VRM設定済みプレイヤーの場合はバニラ描画をスキップしてVRM描画に切り替える。

```java
// Mixin は Java で記述（Kotlin バイトコードとの互換性問題を避けるため）
// Fabric / NeoForge でメソッドシグネチャが異なる場合があるため、
// Mixin はプラットフォーム別モジュール (fabric/, neoforge/) に配置する。
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(AbstractClientPlayer player, ..., CallbackInfo ci) {
        VrmState vrmState = VrmPlayerManager.INSTANCE.get(player);
        if (vrmState == null) return;
        VrmRenderer.INSTANCE.render(vrmState, poseStack, bufferSource, light);
        ci.cancel();
    }
}
```

### CPUスキニング描画フロー

毎フレーム:

1. PoseProvider からボーン姿勢を取得
2. SpringBone 物理シミュレーション更新
3. ボーン行列計算（ローカル→ワールド変換、inverseBindMatrix適用）
4. 各頂点に対してウェイト付きボーン行列を適用（CPU）
5. 変換済み頂点を VertexConsumer に書き込み
6. RenderType に応じて描画

パフォーマンス目標: 同時描画5体で60fps維持（ポリゴン上限50,000/体の場合）。問題が生じた場合、頂点データのフレーム間差分更新や、SSBO + Compute ShaderによるGPUスキニングへの移行を検討。

### マテリアル / RenderType戦略

| フェーズ | 方式 | Iris互換 | MToon再現度 |
|---|---|---|---|
| 初期 | `RenderType.entityCutoutNoCull` + ベースカラーテクスチャ | 完全互換 | 低 |
| 擬似MToon | バニラRenderType + 頂点カラーでシェード色ベイク | 完全互換 | 中 |
| 将来オプション | カスタムシェーダー（Iris無効時のみ） | Iris無効時のみ | 高 |
| 将来オプション | Iris Shader Pack側でMToon対応 | Iris前提 | 高 |

### テクスチャ管理

- VRM内のテクスチャバイナリを `DynamicTexture` として `TextureManager` に登録
- プレイヤーごとに `ResourceLocation` を動的割り当て
- モデルアンロード時にテクスチャ解放

### パフォーマンス対策

- 頂点バッファのキャッシュ（同ポーズならスキップ）
- 距離に応じた描画スキップ / LOD
- ポリゴン数上限（configで設定可能、デフォルト50,000）
- テクスチャ最大解像度制限（configで設定可能、デフォルト2048x2048。VRAMの圧迫を防止）

### 一人称視点での描画

- バニラ一人称（非VR）: VRMモデルは描画しない（バニラと同じく手のみ表示、またはバニラの手をそのまま使う）
- 三人称視点: VRMモデルを描画
- Vivecraft（将来）: VRM の `firstPerson.meshAnnotations` に基づき、一人称で見せるメッシュ / 隠すメッシュを制御

### 影・名札・ヒットボックス

- ヒットボックスはサーバー側の判定なので変更しない
- VRMモデルの描画スケールはバニラプレイヤーのヒットボックスに概ね合わせる（VRMのhipsの高さを基準にスケーリング）
- 名札の位置はモデルのhead boneの高さに合わせて調整
- 影はバニラの影描画をそのまま利用

## アニメーションシステム

### PoseProvider抽象化

```kotlin
data class BonePose(val translation: Vector3f, val rotation: Quaternionf, val scale: Vector3f)
typealias BonePoseMap = Map<HumanBone, BonePose>

interface PoseProvider {
    fun computePose(skeleton: VrmSkeleton, context: PoseContext): BonePoseMap
}
```

### PoseContext

```kotlin
data class PoseContext(
    val player: AbstractClientPlayer,
    val partialTick: Float,
    val limbSwing: Float,
    val limbSwingAmount: Float,
    val isSwinging: Boolean,
    val isSneaking: Boolean,
    val isSprinting: Boolean,
    val isSwimming: Boolean,
    val isFallFlying: Boolean,
    val isRiding: Boolean,
)
```

### VanillaPoseProvider

バニラの `PlayerModel.setupAnim()` のロジックを参考に、VRMヒューマノイドボーンにマッピング:

| Minecraft状態 | 主に動くVRMボーン |
|---|---|
| 歩行 | hips, leftUpperLeg〜leftFoot, rightUpperLeg〜rightFoot, spine |
| 腕振り | leftUpperArm〜leftHand, rightUpperArm〜rightHand |
| スニーク | spine, chest, head（前傾） |
| 攻撃 | rightUpperArm〜rightHand（振り下ろし） |
| 水泳 | 全身（水平姿勢） |
| エリトラ | 全身（飛行姿勢） |
| 騎乗 | leftUpperLeg, rightUpperLeg（開脚） |
| 頭の向き | neck, head |

### VivecraftIKPoseProvider（将来）

Vivecraft APIから3点トラッキングデータ（HMD + 両コントローラー）を取得し、IKソルバー（FABRIK等）で全身ポーズを推定する。VRM の `firstPerson.meshAnnotations` に基づく一人称メッシュ切り替えも実装。

PoseProviderの抽象化により、VanillaとVivecraftの切り替えは透過的。

### ExpressionController

```kotlin
interface ExpressionController {
    fun update(context: ExpressionContext): Map<ExpressionPreset, Float>
}
```

- ダメージ時 → `angry` or `sad`
- 自動まばたき → 定期的に `blink` 再生
- リップシンク（将来）→ `aa`, `ih`, `ou`, `ee`, `oh`

## SpringBone物理

### VRMC_springBone 1.0 の構造

```
SpringBone
├── springs[]           # 揺れチェーン
│   ├── joints[]        # stiffness, gravity, dragForce等
│   └── colliderGroups[]
└── colliders[]         # 球・カプセルコライダー
```

### シミュレーション

UniVRM公式実装に準拠したVerlet積分ベース:

1. Verlet積分（慣性 + 重力）
2. 剛性（元の姿勢に戻る力）
3. 長さ制約（ボーン長を維持）
4. コライダー衝突判定・押し出し

更新タイミング: PoseProvider適用後、レンダリング前。

## マルチプレイ同期

### 方針

- クライアントmod単体: 自分のVRMのみ表示、他プレイヤーはバニラスキン
- サーバーmod併用時: カスタムパケットでVRM設定情報（モデルソース、モデルID/ハッシュ）を同期

### 同期データ

```kotlin
data class VrmSyncPayload(
    val playerUUID: UUID,
    val source: ModelSource,  // LOCAL or VROID_HUB
    val modelId: String?,     // VRoid Hub モデルID
    val modelHash: String?,   // ローカルモデルのハッシュ
)
```

### ローカルモデルのマルチ対応

ローカルファイルは他プレイヤーに配信しない（著作権・サイズの問題）。VRoid Hub連携時のみ、モデルIDで他プレイヤーもDL可能。

## VRoid Hub 連携（将来）

### フロー

1. mod設定画面から連携開始
2. OAuth 2.0 認可（具体的なフロー実装時に調査・決定）
3. VRoid Hub APIでモデル一覧取得・選択
4. optimized_vrm エンドポイントからDL、ローカルキャッシュ
5. マルチ時はモデルIDを同期、受信側もVRoid Hub APIからDL

### 著作権・安全性

VRoid Hubのモデルは作者がアプリ利用を許可しているもののみDL可能。メタ情報のライセンスフィールドをmod内で表示。

## コンフィグ

```kotlin
data class VrmModConfig(
    val modelSource: ModelSource = ModelSource.LOCAL,
    val localModelPath: String? = null,
    val vroidHubModelId: String? = null,
    val enableSpringBone: Boolean = true,
    val enableExpression: Boolean = true,
    val maxPolygonCount: Int = 50000,
    val renderDistance: Double = 64.0,
)
```

キーバインド（デフォルト `V`）で設定画面を開く。Minecraft `Screen` APIでシンプルなGUI。

### モデルのライフサイクル

- **読み込みタイミング**: ワールド参加時、または設定変更時
- **非同期読み込み**: ワーカースレッドで IO + パースを実行（`CompletableFuture`）。完了後にレンダースレッドでテクスチャ登録。読み込み中はバニラスキンをフォールバック表示
- **アンロード**: ワールド退出時、またはモデル変更時に前のモデルのテクスチャ・頂点データを解放
- **マルチ時のキャッシュ**: LRUキャッシュで最大N体分のモデルを保持（configで設定可能）
- **エラー時**: 読み込み失敗時はバニラスキンにフォールバックし、チャットメッセージでユーザーに通知

## 実装フェーズ

| フェーズ | 内容 | 成果物 |
|---|---|---|
| 1 | プロジェクト構築 + VRMパーサー + JglTF PoC | .vrmファイルが読め、VRM拡張データにアクセスできること |
| 2 | 静的レンダリング | VRMモデルがTポーズで表示される |
| 3 | ボーンアニメーション | 歩行・攻撃等に連動して動く |
| 4 | SpringBone | 髪・衣服が揺れる |
| 5 | Expression | 表情が変わる |
| 6 | 擬似MToon | トゥーン調の見た目 |
| 7 | コンフィグUI | ユーザーが設定画面からモデル選択 |
| 8 | マルチ同期（サーバーmod） | 他プレイヤーのVRMが見える |
| 9 | VRoid Hub連携 | OAuth + API連携 |
| 10 | Vivecraft IK | VRで一人称対応 |
| 11 | LookAt | 視線追従 |
| 12 | VRM 0.x対応 | 旧モデルも使える |

## 既知のリスク

- **JglTFのメンテナンス状況**: 更新頻度が低い場合、glTFパーサーの自前実装やAssimp（LWJGL経由）への切り替えが必要になる可能性。フェーズ1のPoCでVRM拡張データへのアクセス可否を検証する
- **Minecraft バージョンアップ追従**: レンダリングAPIの変更への対応工数。Architecturyである程度は吸収される
- **CPUスキニングのパフォーマンス**: 高ポリゴンモデル × 多人数でFPS低下。ポリゴン上限設定で緩和。目標: 5体同時描画で60fps
- **VRoid Hub API変更**: 外部APIへの依存。API仕様変更時にmod側も追従が必要
- **Mod互換性**: プレイヤーレンダリングに介入する他mod（Figura、スキン変更系、エモート系等）との競合リスク。Mixin の priority 設定と、VRM未設定時にバニラ処理を通す設計で緩和。競合検出時はログに警告を出力
- **スレッドセーフティ**: VRM読み込みは非同期で行うため、ロード中のモデル参照やテクスチャ登録でのスレッド安全性に注意が必要
