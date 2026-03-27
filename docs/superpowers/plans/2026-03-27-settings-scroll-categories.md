# Settings Screen Scroll & Categories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace fixed-position settings layout with a scrollable categorized list using Minecraft's `ContainerObjectSelectionList`.

**Architecture:** Create `VrmSettingsList` extending `ContainerObjectSelectionList` with three entry types (category header, widget row, composite row). `VrmModScreen.buildSettingsWidgets()` creates the list and adds entries. Widget references are stored for save logic.

**Tech Stack:** Kotlin, Minecraft 1.21.4 GUI API (`ContainerObjectSelectionList`, `AbstractWidget`)

---

### Task 1: Create VrmSettingsList

**Files:**
- Create: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmSettingsList.kt`

- [ ] **Step 1: Create VrmSettingsList class with entry types**

```kotlin
package net.narazaka.vrmmod.client

import com.google.common.collect.ImmutableList
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.ContainerObjectSelectionList
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.network.chat.Component

class VrmSettingsList(
    minecraft: Minecraft,
    width: Int,
    height: Int,
    y: Int,
    itemHeight: Int = 25,
) : ContainerObjectSelectionList<VrmSettingsList.Entry>(minecraft, width, height, y, itemHeight) {

    override fun getRowWidth(): Int = width - 30

    fun addCategory(label: Component) {
        addEntry(CategoryEntry(label))
    }

    fun addWidgetRow(label: Component, tooltip: Component?, vararg widgets: AbstractWidget) {
        addEntry(WidgetEntry(label, tooltip, widgets.toList()))
    }

    // --- Entry types ---

    abstract class Entry : ContainerObjectSelectionList.Entry<Entry>()

    class CategoryEntry(private val label: Component) : Entry() {
        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int,
            width: Int, height: Int, mouseX: Int, mouseY: Int,
            hovering: Boolean, partialTick: Float,
        ) {
            guiGraphics.drawString(
                Minecraft.getInstance().font,
                label,
                left,
                top + height / 2 - 4,
                0xFFFF00,
            )
        }

        override fun children(): List<GuiEventListener> = emptyList()
        override fun narratables(): List<NarratableEntry> = emptyList()
    }

    class WidgetEntry(
        private val label: Component,
        private val tooltip: Component?,
        private val widgets: List<AbstractWidget>,
    ) : Entry() {
        override fun render(
            guiGraphics: GuiGraphics, index: Int, top: Int, left: Int,
            width: Int, height: Int, mouseX: Int, mouseY: Int,
            hovering: Boolean, partialTick: Float,
        ) {
            val mc = Minecraft.getInstance()
            val labelX = left
            val labelY = top + height / 2 - 4
            guiGraphics.drawString(mc.font, label, labelX, labelY, 0xFFFFFF)

            // Position widgets in the right half
            val widgetAreaX = left + width / 2
            val widgetAreaWidth = width / 2
            if (widgets.size == 1) {
                val w = widgets[0]
                w.setPosition(widgetAreaX, top)
                w.width = widgetAreaWidth
                w.render(guiGraphics, mouseX, mouseY, partialTick)
            } else {
                val gap = 2
                val totalGap = gap * (widgets.size - 1)
                val perWidget = (widgetAreaWidth - totalGap) / widgets.size
                for ((i, w) in widgets.withIndex()) {
                    w.setPosition(widgetAreaX + i * (perWidget + gap), top)
                    w.width = perWidget
                    w.render(guiGraphics, mouseX, mouseY, partialTick)
                }
            }

            // Tooltip on label hover
            if (tooltip != null && mouseX in labelX..(labelX + width / 2) && mouseY in top..(top + height)) {
                guiGraphics.renderTooltip(mc.font, tooltip, mouseX, mouseY)
            }
        }

        override fun children(): List<GuiEventListener> = ImmutableList.copyOf(widgets)
        override fun narratables(): List<NarratableEntry> = widgets.filterIsInstance<NarratableEntry>()
    }
}
```

- [ ] **Step 2: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/client/VrmSettingsList.kt
git commit -m "feat: create VrmSettingsList with category and widget entry types"
```

---

### Task 2: Refactor VrmModScreen to use VrmSettingsList

**Files:**
- Modify: `common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModScreen.kt`

- [ ] **Step 1: Remove old manual layout fields and replace with VrmSettingsList**

Remove the `settingsRows` field and `SettingsRow` data class.

Replace the entire `buildSettingsWidgets()` method with:

```kotlin
private fun buildSettingsWidgets() {
    val config = VrmModConfig.load(configDir)
    val animConfig = AnimationConfig.load(configDir)
    val tabBarHeight = 24
    val bottomBarHeight = 30
    val fieldWidth = width / 2 - 15

    val list = VrmSettingsList(
        minecraft = minecraft!!,
        width = width,
        height = height - tabBarHeight - bottomBarHeight,
        y = tabBarHeight,
    )

    // === General ===
    list.addCategory(Component.translatable("vrmmod.config.category.general"))

    modelSourceButton = CycleButton.builder<ModelSource> { source ->
        Component.translatable("vrmmod.config.model_source.${source.name.lowercase()}")
    }.withValues(*ModelSource.entries.toTypedArray())
        .withInitialValue(config.modelSource)
        .displayOnlyValue()
        .create(0, 0, fieldWidth, 20, Component.translatable("vrmmod.config.model_source"))
    list.addWidgetRow(
        Component.translatable("vrmmod.config.model_source"),
        Component.translatable("vrmmod.config.model_source.tooltip"),
        modelSourceButton!!,
    )

    modelPathInput = EditBox(font, 0, 0, fieldWidth, 18, Component.translatable("vrmmod.config.model_path")).also {
        it.setMaxLength(1024)
        it.value = config.localModelPath ?: ""
    }
    list.addWidgetRow(
        Component.translatable("vrmmod.config.model_path"),
        Component.translatable("vrmmod.config.model_path.tooltip"),
        modelPathInput!!,
    )

    animDirInput = EditBox(font, 0, 0, fieldWidth, 18, Component.translatable("vrmmod.config.animation_dir")).also {
        it.setMaxLength(1024)
        it.value = config.animationDir ?: ""
    }
    list.addWidgetRow(
        Component.translatable("vrmmod.config.animation_dir"),
        Component.translatable("vrmmod.config.animation_dir.tooltip"),
        animDirInput!!,
    )

    useVrmaToggle = CycleButton.onOffBuilder(config.useVrmaAnimation)
        .displayOnlyValue()
        .create(0, 0, fieldWidth, 20, Component.translatable("vrmmod.config.use_vrma"))
    list.addWidgetRow(
        Component.translatable("vrmmod.config.use_vrma"),
        Component.translatable("vrmmod.config.use_vrma.tooltip"),
        useVrmaToggle!!,
    )

    firstPersonButton = CycleButton.builder<FirstPersonMode> { mode ->
        Component.translatable("vrmmod.config.first_person_mode.${mode.name.lowercase()}")
    }.withValues(*FirstPersonMode.entries.toTypedArray())
        .withInitialValue(config.firstPersonMode)
        .displayOnlyValue()
        .create(0, 0, fieldWidth, 20, Component.translatable("vrmmod.config.first_person_mode"))
    list.addWidgetRow(
        Component.translatable("vrmmod.config.first_person_mode"),
        Component.translatable("vrmmod.config.first_person_mode.tooltip"),
        firstPersonButton!!,
    )

    // === Display ===
    list.addCategory(Component.translatable("vrmmod.config.category.display"))

    heldItemScaleInput = EditBox(font, 0, 0, 80, 18, Component.literal("Scale")).also {
        it.setMaxLength(10)
        it.value = animConfig.heldItemScale.toString()
    }
    val scaleResetButton = Button.builder(Component.translatable("vrmmod.config.reset")) { _ ->
        heldItemScaleInput?.value = "0.67"
    }.bounds(0, 0, 50, 20).build()
    list.addWidgetRow(
        Component.translatable("vrmmod.config.held_item_scale"),
        Component.translatable("vrmmod.config.held_item_scale.tooltip"),
        heldItemScaleInput!!, scaleResetButton,
    )

    val offset = animConfig.heldItemOffset
    heldItemOffsetXInput = EditBox(font, 0, 0, 50, 18, Component.literal("X")).also {
        it.setMaxLength(10)
        it.value = offset.getOrElse(0) { 0f }.toString()
    }
    heldItemOffsetYInput = EditBox(font, 0, 0, 50, 18, Component.literal("Y")).also {
        it.setMaxLength(10)
        it.value = offset.getOrElse(1) { 0.0625f }.toString()
    }
    heldItemOffsetZInput = EditBox(font, 0, 0, 50, 18, Component.literal("Z")).also {
        it.setMaxLength(10)
        it.value = offset.getOrElse(2) { -0.125f }.toString()
    }
    val offsetResetButton = Button.builder(Component.translatable("vrmmod.config.reset")) { _ ->
        heldItemOffsetXInput?.value = "0.0"
        heldItemOffsetYInput?.value = "0.0625"
        heldItemOffsetZInput?.value = "-0.125"
    }.bounds(0, 0, 50, 20).build()
    list.addWidgetRow(
        Component.translatable("vrmmod.config.held_item_offset"),
        Component.translatable("vrmmod.config.held_item_offset.tooltip"),
        heldItemOffsetXInput!!, heldItemOffsetYInput!!, heldItemOffsetZInput!!, offsetResetButton,
    )

    heldItemFirstPersonToggle = CycleButton.onOffBuilder(animConfig.heldItemFirstPerson)
        .displayOnlyValue()
        .create(0, 0, fieldWidth, 20, Component.translatable("vrmmod.config.held_item_first_person"))
    list.addWidgetRow(
        Component.translatable("vrmmod.config.held_item_first_person"),
        Component.translatable("vrmmod.config.held_item_first_person.tooltip"),
        heldItemFirstPersonToggle!!,
    )

    heldItemThirdPersonToggle = CycleButton.onOffBuilder(animConfig.heldItemThirdPerson)
        .displayOnlyValue()
        .create(0, 0, fieldWidth, 20, Component.translatable("vrmmod.config.held_item_third_person"))
    list.addWidgetRow(
        Component.translatable("vrmmod.config.held_item_third_person"),
        Component.translatable("vrmmod.config.held_item_third_person.tooltip"),
        heldItemThirdPersonToggle!!,
    )

    addRenderableWidget(list)

    // Bottom buttons (fixed position, outside scroll list)
    addRenderableWidget(
        Button.builder(Component.translatable("vrmmod.config.save")) { _ -> saveSettings() }
            .bounds(5, height - 26, 100, 20).build()
    )
}
```

- [ ] **Step 2: Remove renderSettings and renderSettingsTooltips**

Remove the `renderSettings` method body (keep an empty method or remove calls):

```kotlin
private fun renderSettings(guiGraphics: GuiGraphics) {
    // Rendered by VrmSettingsList entries
}
```

Remove `renderSettingsTooltips` method entirely, and remove its call from the `render` method. Update `render`:

```kotlin
Tab.SETTINGS -> {
    // List renders itself; no manual rendering needed
}
```

- [ ] **Step 3: Remove SettingsRow data class and settingsRows field**

Delete:
```kotlin
private data class SettingsRow(val labelKey: String, val tooltipKey: String, val y: Int)
private var settingsRows: List<SettingsRow> = emptyList()
```

- [ ] **Step 4: Build and verify**

Run: `./gradlew build`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```
git add common/src/main/kotlin/net/narazaka/vrmmod/client/VrmModScreen.kt
git commit -m "refactor: replace fixed settings layout with scrollable VrmSettingsList"
```

---

### Task 3: Add translation keys for Display category

**Files:**
- Modify: `common/src/main/resources/assets/vrmmod/lang/en_us.json`
- Modify: `common/src/main/resources/assets/vrmmod/lang/ja_jp.json`

- [ ] **Step 1: Add Display category key to en_us.json**

Add after the existing `category.general` line:

```json
"vrmmod.config.category.display": "Display",
```

- [ ] **Step 2: Add Display category key to ja_jp.json**

Add after the existing `category.general` line:

```json
"vrmmod.config.category.display": "表示",
```

- [ ] **Step 3: Commit**

```
git add common/src/main/resources/assets/vrmmod/lang/en_us.json common/src/main/resources/assets/vrmmod/lang/ja_jp.json
git commit -m "feat: add Display category translation key"
```
