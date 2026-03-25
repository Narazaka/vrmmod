# VRM Mod 現状と残課題（2026-03-25時点）

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
- 攻撃モーション: isSwinging の立ち上がりエッジで1回再生
- headTracking: アニメーションのHEAD回転にheadYaw/headPitchを乗算
- JSON設定（`vrmmod-animations.json`）: states, transitions（ネストマップ+ワイルドカード）, thresholds
- animationDir に `vrmmod-animations.json` があればそちらを優先読み込み
- バンドルアニメーション: Quaternius UAL1_Standard.vrma (CC0) を mod リソースに同梱
- デフォルトクリップ名は vrma-quaternius-v2 準拠

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
- バニラ手の非表示: `PlayerRenderer.renderRightHand/renderLeftHand` を Mixin でキャンセル
- 3モード: VANILLA / VRM_MC_CAMERA / VRM_VRM_CAMERA
- 三角形単位HEAD除去: 3頂点全てがHEADの子孫ジョイントに主にウェイトされている三角形をスキップ
- `collectHeadJointIndices`: HEAD ボーンの子孫ジョイントインデックスを収集（キャッシュ）

### VRM_VRM_CAMERA モード
- rest-pose の HEAD ワールド行列 × lookAt.offsetFromHeadBone で eyeHeight 計算（モデルロード時1回）
- 毎フレーム: アニメーション後の HEAD XZ offset（body lean追従）+ rest-pose Y（jitter回避）
- CameraMixin: XZ offset を `bodyYawRad + PI` で回転して MC ワールド空間に変換（Z-flip考慮）
- MC のしゃがみ等のカメラ Y 変動はそのまま保持

### コンフィグ
- `vrmmod.json`: localModelPath, animationDir, useVrmaAnimation, firstPersonMode
- `vrmmod-animations.json`: states, transitions, headTracking, walkThreshold, runThreshold, damageExpression, damageExpressionDuration
- Cloth Config API (17.0.144) による GUI
- Mod Menu 統合（Fabric）、IConfigScreenFactory（NeoForge）
- ライブリロード: 設定保存時にモデル即再読み込み
- キーバインド: デフォルト未割当て

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
- `PlayerRenderState` のフィールド: `walkAnimationPos`, `walkAnimationSpeed`, `bodyRot`, `yRot`, `xRot`, `isCrouching`, `isVisuallySwimming`, `isFallFlying`, `isPassenger`, `attackTime`, `speedValue`, `swinging`
- `isSprinting` は存在しない → `speedValue > 0.9f` で代用
- `headYaw` は `renderState.yRot`（body 相対値として既に入っている）
- `hurtTime` は PlayerRenderState に直接なし → `player.hurtTime` を extractRenderState でキャプチャ

### RenderType
- `entityCutoutNoCull` は **QUADS モード**（`VertexFormat.Mode.QUADS`）
- 三角形を退化四角形 (v0, v1, v2, v2) として出力する必要あり
- `entityTranslucent` も QUADS

### 座標系変換
- VRM: 右手系 Y-up、+Z が前方
- MC: Y-up、+Z が南（エンティティのデフォルト前方）
- poseStack 変換順: `rotateY(-bodyYawRad)` → `rotateY(PI)` → `scale(1,1,-1)`
- poseStack は後から適用されたものが先に作用（行列は右から左）
- SpringBone の worldMatrices には `entityTranslation * rotateY(-bodyYaw)` を注入（Z-flip は含めない — Quaternion で表現不可）

### Mixin 配置
- `PlayerRendererMixin`: extractRenderState フック（UUID + entityPos + onGround + hurtTime キャプチャ）
- `LivingEntityRendererMixin`: render フック（VRM描画 + バニラキャンセル）
- `HandRendererMixin`: renderRightHand/renderLeftHand キャンセル
- `CameraMixin`: VRM_VRM_CAMERA モードのカメラ Y/XZ 調整
- Mixin は Java で記述（Kotlin バイトコードとの互換性問題）
- `refmap` を vrmmod.mixins.json に明示
- 継承メソッド（LivingEntityRenderer.render）はコンパイル時に AP 警告が出るがランタイムで正常動作
- VrmRenderContext はMixinパッケージ外に配置（Mixinパッケージ内のクラスは直接参照不可）

## 残課題

### 高優先
1. ~~**NeoForge Mixin 追加**~~: 完了済み
2. ~~**テスト修正**~~: 完了済み
3. **コード整理**: デバッグ用テスト（MorphTargetDebugTest, VrmaAnalysisTest, MtoonAnalysisTest）の整理、不要import除去

### 中優先
4. **マルチプレイ同期**: サーバーmod併用時のカスタムパケット（Architectury のネットワーキングAPI使用）
5. **VRoid Hub連携**: OAuth + API（フロー詳細は実装時に調査）
6. ~~**Expression override**~~: 完了済み（three-vrm の VRMExpressionManager に忠実に移植）
7. **LookAt（視線追従）**: VRM 1.0 spec の lookAt 実装
8. **名札位置調整**: VRMモデルの頭の高さに合わせたオフセット

### 低優先
9. **Iris カスタムシェーダー**: フルMToon再現。法線を (0,1,0) から実際の値に戻す（TODOコメントあり）
10. **アウトライン描画**: 背面法
11. ~~**VRM 0.x 対応**~~: 完了済み（VrmV0Converter で v0 JSON → v1 JSON 変換、VrmParser で自動検出）
12. **Vivecraft IK**: VR 一人称、3点IK
13. **パフォーマンス最適化**: GPU スキニング、多人数時のFPS
14. **README / ドキュメント**: 使い方、設定ファイルの説明
15. **CI/CD / リリースビルド**: GitHub Actions、Modrinth/CurseForge パッケージング

## リファレンス実装
- three-vrm (pixiv) をリファレンスとして使用する方針
- SpringBone → VRMSpringBoneJoint.ts
- vrma リターゲット → VRMAnimationLoaderPlugin.ts + createVRMAnimationClip.ts
- firstPerson → VRMFirstPerson.ts
- Expression → VRMExpressionMorphTargetBind.ts
