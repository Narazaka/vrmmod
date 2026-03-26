# Hierarchical Animation States Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace flat animation state names with dot-separated hierarchical names, add automatic item tag detection, and implement fallback resolution so unconfigured states fall back to their parent.

**Architecture:** States use dot-separated keys (e.g., `move.walk.forward`, `action.swing.weapon.swords`). `resolveState()` strips the last dot-segment repeatedly until a match is found. Item tags from Minecraft's data-driven system are passed through PoseContext to dynamically determine weapon type at swing time.

**Tech Stack:** Kotlin, Java (Mixin), Minecraft 1.21.4 ItemTags API, Gson

---

### Task 1: AnimationConfig — hierarchical state names and weaponTags

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt`

- [ ] **Step 1: Update defaultStates() to hierarchical names**

Replace the entire `defaultStates()` function:

```kotlin
fun defaultStates(): Map<String, StateConfig> = mapOf(
    // Movement
    "move.idle" to StateConfig("Idle_Loop"),
    "move.walk" to StateConfig("Walk_Loop"),
    "move.walk.backward" to StateConfig("Walk_Loop"),
    "move.walk.left" to StateConfig("Walk_Loop"),
    "move.walk.right" to StateConfig("Walk_Loop"),
    "move.sprint" to StateConfig("Sprint_Loop"),
    "move.sprint.backward" to StateConfig("Sprint_Loop"),
    "move.sprint.left" to StateConfig("Sprint_Loop"),
    "move.sprint.right" to StateConfig("Sprint_Loop"),
    "move.jump" to StateConfig("Jump_Loop"),
    "move.sneak" to StateConfig("Crouch_Idle_Loop"),
    "move.sneak.walk" to StateConfig("Crouch_Fwd_Loop"),
    "move.swim" to StateConfig("Swim_Fwd_Loop"),
    "move.swim.idle" to StateConfig("Swim_Idle_Loop"),
    "move.ride" to StateConfig("Sitting_Idle_Loop"),
    "move.elytra" to StateConfig("Swim_Fwd_Loop"),
    // Actions
    "action.swing" to StateConfig("Punch_Jab", loop = false),
    "action.swing.weapon" to StateConfig("Sword_Attack", loop = false),
    "action.swing.item" to StateConfig("Interact", loop = false),
    "action.useItem" to StateConfig("Interact", loop = false),
    "action.hurt" to StateConfig("Hit_Chest", loop = false),
    "action.spinAttack" to StateConfig("Roll", loop = false),
    "action.death" to StateConfig("Death01", loop = false),
)
```

- [ ] **Step 2: Add weaponTags field and defaultWeaponTags()**

Add `weaponTags` parameter to the `AnimationConfig` data class and `defaultWeaponTags()` to companion:

```kotlin
data class AnimationConfig(
    val states: Map<String, StateConfig> = defaultStates(),
    val transitions: Map<String, Map<String, Float>> = defaultTransitions(),
    val weaponTags: Set<String> = defaultWeaponTags(),
    val headTracking: Boolean = true,
    val walkThreshold: Float = 0.01f,
    val runThreshold: Float = 0.5f,
    val damageExpression: Map<String, Float> = mapOf("sad" to 1.0f),
    val damageExpressionDuration: Float = 0.5f,
)
```

```kotlin
fun defaultWeaponTags(): Set<String> = setOf(
    "swords", "axes", "pickaxes", "shovels", "hoes"
)
```

- [ ] **Step 3: Update load() merge to include weaponTags**

In the `load()` function, add `weaponTags` merging:

```kotlin
val merged = if (loaded != null) {
    loaded.copy(
        states = defaultStates() + loaded.states,
        transitions = defaultTransitions() + loaded.transitions,
        weaponTags = defaultWeaponTags() + loaded.weaponTags,
    )
} else {
    AnimationConfig()
}
```

- [ ] **Step 4: Add resolveStateConfig()**

Add a method to resolve a StateConfig using hierarchical fallback:

```kotlin
fun resolveStateConfig(stateName: String): StateConfig? {
    var key = stateName
    while (true) {
        states[key]?.let { return it }
        val dot = key.lastIndexOf('.')
        if (dot < 0) break
        key = key.substring(0, dot)
    }
    return null
}
```

- [ ] **Step 5: Update getTransitionDuration() for hierarchical fallback**

Replace with hierarchical fallback on both `from` and `to`:

```kotlin
fun getTransitionDuration(from: String, to: String): Float {
    var f = from
    while (true) {
        val fromMap = transitions[f]
        if (fromMap != null) {
            var t = to
            while (true) {
                fromMap[t]?.let { return it }
                val dot = t.lastIndexOf('.')
                if (dot < 0) break
                t = t.substring(0, dot)
            }
            fromMap["*"]?.let { return it }
        }
        val dot = f.lastIndexOf('.')
        if (dot < 0) break
        f = f.substring(0, dot)
    }
    // Wildcard fallback
    transitions["*"]?.get(to) ?: transitions["*"]?.get("*")?.let { return it }
    return 0.25f
}
```

- [ ] **Step 6: Build and verify compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL (AnimationPoseProvider still references old state names, but compilation should pass since it only uses string keys)

- [ ] **Step 7: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationConfig.kt
git commit -m "refactor: migrate AnimationConfig to hierarchical state names with weaponTags"
```

---

### Task 2: PoseContext and VrmRenderContext — item tags

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/PoseProvider.kt`
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt`

- [ ] **Step 1: Replace isHoldingWeapon/isHoldingItem with mainHandItemTags in PoseContext**

In `PoseProvider.kt`, replace the Equipment section:

```kotlin
// --- Equipment ---
/** Item tags on the main hand item. minecraft: namespace stripped, others preserved. */
val mainHandItemTags: List<String> = emptyList(),
```

Remove `isHoldingWeapon` and `isHoldingItem`.

- [ ] **Step 2: Replace ThreadLocals in VrmRenderContext**

In `VrmRenderContext.kt`, replace `IS_HOLDING_WEAPON` and `IS_HOLDING_ITEM` with:

```kotlin
@JvmField
val MAIN_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
```

- [ ] **Step 3: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/PoseProvider.kt common/src/main/kotlin/net/narazaka/vrmmod/render/VrmRenderContext.kt
git commit -m "refactor: replace isHoldingWeapon/isHoldingItem with mainHandItemTags"
```

---

### Task 3: Mixin — extract item tags from entity

**Files:**
- Modify: `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java`
- Modify: `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java`
- Modify: `fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java`
- Modify: `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`

- [ ] **Step 1: Update Fabric PlayerRendererMixin**

Replace the item detection block with tag extraction. Remove `ItemTags` import, add `java.util.ArrayList` and `java.util.List`:

```java
package net.narazaka.vrmmod.fabric.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;

import java.util.ArrayList;
import java.util.List;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
        Vec3 pos = player.getPosition(partialTick);
        VrmRenderContext.ENTITY_X.set((float) pos.x);
        VrmRenderContext.ENTITY_Y.set((float) pos.y);
        VrmRenderContext.ENTITY_Z.set((float) pos.z);
        VrmRenderContext.ON_GROUND.set(player.onGround());
        VrmRenderContext.HURT_TIME.set((float) player.hurtTime);
        var mainHandItem = player.getMainHandItem();
        List<String> tags = new ArrayList<>();
        mainHandItem.getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                tags.add(loc.getPath());
            } else {
                tags.add(loc.toString());
            }
        });
        VrmRenderContext.MAIN_HAND_ITEM_TAGS.set(tags);
    }
}
```

- [ ] **Step 2: Update NeoForge PlayerRendererMixin**

Apply the same changes as Step 1 to `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java`. Same code, different package (`net.narazaka.vrmmod.neoforge.mixin`).

- [ ] **Step 3: Update Fabric LivingEntityRendererMixin buildPoseContext**

Replace the `isHoldingWeapon` / `isHoldingItem` lines with `mainHandItemTags`:

```java
                /* mainHandItemTags */  VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
                /* hurtTime */          VrmRenderContext.HURT_TIME.get()
```

Remove the two lines for `isHoldingWeapon` and `isHoldingItem`.

- [ ] **Step 4: Update NeoForge LivingEntityRendererMixin buildPoseContext**

Same change as Step 3 in `neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java`.

- [ ] **Step 5: Build and verify compilation**

Run: `./gradlew build`

Expected: Compilation may fail because `AnimationPoseProvider` still references `isHoldingWeapon`/`isHoldingItem`. This is expected — Task 4 fixes it.

- [ ] **Step 6: Commit (even if build fails — intermediate state)**

```
git add fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/PlayerRendererMixin.java fabric/src/main/java/net/narazaka/vrmmod/fabric/mixin/LivingEntityRendererMixin.java neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/PlayerRendererMixin.java neoforge/src/main/java/net/narazaka/vrmmod/neoforge/mixin/LivingEntityRendererMixin.java
git commit -m "refactor: extract item tags from entity in PlayerRendererMixin"
```

---

### Task 4: AnimationPoseProvider — hierarchical state resolution

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt`

- [ ] **Step 1: Replace tryState with resolveState**

Replace the `tryState` method with hierarchical fallback:

```kotlin
private fun resolveState(stateName: String): String? {
    var key = stateName
    while (true) {
        val clipName = config.states[key]?.clip
        if (clipName != null && clipName.isNotBlank() && clips.containsKey(clipName)) {
            currentStateName = key
            return clipName
        }
        val dot = key.lastIndexOf('.')
        if (dot < 0) break
        key = key.substring(0, dot)
    }
    return null
}
```

- [ ] **Step 2: Add resolveSwingState**

Add a method to determine the swing state from item tags:

```kotlin
private fun resolveSwingState(context: PoseContext): String {
    val weaponTags = context.mainHandItemTags.filter { it in config.weaponTags }
    if (weaponTags.isNotEmpty()) {
        for (tag in weaponTags) {
            if (config.states.containsKey("action.swing.weapon.$tag"))
                return "action.swing.weapon.$tag"
        }
        return "action.swing.weapon"
    }
    if (context.mainHandItemTags.isNotEmpty()) return "action.swing.item"
    return "action.swing"
}
```

- [ ] **Step 3: Rewrite selectClip with hierarchical state names**

Replace the entire `selectClip` method:

```kotlin
private fun selectClip(context: PoseContext): String {
    // Death takes absolute priority
    if (context.deathTime > 0f) {
        return resolveState("action.death") ?: selectMovementClip(context)
    }

    // Spin attack (trident riptide)
    if (context.isAutoSpinAttack) {
        return resolveState("action.spinAttack") ?: selectMovementClip(context)
    }

    // Hurt reaction — rising edge
    val isHurt = context.hurtTime > 0f
    if (isHurt && !wasHurt) {
        wasHurt = true
        resolveState("action.hurt")?.let { return it }
    }
    if (!isHurt) wasHurt = false

    // Swing — rising edge
    if (context.isSwinging && !wasSwinging) {
        wasSwinging = true
        resolveState(resolveSwingState(context))?.let { return it }
    }
    if (!context.isSwinging) wasSwinging = false

    // One-shot continuation: use loop field instead of hardcoded set
    if (currentStateName.isNotEmpty()) {
        val cfg = config.resolveStateConfig(currentStateName)
        if (cfg != null && !cfg.loop) {
            val actionClip = clips[currentClipName]
            if (actionClip != null && currentTime < actionClip.duration) {
                return currentClipName
            }
        }
    }

    // Continuous item use (eating, bow, shield, etc.)
    if (context.isUsingItem) {
        return resolveState("action.useItem") ?: selectMovementClip(context)
    }

    return selectMovementClip(context)
}
```

- [ ] **Step 4: Rewrite selectMovementClip with hierarchical state names**

Replace the entire `selectMovementClip` method:

```kotlin
private fun selectMovementClip(context: PoseContext): String {
    val moveDir = getMovementDirection(context)
    val isMoving = context.limbSwingAmount > config.walkThreshold
    val isSprinting = context.limbSwingAmount > config.runThreshold

    val stateName = when {
        context.isFallFlying -> "move.elytra"
        context.isSwimming && isMoving -> "move.swim.forward"
        context.isSwimming -> "move.swim.idle"
        context.isRiding -> "move.ride"
        !context.isOnGround -> "move.jump"
        context.isSneaking && isMoving -> "move.sneak.walk"
        context.isSneaking -> "move.sneak"
        isSprinting -> "move.sprint.$moveDir"
        isMoving -> "move.walk.$moveDir"
        else -> "move.idle"
    }

    return resolveState(stateName)
        ?: resolveState("move.idle")
        ?: clips.keys.firstOrNull()
        ?: ""
}
```

- [ ] **Step 5: Build and verify full compilation**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/animation/AnimationPoseProvider.kt
git commit -m "feat: implement hierarchical state resolution with item tag detection"
```

---

### Task 5: Delete old config file and verify in-game

**Files:** None (runtime verification)

- [ ] **Step 1: Delete existing config file**

The old `vrmmod-animations.json` has flat state names. Delete it so the new defaults are generated:

```
rm <minecraft-config-dir>/vrmmod-animations.json
```

(Exact path depends on the user's Minecraft instance. Typically `config/vrmmod-animations.json` in the game directory.)

- [ ] **Step 2: Launch game and verify**

Verify in-game:
1. Bare-hand swing → `action.swing` → Punch_Jab
2. Sword swing → `action.swing.weapon.swords` or fallback `action.swing.weapon` → Sword_Attack
3. Axe swing → `action.swing.weapon.axes` or fallback `action.swing.weapon` → Sword_Attack
4. Block placement → `action.swing.item` → Interact
5. Eating/drinking → `action.useItem` → Interact
6. Walking forward → `move.walk.forward` or fallback `move.walk` → Walk_Loop
7. Sprinting → `move.sprint.forward` or fallback `move.sprint` → Sprint_Loop

- [ ] **Step 3: Verify generated config file**

Check that the newly generated `vrmmod-animations.json` contains all hierarchical state names and the `weaponTags` field.

- [ ] **Step 4: Commit all remaining changes**

```
git add -A
git commit -m "feat: complete hierarchical animation state system"
```
