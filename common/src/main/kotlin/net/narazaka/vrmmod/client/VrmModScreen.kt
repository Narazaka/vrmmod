package net.narazaka.vrmmod.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.CycleButton
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.render.VrmPlayerManager
import net.narazaka.vrmmod.vroidhub.*
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Main Screen for VRM Mod with tabbed interface.
 * Tab 1: Settings (model path, animation, first-person mode)
 * Tab 2: VRoid Hub (login, model selection, license display)
 */
class VrmModScreen(private val parent: Screen?) : Screen(Component.translatable("vrmmod.menu.title")) {

    private enum class Tab { SETTINGS, VROID_HUB }

    private enum class VRoidHubState {
        LOADING, NOT_CONFIGURED, LOGIN, LOGGING_IN, LOGGED_IN, LOADING_MODELS, ERROR,
    }

    // --- Tab state ---
    private var activeTab = Tab.SETTINGS

    // --- Settings state ---
    private var modelPathInput: EditBox? = null
    private var animDirInput: EditBox? = null
    private var useVrmaToggle: CycleButton<Boolean>? = null
    private var firstPersonButton: CycleButton<FirstPersonMode>? = null
    private var modelSourceButton: CycleButton<ModelSource>? = null

    // --- VRoid Hub state ---
    private var vroidHubState = VRoidHubState.LOADING
    private var errorMessage = ""
    private var userName = ""
    private var vroidConfig = VRoidHubConfig()
    private var authSession: AuthSession? = null
    private var codeInput: EditBox? = null
    private var heartModels: List<CharacterModel> = emptyList()
    private var myModels: List<CharacterModel> = emptyList()
    private var selectedModel: CharacterModel? = null
    private var scrollOffset = 0
    private var detailScrollOffset = 0
    private var detailTotalLines = 0
    private var detailVisibleLines = 0
    private var useModelButton: Button? = null
    private var loginUrl = ""
    private var vroidHubInitialized = false

    private val configDir get() = Minecraft.getInstance().gameDirectory.resolve("config")

    override fun init() {
        buildWidgets()
    }

    private fun buildWidgets() {
        clearWidgets()
        modelPathInput = null; animDirInput = null; codeInput = null; useModelButton = null

        // --- Tab bar ---
        val tabY = 4
        addRenderableWidget(
            Button.builder(Component.translatable("vrmmod.menu.settings")) { _ ->
                if (activeTab != Tab.SETTINGS) { activeTab = Tab.SETTINGS; buildWidgets() }
            }.bounds(5, tabY, 80, 16).build().also { it.active = activeTab != Tab.SETTINGS }
        )
        vroidConfig = VRoidHubConfig.load(configDir.toPath())
        if (vroidConfig.isAvailable) {
            addRenderableWidget(
                Button.builder(Component.translatable("vrmmod.menu.vroidhub")) { _ ->
                    if (activeTab != Tab.VROID_HUB) {
                        activeTab = Tab.VROID_HUB
                        if (!vroidHubInitialized) initVRoidHub() else buildWidgets()
                    }
                }.bounds(90, tabY, 80, 16).build().also { it.active = activeTab != Tab.VROID_HUB }
            )
        }

        // --- Close button ---
        addRenderableWidget(
            Button.builder(Component.translatable("vrmmod.vroidhub.close")) { _ -> onClose() }
                .bounds(width - 65, height - 26, 60, 20).build()
        )

        when (activeTab) {
            Tab.SETTINGS -> buildSettingsWidgets()
            Tab.VROID_HUB -> buildVRoidHubWidgets()
        }
    }

    // ========================================================================
    // Settings Tab
    // ========================================================================

    /** Label → tooltip key mapping for settings */
    private data class SettingsRow(val labelKey: String, val tooltipKey: String, val y: Int)
    private var settingsRows: List<SettingsRow> = emptyList()

    private fun buildSettingsWidgets() {
        val config = VrmModConfig.load(configDir)
        val contentTop = 30
        val rowHeight = 26
        val fieldX = width / 2
        val fieldWidth = width / 2 - 15

        settingsRows = listOf(
            SettingsRow("vrmmod.config.model_source", "vrmmod.config.model_source.tooltip", contentTop),
            SettingsRow("vrmmod.config.model_path", "vrmmod.config.model_path.tooltip", contentTop + rowHeight),
            SettingsRow("vrmmod.config.animation_dir", "vrmmod.config.animation_dir.tooltip", contentTop + rowHeight * 2),
            SettingsRow("vrmmod.config.use_vrma", "vrmmod.config.use_vrma.tooltip", contentTop + rowHeight * 3),
            SettingsRow("vrmmod.config.first_person_mode", "vrmmod.config.first_person_mode.tooltip", contentTop + rowHeight * 4),
        )

        modelSourceButton = CycleButton.builder<ModelSource> { source ->
            Component.translatable("vrmmod.config.model_source.${source.name.lowercase()}")
        }.withValues(*ModelSource.entries.toTypedArray())
            .withInitialValue(config.modelSource)
            .displayOnlyValue()
            .create(fieldX, contentTop, fieldWidth, 20, Component.translatable("vrmmod.config.model_source")).also {
                addRenderableWidget(it)
            }

        modelPathInput = EditBox(font, fieldX, contentTop + rowHeight, fieldWidth, 18, Component.translatable("vrmmod.config.model_path")).also {
            it.setMaxLength(1024)
            it.value = config.localModelPath ?: ""
            addRenderableWidget(it)
        }

        animDirInput = EditBox(font, fieldX, contentTop + rowHeight * 2, fieldWidth, 18, Component.translatable("vrmmod.config.animation_dir")).also {
            it.setMaxLength(1024)
            it.value = config.animationDir ?: ""
            addRenderableWidget(it)
        }

        useVrmaToggle = CycleButton.onOffBuilder(config.useVrmaAnimation)
            .displayOnlyValue()
            .create(fieldX, contentTop + rowHeight * 3, fieldWidth, 20, Component.translatable("vrmmod.config.use_vrma")).also {
                addRenderableWidget(it)
            }

        firstPersonButton = CycleButton.builder<FirstPersonMode> { mode ->
            Component.translatable("vrmmod.config.first_person_mode.${mode.name.lowercase()}")
        }.withValues(*FirstPersonMode.entries.toTypedArray())
            .withInitialValue(config.firstPersonMode)
            .displayOnlyValue()
            .create(fieldX, contentTop + rowHeight * 4, fieldWidth, 20, Component.translatable("vrmmod.config.first_person_mode")).also {
                addRenderableWidget(it)
            }

        addRenderableWidget(
            Button.builder(Component.translatable("vrmmod.config.save")) { _ -> saveSettings() }
                .bounds(5, height - 26, 100, 20).build()
        )
    }

    private fun saveSettings() {
        val oldConfig = VrmModConfig.load(configDir)
        val newConfig = VrmModConfig(
            localModelPath = modelPathInput?.value?.ifBlank { null },
            animationDir = animDirInput?.value?.ifBlank { null },
            useVrmaAnimation = useVrmaToggle?.value ?: true,
            firstPersonMode = firstPersonButton?.value ?: FirstPersonMode.VRM_MC_CAMERA,
            vroidHubModelId = oldConfig.vroidHubModelId,
            modelSource = modelSourceButton?.value ?: ModelSource.LOCAL,
        )
        VrmModClient.currentConfig = newConfig
        VrmModConfig.save(configDir, newConfig)

        reloadModel(newConfig)
        VrmMod.logger.info("Settings saved")
    }

    private fun reloadModel(config: VrmModConfig) {
        val player = Minecraft.getInstance().player ?: return
        VrmPlayerManager.unload(player.uuid)
        when (config.modelSource) {
            ModelSource.LOCAL -> {
                val file = config.resolveModelFile()
                if (file != null && file.exists()) {
                    val animDir = if (config.useVrmaAnimation) config.animationDir?.let { File(it) } else null
                    val animationConfig = AnimationConfig.load(configDir)
                    VrmPlayerManager.loadLocal(player.uuid, file, animDir, animationConfig)
                }
            }
            ModelSource.VROID_HUB -> {
                if (config.vroidHubModelId != null) {
                    VrmModClient.loadVRoidHubModelFromScreen(player.uuid)
                }
            }
        }
    }

    // ========================================================================
    // VRoid Hub Tab
    // ========================================================================

    private fun initVRoidHub() {
        vroidHubInitialized = true
        if (!vroidConfig.isAvailable) {
            vroidHubState = VRoidHubState.NOT_CONFIGURED
            buildWidgets()
            return
        }
        val token = VRoidHubAuth.loadToken(configDir.toPath())
        if (token != null && !token.isExpired) {
            vroidHubState = VRoidHubState.LOADING_MODELS
            buildWidgets()
            fetchUserAndModels(token.accessToken)
        } else if (token != null) {
            vroidHubState = VRoidHubState.LOADING
            buildWidgets()
            CompletableFuture.supplyAsync {
                VRoidHubAuth.refreshToken(vroidConfig, token.refreshToken)
            }.thenAccept { result ->
                Minecraft.getInstance().execute {
                    result.onSuccess { newToken ->
                        VRoidHubAuth.saveToken(configDir.toPath(), newToken)
                        vroidHubState = VRoidHubState.LOADING_MODELS
                        buildWidgets()
                        fetchUserAndModels(newToken.access_token)
                    }.onFailure { setupLoginState() }
                }
            }
        } else {
            setupLoginState()
        }
    }

    private fun setupLoginState() {
        vroidHubState = VRoidHubState.LOGIN
        val (url, session) = VRoidHubAuth.buildAuthorizeUrl(vroidConfig)
        authSession = session
        loginUrl = url
        buildWidgets()
    }

    private fun buildVRoidHubWidgets() {
        when (vroidHubState) {
            VRoidHubState.LOGIN -> {
                addRenderableWidget(
                    Button.builder(Component.translatable("vrmmod.vroidhub.login_button")) { _ ->
                        Util.getPlatform().openUri(loginUrl)
                    }.bounds(width / 2 - 100, height / 2 - 40, 200, 20).build()
                )
                codeInput = EditBox(font, width / 2 - 100, height / 2, 200, 20, Component.translatable("vrmmod.vroidhub.authenticate")).also {
                    it.setHint(Component.translatable("vrmmod.vroidhub.code_hint"))
                    it.setMaxLength(256)
                    addRenderableWidget(it)
                }
                addRenderableWidget(
                    Button.builder(Component.translatable("vrmmod.vroidhub.authenticate")) { _ ->
                        val code = codeInput?.value?.trim() ?: ""
                        if (code.isNotBlank() && authSession != null) {
                            vroidHubState = VRoidHubState.LOGGING_IN
                            buildWidgets()
                            exchangeToken(code)
                        }
                    }.bounds(width / 2 - 100, height / 2 + 30, 200, 20).build()
                )
            }
            VRoidHubState.LOGGED_IN -> {
                val allModels = getAllDisplayModels()
                val listTop = 52
                val itemHeight = 22
                val visibleCount = (height - 100) / itemHeight

                for (i in scrollOffset until minOf(scrollOffset + visibleCount, allModels.size)) {
                    val model = allModels[i]
                    val label = buildModelLabel(model)
                    val y = listTop + (i - scrollOffset) * itemHeight
                    addRenderableWidget(
                        Button.builder(Component.literal(label.take(35))) { _ ->
                            selectedModel = model
                            detailScrollOffset = 0
                            useModelButton?.active = true
                        }.bounds(5, y, width / 2 - 10, 20).build()
                    )
                }

                if (scrollOffset > 0) {
                    addRenderableWidget(Button.builder(Component.literal("▲")) { _ ->
                        scrollOffset = maxOf(0, scrollOffset - 5); buildWidgets()
                    }.bounds(width / 2 - 20, listTop - 18, 20, 16).build())
                }
                if (scrollOffset + visibleCount < allModels.size) {
                    addRenderableWidget(Button.builder(Component.literal("▼")) { _ ->
                        scrollOffset = minOf(allModels.size - 1, scrollOffset + 5); buildWidgets()
                    }.bounds(width / 2 - 20, height - 48, 20, 16).build())
                }

                useModelButton = Button.builder(Component.translatable("vrmmod.vroidhub.use_model")) { _ ->
                    selectedModel?.let { onModelConfirmed(it) }
                }.bounds(5, height - 26, 200, 20).build().also {
                    it.active = false; addRenderableWidget(it)
                }

                addRenderableWidget(
                    Button.builder(Component.translatable("vrmmod.vroidhub.logout")) { _ ->
                        VRoidHubAuth.loadToken(configDir.toPath())?.let { token ->
                            CompletableFuture.runAsync { VRoidHubAuth.revokeToken(vroidConfig, token.accessToken) }
                        }
                        VRoidHubAuth.deleteToken(configDir.toPath())
                        setupLoginState()
                    }.bounds(210, height - 26, 60, 20).build()
                )
            }
            else -> {}
        }
    }

    // ========================================================================
    // Render
    // ========================================================================

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        when (activeTab) {
            Tab.SETTINGS -> {
                renderSettings(guiGraphics)
                // Tooltips must render last (on top of everything)
                renderSettingsTooltips(guiGraphics, mouseX, mouseY)
            }
            Tab.VROID_HUB -> renderVRoidHub(guiGraphics)
        }
    }

    private fun renderSettings(guiGraphics: GuiGraphics) {
        val labelX = 10

        for (row in settingsRows) {
            guiGraphics.drawString(font, Component.translatable(row.labelKey), labelX, row.y + 5, 0xFFFFFF)
        }

    }

    /** Render tooltip for settings labels on mouse hover (called after super.render) */
    private fun renderSettingsTooltips(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val labelX = 10
        val labelWidth = width / 2 - 15

        for (row in settingsRows) {
            if (mouseX in labelX..(labelX + labelWidth) && mouseY in row.y..(row.y + 18)) {
                val tooltip = Component.translatable(row.tooltipKey)
                guiGraphics.renderTooltip(font, tooltip, mouseX, mouseY)
                break
            }
        }
    }

    private fun renderVRoidHub(guiGraphics: GuiGraphics) {
        when (vroidHubState) {
            VRoidHubState.NOT_CONFIGURED -> {
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.not_configured"), width / 2, height / 2 - 20, 0xFF6666)
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.not_configured.hint1"), width / 2, height / 2, 0xAAAAAA)
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.not_configured.hint2"), width / 2, height / 2 + 12, 0xAAAAAA)
            }
            VRoidHubState.LOADING, VRoidHubState.LOADING_MODELS -> {
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.loading"), width / 2, height / 2, 0xAAAAAA)
            }
            VRoidHubState.LOGIN -> {
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.login_title"), width / 2, height / 2 - 60, 0xFFFFFF)
            }
            VRoidHubState.LOGGING_IN -> {
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.authenticating"), width / 2, height / 2, 0xAAAAAA)
            }
            VRoidHubState.LOGGED_IN -> {
                if (userName.isNotBlank()) {
                    guiGraphics.drawString(font, Component.translatable("vrmmod.vroidhub.logged_in", userName), 5, 22, 0x66FF66)
                }
                val allModels = getAllDisplayModels()
                guiGraphics.drawString(font, Component.translatable("vrmmod.vroidhub.models_count", allModels.size), 5, 34, 0xAAAAAA)
                renderModelDetails(guiGraphics)

                val hasScrolledToBottom = selectedModel != null &&
                    (detailTotalLines <= detailVisibleLines || detailScrollOffset + detailVisibleLines >= detailTotalLines)
                useModelButton?.active = hasScrolledToBottom
                if (selectedModel != null && useModelButton?.active == false) {
                    guiGraphics.drawString(font, Component.translatable("vrmmod.vroidhub.scroll_to_agree"), 5, height - 38, 0xAAAA00)
                }
            }
            VRoidHubState.ERROR -> {
                guiGraphics.drawCenteredString(font, Component.translatable("vrmmod.vroidhub.error", errorMessage), width / 2, height / 2, 0xFF6666)
            }
        }
    }

    private fun renderModelDetails(guiGraphics: GuiGraphics) {
        val model = selectedModel ?: return
        val detailX = width / 2 + 5
        val detailTop = 52
        val detailBottom = height - 34

        data class DetailLine(val text: Component, val color: Int)
        val lines = mutableListOf<DetailLine>()

        lines.add(DetailLine(Component.literal(model.character?.name ?: "?"), 0xFFFFFF))
        model.name?.let { v -> if (v != model.character?.name && v.isNotBlank()) lines.add(DetailLine(Component.translatable("vrmmod.vroidhub.variant", v), 0xCCCCFF)) }
        lines.add(DetailLine(Component.literal("by ${model.character?.user?.name ?: "?"}"), 0xAAAAAA))
        model.latest_character_model_version?.spec_version?.let { lines.add(DetailLine(Component.literal("VRM $it"), 0x888888)) }
        model.age_limit?.let { al ->
            when { al.is_r18 -> lines.add(DetailLine(Component.translatable("vrmmod.vroidhub.age_r18"), 0xFF4444))
                   al.is_r15 -> lines.add(DetailLine(Component.translatable("vrmmod.vroidhub.age_r15"), 0xFF8844)) }
        }
        if (!myModels.any { it.id == model.id } && !model.is_other_users_available) {
            lines.add(DetailLine(Component.translatable("vrmmod.vroidhub.not_available"), 0xFF4444))
        }
        val licenseItems = VRoidHubLicenseDisplay.buildItems(model)
        if (licenseItems.isNotEmpty()) {
            lines.add(DetailLine(Component.literal(""), 0))
            lines.add(DetailLine(Component.translatable("vrmmod.vroidhub.license.title"), 0xFFFF00))
            for (item in licenseItems) {
                val color = when (item.isOk) { true -> 0x66FF66; false -> 0xFF6666; null -> 0xCCCCCC }
                lines.add(DetailLine(item.label.copy().append(": ").append(item.value), color))
            }
        }

        val lineHeight = 10
        val visibleLines = (detailBottom - detailTop) / lineHeight
        detailTotalLines = lines.size; detailVisibleLines = visibleLines
        detailScrollOffset = detailScrollOffset.coerceIn(0, maxOf(0, lines.size - visibleLines))
        for (i in detailScrollOffset until minOf(lines.size, detailScrollOffset + visibleLines)) {
            val y = detailTop + (i - detailScrollOffset) * lineHeight
            if (lines[i].text.string.isNotEmpty()) guiGraphics.drawString(font, lines[i].text, detailX, y, lines[i].color)
        }
        if (detailScrollOffset > 0) guiGraphics.drawString(font, "▲", width - 15, detailTop, 0x888888)
        if (detailScrollOffset + visibleLines < lines.size) guiGraphics.drawString(font, "▼", width - 15, detailBottom - 10, 0x888888)
    }

    // ========================================================================
    // VRoid Hub helpers
    // ========================================================================

    private fun exchangeToken(code: String) {
        val session = authSession ?: return
        CompletableFuture.supplyAsync { VRoidHubAuth.exchangeToken(vroidConfig, code, session) }
            .thenAccept { result ->
                Minecraft.getInstance().execute {
                    result.onSuccess { token ->
                        VRoidHubAuth.saveToken(configDir.toPath(), token)
                        vroidHubState = VRoidHubState.LOADING_MODELS; buildWidgets()
                        fetchUserAndModels(token.access_token)
                    }.onFailure { e ->
                        vroidHubState = VRoidHubState.ERROR; errorMessage = e.message ?: "Unknown"; buildWidgets()
                    }
                }
            }
    }

    private fun fetchUserAndModels(accessToken: String) {
        CompletableFuture.supplyAsync {
            Triple(VRoidHubApi.getAccount(accessToken).getOrNull(),
                VRoidHubApi.getHearts(accessToken).getOrElse { emptyList() },
                VRoidHubApi.getAccountCharacterModels(accessToken).getOrElse { emptyList() })
        }.thenAccept { (account, hearts, mine) ->
            Minecraft.getInstance().execute {
                userName = account?.user_detail?.user?.name ?: ""
                heartModels = hearts; myModels = mine
                vroidHubState = VRoidHubState.LOGGED_IN; selectedModel = null; scrollOffset = 0; buildWidgets()
            }
        }
    }

    private fun getAllDisplayModels(): List<CharacterModel> {
        val myIds = myModels.map { it.id }.toSet()
        val seen = mutableSetOf<String>(); val result = mutableListOf<CharacterModel>()
        for (model in myModels + heartModels) {
            if (model.id in seen) continue; seen.add(model.id)
            if (model.id !in myIds) {
                val meta = model.latest_character_model_version?.vrm_meta
                val v10 = model.latest_character_model_version?.spec_version?.startsWith("1") == true
                val ok = if (v10 && meta != null) meta.avatarPermission == "everyone"
                         else model.license?.characterization_allowed_user == "everyone"
                if (!ok) continue
            }
            result.add(model)
        }
        return result
    }

    private fun buildModelLabel(model: CharacterModel): String {
        val charName = model.character?.name ?: "?"
        val variantName = model.name
        val name = if (variantName != null && variantName != charName && variantName.isNotBlank()) "$charName / $variantName" else charName
        val prefix = if (myModels.any { it.id == model.id }) "★ " else ""
        val age = when { model.age_limit?.is_r18 == true -> " [R18]"; model.age_limit?.is_r15 == true -> " [R15]"; else -> "" }
        return "$prefix$name$age"
    }

    private fun onModelConfirmed(model: CharacterModel) {
        val config = VrmModConfig.load(configDir)
        val newConfig = config.copy(vroidHubModelId = model.id, modelSource = ModelSource.VROID_HUB)
        VrmModConfig.save(configDir, newConfig); VrmModClient.currentConfig = newConfig
        Minecraft.getInstance().player?.let { VrmModClient.loadVRoidHubModelFromScreen(it.uuid) }
    }

    // ========================================================================
    // Navigation & input
    // ========================================================================

    override fun onClose() { minecraft?.setScreen(parent) }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (activeTab == Tab.VROID_HUB && vroidHubState == VRoidHubState.LOGGED_IN) {
            if (mouseX < width / 2.0) {
                scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxOf(0, getAllDisplayModels().size - 1)); buildWidgets()
            } else {
                detailScrollOffset = (detailScrollOffset - scrollY.toInt()).coerceAtLeast(0)
            }
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
