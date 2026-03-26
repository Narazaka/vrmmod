# Off-Hand Animation Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the animation state system to distinguish main hand vs off hand for swing and useItem actions, using off-hand item tags for correct weapon/item classification.

**Architecture:** Add `offHandItemTags`, `isOffHandSwing`, `isOffHandUse` to PoseContext. Extract off-hand tags in PlayerRendererMixin, derive swing/use hand from PlayerRenderState fields (`attackArm`/`mainArm`/`useItemHand`). Update `resolveSwingState` and `selectClip` to build hand-specific state names (`action.swing.mainHand.weapon`, `action.swing.offHand.item`, etc.).

**Tech Stack:** Kotlin, Java (Mixin), Minecraft 1.21.4 HumanoidRenderState API

---

### Task 1: PoseContext and VrmRenderContext — off-hand fields

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/PoseProvider.kt`
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt`

- [ ] **Step 1: Add off-hand fields to PoseContext**

In `PoseProvider.kt`, replace the Equipment section (lines 72-74):

```kotlin
// --- Equipment ---
/** Item tags on the main hand item. minecraft: namespace stripped, others preserved. */
val mainHandItemTags: List<String> = emptyList(),
/** Item tags on the off hand item. minecraft: namespace stripped, others preserved. */
val offHandItemTags: List<String> = emptyList(),
/** True when the swinging arm is the off hand. */
val isOffHandSwing: Boolean = false,
/** True when the item being continuously used is in the off hand. */
val isOffHandUse: Boolean = false,
```

- [ ] **Step 2: Add OFF_HAND_ITEM_TAGS to VrmRenderContext**

In `VrmRenderContext.kt`, add after `MAIN_HAND_ITEM_TAGS` (line 24):

```kotlin
@JvmField
val OFF_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
```

- [ ] **Step 3: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/PoseProvider.kt common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt
git commit -m "feat: add off-hand fields to PoseContext and VrmRenderContext"
```

---

### Task 2: Mixin — extract off-hand tags and hand detection

**Files:**
- Modify: `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java`
- Modify: `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java`
- Modify: `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java`
- Modify: `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`

- [ ] **Step 1: Update Fabric PlayerRendererMixin**

After the existing main hand tag extraction block (line 40), add off-hand extraction:

```java
        var offHandItem = player.getOffhandItem();
        List<String> offTags = new ArrayList<>();
        offHandItem.getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                offTags.add(loc.getPath());
            } else {
                offTags.add(loc.toString());
            }
        });
        VrmRenderContext.OFF_HAND_ITEM_TAGS.set(offTags);
```

- [ ] **Step 2: Update NeoForge PlayerRendererMixin**

Same change as Step 1 in `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java`.

- [ ] **Step 3: Update Fabric LivingEntityRendererMixin buildPoseContext**

Add an import at the top of the file:

```java
import net.minecraft.world.InteractionHand;
```

In `buildPoseContext`, replace:

```java
                /* mainHandItemTags */  VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
                /* hurtTime */          VrmRenderContext.HURT_TIME.get()
```

with:

```java
                /* mainHandItemTags */  VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
                /* offHandItemTags */   VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
                /* isOffHandSwing */    renderState.attackArm != renderState.mainArm,
                /* isOffHandUse */      renderState.useItemHand != InteractionHand.MAIN_HAND,
                /* hurtTime */          VrmRenderContext.HURT_TIME.get()
```

- [ ] **Step 4: Update NeoForge LivingEntityRendererMixin buildPoseContext**

Same changes as Step 3 in `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`.

- [ ] **Step 5: Commit**

```
git add fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java
git commit -m "feat: extract off-hand item tags and hand detection in mixins"
```

---

### Task 3: AnimationConfig — hand-specific default states

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt`

- [ ] **Step 1: Update defaultStates() with hand-specific entries**

In `defaultStates()`, replace lines 110-113:

```kotlin
            "action.swing" to StateConfig("Punch_Jab", loop = false),
            "action.swing.weapon" to StateConfig("Sword_Attack", loop = false),
            "action.swing.item" to StateConfig("Interact", loop = false),
            "action.useItem" to StateConfig("Interact", loop = false),
```

with:

```kotlin
            "action.swing" to StateConfig("Punch_Jab", loop = false),
            "action.swing.mainHand.weapon" to StateConfig("Sword_Attack", loop = false),
            "action.swing.mainHand.item" to StateConfig("Interact", loop = false),
            "action.swing.offHand.weapon" to StateConfig("Sword_Attack", loop = false),
            "action.swing.offHand.item" to StateConfig("Interact", loop = false),
            "action.useItem" to StateConfig("Interact", loop = false),
            "action.useItem.mainHand" to StateConfig("Interact", loop = false),
            "action.useItem.offHand" to StateConfig("Interact", loop = false),
```

- [ ] **Step 2: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt
git commit -m "feat: add hand-specific default animation states"
```

---

### Task 4: AnimationPoseProvider — hand-aware state resolution

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt`

- [ ] **Step 1: Update resolveSwingState to use hand and correct item tags**

Replace the `resolveSwingState` method (lines 277-288):

```kotlin
private fun resolveSwingState(context: PoseContext): String {
    val hand = if (context.isOffHandSwing) "offHand" else "mainHand"
    val itemTags = if (context.isOffHandSwing) context.offHandItemTags else context.mainHandItemTags
    val weaponTags = itemTags.filter { it in config.weaponTags }
    if (weaponTags.isNotEmpty()) {
        for (tag in weaponTags) {
            if (config.states.containsKey("action.swing.$hand.weapon.$tag"))
                return "action.swing.$hand.weapon.$tag"
        }
        return "action.swing.$hand.weapon"
    }
    if (itemTags.isNotEmpty()) return "action.swing.$hand.item"
    return "action.swing"
}
```

- [ ] **Step 2: Update useItem section in selectClip**

Replace lines 254-257:

```kotlin
        // Continuous item use (eating, bow, shield, etc.)
        if (context.isUsingItem) {
            return resolveState("action.useItem") ?: selectMovementClip(context)
        }
```

with:

```kotlin
        // Continuous item use (eating, bow, shield, etc.)
        if (context.isUsingItem) {
            val hand = if (context.isOffHandUse) "offHand" else "mainHand"
            return resolveState("action.useItem.$hand")
                ?: resolveState("action.useItem")
                ?: selectMovementClip(context)
        }
```

- [ ] **Step 3: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt
git commit -m "feat: hand-aware swing and useItem state resolution"
```
