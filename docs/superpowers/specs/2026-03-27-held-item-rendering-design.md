# Held Item Rendering Design

## Overview

Render Minecraft items (swords, tools, blocks, etc.) in the VRM avatar's hands. Both main hand and off hand are supported. Items are positioned using the VRM hand bone world matrices computed during the existing animation pipeline, and drawn using Minecraft's `ItemStackRenderState.render()` API.

## Current Problem

`LivingEntityRendererMixin` calls `ci.cancel()`, skipping the entire vanilla `PlayerRenderer.render()` including `PlayerItemInHandLayer`. This means no items are rendered in the player's hands when using a VRM avatar.

## Architecture

### Data Flow

1. `VrmRenderer.render()` computes bone world matrices (with animation + SpringBone applied)
2. RightHand / LeftHand bone matrices are saved to `VrmState`
3. `VrmRenderer.renderHeldItems()` is called from the Mixin after VRM mesh rendering
4. For each hand: apply model-space transform, apply bone matrix, apply item orientation adjustments, call `ItemStackRenderState.render()`

### Item Data Source

`PlayerRenderState` (populated by vanilla's `extractRenderState`) provides:
- `rightHandItem: ItemStackRenderState` — right hand item render data
- `leftHandItem: ItemStackRenderState` — left hand item render data

These are available because `extractRenderState` runs before our `render` injection. No additional data capture is needed.

## VrmState Changes

Add mutable fields for hand bone matrices:

```kotlin
var rightHandMatrix: Matrix4f? = null
var leftHandMatrix: Matrix4f? = null
```

Updated every frame in `VrmRenderer.render()` after SpringBone simulation completes and `worldMatrices` are computed. Null when VRM has no corresponding hand bone.

## VrmRenderer Changes

### Extract model transform into reusable function

The transform currently applied inline in `render()` (bodyYaw rotation, Y 180° rotation, Z-flip, scale) is extracted into a shared function:

```kotlin
private fun applyModelTransform(poseStack: PoseStack, bodyYawRad: Float, scale: Float) {
    poseStack.mulPose(Quaternionf().rotateY(-bodyYawRad))
    poseStack.mulPose(Quaternionf().rotateY(Math.PI.toFloat()))
    poseStack.scale(1f, 1f, -1f)
    poseStack.scale(scale, scale, scale)
}
```

Used by both `render()` (for VRM mesh) and `renderHeldItems()` (for held items).

### Save hand bone matrices in render()

After the final `worldMatrices` computation (post-SpringBone, line 142 area), save hand bone matrices:

```kotlin
val rightHandBone = model.humanoid.humanBones[HumanBone.RIGHT_HAND]
if (rightHandBone != null) {
    state.rightHandMatrix = Matrix4f(worldMatrices[rightHandBone.nodeIndex])
}
val leftHandBone = model.humanoid.humanBones[HumanBone.LEFT_HAND]
if (leftHandBone != null) {
    state.leftHandMatrix = Matrix4f(worldMatrices[leftHandBone.nodeIndex])
}
```

### New renderHeldItems() method

```kotlin
fun renderHeldItems(
    state: VrmState,
    rightHandItem: ItemStackRenderState,
    leftHandItem: ItemStackRenderState,
    poseStack: PoseStack,
    bufferSource: MultiBufferSource,
    packedLight: Int,
    bodyYawRad: Float,
)
```

For each hand (right/left):
1. Skip if `ItemStackRenderState.isEmpty()`
2. Skip if corresponding hand matrix is null
3. `poseStack.pushPose()`
4. `applyModelTransform(poseStack, bodyYawRad, scale)` — same transform as VRM mesh
5. Multiply the hand bone's world matrix onto the poseStack
6. Apply item orientation adjustments (rotation and offset to align item with VRM hand coordinate system)
7. `itemRenderState.render(poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY)`
8. `poseStack.popPose()`

### Item orientation adjustments

Vanilla's `ItemInHandLayer.renderArmWithItem()` applies:
```java
poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
poseStack.translate((isLeft ? -1 : 1) / 16.0F, 0.125F, -0.625F);
```

These values are for the vanilla model's hand bone coordinate system. VRM hand bones have a standardized orientation (palm down, fingers along X axis), so the rotation/offset values will differ. The exact values will be determined during implementation by testing in-game. The offset constants should be extracted as named values for easy tuning.

## Mixin Changes

### LivingEntityRendererMixin (Fabric + NeoForge)

In `vrmmod$onRender`, after `VrmRenderer.render()` and before `ci.cancel()`:

```java
VrmRenderer.INSTANCE.renderHeldItems(
    state,
    renderState.rightHandItem,
    renderState.leftHandItem,
    poseStack,
    bufferSource,
    packedLight,
    (float) Math.toRadians(renderState.bodyRot)
);
```

Import needed: `net.minecraft.client.renderer.item.ItemStackRenderState` (only if type is explicitly referenced; since `renderState.rightHandItem` is already typed, this may not be needed).

## Files to Modify

| File | Change |
|------|--------|
| `VrmState.kt` | Add `rightHandMatrix`, `leftHandMatrix` fields |
| `VrmRenderer.kt` | Extract `applyModelTransform()`, save hand matrices in `render()`, add `renderHeldItems()` |
| `LivingEntityRendererMixin.java` (Fabric) | Call `renderHeldItems()` after VRM render |
| `LivingEntityRendererMixin.java` (NeoForge) | Same |
