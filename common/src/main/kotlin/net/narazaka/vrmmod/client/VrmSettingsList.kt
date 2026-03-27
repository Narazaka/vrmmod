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

            if (tooltip != null && mouseX in labelX..(labelX + width / 2) && mouseY in top..(top + height)) {
                guiGraphics.renderTooltip(mc.font, tooltip, mouseX, mouseY)
            }
        }

        override fun children(): List<GuiEventListener> = ImmutableList.copyOf(widgets)
        override fun narratables(): List<NarratableEntry> = widgets.filterIsInstance<NarratableEntry>()
    }
}
