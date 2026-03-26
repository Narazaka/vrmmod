package net.narazaka.vrmmod.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.narazaka.vrmmod.vroidhub.VRoidHubConfig
import net.minecraft.client.Minecraft

/**
 * Launcher screen for VRM Mod. Provides access to Settings and VRoid Hub.
 */
class VrmMenuScreen(private val parent: Screen?) : Screen(Component.translatable("vrmmod.menu.title")) {

    override fun init() {
        val buttonWidth = 200
        val buttonHeight = 20
        val centerX = width / 2 - buttonWidth / 2
        val startY = height / 2 - 30

        // Settings button
        addRenderableWidget(
            Button.builder(Component.translatable("vrmmod.menu.settings")) { _ ->
                minecraft?.setScreen(VrmConfigScreen.create(this))
            }.bounds(centerX, startY, buttonWidth, buttonHeight).build()
        )

        // VRoid Hub button (only if configured)
        val configDir = Minecraft.getInstance().gameDirectory.resolve("config").toPath()
        val vroidConfig = VRoidHubConfig.load(configDir)
        if (vroidConfig.isAvailable) {
            addRenderableWidget(
                Button.builder(Component.translatable("vrmmod.menu.vroidhub")) { _ ->
                    minecraft?.setScreen(VRoidHubScreen(this))
                }.bounds(centerX, startY + 26, buttonWidth, buttonHeight).build()
            )
        }

        // Close
        addRenderableWidget(
            Button.builder(Component.translatable("vrmmod.vroidhub.close")) { _ ->
                onClose()
            }.bounds(centerX, startY + 60, buttonWidth, buttonHeight).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)
        guiGraphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF)
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }
}
