# Animation Mirror (Left-Right Flip) Design

## Overview

The bundled animation set (Quaternius UAL1_Standard) is left-hand dominant — the attack and idle animations swing/hold with the left hand. Minecraft's default main hand is right. This causes a mismatch: items are held in the right hand but the animation moves the left hand.

Solution: Add a per-state `mirror` flag that flips animation poses left ↔ right at playback time. Bundled attack and idle animations are mirrored by default so the right hand becomes the active hand, matching Minecraft's main hand convention.

## Mirror Operation on BonePoseMap

Given a `BonePoseMap` (output of `AnimationClip.sample()`), mirroring performs:

### 1. Swap left/right bone pairs

All left/right bone pairs exchange their poses:

| Left | Right |
|------|-------|
| LEFT_EYE | RIGHT_EYE |
| LEFT_UPPER_LEG | RIGHT_UPPER_LEG |
| LEFT_LOWER_LEG | RIGHT_LOWER_LEG |
| LEFT_FOOT | RIGHT_FOOT |
| LEFT_TOES | RIGHT_TOES |
| LEFT_SHOULDER | RIGHT_SHOULDER |
| LEFT_UPPER_ARM | RIGHT_UPPER_ARM |
| LEFT_LOWER_ARM | RIGHT_LOWER_ARM |
| LEFT_HAND | RIGHT_HAND |
| LEFT_THUMB_METACARPAL | RIGHT_THUMB_METACARPAL |
| LEFT_THUMB_PROXIMAL | RIGHT_THUMB_PROXIMAL |
| LEFT_THUMB_DISTAL | RIGHT_THUMB_DISTAL |
| LEFT_INDEX_PROXIMAL | RIGHT_INDEX_PROXIMAL |
| LEFT_INDEX_INTERMEDIATE | RIGHT_INDEX_INTERMEDIATE |
| LEFT_INDEX_DISTAL | RIGHT_INDEX_DISTAL |
| LEFT_MIDDLE_PROXIMAL | RIGHT_MIDDLE_PROXIMAL |
| LEFT_MIDDLE_INTERMEDIATE | RIGHT_MIDDLE_INTERMEDIATE |
| LEFT_MIDDLE_DISTAL | RIGHT_MIDDLE_DISTAL |
| LEFT_RING_PROXIMAL | RIGHT_RING_PROXIMAL |
| LEFT_RING_INTERMEDIATE | RIGHT_RING_INTERMEDIATE |
| LEFT_RING_DISTAL | RIGHT_RING_DISTAL |
| LEFT_LITTLE_PROXIMAL | RIGHT_LITTLE_PROXIMAL |
| LEFT_LITTLE_INTERMEDIATE | RIGHT_LITTLE_INTERMEDIATE |
| LEFT_LITTLE_DISTAL | RIGHT_LITTLE_DISTAL |

Center bones (HIPS, SPINE, CHEST, UPPER_CHEST, NECK, HEAD, JAW) keep their own key but their pose values are mirrored.

### 2. Mirror translation

For all bones (center and swapped), negate the X component:

```kotlin
Vector3f(-translation.x, translation.y, translation.z)
```

### 3. Mirror rotation

For all bones (center and swapped), negate Y and Z components of the quaternion (mirror across the YZ plane):

```kotlin
Quaternionf(rotation.x, -rotation.y, -rotation.z, rotation.w)
```

## Implementation: mirrorPoses utility function

A standalone function in the animation package:

```kotlin
fun mirrorPoses(poses: BonePoseMap): BonePoseMap {
    val result = mutableMapOf<HumanBone, BonePose>()
    for ((bone, pose) in poses) {
        val targetBone = bone.mirrorPair() ?: bone
        val mirroredPose = BonePose(
            translation = Vector3f(-pose.translation.x, pose.translation.y, pose.translation.z),
            rotation = Quaternionf(pose.rotation.x, -pose.rotation.y, -pose.rotation.z, pose.rotation.w),
        )
        result[targetBone] = mirroredPose
    }
    return result
}
```

`HumanBone.mirrorPair()` returns the opposite-side bone, or null for center bones.

## StateConfig: mirror flag

```kotlin
data class StateConfig(
    val clip: String,
    val loop: Boolean = true,
    val mirror: Boolean = false,
)
```

## AnimationPoseProvider: apply mirror after sampling

In `computePose`, after `clip.sample(currentTime)` and before hips translation scaling:

```kotlin
var poses = clip.sample(currentTime)
if (currentMirror) {
    poses = mirrorPoses(poses)
}
```

`currentMirror` is set alongside `currentStateName` when a state is resolved. The mirror flag is obtained from `config.resolveStateConfig(stateName)?.mirror ?: false`.

## Default states with mirror

Only idle and attack-related states are mirrored (left-hand animations that need to become right-hand):

```kotlin
"move.idle" to StateConfig("Idle_Loop", mirror = true),
"action.swing" to StateConfig("Punch_Jab", loop = false, mirror = true),
"action.swing.mainHand.weapon" to StateConfig("Sword_Attack", loop = false, mirror = true),
"action.swing.mainHand.item" to StateConfig("Interact", loop = false, mirror = true),
"action.swing.offHand.weapon" to StateConfig("Sword_Attack", loop = false),
"action.swing.offHand.item" to StateConfig("Interact", loop = false),
```

Off-hand swing states are NOT mirrored because the bundled animations already swing the left hand, which is the off-hand in MC's right-hand-dominant default.

Walk, sprint, swim, sneak, and other movement states are left-right symmetric, so no mirror needed.

## Files to Modify

| File | Change |
|------|--------|
| `VrmHumanoid.kt` | Add `mirrorPair()` method to `HumanBone` enum |
| `AnimationPoseProvider.kt` | Add `mirrorPoses()` function, track `currentMirror`, apply after sampling |
| `AnimationConfig.kt` | Add `mirror` field to `StateConfig`, update `defaultStates()` |
