# Held Item Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render Minecraft items in both hands of VRM avatars by using the animated hand bone world matrices and Minecraft's `ItemStackRenderState.render()` API.

**Architecture:** `VrmRenderer.render()` saves RightHand/LeftHand bone world matrices to `VrmState` each frame. A new `renderHeldItems()` method applies the same model transform as VRM mesh rendering, positions items at hand bone locations, and delegates to Minecraft's item rendering. The Mixin calls `renderHeldItems()` after VRM mesh rendering.

**Tech Stack:** Kotlin, Java (Mixin), Minecraft 1.21.4 `ItemStackRenderState` API, JOML Matrix4f

---

### Task 1: VrmState — add hand bone matrix fields

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmState.kt`

- [ ] **Step 1: Add hand matrix fields**

Add two mutable fields after `currentEyeOffset`:

```kotlin
@Volatile
var rightHandMatrix: org.joml.Matrix4f? = null
@Volatile
var leftHandMatrix: org.joml.Matrix4f? = null
```

- [ ] **Step 2: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/render/VrmState.kt
git commit -m "feat: add hand bone matrix fields to VrmState"
```

---

### Task 2: VrmRenderer — extract applyModelTransform, save hand matrices

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt`

- [ ] **Step 1: Extract applyModelTransform function**

Add a new private function before `estimateScale()` (around line 383):

```kotlin
private fun applyModelTransform(poseStack: PoseStack, bodyYawRad: Float, scale: Float) {
    poseStack.mulPose(org.joml.Quaternionf().rotateY(-bodyYawRad))
    poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
    poseStack.scale(1f, 1f, -1f)
    poseStack.scale(scale, scale, scale)
}
```

- [ ] **Step 2: Replace inline transform in render() with applyModelTransform call**

Replace lines 115-125 in `render()`:

```kotlin
        // Body rotation in MC space
        poseStack.mulPose(org.joml.Quaternionf().rotateY(-bodyYawRad))

        // VRM model faces +Z. After Z-flip it faces -Z (north in MC).
        // MC entities face south (+Z) at yaw=0, so rotate 180 degrees,
        // then Z-flip to convert coordinate system.
        poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
        poseStack.scale(1f, 1f, -1f)

        // Scale model to approximately player height (~1.8 blocks).
        poseStack.scale(scale, scale, scale)
```

with:

```kotlin
        applyModelTransform(poseStack, bodyYawRad, scale)
```

- [ ] **Step 3: Save hand bone matrices after worldMatrices computation**

After the line `val worldMatrices = VrmSkinningEngine.computeWorldMatrices(model.skeleton, nodeOverrides)` (line 142), add:

```kotlin
        // Save hand bone world matrices for held item rendering
        val rightHandBone = model.humanoid.humanBones[HumanBone.RIGHT_HAND]
        state.rightHandMatrix = if (rightHandBone != null) Matrix4f(worldMatrices[rightHandBone.nodeIndex]) else null
        val leftHandBone = model.humanoid.humanBones[HumanBone.LEFT_HAND]
        state.leftHandMatrix = if (leftHandBone != null) Matrix4f(worldMatrices[leftHandBone.nodeIndex]) else null
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt
git commit -m "refactor: extract applyModelTransform, save hand bone matrices"
```

---

### Task 3: VrmRenderer — add renderHeldItems method

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt`

- [ ] **Step 1: Add renderHeldItems method**

Add after the `render()` method (after line 216 `}`), before `convertToNodeOverrides`:

```kotlin
/**
 * Renders held items at VRM hand bone positions.
 *
 * Called from the Mixin after VRM mesh rendering. Uses the hand bone
 * world matrices saved during [render] to position items correctly.
 */
fun renderHeldItems(
    state: VrmState,
    rightHandItem: net.minecraft.client.renderer.item.ItemStackRenderState,
    leftHandItem: net.minecraft.client.renderer.item.ItemStackRenderState,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource,
    packedLight: Int,
    bodyYawRad: Float,
) {
    val scale = estimateScale(state)
    renderSingleHandItem(state.rightHandMatrix, rightHandItem, false, poseStack, bufferSource, packedLight, bodyYawRad, scale)
    renderSingleHandItem(state.leftHandMatrix, leftHandItem, true, poseStack, bufferSource, packedLight, bodyYawRad, scale)
}

private fun renderSingleHandItem(
    handMatrix: Matrix4f?,
    itemRenderState: net.minecraft.client.renderer.item.ItemStackRenderState,
    isLeft: Boolean,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource,
    packedLight: Int,
    bodyYawRad: Float,
    scale: Float,
) {
    if (handMatrix == null || itemRenderState.isEmpty) return

    poseStack.pushPose()
    applyModelTransform(poseStack, bodyYawRad, scale)

    // Apply hand bone world matrix
    poseStack.last().pose().mul(handMatrix)
    poseStack.last().normal().mul(Matrix3f(handMatrix))

    // Item orientation adjustments for VRM hand bone coordinate system.
    // VRM hand bones: palm faces -Y, fingers extend along +X (right) / -X (left).
    // Vanilla items expect: held upright, blade/tip pointing up.
    // These values may need tuning in-game.
    poseStack.mulPose(org.joml.Quaternionf().rotateX((-Math.PI / 2).toFloat()))
    poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
    poseStack.translate(
        (if (isLeft) -1 else 1) / 16.0f,
        0.125f,
        -0.625f
    )

    itemRenderState.render(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY)
    poseStack.popPose()
}
```

Note: The rotation/offset values are initial estimates matching vanilla's `ItemInHandLayer`. They will likely need adjustment after in-game testing since VRM hand bone orientation differs from vanilla model bones.

- [ ] **Step 2: Add Matrix3f import if not present**

Ensure `org.joml.Matrix3f` is available. It should already be transitively available via JOML, but add the import at the top of the file if needed:

```kotlin
import org.joml.Matrix3f
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt
git commit -m "feat: add renderHeldItems for VRM hand item rendering"
```

---

### Task 4: Mixin — call renderHeldItems after VRM render

**Files:**
- Modify: `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java`
- Modify: `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`

- [ ] **Step 1: Update Fabric LivingEntityRendererMixin**

In `vrmmod$onRender`, replace:

```java
        PoseContext poseContext = buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        ci.cancel();
```

with:

```java
        PoseContext poseContext = buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        VrmRenderer.INSTANCE.renderHeldItems(
                state,
                renderState.rightHandItem,
                renderState.leftHandItem,
                poseStack,
                bufferSource,
                packedLight,
                (float) Math.toRadians(renderState.bodyRot)
        );
        ci.cancel();
```

- [ ] **Step 2: Update NeoForge LivingEntityRendererMixin**

Same change as Step 1 in `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`.

- [ ] **Step 3: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java
git commit -m "feat: call renderHeldItems from LivingEntityRendererMixin"
```

---

### Task 5: In-game verification and orientation tuning

**Files:** Potentially `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt` (rotation/offset adjustments)

- [ ] **Step 1: Delete old config and launch game**

Delete existing `vrmmod-animations.json` so new defaults are generated. Launch Minecraft with the mod.

- [ ] **Step 2: Verify items appear in hands**

Test with:
1. Sword in main hand — should render near right hand
2. Block in off hand — should render near left hand
3. No items — nothing should render
4. Switching items — should update immediately

- [ ] **Step 3: Adjust rotation and offset if needed**

If items are misoriented (e.g., sword pointing wrong direction, block rotated), adjust the rotation and translate values in `renderSingleHandItem`:

```kotlin
// These are the values to tune:
poseStack.mulPose(org.joml.Quaternionf().rotateX((-Math.PI / 2).toFloat()))
poseStack.mulPose(org.joml.Quaternionf().rotateY(Math.PI.toFloat()))
poseStack.translate(
    (if (isLeft) -1 else 1) / 16.0f,
    0.125f,
    -0.625f
)
```

VRM hand bone coordinate system:
- RightHand: fingers extend +X direction, palm faces -Y
- LeftHand: fingers extend -X direction, palm faces -Y

Vanilla expects items held vertically with blade/tip upward. Adjust rotations to match.

- [ ] **Step 4: Commit final tuned values**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderer.kt
git commit -m "fix: tune held item orientation for VRM hand bones"
```
