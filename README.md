# VRM Mod

Minecraft のプレイヤーモデルを VRM アバターに置き換える MOD です。VRM 1.0 および VRM 0.x 形式に対応しています。

## 機能

### VRM アバター表示

ローカルの `.vrm` ファイルをプレイヤーモデルとして描画します。VRM 1.0 と VRM 0.x の両方を自動検出してパースします。VRM 0.x モデルは内部で VRM 1.0 形式に変換されるため、同一のレンダリングパイプラインで処理されます。OPAQUE / BLEND（半透明）のアルファモードに対応しています。デフォルトでは法線を均一化した Unlit 風のシェーディングで、Minecraft の光源に左右されないフラットな見た目になります。

### VRMA アニメーション

`.vrma` 形式のアニメーションファイルを読み込み、VRM モデルに適用します。

- アイドル、歩行、走行、ジャンプ、スニーク、水泳、エリトラ飛行、騎乗、攻撃などのステートに対応
- ステート間のクロスフェードトランジション（秒数をカスタマイズ可能）
- デフォルトのアニメーションクリップ同梱（Quaternius UAL1 Standard / CC0）
- プレイヤーの視線方向に合わせた頭の回転（ヘッドトラッキング）

### プロシージャルアニメーション（フォールバック）

VRMA ファイルが無い場合や `useVrmaAnimation` を `false` にした場合、Minecraft のプレイヤーアニメーションデータから自動生成されるモーションで動作します。歩行・走行・ジャンプ・スニーク・水泳・エリトラ・騎乗・攻撃モーションに対応しています。

### SpringBone

VRM モデルの SpringBone 定義に基づき、髪や衣装などの揺れものをシミュレーションします。球体・カプセルコライダーによる衝突判定に対応しています。

### 表情（Expression）

- **自動まばたき**: 2〜4秒間隔でランダムにまばたきします
- **ダメージ表情**: プレイヤーがダメージを受けた際に指定した表情を表示します（表情名・強度・フェードアウト時間をカスタマイズ可能）
- **Expression Override**: 感情表情（happy, angry 等）がアクティブな時に blink/lookAt/mouth カテゴリの自動表情を抑制します（three-vrm VRMExpressionManager 準拠）

### 一人称視点

一人称視点での VRM モデルの表示方法を3つのモードから選択できます。

| モード | 説明 |
|--------|------|
| `VANILLA` | Minecraft デフォルトの手を表示。VRM モデルは一人称では非表示 |
| `VRM_MC_CAMERA` | VRM の身体を表示（頭は非表示）。カメラ位置は Minecraft デフォルト |
| `VRM_VRM_CAMERA` | VRM の身体を表示（頭は非表示）。カメラ位置を VRM の目の高さに合わせる |

## 設定

設定は **GUI**（Cloth Config 設定画面）または **JSON ファイル** の直接編集で変更できます。

- **GUI**: ゲーム内の MOD 設定画面から開けます（Fabric の場合は Mod Menu、NeoForge の場合は MOD 一覧から）
- **キーバインド**: 未割り当てのキーバインド「VRM Config」を任意のキーに割り当てると、直接設定画面を開けます

### 基本設定（`config/vrmmod.json`）

| 項目 | 型 | デフォルト | 説明 |
|------|----|-----------|------|
| `localModelPath` | String \| null | `null` | `.vrm` モデルファイルの絶対パス |
| `animationDir` | String \| null | `null` | `.vrma` アニメーションファイルが入ったディレクトリの絶対パス |
| `useVrmaAnimation` | Boolean | `true` | VRMA アニメーションを使用するか。`false` にするとプロシージャルアニメーションを使用 |
| `firstPersonMode` | String | `"VRM_MC_CAMERA"` | 一人称視点モード（`VANILLA` / `VRM_MC_CAMERA` / `VRM_VRM_CAMERA`） |

GUI から変更すると即座にモデルがリロードされます。

### アニメーション設定（`config/vrmmod-animations.json`）

アニメーションのステート割り当てやトランジション時間を細かくカスタマイズできます。`animationDir` 内に同名ファイルを置くとそちらが優先されます。このファイルは GUI からは編集できないため、直接 JSON を編集してください。

| 項目 | 型 | デフォルト | 説明 |
|------|----|-----------|------|
| `states` | Object | 下記参照 | 各ステートに割り当てるアニメーションクリップ名 |
| `transitions` | Object | 下記参照 | ステート間のクロスフェード秒数 |
| `headTracking` | Boolean | `true` | ヘッドトラッキングの有効/無効 |
| `walkThreshold` | Float | `0.01` | 歩行判定の最低移動速度 |
| `runThreshold` | Float | `0.5` | 走行判定の最低移動速度 |
| `damageExpression` | Object | `{"sad": 1.0}` | ダメージ時に適用する表情（表情名→強度） |
| `damageExpressionDuration` | Float | `0.5` | ダメージ表情のフェードアウト秒数 |

#### デフォルトのステート割り当て

| ステート | クリップ名 | ループ |
|----------|-----------|--------|
| `idle` | `Idle_Loop` | Yes |
| `walk` | `Walk_Loop` | Yes |
| `walkBackward` | `Walk_Loop` | Yes |
| `walkLeft` | `Walk_Loop` | Yes |
| `walkRight` | `Walk_Loop` | Yes |
| `run` | `Sprint_Loop` | Yes |
| `jump` | `Jump_Loop` | Yes |
| `sneak` | `Crouch_Idle_Loop` | Yes |
| `sneakWalk` | `Crouch_Fwd_Loop` | Yes |
| `swim` | `Swim_Fwd_Loop` | Yes |
| `ride` | `Sitting_Idle_Loop` | Yes |
| `elytra` | `Swim_Fwd_Loop` | Yes |
| `attack` | `Punch_Jab` | No |

#### デフォルトのトランジション

| 遷移元 | 遷移先 | 秒数 |
|--------|--------|------|
| `run` | `idle` | 0.1 |
| `run` | `walk` | 0.2 |
| `walk` | `idle` | 0.1 |
| `walk` | `run` | 0.2 |
| `jump` | `*`（すべて） | 0.1 |
| `idle` | `walk` | 0.15 |
| `idle` | `run` | 0.15 |
| `*` | `*`（フォールバック） | 0.25 |

### ファイル構成

```
<minecraft>/config/
├── vrmmod.json                  # 基本設定
└── vrmmod-animations.json       # アニメーション設定（自動生成）

<animationDir>/                  # animationDir を指定した場合
├── vrmmod-animations.json       # こちらが優先される（任意）
└── *.vrma                       # アニメーションクリップ
```

## VRM モデルの準備

[VRoid Studio](https://vroid.com/studio) などで作成した VRM モデルを使用できます。VRM 1.0 と VRM 0.x の両方に対応しています。`.vrm` ファイルの絶対パスを設定の `localModelPath` に指定してください。

### VRMA アニメーションの用意

独自の `.vrma` アニメーションファイルを使いたい場合は、アニメーションファイルを任意のディレクトリに配置し、`animationDir` にそのディレクトリパスを設定します。`vrmmod-animations.json` の `states` でステートごとにクリップ名を割り当ててください。

## 対応プラットフォーム

- **Fabric** (Minecraft 1.21.4)
- **NeoForge** (Minecraft 1.21.4)

## 依存 MOD

### Fabric

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)（設定画面）
- [Mod Menu](https://modrinth.com/mod/modmenu)（任意。設定画面へのアクセス用）

### NeoForge

- [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge)
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Cloth Config](https://modrinth.com/mod/cloth-config)（設定画面）

## ライセンス

ソースコードは [zlib License](LICENSE) の下で公開されています。

サードパーティのソフトウェアおよびアセットについては [THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES) を参照してください。
