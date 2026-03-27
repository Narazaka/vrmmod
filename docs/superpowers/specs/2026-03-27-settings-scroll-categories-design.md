# Settings Screen Scroll & Categories Design

## Overview

Replace the fixed-position widget layout in `VrmModScreen` settings tab with a scrollable list using Minecraft's `ContainerObjectSelectionList`. Group settings into categories with visual headers.

## Categories

### General
- Model Source
- VRM Model Path
- Animation Directory
- VRMA Animation Use
- First Person Mode

### Display
- Held Item Scale (+Reset button)
- Held Item Offset X/Y/Z (+Reset button)
- Items in First Person
- Items in Third Person

## Architecture

### VrmSettingsList

A new class extending `ContainerObjectSelectionList<VrmSettingsList.Entry>`. Placed in the settings tab area, occupying the space between the tab bar and the bottom buttons.

### Entry Types

1. **CategoryEntry** — Displays a colored category header text (e.g., "General", "Display"). No interactive widget. Left-aligned, bold/colored.

2. **WidgetEntry** — A row with label on the left and widget on the right. Label supports tooltip on hover. Widget can be `EditBox`, `CycleButton`, or `Button`.

3. **CompositeEntry** — A row with label on the left and multiple widgets on the right (e.g., 3 EditBox fields + Reset button for item offset).

### Data Flow

- On init: `buildSettingsWidgets()` creates the `VrmSettingsList`, adds entries for each setting. Widget references are stored as instance fields (same as current) for reading values on save.
- On save: `saveSettings()` reads values from widget references (unchanged from current logic).
- Scroll is handled automatically by `ContainerObjectSelectionList`.

### Layout

```
[Settings tab] [VRoid Hub tab]
┌──────────────────────────────────────┐
│ ── General ──                        │  ← CategoryEntry
│ Model Source        [VRoid Hub    ▼] │  ← WidgetEntry
│ VRM Model Path      [/path/to/vrm ] │
│ Animation Directory  [            ] │
│ VRMA Animation Use   [ON         ▼] │
│ First Person Mode    [VRM body   ▼] │
│ ── Display ──                        │  ← CategoryEntry
│ Held Item Scale      [0.67 ] [Reset] │  ← CompositeEntry
│ Held Item Offset     [X][Y][Z][Reset]│  ← CompositeEntry
│ Items in First Person [ON        ▼] │
│ Items in Third Person [ON        ▼] │
└──────────────────────────────────────┘
[Save]                          [Close]
```

Scrollbar appears automatically when content exceeds visible area.

## VrmModScreen Changes

- Remove manual widget positioning (fixed Y coordinates)
- Remove `settingsRows` and `renderSettingsTooltips` (tooltips handled by entry hover)
- Create `VrmSettingsList` in `buildSettingsWidgets()`
- Add it as a child widget via `addRenderableWidget()`
- Save/Close buttons remain at fixed bottom positions

## Files

| File | Change |
|------|--------|
| Create: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmSettingsList.kt` | New scrollable settings list with entry types |
| Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModScreen.kt` | Replace fixed layout with VrmSettingsList |

## Translation Keys

New keys for category headers:

```json
"vrmmod.config.category.general": "General",
"vrmmod.config.category.display": "Display"
```

`category.general` already exists in en_us.json. Add `category.display` and add Japanese translations.
