# Code Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** コードレビューで発見された全指摘（Important 8件 + Suggestion 10件 = 計18件）を優先度順に修正する

**Architecture:** 各タスクは独立した修正。大きく3グループに分かれる: (A) 実バグ修正 (Task 1-8)、(B) 設計改善 (Task 9-14)、(C) 軽微な改善 (Task 15-18)。

**Tech Stack:** Kotlin, Java (Mixin), JOML, JglTF, Minecraft 1.21.4

---

## File Structure

| File | 変更タスク |
|------|-----------|
| `common/.../render/VrmState.kt` | Task 1, 9 |
| `common/.../render/VrmRenderer.kt` | Task 1, 3, 4, 7, 15, 16 |
| `common/.../render/VrmSkinningEngine.kt` | Task 3 |
| `common/.../render/VrmTextureManager.kt` | Task 11 |
| `common/.../render/VrmPlayerManager.kt` | Task 4 |
| `common/.../client/VrmModClient.kt` | Task 2 |
| `common/.../vroidhub/VRoidHubApi.kt` | Task 2 |
| `common/.../vrm/VrmV0Converter.kt` | Task 5 |
| `common/.../vrm/VrmParser.kt` | Task 6 |
| `common/.../vrm/VrmMesh.kt` | Task 10 |
| `common/.../vrm/VrmExpression.kt` | Task 9 |
| `common/.../vrm/VrmExtensionParser.kt` | Task 17 |
| `common/.../animation/AnimationPoseProvider.kt` | Task 7 |
| `common/.../animation/VanillaPoseProvider.kt` | Task 8 |
| `common/.../animation/AnimationConfig.kt` | Task 14 |
| `fabric/.../mixin/LivingEntityRendererMixin.java` | Task 12, 13 |
| `neoforge/.../mixin/LivingEntityRendererMixin.java` | Task 12, 13 |

---

## Group A: 実バグ修正 (Important)

### Task 1: `lastRenderTimeNano` をプレイヤーごとに分離

マルチプレイヤーで2人目以降の SpringBone/Expression の deltaTime が極端に小さくなり、物理・アニメーションが約16倍遅くなるバグ。

**Files:**
- Modify: `common/.../render/VrmState.kt`
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: VrmState に `lastRenderTimeNano` を追加**

`VrmState.kt` の `leftHandMatrix` の後に追加:

```kotlin
@Volatile
var lastRenderTimeNano: Long = 0L
```

- [ ] **Step 2: VrmRenderer のグローバル `lastRenderTimeNano` を削除し VrmState のものを使う**

`VrmRenderer.kt` L29 の `private var lastRenderTimeNano = 0L` を削除。

L59-65 を以下に変更:

```kotlin
val now = System.nanoTime()
val deltaTime = if (state.lastRenderTimeNano == 0L) {
    1f / 60f
} else {
    ((now - state.lastRenderTimeNano) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
}
state.lastRenderTimeNano = now
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
fix: move lastRenderTimeNano to VrmState for per-player delta time
```

---

### Task 2: VRoid Hub キャッシュバージョン管理修正 + POST ステータスコード

キャッシュバージョンに `""` を渡しているため更新が反映されない。また `post` ヘルパーが 200 のみ成功判定のため 201 等で失敗する。

**Files:**
- Modify: `common/.../client/VrmModClient.kt`
- Modify: `common/.../vroidhub/VRoidHubApi.kt`

- [ ] **Step 1: VrmModClient のキャッシュ呼び出しでバージョン ID を使う**

`VrmModClient.kt` の `loadVRoidHubModel` メソッド内。`CharacterModelVersion.id` は既に `VRoidHubModels.kt` に存在する。

ダウンロードフローで API からモデル情報を取得してバージョン ID を得る。L136-143 を以下に変更:

```kotlin
// Check cache — fetch latest version from API to detect updates
val gameDir = Minecraft.getInstance().gameDirectory.toPath()
val versionId = try {
    val hearts = VRoidHubApi.getHearts(token.accessToken).getOrNull() ?: emptyList()
    val ownModels = VRoidHubApi.getAccountCharacterModels(token.accessToken).getOrNull() ?: emptyList()
    val allModels = hearts + ownModels
    allModels.firstOrNull { it.id == modelId }
        ?.latest_character_model_version?.id ?: ""
} catch (_: Exception) { "" }

val cached = VRoidHubModelCache.getCachedModel(gameDir, modelId, versionId)
if (cached != null) {
    VrmMod.logger.info("Loading VRoid Hub model from cache: {}", cached.absolutePath)
    return@supplyAsync cached
}
```

L162 も修正:

```kotlin
val file = VRoidHubModelCache.cacheModel(gameDir, modelId, versionId, vrmBytes)
```

- [ ] **Step 2: VRoidHubApi の `post` ヘルパーを 2xx 対応に**

`VRoidHubApi.kt` L129 を変更:

```kotlin
// 変更前:
// if (response.statusCode() == 200) {

// 変更後:
if (response.statusCode() in 200..299) {
```

`get` ヘルパー (L109) も同様に:

```kotlin
if (response.statusCode() in 200..299) {
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
fix: use API version ID for VRoid Hub cache and accept 2xx status codes
```

---

### Task 3: `computeWorldMatrices` 重複呼び出し最適化

`render()` 内で animated world matrices が2回、skinning 内でも呼ばれている。1回に統合する。

**Files:**
- Modify: `common/.../render/VrmSkinningEngine.kt`
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: `VrmSkinningEngine` に pre-computed world matrices を受けるオーバーロード追加**

`VrmSkinningEngine.kt` の `computeSkinningMatrices` の後に追加:

```kotlin
/**
 * Computes skinning matrices using pre-computed world matrices.
 */
fun computeSkinningMatrices(
    skeleton: VrmSkeleton,
    worldMatrices: List<Matrix4f>,
    skinIndex: Int = 0,
): List<Matrix4f> {
    val skin = skeleton.skins.getOrNull(skinIndex) ?: return emptyList()
    val joints = skin.jointNodeIndices
    val ibms = skin.inverseBindMatrices

    return List(joints.size) { j ->
        val nodeIdx = joints[j]
        Matrix4f(worldMatrices[nodeIdx]).mul(ibms[j])
    }
}
```

- [ ] **Step 2: `VrmRenderer.render()` の worldMatrices 計算を統合**

SpringBone 適用後の L132 `val worldMatrices = ...` を `animatedWorldMatrices` にリネームし、この変数を skinning/hand bones/unskinned meshes/eye offset で共有する。

L98-106 を変更:

```kotlin
// Compute animated world matrices ONCE after SpringBone
val animatedWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)

val skinningMatricesCache = mutableMapOf<Int, List<Matrix4f>>()
fun getSkinningMatrices(skinIndex: Int): List<Matrix4f> {
    return skinningMatricesCache.getOrPut(skinIndex) {
        VrmSkinningEngine.computeSkinningMatrices(model.skeleton, animatedWorldMatrices, skinIndex)
    }
}
val skinningMatrices = getSkinningMatrices(0)
```

L132 の `val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)` を削除。L136-138 の `worldMatrices` 参照を `animatedWorldMatrices` に変更。L204 の `worldMatrices` も同様。

- [ ] **Step 3: `updateEyeOffset` の animated 側を引数で受ける**

シグネチャ変更:

```kotlin
private fun updateEyeOffset(
    state: VrmState,
    model: VrmModel,
    animatedWorldMatrices: List<Matrix4f>,
    scale: Float,
)
```

L430 の `val animWorldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)` を削除し、引数の `animatedWorldMatrices` を使う。rest-pose (L423) はそのまま残す（呼び出し頻度は1回/フレーム）。

呼び出し側: `updateEyeOffset(state, model, animatedWorldMatrices, scale)`

- [ ] **Step 4: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 5: コミット**

```
perf: compute world matrices once per render pass instead of 4-5 times
```

---

### Task 4: `estimateScale` をワールド空間 Hips Y に変更

Armature offset モデルで Y 浮きする既知問題。ローカル Y → ワールド Y に変更。

**Files:**
- Modify: `common/.../render/VrmRenderer.kt`
- Modify: `common/.../render/VrmPlayerManager.kt`

- [ ] **Step 1: `VrmRenderer.estimateScale` を修正**

```kotlin
private fun estimateScale(state: VrmState): Float {
    val model = state.model
    val hipsNode = model.humanoid.humanBones[HumanBone.HIPS] ?: return DEFAULT_SCALE
    val nodeIndex = hipsNode.nodeIndex
    if (nodeIndex !in model.skeleton.nodes.indices) return DEFAULT_SCALE

    val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
    val hipsWorldPos = Vector3f()
    worldMatrices[nodeIndex].getTranslation(hipsWorldPos)
    val hipsY = hipsWorldPos.y
    return if (hipsY > 0f) TARGET_HEIGHT / (hipsY * 2f) else DEFAULT_SCALE
}
```

- [ ] **Step 2: `VrmPlayerManager.computeEyeHeight` の scale 計算も同様に修正**

L181-185 を変更。L172 で既に `computeWorldMatrices` を呼んでいるので、その結果を再利用:

```kotlin
private fun computeEyeHeight(model: net.narazaka.vrmmod.vrm.VrmModel): Float {
    val headBoneNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD]
        ?: return 1.62f

    val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton)
    val headWorldMatrix = worldMatrices[headBoneNode.nodeIndex]

    val offset = model.lookAtOffsetFromHeadBone
    val eyePos = org.joml.Vector3f(offset)
    headWorldMatrix.transformPosition(eyePos)

    // Use world-space hips Y for scale
    val hipsNode = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HIPS]
    val scale = if (hipsNode != null) {
        val hipsWorldPos = org.joml.Vector3f()
        worldMatrices[hipsNode.nodeIndex].getTranslation(hipsWorldPos)
        val hipsY = hipsWorldPos.y
        if (hipsY > 0f) 1.8f / (hipsY * 2f) else 0.9f
    } else 0.9f

    val eyeHeight = eyePos.y * scale
    VrmMod.logger.info(
        "VRM eye height: {} blocks (eye model Y={}, offset={}, scale={})",
        eyeHeight, eyePos.y, offset, scale,
    )
    return eyeHeight
}
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
fix: use world-space hips Y for scale estimation
```

---

### Task 5: `flipXZ(FloatArray)` に境界チェック追加

配列サイズが3の倍数でない不正VRMファイルでのクラッシュ防止。

**Files:**
- Modify: `common/.../vrm/VrmV0Converter.kt`

- [ ] **Step 1: ループ条件を修正**

`VrmV0Converter.kt` L488 を変更:

```kotlin
// 変更前: while (i < result.size)
// 変更後:
while (i + 2 < result.size) {
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: prevent ArrayIndexOutOfBoundsException in flipXZ for malformed VRM
```

---

### Task 6: sparse accessor の `byteOffset` 適用

glTF 仕様では有効オフセット = `bufferView.byteOffset + accessor.byteOffset`。

**Files:**
- Modify: `common/.../vrm/VrmParser.kt`

- [ ] **Step 1: `readBufferViewFloats` に `accessorByteOffset` パラメータ追加**

```kotlin
private fun readBufferViewFloats(
    bufferViewIndex: Int,
    gltf: GlTF,
    binaryData: ByteBuffer,
    count: Int,
    accessorByteOffset: Int = 0,
): FloatArray {
    val bufferViews = gltf.bufferViews ?: return floatArrayOf()
    val bv = bufferViews.getOrNull(bufferViewIndex) ?: return floatArrayOf()
    val offset = (bv.byteOffset ?: bv.defaultByteOffset() ?: 0) + accessorByteOffset
    val buf = binaryData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
    buf.position(offset)
    val result = FloatArray(count)
    for (i in 0 until count) {
        result[i] = buf.float
    }
    return result
}
```

- [ ] **Step 2: `resolveSparseAccessor` の呼び出し側で byteOffset を渡す**

L320-322 を変更:

```kotlin
val bufferViewIndex = accessor.bufferView
if (bufferViewIndex != null) {
    val accessorByteOffset = accessor.byteOffset ?: accessor.defaultByteOffset() ?: 0
    val baseData = readBufferViewFloats(bufferViewIndex, gltf, binaryData, count * numComponents, accessorByteOffset)
    baseData.copyInto(result)
}
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
fix: apply accessor byteOffset when reading sparse accessor base data
```

---

### Task 7: 一時停止復帰時のアニメーションスキップ防止

`System.nanoTime()` ベースの deltaTime が一時停止後にクランプ上限（0.1s）まで蓄積しアニメーションが飛ぶ。

**Files:**
- Modify: `common/.../animation/AnimationPoseProvider.kt`
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: `AnimationPoseProvider.computePose` の deltaTime 計算を修正**

L66-71 を変更:

```kotlin
val now = System.nanoTime()
val rawDelta = if (lastTimeNano == 0L) {
    1f / 60f
} else {
    (now - lastTimeNano) / 1_000_000_000f
}
lastTimeNano = now
val deltaTime = if (rawDelta > 0.1f) 0f else rawDelta.coerceAtLeast(0.001f)
```

- [ ] **Step 2: `VrmRenderer.render()` の deltaTime も同様に修正**

Task 1 で変更済みの箇所を更に修正:

```kotlin
val now = System.nanoTime()
val rawDelta = if (state.lastRenderTimeNano == 0L) {
    1f / 60f
} else {
    (now - state.lastRenderTimeNano) / 1_000_000_000f
}
state.lastRenderTimeNano = now
val deltaTime = if (rawDelta > 0.1f) 0f else rawDelta.coerceAtLeast(0.001f)
```

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
fix: discard delta time on pause resume to prevent animation skipping
```

---

### Task 8: `VanillaPoseProvider.applySwingAttack` の左右手対応

常に右腕にスイングを適用している。`isOffHandSwing` XOR `isLeftHanded` で正しい腕を選択。

**Files:**
- Modify: `common/.../animation/VanillaPoseProvider.kt`

- [ ] **Step 1: `applySwingAttack` を修正**

```kotlin
private fun applySwingAttack(poses: MutableMap<HumanBone, BonePose>, ctx: PoseContext) {
    val isLeftArm = ctx.isOffHandSwing != ctx.isLeftHanded
    val upperArm = if (isLeftArm) HumanBone.LEFT_UPPER_ARM else HumanBone.RIGHT_UPPER_ARM
    val lowerArm = if (isLeftArm) HumanBone.LEFT_LOWER_ARM else HumanBone.RIGHT_LOWER_ARM

    val existing = poses[upperArm]
    val baseRot = existing?.rotation ?: Quaternionf()
    poses[upperArm] = BonePose(
        rotation = Quaternionf(baseRot).rotateX(-1.2f),
    )
    poses[lowerArm] = BonePose(
        rotation = Quaternionf().rotateX(Math.toRadians(45.0).toFloat()),
    )
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: apply swing attack animation to correct arm based on hand context
```

---

## Group B: 設計改善 (Suggestion → 対処推奨)

### Task 9: `VrmExpression` / `VrmState` の data class + 可変フィールド問題

data class の `copy()` で `@Transient` / `@Volatile` な可変フィールドがリセットされる設計上の罠。`VrmExpression` からランタイム状態を分離し、`VrmState` は通常 class に変更。

**Files:**
- Modify: `common/.../vrm/VrmExpression.kt`
- Modify: `common/.../animation/ExpressionController.kt`
- Modify: `common/.../render/VrmState.kt`

- [ ] **Step 1: `VrmExpression` から `_weight` を除去し、`ExpressionController` に weight マップを持たせる**

`VrmExpression.kt` から以下を削除:
- `_weight` フィールド (L55-56)
- `weight` プロパティ (L58-60)
- `outputWeight` プロパティ (L39-40)
- `overrideBlinkAmount` / `overrideLookAtAmount` / `overrideMouthAmount` プロパティ (L43-52)
- `overrideAmount` メソッド (L62-66)

代わりに、weight を引数として受け取る純粋関数に変更:

```kotlin
data class VrmExpression(
    val name: String,
    val preset: String = "",
    val morphTargetBinds: List<MorphTargetBind> = emptyList(),
    val isBinary: Boolean = false,
    val overrideBlink: ExpressionOverrideType = ExpressionOverrideType.NONE,
    val overrideLookAt: ExpressionOverrideType = ExpressionOverrideType.NONE,
    val overrideMouth: ExpressionOverrideType = ExpressionOverrideType.NONE,
) {
    fun outputWeight(weight: Float): Float =
        if (isBinary) (if (weight > 0.5f) 1.0f else 0.0f) else weight

    fun overrideAmount(type: ExpressionOverrideType, weight: Float): Float = when (type) {
        ExpressionOverrideType.BLOCK -> if (outputWeight(weight) > 0f) 1.0f else 0.0f
        ExpressionOverrideType.BLEND -> outputWeight(weight)
        ExpressionOverrideType.NONE -> 0.0f
    }
}
```

- [ ] **Step 2: `ExpressionController.computeMorphWeights` を weight を外部で管理するよう変更**

```kotlin
fun computeMorphWeights(expressions: List<VrmExpression>): Map<Pair<Int, Int>, Float> {
    // Step 1: Resolve weight for each expression
    val exprWeights = expressions.map { expr ->
        val w = (weights[expr.name] ?: weights[expr.preset] ?: 0f).coerceIn(0f, 1f)
        expr to w
    }

    // Step 2: Calculate weight multipliers
    var blinkMultiplier = 1.0f
    var lookAtMultiplier = 1.0f
    var mouthMultiplier = 1.0f

    for ((expr, w) in exprWeights) {
        blinkMultiplier -= expr.overrideAmount(expr.overrideBlink, w)
        lookAtMultiplier -= expr.overrideAmount(expr.overrideLookAt, w)
        mouthMultiplier -= expr.overrideAmount(expr.overrideMouth, w)
    }

    blinkMultiplier = blinkMultiplier.coerceAtLeast(0f)
    lookAtMultiplier = lookAtMultiplier.coerceAtLeast(0f)
    mouthMultiplier = mouthMultiplier.coerceAtLeast(0f)

    // Step 3 & 4: Apply multipliers and accumulate morph weights
    val result = mutableMapOf<Pair<Int, Int>, Float>()

    for ((expr, w) in exprWeights) {
        var multiplier = 1.0f
        if (expr.name in blinkExpressionNames) multiplier *= blinkMultiplier
        if (expr.name in lookAtExpressionNames) multiplier *= lookAtMultiplier
        if (expr.name in mouthExpressionNames) multiplier *= mouthMultiplier

        var actualWeight = expr.outputWeight(w) * multiplier
        if (expr.isBinary && actualWeight < 1.0f) actualWeight = 0.0f
        if (actualWeight <= 0f) continue

        for (bind in expr.morphTargetBinds) {
            if (bind.nodeIndex < 0) continue
            val key = Pair(bind.nodeIndex, bind.morphTargetIndex)
            result[key] = (result[key] ?: 0f) + bind.weight * actualWeight
        }
    }

    return result
}
```

- [ ] **Step 3: `VrmState` を通常 class に変更**

```kotlin
class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
    val poseProvider: PoseProvider = VanillaPoseProvider(),
    val springBoneSimulator: SpringBoneSimulator? = null,
    val expressionController: ExpressionController = ExpressionController(),
    val animationConfig: AnimationConfig = AnimationConfig(),
    val eyeHeight: Float = 1.62f,
) {
    @Volatile
    var currentEyeOffset: org.joml.Vector3f = org.joml.Vector3f(0f, eyeHeight, 0f)
    @Volatile
    var rightHandMatrix: org.joml.Matrix4f? = null
    @Volatile
    var leftHandMatrix: org.joml.Matrix4f? = null
    @Volatile
    var lastRenderTimeNano: Long = 0L
}
```

- [ ] **Step 4: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 5: コミット**

```
refactor: remove mutable state from VrmExpression data class, make VrmState a regular class
```

---

### Task 10: `VrmPrimitive.equals()` に `alphaMode` 追加

**Files:**
- Modify: `common/.../vrm/VrmMesh.kt`

- [ ] **Step 1: `equals` と `hashCode` に `alphaMode` を追加**

`VrmMesh.kt` L37-58 を修正。`equals` の比較条件に `alphaMode == other.alphaMode` を追加:

```kotlin
override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is VrmPrimitive) return false
    return positions.contentEquals(other.positions) &&
        normals.contentEquals(other.normals) &&
        texCoords.contentEquals(other.texCoords) &&
        joints.contentEquals(other.joints) &&
        weights.contentEquals(other.weights) &&
        indices.contentEquals(other.indices) &&
        vertexCount == other.vertexCount &&
        materialIndex == other.materialIndex &&
        imageIndex == other.imageIndex &&
        alphaMode == other.alphaMode &&
        morphTargets == other.morphTargets
}

override fun hashCode(): Int {
    var result = positions.contentHashCode()
    result = 31 * result + normals.contentHashCode()
    result = 31 * result + indices.contentHashCode()
    result = 31 * result + vertexCount
    result = 31 * result + alphaMode.hashCode()
    return result
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: include alphaMode in VrmPrimitive equals/hashCode
```

---

### Task 11: `VrmTextureManager` の NativeImage エラー時リーク防止

`NativeImage.read()` 成功後、`DynamicTexture` 構築やテクスチャ登録が失敗した場合に NativeImage がリークする。

**Files:**
- Modify: `common/.../render/VrmTextureManager.kt`

- [ ] **Step 1: try-catch で NativeImage をクローズ**

`VrmTextureManager.kt` L37-46 を変更:

```kotlin
registeredTextures.getOrPut(key) {
    val nativeImage = NativeImage.read(ByteArrayInputStream(vrmTexture.imageData))
    try {
        val dynamicTexture = DynamicTexture(nativeImage)
        val location = ResourceLocation.fromNamespaceAndPath(
            VrmMod.MOD_ID,
            "vrm_tex/$playerUUID/$idx",
        )
        textureManager.register(location, dynamicTexture)
        location
    } catch (e: Exception) {
        nativeImage.close()
        throw e
    }
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: close NativeImage on DynamicTexture registration failure
```

---

### Task 12: ThreadLocal のクリーンアップ

`VrmRenderContext.CURRENT_PLAYER_UUID` がレンダリング後にクリアされない。防衛的にクリーンアップを追加。

**Files:**
- Modify: `fabric/.../mixin/LivingEntityRendererMixin.java`
- Modify: `neoforge/.../mixin/LivingEntityRendererMixin.java`

- [ ] **Step 1: 両プラットフォームの `LivingEntityRendererMixin` に RETURN injection 追加**

Fabric 版 (`fabric/.../mixin/LivingEntityRendererMixin.java`) に追加:

```java
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
```

NeoForge 版 (`neoforge/.../mixin/LivingEntityRendererMixin.java`) にも同一のメソッドを追加。

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: clear ThreadLocal player UUID after render to prevent stale state
```

---

### Task 13: Mixin ロジックの共通化

Fabric/NeoForge で完全に同一の `buildPoseContext` メソッドを共通モジュールに抽出。

**Files:**
- Create: `common/.../render/MixinHelper.kt`
- Modify: `fabric/.../mixin/LivingEntityRendererMixin.java`
- Modify: `neoforge/.../mixin/LivingEntityRendererMixin.java`

- [ ] **Step 1: 共通ヘルパーを作成**

```kotlin
// common/.../render/MixinHelper.kt
package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.PoseContext
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.world.InteractionHand

object MixinHelper {
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
}
```

- [ ] **Step 2: 両プラットフォームの Mixin から `buildPoseContext` を削除し、`MixinHelper` を呼ぶ**

両方の `LivingEntityRendererMixin.java` で:

```java
// 変更前:
// PoseContext poseContext = buildPoseContext(renderState);

// 変更後:
PoseContext poseContext = net.narazaka.vrmmod.render.MixinHelper.buildPoseContext(renderState);
```

`private static PoseContext buildPoseContext(...)` メソッドを削除。`InteractionHand` のインポートも不要になれば削除。

- [ ] **Step 3: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 4: コミット**

```
refactor: extract buildPoseContext to common MixinHelper to reduce mixin duplication
```

---

### Task 14: `AnimationConfig.save` のサイレント例外にログ追加

`load` はエラーログを出しているが `save` はサイレント。

**Files:**
- Modify: `common/.../animation/AnimationConfig.kt`

- [ ] **Step 1: `save` と `load` の write-back の catch にログ追加**

`AnimationConfig.kt` L96-97 (load 内の write-back):

```kotlin
// 変更前: } catch (_: Exception) {}
// 変更後:
} catch (e: Exception) {
    VrmMod.logger.warn("Failed to write animation config", e)
}
```

L106-107 (save):

```kotlin
// 変更前: } catch (_: Exception) {}
// 変更後:
} catch (e: Exception) {
    VrmMod.logger.warn("Failed to save animation config", e)
}
```

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
fix: log warning on animation config save failure instead of silently swallowing
```

---

## Group C: 軽微な改善 (Suggestion)

### Task 15: `drawPrimitive` のオブジェクト割り当て削減

頂点ごとに `Vector3f` / `Vector4f` を new しているのを、ループ外で再利用に変更。

**Files:**
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: `drawPrimitive` の skinning/unskinned ブロックで再利用変数を使う**

`drawPrimitive` メソッドの冒頭（L516 `val vertJoints` の付近）に再利用用変数を追加:

```kotlin
val reusablePos = Vector3f()
val reusableNorm = Vector3f()
```

L594 付近の skinVertex 呼び出しを変更:

```kotlin
// 変更前: val skinnedPos = VrmSkinningEngine.skinVertex(Vector3f(px, py, pz), ...)
// 変更後:
reusablePos.set(px, py, pz)
val skinnedPos = VrmSkinningEngine.skinVertex(reusablePos, vertJoints!!, vertWeights!!, skinningMatrices)
```

L604 付近の skinNormal:

```kotlin
// 変更前: val skinnedNormal = VrmSkinningEngine.skinNormal(Vector3f(nx, ny, nz), ...)
// 変更後:
reusableNorm.set(nx, ny, nz)
val skinnedNormal = VrmSkinningEngine.skinNormal(reusableNorm, vertJoints, vertWeights, skinningMatrices)
```

L618 付近の unskinned mesh:

```kotlin
// 変更前: val pos = Vector3f(px, py, pz)
// 変更後:
reusablePos.set(px, py, pz)
nodeWorldMatrix.transformPosition(reusablePos)
px = reusablePos.x; py = reusablePos.y; pz = reusablePos.z
```

L623 付近:

```kotlin
// 変更前: val norm = Vector3f(nx, ny, nz)
// 変更後:
reusableNorm.set(nx, ny, nz)
nodeWorldMatrix.transformDirection(reusableNorm)
val len = reusableNorm.length()
if (len > 1e-6f) reusableNorm.div(len)
nx = reusableNorm.x; ny = reusableNorm.y; nz = reusableNorm.z
```

注: `VrmSkinningEngine.skinVertex` / `skinNormal` は内部で新しい `Vector3f` を返すため、そちらも将来的には out パラメータに変更すべきだが、このタスクでは呼び出し側のみ変更。

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
perf: reuse Vector3f instances in drawPrimitive inner loop
```

---

### Task 16: `data class IndexedPrimitive` / `RenderKey` をトップレベルに移動

`render()` 内で定義されている data class を companion object に移動して、フレームごとのクラスメタデータオーバーヘッドを回避。

**Files:**
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: 両 data class を VrmRenderer の先頭（companion object または private トップレベル）に移動**

`VrmRenderer.kt` の `object VrmRenderer {` 直後に移動:

```kotlin
object VrmRenderer {

    private data class IndexedPrimitive(val meshIndex: Int, val skinIndex: Int, val primitive: VrmPrimitive)
    private data class RenderKey(val texture: ResourceLocation, val alphaMode: AlphaMode)

    // ... 以降既存コード
```

L140 と L167 のインライン定義を削除。L143-166 で `net.narazaka.vrmmod.vrm.VrmPrimitive` 等のFQCN参照は import 済みなので短縮可能な箇所は短縮。

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
refactor: move IndexedPrimitive and RenderKey out of render() method
```

---

### Task 17: `VrmExtensionParser` の壊れた KDoc コメント修正

`parseVector3f` の KDoc が `parseLookAtOffset` の上に誤配置されている。

**Files:**
- Modify: `common/.../vrm/VrmExtensionParser.kt`

- [ ] **Step 1: KDoc を正しい位置に移動**

L241-246 付近。`parseLookAtOffset` の上にある `/** Parses a Vector3f ... */` を削除し、L278 の `private fun parseVector3f` の直前に移動:

```kotlin
/**
 * Parses a Vector3f from either a JSON array [x,y,z] or a JSON object {x,y,z}.
 */
private fun parseVector3f(element: JsonElement?): org.joml.Vector3f {
```

L244 の `parseLookAtOffset` の前にある重複 KDoc ブロック `/** ... */` を削除。

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
docs: fix misplaced KDoc comment for parseVector3f
```

---

### Task 18: `isDescendantOfNode` を効率化（オプション）

現在は O(depth * N) の上向き線形スキャン。キャッシュされているため実害はないが、HEAD からの下向き DFS の方が明快。

**Files:**
- Modify: `common/.../render/VrmRenderer.kt`

- [ ] **Step 1: `collectHeadJointIndices` を DFS ベースに書き換え**

`isDescendantOfNode` を削除し、`collectHeadJointIndices` を直接 DFS で実装:

```kotlin
private fun collectHeadJointIndices(model: VrmModel, skinIndex: Int): Set<Int> {
    val modelId = System.identityHashCode(model)
    if (modelId != headJointCacheModelId) {
        headJointIndicesCache.clear()
        headJointCacheModelId = modelId
    }
    headJointIndicesCache[skinIndex]?.let { return it }

    val headBoneNode = model.humanoid.humanBones[HumanBone.HEAD] ?: return emptySet()
    val skin = model.skeleton.skins.getOrNull(skinIndex) ?: return emptySet()

    // Collect all descendant node indices of HEAD via DFS
    val headDescendants = mutableSetOf<Int>()
    fun dfs(nodeIndex: Int) {
        headDescendants.add(nodeIndex)
        for (child in model.skeleton.nodes.getOrNull(nodeIndex)?.childIndices ?: emptyList()) {
            dfs(child)
        }
    }
    dfs(headBoneNode.nodeIndex)

    // Map to joint indices for this skin
    val result = mutableSetOf<Int>()
    for ((jointIdx, nodeIdx) in skin.jointNodeIndices.withIndex()) {
        if (nodeIdx in headDescendants) {
            result.add(jointIdx)
        }
    }
    headJointIndicesCache[skinIndex] = result
    return result
}
```

`isHeadDescendantNode` も同様に headDescendants を使うよう変更（または `collectHeadJointIndices` で作った headDescendants を再利用するキャッシュ構造にする）:

```kotlin
private var headDescendantNodesCache: Set<Int> = emptySet()

private fun isHeadDescendantNode(model: VrmModel, nodeIndex: Int): Boolean {
    val headBoneNode = model.humanoid.humanBones[HumanBone.HEAD] ?: return false
    // Ensure cache is populated
    val modelId = System.identityHashCode(model)
    if (modelId != headJointCacheModelId || headDescendantNodesCache.isEmpty()) {
        val descendants = mutableSetOf<Int>()
        fun dfs(idx: Int) {
            descendants.add(idx)
            for (child in model.skeleton.nodes.getOrNull(idx)?.childIndices ?: emptyList()) {
                dfs(child)
            }
        }
        dfs(headBoneNode.nodeIndex)
        headDescendantNodesCache = descendants
    }
    return nodeIndex in headDescendantNodesCache
}
```

`isDescendantOfNode` メソッドを削除。

- [ ] **Step 2: ビルド確認**

Run: `./gradlew build`

- [ ] **Step 3: コミット**

```
refactor: replace upward parent scan with downward DFS for head descendant detection
```
