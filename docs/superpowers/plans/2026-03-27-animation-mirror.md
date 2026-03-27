# Animation Mirror Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-state left-right mirror flag so bundled left-hand-dominant animations play as right-hand-dominant, matching Minecraft's default main hand.

**Architecture:** `HumanBone.mirrorPair()` provides the left↔right mapping. A `mirrorPoses()` function swaps bone pairs and negates X translation / Y,Z rotation. `StateConfig.mirror` flag controls activation per state. `AnimationPoseProvider` applies mirroring after clip sampling.

**Tech Stack:** Kotlin, JOML Quaternionf/Vector3f

---

### Task 1: HumanBone — add mirrorPair() method

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmHumanoid.kt`

- [ ] **Step 1: Add mirrorPair() to HumanBone enum**

Add a method and companion mapping after the existing `companion object`:

```kotlin
/** Returns the opposite-side bone for left/right pairs, or null for center bones. */
fun mirrorPair(): HumanBone? = MIRROR_PAIRS[this]

companion object {
    private val byVrmName = entries.associateBy { it.vrmName }

    fun fromVrmName(name: String): HumanBone? = byVrmName[name]

    private val MIRROR_PAIRS: Map<HumanBone, HumanBone> = buildMap {
        fun pair(a: HumanBone, b: HumanBone) { put(a, b); put(b, a) }
        pair(LEFT_EYE, RIGHT_EYE)
        pair(LEFT_UPPER_LEG, RIGHT_UPPER_LEG)
        pair(LEFT_LOWER_LEG, RIGHT_LOWER_LEG)
        pair(LEFT_FOOT, RIGHT_FOOT)
        pair(LEFT_TOES, RIGHT_TOES)
        pair(LEFT_SHOULDER, RIGHT_SHOULDER)
        pair(LEFT_UPPER_ARM, RIGHT_UPPER_ARM)
        pair(LEFT_LOWER_ARM, RIGHT_LOWER_ARM)
        pair(LEFT_HAND, RIGHT_HAND)
        pair(LEFT_THUMB_METACARPAL, RIGHT_THUMB_METACARPAL)
        pair(LEFT_THUMB_PROXIMAL, RIGHT_THUMB_PROXIMAL)
        pair(LEFT_THUMB_DISTAL, RIGHT_THUMB_DISTAL)
        pair(LEFT_INDEX_PROXIMAL, RIGHT_INDEX_PROXIMAL)
        pair(LEFT_INDEX_INTERMEDIATE, RIGHT_INDEX_INTERMEDIATE)
        pair(LEFT_INDEX_DISTAL, RIGHT_INDEX_DISTAL)
        pair(LEFT_MIDDLE_PROXIMAL, RIGHT_MIDDLE_PROXIMAL)
        pair(LEFT_MIDDLE_INTERMEDIATE, RIGHT_MIDDLE_INTERMEDIATE)
        pair(LEFT_MIDDLE_DISTAL, RIGHT_MIDDLE_DISTAL)
        pair(LEFT_RING_PROXIMAL, RIGHT_RING_PROXIMAL)
        pair(LEFT_RING_INTERMEDIATE, RIGHT_RING_INTERMEDIATE)
        pair(LEFT_RING_DISTAL, RIGHT_RING_DISTAL)
        pair(LEFT_LITTLE_PROXIMAL, RIGHT_LITTLE_PROXIMAL)
        pair(LEFT_LITTLE_INTERMEDIATE, RIGHT_LITTLE_INTERMEDIATE)
        pair(LEFT_LITTLE_DISTAL, RIGHT_LITTLE_DISTAL)
    }
}
```

Note: The existing `companion object` already has `byVrmName` and `fromVrmName`. Merge `MIRROR_PAIRS` into the existing companion object — do NOT create a second one.

- [ ] **Step 2: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/vrm/VrmHumanoid.kt
git commit -m "feat: add mirrorPair() to HumanBone for left-right bone mapping"
```

---

### Task 2: mirrorPoses utility function

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt`

- [ ] **Step 1: Add mirrorPoses function**

Add as a top-level private function at the end of the file (or as a companion method):

```kotlin
/**
 * Mirrors a BonePoseMap left ↔ right.
 * - Swaps left/right bone pairs
 * - Negates X translation (mirror across YZ plane)
 * - Negates Y and Z rotation quaternion components (mirror rotation)
 */
private fun mirrorPoses(poses: BonePoseMap): BonePoseMap {
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

- [ ] **Step 2: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt
git commit -m "feat: add mirrorPoses utility for left-right animation flipping"
```

---

### Task 3: StateConfig mirror flag and default states

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt`

- [ ] **Step 1: Add mirror field to StateConfig**

Change:

```kotlin
data class StateConfig(
    val clip: String,
    val loop: Boolean = true,
)
```

to:

```kotlin
data class StateConfig(
    val clip: String,
    val loop: Boolean = true,
    val mirror: Boolean = false,
)
```

- [ ] **Step 2: Update defaultStates() with mirror flags**

Set `mirror = true` on idle and main-hand attack states. Replace the current action states section:

```kotlin
            "action.swing" to StateConfig("Punch_Jab", loop = false, mirror = true),
            "action.swing.mainHand.weapon" to StateConfig("Sword_Attack", loop = false, mirror = true),
            "action.swing.mainHand.item" to StateConfig("Interact", loop = false, mirror = true),
            "action.swing.offHand.weapon" to StateConfig("Sword_Attack", loop = false),
            "action.swing.offHand.item" to StateConfig("Interact", loop = false),
```

And update idle:

```kotlin
            "move.idle" to StateConfig("Idle_Loop", mirror = true),
```

All other states remain unchanged (symmetric animations don't need mirroring).

- [ ] **Step 3: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt
git commit -m "feat: add mirror flag to StateConfig, enable for idle and mainHand attacks"
```

---

### Task 4: AnimationPoseProvider — apply mirror after sampling

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt`

- [ ] **Step 1: Add currentMirror state field**

Add after the existing `private var forceClipRestart = false` (around line 40):

```kotlin
private var currentMirror = false
```

- [ ] **Step 2: Update resolveState to track mirror flag**

Replace the `resolveState` method:

```kotlin
private fun resolveState(stateName: String): String? {
    var key = stateName
    while (true) {
        val clipName = config.states[key]?.clip
        if (clipName != null && clipName.isNotBlank() && clips.containsKey(clipName)) {
            currentStateName = key
            currentMirror = config.states[key]?.mirror ?: false
            return clipName
        }
        val dot = key.lastIndexOf('.')
        if (dot < 0) break
        key = key.substring(0, dot)
    }
    return null
}
```

Note: The mirror flag is read from the **exact key that matched**, not from fallback resolution. This ensures that if `action.swing.mainHand.weapon.swords` falls back to `action.swing.mainHand.weapon`, the mirror flag from `action.swing.mainHand.weapon` is used.

- [ ] **Step 3: Apply mirrorPoses in computePose**

In `computePose`, after the line `var poses = clip.sample(currentTime)` (around line 79), add mirror application:

```kotlin
        var poses = clip.sample(currentTime)
        if (currentMirror) {
            poses = mirrorPoses(poses)
        }
        poses = scaleHipsTranslation(poses, clip) ?: poses
```

Also apply mirroring to the snapshot for cross-fade blending. In the cross-fade setup block (around line 64), after `prevPose = currentClip.sample(currentTime)`:

```kotlin
            if (currentClip != null) {
                prevPose = currentClip.sample(currentTime)
                if (currentMirror) {
                    prevPose = mirrorPoses(prevPose)
                }
                scaleHipsTranslation(prevPose, currentClip)?.let { prevPose = it }
            }
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt
git commit -m "feat: apply animation mirror based on StateConfig.mirror flag"
```
