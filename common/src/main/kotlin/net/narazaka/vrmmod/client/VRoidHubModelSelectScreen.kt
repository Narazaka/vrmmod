package net.narazaka.vrmmod.client

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.ObjectSelectionList
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.narazaka.vrmmod.vroidhub.CharacterModel
import net.narazaka.vrmmod.vroidhub.CharacterModelLicense

/**
 * Custom Screen for selecting a VRoid Hub model.
 * Shows a scrollable list of models on the left and license details on the right.
 */
class VRoidHubModelSelectScreen(
    private val parent: Screen?,
    private val models: List<CharacterModel>,
    private val onSelect: (CharacterModel) -> Unit,
) : Screen(Component.literal("VRoid Hub - Select Model")) {

    private var modelList: ModelListWidget? = null
    private var selectButton: Button? = null
    private var selectedModel: CharacterModel? = null

    override fun init() {
        val listWidth = width / 2 - 10

        // Model list (left side)
        modelList = ModelListWidget(minecraft!!, listWidth, height - 64, 32, 24).also {
            it.x = 5
            for (model in models.filter { it.is_downloadable }) {
                it.addEntry(ModelEntry(it, model))
            }
            addRenderableWidget(it)
        }

        // Select button (bottom)
        selectButton = Button.builder(Component.literal("Use this model (agree to license)")) { _ ->
            selectedModel?.let { onSelect(it) }
            onClose()
        }.bounds(width / 2 - 150, height - 28, 150, 20).build().also {
            it.active = false
            addRenderableWidget(it)
        }

        // Cancel button
        addRenderableWidget(
            Button.builder(Component.literal("Cancel")) { _ -> onClose() }
                .bounds(width / 2 + 10, height - 28, 80, 20)
                .build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)

        // Right side: selected model details + license
        val detailX = width / 2 + 5
        val detailWidth = width / 2 - 10
        val model = selectedModel
        if (model != null) {
            var y = 36
            val lineHeight = 11

            // Model name
            guiGraphics.drawString(font, model.character?.name ?: model.name ?: "Unknown", detailX, y, 0xFFFFFF)
            y += lineHeight

            // Author
            val author = model.character?.user?.name ?: ""
            if (author.isNotBlank()) {
                guiGraphics.drawString(font, "by $author", detailX, y, 0xAAAAAA)
                y += lineHeight
            }

            // Spec version
            model.latest_character_model_version?.spec_version?.let { ver ->
                guiGraphics.drawString(font, "VRM $ver", detailX, y, 0x888888)
                y += lineHeight
            }

            y += 6

            // License
            val license = model.license
            if (license != null) {
                guiGraphics.drawString(font, "--- License ---", detailX, y, 0xFFFF00)
                y += lineHeight
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Avatar use", license.characterization_allowed_user)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Violence", license.violent_expression)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Sexual", license.sexual_expression)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Corp. commercial", license.corporate_commercial_use)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Personal commercial", license.personal_commercial_use)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Modification", license.modification)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Redistribution", license.redistribution)
                y = drawLicenseField(guiGraphics, detailX, y, lineHeight, "Credit", license.credit)
            } else {
                guiGraphics.drawString(font, "No license info", detailX, y, 0xFF6666)
            }
        } else {
            guiGraphics.drawString(font, "Select a model from the list", detailX, 36, 0x888888)
        }
    }

    private fun drawLicenseField(guiGraphics: GuiGraphics, x: Int, y: Int, lineHeight: Int, label: String, value: String): Int {
        val color = when (value) {
            "allow", "everyone", "unnecessary" -> 0x66FF66
            "disallow", "author" -> 0xFF6666
            else -> 0xCCCCCC
        }
        guiGraphics.drawString(font, "$label: $value", x, y, color)
        return y + lineHeight
    }

    fun onModelSelected(model: CharacterModel) {
        selectedModel = model
        selectButton?.active = true
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    // ---- Inner classes for the model list ----

    inner class ModelListWidget(
        minecraft: net.minecraft.client.Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
    ) : ObjectSelectionList<ModelEntry>(minecraft, width, height, y, itemHeight) {

        public override fun addEntry(entry: ModelEntry): Int = super.addEntry(entry)

        override fun getRowWidth(): Int = width - 12
    }

    inner class ModelEntry(
        private val list: ModelListWidget,
        val model: CharacterModel,
    ) : ObjectSelectionList.Entry<ModelEntry>() {

        private val displayName = model.character?.name ?: model.name ?: "Unknown"
        private val authorName = model.character?.user?.name ?: ""

        override fun render(
            guiGraphics: GuiGraphics,
            index: Int,
            top: Int,
            left: Int,
            width: Int,
            height: Int,
            mouseX: Int,
            mouseY: Int,
            hovering: Boolean,
            partialTick: Float,
        ) {
            guiGraphics.drawString(font, displayName, left + 2, top + 2, 0xFFFFFF)
            if (authorName.isNotBlank()) {
                guiGraphics.drawString(font, authorName, left + 2, top + 12, 0x888888)
            }
        }

        override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            list.setSelected(this)
            onModelSelected(model)
            return true
        }

        override fun getNarration(): Component = Component.literal(displayName)
    }
}
