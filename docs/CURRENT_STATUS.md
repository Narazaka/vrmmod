# VRM Mod 現状と残課題（2026-03-27時点）

## 完成済み機能

### VRM 1.0 パーサー
- JglTF (`jgltf-model:2.0.4`) でglTF基本データをパース
- VRM拡張（VRMC_vrm, VRMC_springBone, VRMC_materials_mtoon）をGsonでパース
- sparse accessor の手動解決（JglTFが解決できないmorph target用）
- node → mesh マッピング（`buildNodeToMeshMap`）
- lookAt.offsetFromHeadBone パース

### メッシュ描画
- MC 1.21.4 の `RenderType.entityCutoutNoCull` は **QUADS モード**（3頂点の三角形ではなく4頂点の退化四角形として出力）
- alphaMode に応じた RenderType 切替（OPAQUE/MASK → entityCutoutNoCull、BLEND → entityTranslucent）
- unlit風シェーディング（全頂点の法線を (0,1,0) に統一してMCの法線ベースシェーディングを無効化）
- 不透明を先に描画、半透明を後に描画

### スキニング
- CPUスキニング（毎フレーム全頂点をボーン行列で変換）
- `VrmSkinningEngine`: computeWorldMatrices → computeSkinningMatrices → skinVertex/skinNormal

### テクスチャ
- `DynamicTexture` として `TextureManager` に登録
- material → baseColorTexture → texture → image の参照チェーン（`MaterialModelV2`使用）
- プレイヤーごとに `ResourceLocation` を動的割り当て

### アニメーション（vrma）
- `.vrma` ファイルパース（`VrmaParser`: JglTF + VRMC_vrm_animation拡張）
- three-vrm準拠のリターゲット: パース時に `parentWorldRot * localRot * inverse(selfWorldRot)` で正規化
- 適用時: `restRot * normalizedRot`（`convertToNodeOverrides` の `isAbsolute=true` パス）
- hips translation: vrma/モデルの高さ比でスケーリング。rest position を置き換え（加算ではない）
- クロスフェード: クリップ切替時に slerp/lerp でブレンド。状態依存の遷移時間
- 方向検出: entityPos差分とbodyYawの角度差で forward/backward/left/right を判定
- headTracking: アニメーションのHEAD回転にheadYaw/headPitchを乗算
- JSON設定（`vrmmod-animations.json`）: states, transitions（ネストマップ+ワイルドカード）, thresholds
- animationDir に `vrmmod-animations.json` があればそちらを優先読み込み
- バンドルアニメーション: Quaternius UAL1_Standard.vrma (CC0) を mod リソースに同梱
- デフォルトクリップ名は vrma-quaternius-v2 準拠
- `useVrmaAnimation=false` でバンドル含む全VRMAを無効化し VanillaPoseProvider にフォールバック

### 階層型アニメーションステート
- ドット区切りのステート名（`move.walk.forward`, `action.swing.weapon.swords` 等）
- `resolveState()`: 右端のセグメントを削りながらフォールバック解決
- `resolveStateConfig()`: StateConfig も同じ階層フォールバック
- `getTransitionDuration()`: from/to 両方で階層フォールバック + ワイルドカード `*`
- `weaponTags`: 設定ファイルで武器タグを定義（デフォルト: swords, axes, pickaxes, shovels, hoes）
- アイテムタグ自動検出: `ItemStack.getTags()` で全タグ取得、`minecraft:` namespace 省略
- オフハンド対応: `attackArm != mainArm` でswing手判定、`useItemHand` で継続使用手判定
- `PoseContext.mainHandItemTags` / `offHandItemTags` / `isOffHandSwing` / `isOffHandUse`
- 連続swing検出: `attackTime` リセット（値が0.1以上下がる）で連続攻撃/採掘アニメーション
- `forceClipRestart`: 同じクリップでもアニメーション再生を最初からやり直す
- one-shot判定: `StateConfig.loop` フィールドベース（ハードコードセットを廃止）
- デフォルトステート設定は `load()` で旧設定とマージ（新ステートの自動追加）

### アニメーション左右反転（mirror）
- `StateConfig.mirror: Boolean`: per-state の左右反転フラグ
- `HumanBone.mirrorPair()`: 左右ボーンペアマッピング（25ペア）
- `mirrorPoses()`: ボーンペア入替 + translation.x 反転 + rotation.y/z 反転
- バンドルアニメーション（Quaternius）は左手主体のため、mainHand.weapon 等にmirror適用
- computePose でサンプリング後・scaleHipsTranslation前に適用（クロスフェードスナップショットにも適用）

### アイテム手持ち描画
- VRM手ボーン（RIGHT_HAND/LEFT_HAND）のワールド行列を `VrmState` に毎フレーム保存
- `renderHeldItems()` / `renderSingleHandItem()`: 手ボーン位置にMCアイテムを描画
- `applyModelTransform()`: bodyYaw回転 + スケールを共通関数化（VRMメッシュ/アイテム共用）
- アイテム向き調整: Z回転（指方向）→ Y180°（面反転）→ X-90°（刃を前に傾ける）
- 一人称アイテム: `VrmFirstPersonRenderer` で `ItemModelResolver.updateForLiving()` を使用
- 設定: `heldItemScale`(0.67), `heldItemOffset`[x,y,z], `heldItemFirstPerson`, `heldItemThirdPerson`
- `PlayerRenderState.rightHandItem`/`leftHandItem`（三人称）は `ArmedEntityRenderState` から取得

### Z-flip 除去
- 旧: `rotateY(PI) + scale(1,1,-1)` = `scale(-1,1,1)` → X軸ミラー（モデル左右反転）
- 新: bodyYaw回転 + スケールのみ（VRMとMCは同じ+Z前方なので座標変換不要）
- headTracking: `rotateY(yawRad)` → `rotateY(-yawRad)` に修正
- CameraMixin: XZ offset の座標変換も修正済み（旧 `bodyYaw+PI` + Z反転 → 単純な `bodyYaw` 回転）

### アバタースケール・MC目線マッチ
- `avatarScale: Float = 1.0`: 任意のスケール倍率。モデルロード時に `cachedScale` に掛けられる
- `matchMcEyeHeight: Boolean = false`: VRM の目の高さが MC デフォルト（1.62ブロック）に一致するようスケール自動補正
- 計算順序: baseScale（hipsY基準）→ matchMcEyeHeight 補正（`1.62 / computeEyeHeight(baseScale)`）→ avatarScale 倍率 → 最終 eyeHeight 再計算
- VRM_MC_CAMERA モードでの「カメラ位置と身体の大きさの不整合」を緩和する目的
- `cachedScale` はモデルロード時に1回計算して `VrmState` に保存（コードレビューで `estimateScale` 毎フレーム呼び出しを廃止）

### 利き手対応
- `PoseContext.isLeftHanded`: `player.mainArm == HumanoidArm.LEFT` から取得
- mirror フラグと XOR: `shouldMirror = currentMirror != context.isLeftHanded`
- 左利き設定ではバンドルアニメーション（左手主体）がそのまま再生、右利きでは反転
- アイテム描画・オフハンド判定は MC の `mainArm`/`attackArm`/`useItemHand` が既に利き手を反映するため変更不要

### VanillaPoseProvider（vrma不使用時のフォールバック）
- 歩行/走行/しゃがみ/水泳/エリトラ/騎乗のプロシージャルアニメーション
- 走行時は振り幅・膝曲げ・肘曲げが増大
- 腕は rest angle 75度で下ろし、X回転でスイング
- `convertToNodeOverrides` の `isAbsolute=false` パス: `poseRot * restRot`（親空間）

### SpringBone
- three-vrm の `VRMSpringBoneJoint.ts` に忠実に移植
- Verlet積分、center空間、コライダー衝突（球/カプセル）
- **ジョイントチェーン伝播**: 各ジョイントの回転計算後に worldMatrix を更新し、子ジョイントに反映（three-vrm Step 8）
- entityPos を worldMatrices に注入（translation のみ + bodyYaw回転）してエンティティ移動を追跡
- 実フレームレート対応（`System.nanoTime()` で deltaTime 計測）

### Expression（表情）
- morph target の頂点適用（CPU）: スキニング前に delta を加算
- sparse accessor 手動解決（色見いつは VRM等、JglTFが解決できないケース対応）
- VRM 1.0 の morphTargetBinds: `"node"` フィールド → nodeToMeshMap で mesh index に変換
- 自動まばたき: 2-4秒ランダム間隔、0.3秒三角波
- ダメージ表情: hurtTime の立ち上がりエッジで発火、configurable な複数モーフ合成（`Map<String, Float>`）
- ExpressionController: clear-then-accumulate パターン（three-vrm準拠）
- Expression Override: overrideBlink/overrideLookAt/overrideMouth による blink/lookAt/mouth カテゴリの抑制（three-vrm VRMExpressionManager 準拠）
- isBinary 対応: weight > 0.5 を 1.0 として扱い、override 後も binary 制約を維持

### VRM 0.x 対応
- `VrmV0Converter`: VRM 0.x の JSON 構造を VRM 1.0 形式に変換し、既存 v1 パーサーに合流
- `VrmParser`: 拡張キー（`VRM` vs `VRMC_vrm`）で v0/v1 を自動検出
- Humanoid: 配列→オブジェクト変換、親指ボーンリネーム（leftThumbProximal→leftThumbMetacarpal等）
- Expression: blendShapeMaster → expressions、プリセット名変換（joy→happy等）、weight 0-100→0-1
- SpringBone: boneGroups → springs、ルートボーンから全子孫を再帰探索してチェーン構築
- Meta/FirstPerson/LookAt: フィールド名マッピング
- 同一モデル（色見いつは）の v0/v1 ペアで検証済み

### 一人称VRM表示
- Fabric: `WorldRenderEvents.AFTER_ENTITIES` でフック
- NeoForge: `RenderLevelStageEvent.Stage.AFTER_ENTITIES` でフック
- カメラ相対位置でVRMモデルを描画
- バニラ手の非表示: `ItemInHandRenderer.renderHandsWithItems` を Mixin でキャンセル
- 3モード: VANILLA / VRM_MC_CAMERA / VRM_VRM_CAMERA
- 三角形単位HEAD除去: 3頂点全てがHEADの子孫ジョイントに主にウェイトされている三角形をスキップ
- `collectHeadJointIndices`: HEAD ボーンの子孫ジョイントインデックスを収集（キャッシュ）
- 一人称アイテム描画: `VrmFirstPersonRenderer` で `ItemModelResolver.updateForLiving()` を使い手持ちアイテム描画
- 一人称 PoseContext: `mainHandItemTags`/`offHandItemTags`/`isOffHandSwing`/`isOffHandUse` をエンティティから直接取得（三人称は Mixin 経由の ThreadLocal だが、一人称は `collectVisibleEntities` でスキップされるため独自構築が必要）

### VRM_VRM_CAMERA モード
- rest-pose の HEAD ワールド行列 × lookAt.offsetFromHeadBone で eyeHeight 計算（モデルロード時1回）
- 毎フレーム: アニメーション後の HEAD XZ offset（body lean追従）+ rest-pose Y（jitter回避）
- CameraMixin: XZ offset を bodyYaw 回転で MC ワールド空間に変換（Z-flip名残は修正済み）
- one-shot アニメーション再生中はカメラ XZ オフセットを固定（Jab 等による前後揺れ防止）
- GameRendererMixin: VRM_VRM_CAMERA モード時にレイキャスト起点を Camera.getPosition() に差し替え（エイム一致）
- MC のしゃがみ等のカメラ Y 変動はそのまま保持

### コンフィグ
- `vrmmod.json`: localModelPath, animationDir, useVrmaAnimation, firstPersonMode, modelSource, vroidHubModelId
- `vrmmod-animations.json`: states(階層型), transitions(階層フォールバック+ワイルドカード), weaponTags, headTracking, walkThreshold, runThreshold, avatarScale, matchMcEyeHeight, damageExpression, damageExpressionDuration, heldItemScale, heldItemOffset, heldItemFirstPerson, heldItemThirdPerson
- 独自 GUI（`VrmModScreen` + `VrmSettingsList`）: スクロール可能なカテゴリ分け設定画面
- カテゴリ: 一般（モデルソース、パス、アニメーション、一人称モード）、表示（アバタースケール/MC目線合わせ/アイテムスケール/位置/表示トグル）
- リセットボタン: アバタースケール/アイテムスケール/位置をデフォルト値に戻す
- Mod Menu 統合（Fabric）、IConfigScreenFactory（NeoForge）
- ライブリロード: 設定保存時にモデル即再読み込み
- キーバインド: デフォルト未割当て
- デフォルトステート/weaponTags のマージ: 新ステート追加時に既存設定を壊さない

### ビルド・プラットフォーム
- Architectury API (15.0.3) で Fabric / NeoForge 両対応
- Kotlin + Java (Mixin のみ)、Java 21
- MC 1.21.4
- JglTF を shadow JAR にバンドル（Jackson は NeoForge 向けに除外）

## MC 1.21.4 固有の技術的知見

### PlayerRenderer / EntityRenderState
- MC 1.21.4 で `EntityRenderState` ベースにリファクタリング
- `PlayerRenderer` は `render()` を override していない（`LivingEntityRenderer` から継承）
- Mixin は `LivingEntityRenderer.render(LivingEntityRenderState, ...)` をターゲット
- `PlayerRenderer.extractRenderState(AbstractClientPlayer, PlayerRenderState, float)` でプレイヤーUUIDをキャプチャ
- UUID は `VrmRenderContext`（ThreadLocal）で extractRenderState → render 間を受け渡し
- `PlayerRenderState` のフィールド: `walkAnimationPos`, `walkAnimationSpeed`, `bodyRot`, `yRot`, `xRot`, `isCrouching`, `isVisuallySwimming`, `isFallFlying`, `isPassenger`, `attackTime`, `speedValue`, `swinging`, `isUsingItem`, `ticksUsingItem`, `isAutoSpinAttack`, `deathTime`, `attackArm`, `mainArm`, `useItemHand`
- `isSprinting` は存在しない → `speedValue > 0.9f` で代用
- `headYaw` は `renderState.yRot`（body 相対値として既に入っている）
- `hurtTime` は PlayerRenderState に直接なし → `player.hurtTime` を extractRenderState でキャプチャ
- `rightHandItem` / `leftHandItem` は `ArmedEntityRenderState` から取得（`ItemStackRenderState`）
- 一人称ではエンティティレンダラーが呼ばれない（`collectVisibleEntities` がカメラエンティティをスキップ）
- `TieredItem` は MC 1.21.4 に存在しない（`SwordItem` 等の共通親クラスだった）→ `ItemTags` ベースで武器判定
- `ItemStack.getTags()` は `Stream<TagKey<Item>>` を返す。`TagKey.location()` で `ResourceLocation` 取得
- `ItemModelResolver.updateForLiving()` で `ItemStack` → `ItemStackRenderState` を構築可能（一人称アイテム描画で使用）

### レイキャスト（エイム）の仕組み
- `GameRenderer.pick(float)` → `pick(Entity, blockRange, entityRange, partialTick)`
- レイ起点: `entity.getEyePosition(partialTick)` = 足元 + `entity.eyeHeight`（**Camera は使わない**）
- レイ方向: `entity.getViewVector(partialTick)` = マウスの yaw/pitch から計算
- `Entity.eyeHeight` は `private` フィールド、`refreshDimensions()` で `getDimensions(pose).eyeHeight()` から設定
- `getEyeHeight()` と `getEyePosition()` は `final` → Mixin でオーバーライド不可
- カメラ位置とレイキャスト起点は独立 → VRM_VRM_CAMERA でカメラを動かしてもエイムがずれる
- 解決: `GameRendererMixin` で `pick()` 全体を差し替え、起点を `Camera.getPosition()` に変更

### RenderType
- `entityCutoutNoCull` は **QUADS モード**（`VertexFormat.Mode.QUADS`）
- 三角形を退化四角形 (v0, v1, v2, v2) として出力する必要あり
- `entityTranslucent` も QUADS

### 座標系変換
- VRM: 右手系 Y-up、+Z が前方
- MC: Y-up、+Z が南（エンティティのデフォルト前方）
- 両方 +Z が前方 → 座標系変換不要（Z-flip を除去済み）
- poseStack 変換: `rotateY(-bodyYawRad)` → `scale(s, s, s)` のみ
- SpringBone の worldMatrices には `entityTranslation * rotateY(-bodyYaw)` を注入

### Mixin 配置
- `PlayerRendererMixin`: extractRenderState フック（UUID + entityPos + onGround + hurtTime + mainHandItemTags + offHandItemTags キャプチャ）
- `LivingEntityRendererMixin`: render HEAD フック（VRM描画 + アイテム描画 + バニラキャンセル）+ RETURN フック（ThreadLocal UUID クリア）
- `HandRendererMixin`: renderHandsWithItems キャンセル（VANILLA モード以外）
- `CameraMixin`: VRM_VRM_CAMERA モードのカメラ Y/XZ 調整
- `GameRendererMixin`: VRM_VRM_CAMERA モード時のレイキャスト起点をカメラ位置に差し替え
- `MixinHelper`（common）: buildPoseContext を共通化（Fabric/NeoForge の Mixin から呼び出し）
- Mixin は Java で記述（Kotlin バイトコードとの互換性問題）
- `refmap` を vrmmod.mixins.json に明示
- 継承メソッド（LivingEntityRenderer.render）はコンパイル時に AP 警告が出るがランタイムで正常動作
- VrmRenderContext はMixinパッケージ外に配置（Mixinパッケージ内のクラスは直接参照不可）
- render RETURN で ThreadLocal をクリアし stale UUID を防止

## コードレビュー修正（2026-03-27実施）

計18件の指摘を修正:
- `lastRenderTimeNano` をプレイヤーごとに分離（マルチプレイ時のSprignBone/Expression速度バグ修正）
- VRoid Hub キャッシュバージョン管理修正（API version ID 使用）+ POST 2xx対応
- `computeWorldMatrices` 重複呼び出し最適化（render() 内で1回に統合）
- `estimateScale` をワールド空間 Hips Y で計算（ローカル Y → ワールド Y）
- VrmV0Converter: 親指ボーンリネーム漏れ修正（Proximal→Metacarpal）
- VrmParser: 未使用 firstPerson パース例外のサイレント処理
- pause/resume 時の deltaTime 異常値を破棄
- VanillaPoseProvider の headTracking yaw 符号修正（Z-flip 除去対応）
- VrmExpression/VrmState を immutable 化（data class → regular class）
- VrmPrimitive: alphaMode を equals/hashCode に含める
- NativeImage リーク防止（DynamicTexture 登録失敗時に close）
- ThreadLocal UUID クリア（render RETURN で stale 状態防止）
- buildPoseContext を common の MixinHelper に共通化
- AnimationConfig: save 失敗時にログ出力
- IndexedPrimitive/RenderKey を render() 外に移動
- drawPrimitive の Vector3f 再利用
- HEAD子孫検出を下方向DFSに改善

## VRoid Hub OAuth credentials ビルド時注入（2026-03-27実施）

- Gradle タスク `generateVRoidHubSecrets`（`common/build.gradle.kts`）が OAuth clientId/clientSecret をビルド時に注入
- 優先順: 環境変数 `VROIDHUB_CLIENT_ID`/`VROIDHUB_CLIENT_SECRET` > ルートの `vrmmod-vroidhub-secrets.json` > 空（VRoid Hub 機能無効）
- XOR+Base64 で難読化して `build/generated/.../VRoidHubSecrets.kt` を生成（gitignore 対象）
- XOR キーは `SecureRandom` でビルドごとにランダム生成、private 変数名・メソッド名もランダム化
- `VRoidHubConfig.load()` はランタイム `config/vrmmod-vroidhub-secrets.json` → ビルド時注入デフォルト → 空の順でフォールバック
- GitHub Actions: `env:` で secrets を直接 Gradle に渡す（ファイル生成なし、GitHub 公式推奨の最安全方式）
- ファイル名を `vrmmod-vroidhub.json` → `vrmmod-vroidhub-secrets.json` に改名（機密ファイルであることを明示）

## Iris シェーダー互換性（2026-03-28実施）

### 根本原因の特定
- Iris の `MixinBufferBuilder.fillExtendedData()` が QUADS モードの描画で全頂点の法線をフェイス法線（面法線）で強制上書きしていた
- TRIANGLES モードの場合は per-vertex 法線を保持する（Iris Issue #1406 の修正が TRIANGLES のみに適用）
- バニラでは smooth normals が正しく描画されることを実機確認済み

### 対応内容
- **TRIANGLES モード RenderType**: `VrmRenderType` で `entityCutoutNoCull` / `entityTranslucent` と同一 CompositeState、Mode だけ TRIANGLES に変えたカスタム RenderType を作成
- **Access Widener**: `RenderType.create()`（package-private）と `RenderStateShard` の protected フィールドに正規の方法でアクセス（`vrmmod.accesswidener`）
- **NormalMode enum** (AUTO/OFF/ON): `ShaderDetector` が Iris API (`IrisApi.isShaderPackInUse()`) をリフレクションで検出し、AUTO モードではシェーダーパック有効時のみ実法線を使用
- **退化四角形モード**: レガシー QUADS モードへのフォールバックオプション（Sodium 等で問題が出た場合用）
- **影色ティント**: MToon の shadeColorFactor を頂点カラーに反映するオプション（効果は限定的、乗算パイプラインの制約）
- デフォルト: TRIANGLES モード + NormalMode.AUTO（空 JSON 設定でも最適な動作）

### 技術的知見
- NeoForge は JPMS を使うため `net.minecraft.client.renderer` パッケージに mod クラスを置くとパッケージ衝突（同一パッケージトリック不可）
- Fabric は intermediary マッピングのためリフレクションでメソッド名/フィールド名指定が不可
- Access Widener が Architectury (Fabric + NeoForge) での正規のアクセス手段
- Iris Issue #1406: 法線上書き問題。修正は TRIANGLES のみ適用、QUADS は意図的に面法線上書き維持（ブロック描画の法線が不正確なため）
- Iris の `ImmediateState.isRenderingLevel` が true（ワールド描画中）のときのみ QUADS 法線を上書き
- Iris の `IrisApi.getInstance().isShaderPackInUse()` でシェーダーパック有効を検出可能（リフレクション経由、`ShaderDetector.kt`）
- Iris は RenderType 名に `entity` を含むものを `gbuffers_entities` にルーティングする（カスタム RenderType 名に `vrm_entity_` prefix を使用）
- MC 1.21.4 の `VertexFormat.Mode.QUADS` と `TRIANGLES` はどちらも最終的に `GL_TRIANGLES` だが、QUADS は4頂点→6インデックス変換、TRIANGLES は3頂点そのまま
- Complementary / BSL 等のシェーダーパックは `gbuffers_entities` で `smooth` interpolation を使用（`flat` ではない）。フラットシェーディングの原因はシェーダーパック側ではなく Iris のパイプライン側

### 設定項目（`vrmmod-animations.json`）
- `normalMode`: AUTO（デフォルト）/ OFF / ON — 法線モード
- `useShadeColorTint`: false（デフォルト）— MToon 影色ティント
- `useDegenerateQuadRenderType`: false（デフォルト）— レガシー QUADS モード

## マルチプレイ VRoid Hub モデル同期（2026-03-28実施）

### 概要
- サーバー経由で各プレイヤーの VRoid Hub モデルIDとスケール値・法線モードを共有
- 受信側が VRoid Hub API でモデルをダウンロードし表示
- mod未導入サーバーではグレースフルデグラデーション（従来通り自分のVRMのみ）

### パケット設計
- `ModelAnnouncePayload` (C2S): `vroidHubModelId`, `multiplayLicenseId`, `scale`, `normalMode`
- `PlayerModelPayload` (S2C): `playerUUID`, `vroidHubModelId`, `multiplayLicenseId`, `scale`, `normalMode`
- Architectury NetworkManager の `CustomPacketPayload` + `StreamCodec` で実装

### サーバー側 (`VrmModServer`)
- `ConcurrentHashMap<UUID, PlayerModelInfo>` でメモリ管理（永続化なし）
- 初回アナウンス時のみ既存プレイヤーの一括送信、モデル変更時は変更者の情報のみブロードキャスト
- プレイヤー切断時にマップ削除 + null ブロードキャスト（`PlayerEvent.PLAYER_QUIT`）

### クライアント側
- **送信 (`VrmModClient.announceModel`)**: モデルロード完了後に `onLoaded` コールバックで呼び出し。`postDownloadLicenseMultiplay` でマルチプレイ用ライセンス取得を試み、失敗しても `license: null` で送信
- **受信 (`MultiplayModelHandler`)**: `PlayerModelPayload` 受信 → VRoid Hub ダウンロード → `VrmPlayerManager.loadFromBytes` でロード → `cachedScale` オーバーライド
- **ダウンロード方式**: マルチプレイ用ライセンス → 失敗時は自分の認証で通常ライセンス（publicモデルのみ）にフォールバック
- **メモリキャッシュ**: ダウンロード済み VRM は `ConcurrentHashMap<String, ByteArray>` でメモリ上にのみ保持。ディスクには書かない（ダウンロード許可されていないモデルの永続化を防止）。セッション終了時にクリア
- **未ログイン通知**: VRoid Hub 未ログイン時は初回のみチャットに通知
- `VrmPlayerManager.loadFromBytes()`: `ByteArray` から直接パースするメソッド。`loadLocal` と内部実装 (`loadInternal`) を共有

### 技術的知見
- Architectury `NetworkManager.registerReceiver(s2c(), ...)` は内部で `registerS2CPayloadType` も行うため、事前の `registerS2CPayloadType` は不要（重複登録で `IllegalArgumentException`）
- `VrmPlayerManager.loadLocal()` は非同期（`CompletableFuture` + render thread execute）。呼び出し直後に `get()` しても null。`onLoaded` コールバックパラメータを追加して解決
- LANサーバーでは統合サーバーとクライアントが同一プロセスのため、パケットフォーマット変更時は両側のビルドを揃える必要あり

## 残課題

### 高優先
1. **コード整理**: デバッグ用テスト（MorphTargetDebugTest, VrmaAnalysisTest, MtoonAnalysisTest, VrmV0DiagnosticTest, JglTFSkinApiTest, VrmV0CoordinateTest）の整理、不要import除去

### 中優先（次に着手予定）
2. **MToon カスタムシェーダー**: 設計ブレインストーミング途中。以下が合意済み:
   - **バニラ + Iris 両対応**: バニラ環境ではカスタム RenderType + フラグメントシェーダー、Iris 環境では shader pack として実装。Iris の有無で分岐
   - **スコープ**: まず A（コア機能のみ）から。baseColor/shadeColor の2色トゥーン + shadingToonyFactor + shadingShiftFactor。パラメータは既にパース済み（`VrmExtensionParser.parseMtoonMaterials`、`VrmMtoonMaterial` data class）
   - **RenderType 方式**: 既存シェーダー名上書きではなく、カスタムシェーダー名の RenderType を作成（VRM モデルだけに MToon が適用される、他エンティティに影響しない）
   - **パラメータ受け渡し**: 頂点カラーの RGB に shadeColor をエンコード、toony/shift はまずシェーダー内ハードコード（デフォルト値 toony=0.9, shift=0.0）で始める方針（未確定、次セッションで確認要）
   - **ON/OFF 切り替え**: バニラでもシェーダー有効/無効を設定で切り替え可能にする
   - **影色ティント（既存）**: `useShadeColorTint` オプションは頂点カラーに shadeColor を直接設定する方式だが、乗算パイプライン（`texture × vertexColor × lighting`）の制約で影部分だけの色制御は不可能。効果は限定的で微妙。MToon シェーダー実装後は不要になる可能性あり
   - **将来拡張 B**: shadeMultiplyTexture、matcap、rim light、outline
   - **将来拡張 C**: MToon 1.0 フル準拠（emissive、UV animation、transparentWithZWrite 等）

### 中優先
3. **一人称カメラ XZ 揺れ問題**: アイドルモーション等でカメラが揺れる。FPSゲームの知見（ビューモデルは揺れるがカメラは固定）を取り入れた設計が必要
4. **LookAt（視線追従）**: VRM 1.0 spec の lookAt 実装
5. **名札位置調整**: VRMモデルの頭の高さに合わせたオフセット
6. **一人称カメラ位置改善**: 下を向くと身体内部が見える問題（首の内部が見える）

### 低優先
7. **特殊メッシュの描画破綻**: `Cynthia_Maya_VRM_チアリーダー.vrm` のポンポンメッシュのみがノイズ状に描画される
8. **Vivecraft IK**: VR 一人称、3点IK
9. **パフォーマンス最適化**: GPU スキニング、多人数時のFPS
10. **README / ドキュメント**: 使い方、設定ファイルの説明
11. **CI/CD / リリースビルド**: Modrinth/CurseForge パッケージング（GitHub Actions ビルド + VRoid Hub secrets 注入は実装済み）

## 設計書・計画書

### 設計書 (`docs/superpowers/specs/`)
- `2026-03-24-vrm-minecraft-mod-design.md` — 全体設計
- `2026-03-26-vroid-hub-integration-design.md` — VRoid Hub 連携
- `2026-03-27-hierarchical-animation-states-design.md` — 階層型アニメーションステート
- `2026-03-27-offhand-animation-support-design.md` — オフハンドアニメーション
- `2026-03-27-held-item-rendering-design.md` — アイテム手持ち描画
- `2026-03-27-animation-mirror-design.md` — アニメーション左右反転
- `2026-03-27-settings-scroll-categories-design.md` — 設定画面スクロール・カテゴリ
- `2026-03-27-multiplayer-vroidhub-sync-design.md` — マルチプレイ VRoid Hub モデル同期

### 計画書 (`docs/superpowers/plans/`)
- `2026-03-24-vrm-mod-mvp.md` — MVP計画
- `2026-03-25-vrm-v0-support.md` — VRM 0.x 対応
- `2026-03-27-hierarchical-animation-states.md` — 階層型ステート実装
- `2026-03-27-offhand-animation-support.md` — オフハンド対応実装
- `2026-03-27-held-item-rendering.md` — アイテム描画実装
- `2026-03-27-animation-mirror.md` — mirror 実装
- `2026-03-27-settings-scroll-categories.md` — 設定画面改善
- `2026-03-27-code-review-fixes.md` — コードレビュー修正
- `2026-03-27-multiplayer-vroidhub-sync.md` — マルチプレイ VRoid Hub 同期

## 開発環境メモ
- `minecraft-dev-mcp` MCP サーバー: MC 1.21.4 mojmap でインデックス済み（`search_indexed` で高速検索可能）
- `analyze_mixin` でMixin の妥当性検証が可能

## リファレンス実装
- three-vrm (pixiv) をリファレンスとして使用する方針
- SpringBone → VRMSpringBoneJoint.ts
- vrma リターゲット → VRMAnimationLoaderPlugin.ts + createVRMAnimationClip.ts
- firstPerson → VRMFirstPerson.ts
- Expression → VRMExpressionMorphTargetBind.ts
