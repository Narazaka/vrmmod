# VRM Mod

Minecraft のプレイヤーモデルを VRM アバターに置き換える MOD です。VRM 1.0 および VRM 0.x 形式に対応しています。

## 対応

- **Fabric** (Minecraft 1.21.4 / 1.21.1 / 1.20.1)
- **NeoForge** (Minecraft 1.21.4 / 1.21.1)
- **Forge** (Minecraft 1.20.1)

## 依存 MOD

### Fabric

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Mod Menu](https://modrinth.com/mod/modmenu)（任意。設定画面へのアクセス用）

### NeoForge / Forge

- [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge)
- [Architectury API](https://modrinth.com/mod/architectury-api)

## インストール

[Releases](https://github.com/Narazaka/vrmmod/releases) から対応するバージョンの JAR をダウンロードし、依存 MOD と一緒に mods フォルダに配置する。

- 例: `vrmmod-fabric-0.1.0+mc1.21.4.jar`、`vrmmod-neoforge-0.1.0+mc1.21.1.jar`

## VRoid Hub 連携

[VRoid Hub](https://hub.vroid.com/) 上のモデルを直接読み込んで使用できます。設定画面の VRoid Hub タブから認証すると、自分のモデルやハートしたモデルから選択できます。

### マルチプレイ同期

サーバーにもこの MOD が導入されている場合、各プレイヤーの VRoid Hub モデルが他のプレイヤーにも表示されます。

- 他プレイヤーのモデルを表示するには、自分も VRoid Hub にログインしている必要があります
- VRoid Hub 側で「モデル登録者以外の利用」が許可されていないモデルは、他プレイヤーには表示されません
- サーバーに MOD がない場合でも、従来通り自分のモデルだけが表示されます
- アニメーションは各クライアントローカルで計算されます。同じ行動をしていれば同じアニメーションが再生されますが、タイミングや物理演算の微差は生じます。

## 機能

### VRM アバター表示

ローカルの `.vrm` ファイルをプレイヤーモデルとして描画します。

- VRM 1.0 と VRM 0.x の両方をに対応しています。
- VRM 0.x モデルは内部で VRM 1.0 形式に変換され、同一のレンダリングパイプラインで処理されます。
- OPAQUE / BLEND（半透明）のアルファモードに対応しています。

#### 法線

- デフォルトでは法線を均一化した Unlit 風のシェーディングで、Minecraft の光源に左右されないフラットな見た目になります。
- Iris Shaderのカスタムシェーダーを検出すると自動でモデル法線になります。
- この挙動は設定からカスタム可能です。

### アニメーション

`.vrma` 形式のアニメーションファイルを読み込み、VRM モデルに適用します。

- 階層型ステートシステムによる柔軟なアニメーション割り当て（後述）
- ステート間のクロスフェードトランジション（秒数をカスタマイズ可能）
- デフォルトのアニメーションクリップ同梱（Quaternius UAL1 Standard / CC0）
- プレイヤーの視線方向に合わせた頭の回転（ヘッドトラッキング）
- スキンのカスタマイズ→利き手 対応
  - アニメーションを左右反転（mirror）して表示します

#### プロシージャルアニメーション（フォールバック）

VRMA ファイルが無い場合や `useVrmaAnimation` を `false` にした場合、Minecraft のプレイヤーアニメーションデータから自動生成されるモーションで動作します。歩行・走行・ジャンプ・スニーク・水泳・エリトラ・騎乗・攻撃モーションなどに対応しています。

### SpringBone

VRM モデルの SpringBone 定義に基づき、髪や衣装などの揺れものをシミュレーションします。球体・カプセルコライダーによる衝突判定に対応しています。

### マイクラ要素の描画

#### 手持ちアイテム

VRM アバターの手にアイテム（剣、ツール、ブロック等）を描画します。メインハンド・オフハンドの両方に対応しています。設定画面からアイテムの大きさや位置を調整できます。

#### 防具

防具（ヘルメット、チェストプレート等）の表示には現在対応していません。

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

**一人称モードの使い方のヒント:**

- `VRM_MC_CAMERA` を使う場合は、設定の「MC目線高さに合わせる」をONにするとアバターの目の高さがMCのカメラ高さに揃い、体感が改善します
- `VRM_VRM_CAMERA` は現状実験的です。アニメーションによるカメラの揺れが大きく、操作感に影響する場合があります
- 一人称視点で手持ちアイテムが視界の邪魔になる場合は、設定の「一人称でアイテム表示」をOFFにできます

## 設定

設定は **GUI**（MOD 設定画面）または **JSON ファイル** の直接編集で変更できます。

- **GUI**: ゲーム内の MOD 設定画面から開けます（Fabric の場合は Mod Menu、NeoForge の場合は MOD 一覧から）
- **キーバインド**: 未割り当てのキーバインド「VRM Config」を任意のキーに割り当てると、直接設定画面を開けます

基本設定は `config/vrmmod.json`、アニメーション設定は `config/vrmmod-animations.json` に保存されます。GUI から変更すると即座にモデルがリロードされます。

### アニメーション設定（`config/vrmmod-animations.json`）

アニメーションのステート割り当てやトランジション時間を細かくカスタマイズできます。`animationDir` 内に同名ファイルを置くとそちらが優先されます。このファイルはステート・トランジション等の高度な設定を含むため、GUI からは編集できません。直接 JSON を編集してください。

### ファイル構成

```
<minecraft>/config/
├── vrmmod.json                  # 基本設定
└── vrmmod-animations.json       # アニメーション設定（自動生成）

<animationDir>/                  # animationDir を指定した場合
├── vrmmod-animations.json       # こちらが優先される（任意）
└── *.vrma                       # アニメーションクリップ
```

## 階層型アニメーションステート

アニメーションステートはドット区切りの階層名で管理されます。設定されていないステートは親階層にフォールバックします。

例: `action.swing.mainHand.weapon.swords` が未設定 → `action.swing.mainHand.weapon` を使用 → それも未設定なら `action.swing` を使用

### 移動ステート

| ステート | 説明 | デフォルトクリップ |
|----------|------|-------------------|
| `move.idle` | 静止 | `Idle_Loop` |
| `move.walk` | 前方歩行 | `Walk_Loop` |
| `move.walk.backward` | 後退 | `Walk_Loop` |
| `move.walk.left` | 左歩行 | `Walk_Loop` |
| `move.walk.right` | 右歩行 | `Walk_Loop` |
| `move.sprint` | ダッシュ | `Sprint_Loop` |
| `move.sprint.backward` | 後退ダッシュ | (フォールバック: `move.sprint`) |
| `move.sprint.left` | 左ダッシュ | (フォールバック: `move.sprint`) |
| `move.sprint.right` | 右ダッシュ | (フォールバック: `move.sprint`) |
| `move.jump` | ジャンプ | `Jump_Loop` |
| `move.sneak` | スニーク | `Crouch_Idle_Loop` |
| `move.sneak.idle` | スニーク静止 | `Crouch_Idle_Loop` |
| `move.sneak.walk` | スニーク歩行 | `Crouch_Fwd_Loop` |
| `move.swim` | 水泳 | `Swim_Fwd_Loop` |
| `move.swim.idle` | 水中静止 | `Swim_Idle_Loop` |
| `move.ride` | 騎乗 | `Sitting_Idle_Loop` |
| `move.elytra` | エリトラ飛行 | `Swim_Fwd_Loop` |

### アクションステート

アクションステートは1回再生（ループなし）で、完了後に移動ステートに戻ります。

メインハンド/オフハンドで異なるアニメーションを設定できます。アイテムの種類はMinecraftのアイテムタグで自動判定されるため、MOD追加の武器やツールにも対応します。

| ステート | 説明 | デフォルトクリップ |
|----------|------|-------------------|
| `action.swing` | 素手スイング | `Punch_Jab` |
| `action.swing.mainHand.weapon` | メインハンド武器/ツール攻撃 | `Sword_Attack` |
| `action.swing.mainHand.item` | メインハンドアイテム使用（設置等） | `Interact` |
| `action.swing.offHand.weapon` | オフハンド武器/ツール攻撃 | `Sword_Attack` |
| `action.swing.offHand.item` | オフハンドアイテム使用（設置等） | `Interact` |
| `action.swing.mainHand.weapon.swords` | メインハンド剣攻撃 | (フォールバック: `.weapon`) |
| `action.swing.mainHand.weapon.axes` | メインハンド斧攻撃 | (フォールバック: `.weapon`) |
| `action.useItem` | 継続使用（飲食・弓等） | `Interact` |
| `action.useItem.mainHand` | メインハンド継続使用 | `Interact` |
| `action.useItem.offHand` | オフハンド継続使用 | `Interact` |
| `action.hurt` | 被ダメージ | `Hit_Chest` |
| `action.spinAttack` | トライデント回転攻撃 | `Roll` |
| `action.death` | 死亡 | `Death01` |

### 武器タグ（`weaponTags`）

`action.swing.*.weapon` ステートが使用されるアイテムの種類を定義します。デフォルトでは `swords`, `axes`, `pickaxes`, `shovels`, `hoes` が設定されています。MOD で追加された武器カテゴリのタグ（例: `mymod:halberds`）を追加できます。

### ステートごとの左右反転（`mirror`）

各ステートに `mirror: true` を設定すると、アニメーションの左右が反転されます。バンドルアニメーションは左手主体で作られているため、一部のステートにはデフォルトで mirror が設定されています。

### デフォルトのトランジション

| 遷移元 | 遷移先 | 秒数 |
|--------|--------|------|
| `move.sprint` | `move.idle` | 0.1 |
| `move.sprint` | `move.walk` | 0.2 |
| `move.walk` | `move.idle` | 0.1 |
| `move.walk` | `move.sprint` | 0.2 |
| `move.jump` | `*`（すべて） | 0.1 |
| `move.idle` | `move.walk` | 0.15 |
| `move.idle` | `move.sprint` | 0.15 |
| `*` | `*`（フォールバック） | 0.25 |

トランジションも階層フォールバックに対応しています。`move.sprint.forward` → `move.walk` のトランジション時間を探す際、`move.sprint.forward` → `move.sprint` と順に検索されます。

## VRM モデルの準備

[VRoid Studio](https://vroid.com/studio) などで作成した VRM モデルを使用できます。VRM 1.0 と VRM 0.x の両方に対応しています。`.vrm` ファイルの絶対パスを設定の `localModelPath` に指定してください。

### VRMA アニメーションの用意

独自の `.vrma` アニメーションファイルを使いたい場合は、アニメーションファイルを任意のディレクトリに配置し、`animationDir` にそのディレクトリパスを設定します。`vrmmod-animations.json` の `states` でステートごとにクリップ名を割り当ててください。

## ライセンス

ソースコードは [zlib License](LICENSE) の下で公開されています。

サードパーティのソフトウェアおよびアセットについては [THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES) を参照してください。
