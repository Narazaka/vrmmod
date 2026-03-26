# Off-Hand Animation Support Design

## Overview

Extend the hierarchical animation state system to distinguish between main hand and off hand actions. The swinging arm is detected from `HumanoidRenderState.attackArm` / `mainArm`, and continuous item use hand from `useItemHand`. Off-hand item tags are extracted alongside main hand tags for correct weapon/item classification.

## State Tree Changes

### action.swing

```
action.swing
├── mainHand
│   ├── weapon
│   │   ├── swords
│   │   ├── axes
│   │   └── {tag}
│   └── item
├── offHand
│   ├── weapon
│   │   ├── swords
│   │   ├── axes
│   │   └── {tag}
│   └── item
└── (bare-hand fallback: Punch_Jab)
```

### action.useItem

```
action.useItem
├── mainHand
└── offHand
```

## Default States

```json
{
  "action.swing": { "clip": "Punch_Jab", "loop": false },
  "action.swing.mainHand.weapon": { "clip": "Sword_Attack", "loop": false },
  "action.swing.mainHand.item": { "clip": "Interact", "loop": false },
  "action.swing.offHand.weapon": { "clip": "Sword_Attack", "loop": false },
  "action.swing.offHand.item": { "clip": "Interact", "loop": false },
  "action.useItem": { "clip": "Interact", "loop": false },
  "action.useItem.mainHand": { "clip": "Interact", "loop": false },
  "action.useItem.offHand": { "clip": "Interact", "loop": false }
}
```

## Fallback Examples

- Main hand sword swing: `action.swing.mainHand.weapon.swords` → not configured → `action.swing.mainHand.weapon` → Sword_Attack
- Off hand block placement: `action.swing.offHand.item` → Interact
- Bare-hand punch: `action.swing` → Punch_Jab
- Off hand eating: `action.useItem.offHand` → Interact

## PoseContext Changes

Add two fields:

```kotlin
/** Off hand item tags. minecraft: namespace stripped, others preserved. */
val offHandItemTags: List<String> = emptyList(),
/** True when the swinging arm is the off hand. */
val isOffHandSwing: Boolean = false,
/** True when the item being used is in the off hand. */
val isOffHandUse: Boolean = false,
```

## VrmRenderContext Changes

Add one ThreadLocal for off-hand item tags:

```kotlin
@JvmField
val OFF_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
```

`isOffHandSwing` and `isOffHandUse` are derived from `PlayerRenderState` fields in `buildPoseContext`, so no ThreadLocal is needed for those.

## Mixin Changes

### PlayerRendererMixin (Fabric + NeoForge)

Extract off-hand item tags in addition to main hand:

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

### LivingEntityRendererMixin (Fabric + NeoForge)

In `buildPoseContext`, compute off-hand flags from `PlayerRenderState`:

```java
/* offHandItemTags */   VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
/* isOffHandSwing */    renderState.attackArm != renderState.mainArm,
/* isOffHandUse */      renderState.useItemHand != net.minecraft.world.InteractionHand.MAIN_HAND,
```

Note: `ArmedEntityRenderState` has `mainArm` field. `HumanoidRenderState` has `attackArm` and `useItemHand`.

## AnimationPoseProvider Changes

### resolveSwingState

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

### selectClip useItem section

```kotlin
if (context.isUsingItem) {
    val hand = if (context.isOffHandUse) "offHand" else "mainHand"
    return resolveState("action.useItem.$hand") ?: resolveState("action.useItem") ?: selectMovementClip(context)
}
```

## AnimationConfig Changes

### defaultStates()

Replace existing swing/useItem entries:

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

Remove the old `action.swing.weapon` and `action.swing.item` entries (replaced by hand-specific versions).

## Files to Modify

| File | Change |
|------|--------|
| `PoseProvider.kt` | Add `offHandItemTags`, `isOffHandSwing`, `isOffHandUse` |
| `VrmRenderContext.kt` | Add `OFF_HAND_ITEM_TAGS` |
| `AnimationConfig.kt` | Update `defaultStates()` with hand-specific entries |
| `AnimationPoseProvider.kt` | Update `resolveSwingState()` and useItem section in `selectClip()` |
| `PlayerRendererMixin.java` (Fabric) | Extract off-hand item tags |
| `PlayerRendererMixin.java` (NeoForge) | Same |
| `LivingEntityRendererMixin.java` (Fabric) | Pass `offHandItemTags`, `isOffHandSwing`, `isOffHandUse` |
| `LivingEntityRendererMixin.java` (NeoForge) | Same |
