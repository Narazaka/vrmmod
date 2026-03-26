# Hierarchical Animation States Design

## Overview

Animation states use dot-separated hierarchical names with automatic fallback. This enables fine-grained customization (per-weapon-type animations) while keeping minimal configuration (unconfigured states fall back to their parent).

Item tags from Minecraft's data-driven tag system are used to automatically detect weapon/tool categories, including mod-added tags.

## State Tree

```
move
├── idle
├── walk
│   ├── forward
│   ├── backward
│   ├── left
│   └── right
├── sprint
│   ├── forward
│   ├── backward
│   ├── left
│   └── right
├── sneak
│   ├── idle
│   └── walk
├── jump
├── swim
│   ├── forward
│   └── idle
├── ride
└── elytra

action
├── swing                          ← bare-hand fallback
│   ├── weapon                     ← weapon/tool fallback
│   │   ├── swords                 ← #minecraft:swords
│   │   ├── axes                   ← #minecraft:axes
│   │   ├── pickaxes               ← #minecraft:pickaxes
│   │   ├── shovels                ← #minecraft:shovels
│   │   ├── hoes                   ← #minecraft:hoes
│   │   └── {mod:tag}              ← mod-added tags
│   └── item                       ← non-weapon item (block placement, potion throw, etc.)
├── useItem                        ← continuous use (eating, bow, shield, etc.)
├── hurt
├── spinAttack
└── death
```

## Fallback Resolution

When resolving a state name, strip the last dot-segment repeatedly until a match is found.

```
action.swing.weapon.swords  →  not configured
action.swing.weapon         →  configured: Sword_Attack  ← use this
```

Implementation:

```kotlin
fun resolveState(stateName: String): String? {
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

## Item Tag Detection

### Data flow

1. `PlayerRendererMixin.extractRenderState()` reads `player.getMainHandItem().getTags()`
2. Tags are converted to strings: `minecraft:` namespace is stripped, other namespaces are preserved
   - `#minecraft:swords` → `"swords"`
   - `#mymod:halberds` → `"mymod:halberds"`
3. Tag list is passed through `VrmRenderContext.MAIN_HAND_ITEM_TAGS` ThreadLocal
4. `PoseContext.mainHandItemTags: List<String>` receives the list

### Weapon classification

`AnimationConfig.weaponTags` defines which tags are considered weapons/tools.

```json
{
  "weaponTags": ["swords", "axes", "pickaxes", "shovels", "hoes"]
}
```

Users add mod weapon tags here (e.g., `"mymod:halberds"`).

### Swing state resolution

When a swing is detected:

1. Intersect `mainHandItemTags` with `weaponTags`
2. If match found: try `action.swing.weapon.{tag}` for each matching tag, pick the one with an existing state definition. Fall back to `action.swing.weapon`.
3. If no match but holding an item (`mainHandItemTags` non-empty): `action.swing.item`
4. If empty hand (`mainHandItemTags` empty): `action.swing`

When multiple weapon tags match and multiple have state definitions, pick the first one that has the most specific (longest) state key.

## PoseContext Changes

Replace `isHoldingWeapon: Boolean` and `isHoldingItem: Boolean` with:

```kotlin
val mainHandItemTags: List<String> = emptyList()
```

## VrmRenderContext Changes

Replace `IS_HOLDING_WEAPON` and `IS_HOLDING_ITEM` with:

```kotlin
@JvmField
val MAIN_HAND_ITEM_TAGS: ThreadLocal<List<String>> = ThreadLocal.withInitial { emptyList() }
```

## AnimationConfig Changes

### New field

```kotlin
val weaponTags: Set<String> = defaultWeaponTags()
```

Default: `setOf("swords", "axes", "pickaxes", "shovels", "hoes")`

### State names

All state keys migrate to hierarchical dot-separated names.

Old → New mapping:

| Old | New |
|-----|-----|
| `idle` | `move.idle` |
| `walk` | `move.walk` |
| `walkBackward` | `move.walk.backward` |
| `walkLeft` | `move.walk.left` |
| `walkRight` | `move.walk.right` |
| `run` | `move.sprint` |
| `jump` | `move.jump` |
| `sneak` | `move.sneak` |
| `sneakWalk` | `move.sneak.walk` |
| `swim` | `move.swim` |
| `swimIdle` | `move.swim.idle` |
| `ride` | `move.ride` |
| `elytra` | `move.elytra` |
| `attack` | `action.swing` |
| `weaponAttack` | `action.swing.weapon` |
| `interact` | `action.swing.item` |
| `useItem` | `action.useItem` |
| `hurt` | `action.hurt` |
| `spinAttack` | `action.spinAttack` |
| `death` | `action.death` |

### Default states

```json
{
  "states": {
    "move.idle": { "clip": "Idle_Loop" },
    "move.walk": { "clip": "Walk_Loop" },
    "move.walk.backward": { "clip": "Walk_Loop" },
    "move.walk.left": { "clip": "Walk_Loop" },
    "move.walk.right": { "clip": "Walk_Loop" },
    "move.sprint": { "clip": "Sprint_Loop" },
    "move.jump": { "clip": "Jump_Loop" },
    "move.sneak": { "clip": "Crouch_Idle_Loop" },
    "move.sneak.walk": { "clip": "Crouch_Fwd_Loop" },
    "move.swim": { "clip": "Swim_Fwd_Loop" },
    "move.swim.idle": { "clip": "Swim_Idle_Loop" },
    "move.ride": { "clip": "Sitting_Idle_Loop" },
    "move.elytra": { "clip": "Swim_Fwd_Loop" },
    "action.swing": { "clip": "Punch_Jab", "loop": false },
    "action.swing.weapon": { "clip": "Sword_Attack", "loop": false },
    "action.swing.item": { "clip": "Interact", "loop": false },
    "action.useItem": { "clip": "Interact", "loop": false },
    "action.hurt": { "clip": "Hit_Chest", "loop": false },
    "action.spinAttack": { "clip": "Roll", "loop": false },
    "action.death": { "clip": "Death01", "loop": false }
  },
  "weaponTags": ["swords", "axes", "pickaxes", "shovels", "hoes"]
}
```

## One-Shot State Detection

Remove the hardcoded set of one-shot state names. Use `StateConfig.loop` field instead:

```kotlin
val currentConfig = resolveStateConfig(currentStateName)
if (currentConfig != null && !currentConfig.loop) {
    // keep playing until clip duration ends
}
```

`resolveStateConfig` uses the same fallback resolution as `resolveState` to find the config.

## Transition Duration

`getTransitionDuration(from, to)` applies the same hierarchical fallback. When looking up `transitions[from][to]`:

1. Try exact `from` and exact `to`
2. Fallback `from` (strip last segment) with exact `to`
3. Continue stripping `from`, then strip `to`
4. Existing wildcard `*` still works as final fallback

## Migration

No explicit migration code. The `load()` merge logic (`defaultStates() + loaded.states`) ensures new hierarchical defaults are available even with an old config file. Old flat keys become inert (never referenced by code). The merged config is written back to disk.

Since the mod is in development, this breaking change to config format is acceptable.

## selectClip Logic

### Action states (priority order)

```kotlin
fun selectClip(context: PoseContext): String {
    // 1. Death
    if (context.deathTime > 0f)
        return resolveState("action.death") ?: selectMovementClip(context)

    // 2. Spin attack (riptide)
    if (context.isAutoSpinAttack)
        return resolveState("action.spinAttack") ?: selectMovementClip(context)

    // 3. Hurt (rising edge)
    if (isHurt && !wasHurt) → resolveState("action.hurt")

    // 4. Swing (rising edge)
    if (isSwinging && !wasSwinging) → resolveState(resolveSwingState(context))

    // 5. One-shot continuation (loop=false, still playing)

    // 6. Continuous item use
    if (context.isUsingItem) → resolveState("action.useItem")

    // 7. Movement
    return selectMovementClip(context)
}
```

### Movement states

```kotlin
fun selectMovementClip(context: PoseContext): String {
    val moveDir = getMovementDirection(context)
    val isMoving = limbSwingAmount > walkThreshold
    val isSprinting = limbSwingAmount > runThreshold

    val state = when {
        isFallFlying          → "move.elytra"
        isSwimming && isMoving → "move.swim.forward"
        isSwimming            → "move.swim.idle"
        isRiding              → "move.ride"
        !isOnGround           → "move.jump"
        isSneaking && isMoving → "move.sneak.walk"
        isSneaking            → "move.sneak.idle"
        isSprinting           → "move.sprint.$moveDir"
        isMoving              → "move.walk.$moveDir"
        else                  → "move.idle"
    }

    return resolveState(state)
        ?: resolveState("move.idle")
        ?: clips.keys.first()
}
```

### Swing state resolution

```kotlin
fun resolveSwingState(context: PoseContext): String {
    val weaponTags = context.mainHandItemTags.filter { it in config.weaponTags }
    if (weaponTags.isNotEmpty()) {
        // Pick tag with most specific state definition
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

## Files to Modify

| File | Change |
|------|--------|
| `PoseProvider.kt` | Replace `isHoldingWeapon`/`isHoldingItem` with `mainHandItemTags` |
| `VrmRenderContext.kt` | Replace `IS_HOLDING_WEAPON`/`IS_HOLDING_ITEM` with `MAIN_HAND_ITEM_TAGS` |
| `AnimationConfig.kt` | Hierarchical state names, `weaponTags` field, `resolveStateConfig()` |
| `AnimationPoseProvider.kt` | `resolveState()` with fallback, rewrite `selectClip`/`selectMovementClip` |
| `PlayerRendererMixin.java` (Fabric) | Extract item tags from entity |
| `PlayerRendererMixin.java` (NeoForge) | Same |
| `LivingEntityRendererMixin.java` (Fabric) | Pass `mainHandItemTags` to PoseContext |
| `LivingEntityRendererMixin.java` (NeoForge) | Same |
