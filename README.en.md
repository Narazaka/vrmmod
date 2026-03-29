# VRM Mod

A Minecraft mod that replaces the player model with a VRM avatar. Supports both VRM 1.0 and VRM 0.x formats.

## Supported Platforms

- **Fabric** (Minecraft 1.21.4 / 1.21.1)
- **NeoForge** (Minecraft 1.21.4 / 1.21.1)

## Dependencies

### Fabric

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin)
- [Architectury API](https://modrinth.com/mod/architectury-api)
- [Mod Menu](https://modrinth.com/mod/modmenu) (optional, for settings screen access)

### NeoForge

- [Kotlin for Forge](https://modrinth.com/mod/kotlin-for-forge)
- [Architectury API](https://modrinth.com/mod/architectury-api)

## Installation

Download the JAR for your Minecraft version from [Releases](https://github.com/Narazaka/vrmmod/releases) and place it in your mods folder along with the required dependencies.

- e.g., `vrmmod-fabric-0.1.0+mc1.21.4.jar`, `vrmmod-neoforge-0.1.0+mc1.21.1.jar`
- Files with `-dev-shadow` or `-sources` at the end of the name (e.g., `vrmmod-fabric-0.1.0+mc1.21.4-dev-shadow.jar`) are for development only. Download the one without these suffixes

## VRoid Hub Integration

You can load and use models directly from [VRoid Hub](https://hub.vroid.com/). Authenticate via the VRoid Hub tab in the settings screen to browse and select from your own models or models you've liked.

### Multiplayer Sync

When the server also has this mod installed, each player's VRoid Hub model is visible to other players.

- You must be logged in to VRoid Hub to see other players' models
- Models that do not allow use by other users on VRoid Hub will not be visible to other players
- If the server does not have the mod, only your own model is displayed as usual
- Animations are computed locally on each client. Players performing the same actions will see the same animations, but slight timing and physics differences may occur

## Features

### VRM Avatar Rendering

Renders a local `.vrm` file as the player model.

- Supports both VRM 1.0 and VRM 0.x
- VRM 0.x models are internally converted to VRM 1.0 format and processed through the same rendering pipeline
- Supports OPAQUE / BLEND (translucent) alpha modes

#### Normals

- By default, normals are unified for an unlit-style shading that gives a flat appearance unaffected by Minecraft's light sources
- When Iris Shader custom shaders are detected, model normals are automatically used
- This behavior can be customized in settings

### Animation

Loads `.vrma` animation files and applies them to the VRM model.

- Flexible animation assignment via a hierarchical state system (details below)
- Crossfade transitions between states (customizable duration)
- Built-in default animation clips (Quaternius UAL1 Standard / CC0)
- Head tracking that follows the player's view direction
- Handedness support (Skin Customization > Main Hand)
  - Animations are mirrored to match the selected dominant hand

#### Procedural Animation (Fallback)

When no VRMA files are available or `useVrmaAnimation` is set to `false`, motions are procedurally generated from Minecraft's player animation data. Supports walking, running, jumping, sneaking, swimming, elytra gliding, riding, and attack motions.

### SpringBone

Simulates secondary motion (hair, clothing, etc.) based on the VRM model's SpringBone definitions. Supports sphere and capsule collider collision detection.

### Minecraft Element Rendering

#### Held Items

Renders items (swords, tools, blocks, etc.) in the VRM avatar's hands. Supports both main hand and off hand. Item scale and position can be adjusted in the settings screen.

#### Armor

Armor rendering (helmets, chestplates, etc.) is not currently supported.

### Expression

- **Auto Blink**: Random blinking at 2-4 second intervals
- **Damage Expression**: Displays a specified expression when the player takes damage (expression name, intensity, and fade-out duration are customizable)
- **Expression Override**: Suppresses auto-expressions in the blink/lookAt/mouth categories when emotion expressions (happy, angry, etc.) are active (follows three-vrm VRMExpressionManager behavior)

### First-Person View

Choose from three modes for displaying the VRM model in first-person view:

| Mode | Description |
|------|-------------|
| `VANILLA` | Shows Minecraft's default hands. VRM model is hidden in first person |
| `VRM_MC_CAMERA` | Shows VRM body (head hidden). Camera stays at Minecraft's default position |
| `VRM_VRM_CAMERA` | Shows VRM body (head hidden). Camera position matches VRM eye height |

**Tips for first-person modes:**

- When using `VRM_MC_CAMERA`, enable "Match MC Eye Height" in settings to align the avatar's eye height with MC's camera height for a better experience
- `VRM_VRM_CAMERA` is currently experimental. Camera sway from animations may be noticeable and affect controls
- If held items obstruct your view in first person, you can disable "Show Items in First Person" in settings

## Configuration

Settings can be changed via the **GUI** (mod settings screen) or by directly editing **JSON files**.

- **GUI**: Open from the in-game mod settings (via Mod Menu on Fabric, or the mod list on NeoForge)
- **Keybind**: Assign the unbound "VRM Config" keybind to any key to open the settings screen directly

Basic settings are saved to `config/vrmmod.json`, and animation settings to `config/vrmmod-animations.json`. Changes made via the GUI reload the model immediately.

### Animation Settings (`config/vrmmod-animations.json`)

Allows detailed customization of animation state assignments and transition durations. Placing a file with the same name in `animationDir` gives it priority. This file contains advanced settings for states, transitions, etc. and cannot be edited from the GUI — edit the JSON directly.

### File Structure

```
<minecraft>/config/
├── vrmmod.json                  # Basic settings
└── vrmmod-animations.json       # Animation settings (auto-generated)

<animationDir>/                  # When animationDir is specified
├── vrmmod-animations.json       # Takes priority (optional)
└── *.vrma                       # Animation clips
```

## Hierarchical Animation States

Animation states are managed using dot-separated hierarchical names. Undefined states fall back to their parent hierarchy.

Example: If `action.swing.mainHand.weapon.swords` is not set → uses `action.swing.mainHand.weapon` → if that's also not set → uses `action.swing`

### Movement States

| State | Description | Default Clip |
|-------|-------------|--------------|
| `move.idle` | Standing still | `Idle_Loop` |
| `move.walk` | Walking forward | `Walk_Loop` |
| `move.walk.backward` | Walking backward | `Walk_Loop` |
| `move.walk.left` | Walking left | `Walk_Loop` |
| `move.walk.right` | Walking right | `Walk_Loop` |
| `move.sprint` | Sprinting | `Sprint_Loop` |
| `move.sprint.backward` | Sprinting backward | (fallback: `move.sprint`) |
| `move.sprint.left` | Sprinting left | (fallback: `move.sprint`) |
| `move.sprint.right` | Sprinting right | (fallback: `move.sprint`) |
| `move.jump` | Jumping | `Jump_Loop` |
| `move.sneak` | Sneaking | `Crouch_Idle_Loop` |
| `move.sneak.idle` | Sneaking (still) | `Crouch_Idle_Loop` |
| `move.sneak.walk` | Sneaking (walking) | `Crouch_Fwd_Loop` |
| `move.swim` | Swimming | `Swim_Fwd_Loop` |
| `move.swim.idle` | Treading water | `Swim_Idle_Loop` |
| `move.ride` | Riding | `Sitting_Idle_Loop` |
| `move.elytra` | Elytra gliding | `Swim_Fwd_Loop` |

### Action States

Action states play once (no loop) and return to movement states after completion.

Different animations can be set for main hand and off hand. Item types are automatically detected via Minecraft's item tags, so modded weapons and tools are also supported.

| State | Description | Default Clip |
|-------|-------------|--------------|
| `action.swing` | Unarmed swing | `Punch_Jab` |
| `action.swing.mainHand.weapon` | Main hand weapon/tool attack | `Sword_Attack` |
| `action.swing.mainHand.item` | Main hand item use (placing, etc.) | `Interact` |
| `action.swing.offHand.weapon` | Off hand weapon/tool attack | `Sword_Attack` |
| `action.swing.offHand.item` | Off hand item use (placing, etc.) | `Interact` |
| `action.swing.mainHand.weapon.swords` | Main hand sword attack | (fallback: `.weapon`) |
| `action.swing.mainHand.weapon.axes` | Main hand axe attack | (fallback: `.weapon`) |
| `action.useItem` | Sustained use (eating, bow, etc.) | `Interact` |
| `action.useItem.mainHand` | Main hand sustained use | `Interact` |
| `action.useItem.offHand` | Off hand sustained use | `Interact` |
| `action.hurt` | Taking damage | `Hit_Chest` |
| `action.spinAttack` | Trident spin attack | `Roll` |
| `action.death` | Death | `Death01` |

### Weapon Tags (`weaponTags`)

Defines which item types trigger the `action.swing.*.weapon` states. By default, `swords`, `axes`, `pickaxes`, `shovels`, and `hoes` are configured. You can add tags for modded weapon categories (e.g., `mymod:halberds`).

### Per-State Mirroring (`mirror`)

Setting `mirror: true` on a state mirrors the animation left-to-right. The bundled animations are authored for the left hand, so some states have mirroring enabled by default.

### Default Transitions

| From | To | Duration |
|------|----|----------|
| `move.sprint` | `move.idle` | 0.1s |
| `move.sprint` | `move.walk` | 0.2s |
| `move.walk` | `move.idle` | 0.1s |
| `move.walk` | `move.sprint` | 0.2s |
| `move.jump` | `*` (any) | 0.1s |
| `move.idle` | `move.walk` | 0.15s |
| `move.idle` | `move.sprint` | 0.15s |
| `*` | `*` (fallback) | 0.25s |

Transitions also support hierarchical fallback. When looking up the transition duration from `move.sprint.forward` → `move.walk`, it searches `move.sprint.forward` → `move.sprint` in order.

## Preparing a VRM Model

You can use VRM models created with [VRoid Studio](https://vroid.com/studio) or similar tools. Both VRM 1.0 and VRM 0.x are supported. Set the absolute path to your `.vrm` file in the `localModelPath` setting.

### Preparing VRMA Animations

To use custom `.vrma` animation files, place them in any directory and set `animationDir` to that directory path. Assign clip names to states in the `states` section of `vrmmod-animations.json`.

## License

Source code is released under the [zlib License](LICENSE).

For third-party software and assets, see [THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES).
