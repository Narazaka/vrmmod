package net.narazaka.vrmmod.client

import net.minecraft.Util
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.vroidhub.*
import java.util.concurrent.CompletableFuture

/**
 * Standalone Screen for VRoid Hub integration.
 * Handles the full OAuth login flow, model selection, and license display.
 */
class VRoidHubScreen(private val parent: Screen?) : Screen(Component.literal("VRoid Hub")) {

    private enum class State {
        LOADING,
        NOT_CONFIGURED,
        LOGIN,
        LOGGING_IN,
        LOGGED_IN,
        LOADING_MODELS,
        ERROR,
    }

    private var state = State.LOADING
    private var errorMessage = ""
    private var userName = ""

    private var vroidConfig = VRoidHubConfig()
    private var authSession: AuthSession? = null
    private var codeInput: EditBox? = null

    private var heartModels: List<CharacterModel> = emptyList()
    private var myModels: List<CharacterModel> = emptyList()
    private var selectedModel: CharacterModel? = null
    private var selectedIndex = -1
    private var scrollOffset = 0

    // Buttons that need enable/disable toggling
    private var useModelButton: Button? = null

    private val configDir get() = Minecraft.getInstance().gameDirectory.resolve("config").toPath()

    override fun init() {
        vroidConfig = VRoidHubConfig.load(configDir)

        if (!vroidConfig.isAvailable) {
            state = State.NOT_CONFIGURED
            buildWidgets()
            return
        }

        val token = VRoidHubAuth.loadToken(configDir)
        if (token != null && !token.isExpired) {
            state = State.LOADING_MODELS
            buildWidgets()
            fetchUserAndModels(token.accessToken)
        } else if (token != null) {
            state = State.LOADING
            buildWidgets()
            CompletableFuture.supplyAsync {
                VRoidHubAuth.refreshToken(vroidConfig, token.refreshToken)
            }.thenAccept { result ->
                Minecraft.getInstance().execute {
                    result.onSuccess { newToken ->
                        VRoidHubAuth.saveToken(configDir, newToken)
                        state = State.LOADING_MODELS
                        buildWidgets()
                        fetchUserAndModels(newToken.access_token)
                    }.onFailure {
                        setupLoginState()
                    }
                }
            }
        } else {
            setupLoginState()
        }
    }

    private fun setupLoginState() {
        state = State.LOGIN
        val (url, session) = VRoidHubAuth.buildAuthorizeUrl(vroidConfig)
        authSession = session
        buildWidgets()

        // Store URL for login button (added in buildWidgets)
        loginUrl = url
    }

    private var loginUrl = ""

    private fun buildWidgets() {
        clearWidgets()
        codeInput = null
        useModelButton = null

        when (state) {
            State.LOGIN -> {
                addRenderableWidget(
                    Button.builder(Component.literal("Open VRoid Hub Login")) { _ ->
                        Util.getPlatform().openUri(loginUrl)
                    }.bounds(width / 2 - 100, height / 2 - 40, 200, 20).build()
                )

                codeInput = EditBox(font, width / 2 - 100, height / 2, 200, 20, Component.literal("Code")).also {
                    it.setHint(Component.literal("Paste authorization code here"))
                    it.setMaxLength(256)
                    addRenderableWidget(it)
                }

                addRenderableWidget(
                    Button.builder(Component.literal("Authenticate")) { _ ->
                        val code = codeInput?.value?.trim() ?: ""
                        if (code.isNotBlank() && authSession != null) {
                            state = State.LOGGING_IN
                            buildWidgets()
                            exchangeToken(code)
                        }
                    }.bounds(width / 2 - 100, height / 2 + 30, 200, 20).build()
                )
            }

            State.LOGGED_IN -> {
                val allModels = getAllDisplayModels()
                val listTop = 52
                val itemHeight = 22
                val visibleCount = (height - 100) / itemHeight

                for (i in scrollOffset until minOf(scrollOffset + visibleCount, allModels.size)) {
                    val model = allModels[i]
                    val label = buildModelLabel(model)
                    val y = listTop + (i - scrollOffset) * itemHeight
                    val index = i
                    addRenderableWidget(
                        Button.builder(Component.literal(label.take(35))) { _ ->
                            selectedModel = model
                            selectedIndex = index
                            useModelButton?.active = true
                        }.bounds(5, y, width / 2 - 10, 20).build()
                    )
                }

                // Scroll buttons
                if (scrollOffset > 0) {
                    addRenderableWidget(
                        Button.builder(Component.literal("▲")) { _ ->
                            scrollOffset = maxOf(0, scrollOffset - 5)
                            buildWidgets()
                        }.bounds(width / 2 - 20, listTop - 18, 20, 16).build()
                    )
                }
                if (scrollOffset + visibleCount < allModels.size) {
                    addRenderableWidget(
                        Button.builder(Component.literal("▼")) { _ ->
                            scrollOffset = minOf(allModels.size - 1, scrollOffset + 5)
                            buildWidgets()
                        }.bounds(width / 2 - 20, height - 48, 20, 16).build()
                    )
                }

                // Use model button (always present, disabled until selection)
                useModelButton = Button.builder(Component.literal("Use this model (agree to license)")) { _ ->
                    selectedModel?.let { onModelConfirmed(it) }
                }.bounds(5, height - 26, 200, 20).build().also {
                    it.active = selectedModel != null
                    addRenderableWidget(it)
                }

                // Logout
                addRenderableWidget(
                    Button.builder(Component.literal("Logout")) { _ ->
                        val token = VRoidHubAuth.loadToken(configDir)
                        if (token != null) {
                            CompletableFuture.runAsync {
                                VRoidHubAuth.revokeToken(vroidConfig, token.accessToken)
                            }
                        }
                        VRoidHubAuth.deleteToken(configDir)
                        setupLoginState()
                    }.bounds(210, height - 26, 60, 20).build()
                )
            }

            else -> {} // LOADING, NOT_CONFIGURED, ERROR etc. - no interactive widgets needed
        }

        // Close button (always)
        addRenderableWidget(
            Button.builder(Component.literal("Close")) { _ -> onClose() }
                .bounds(width - 65, height - 26, 60, 20).build()
        )
    }

    private fun getAllDisplayModels(): List<CharacterModel> {
        // My models first, then favorites, deduplicated by ID
        val seen = mutableSetOf<String>()
        val result = mutableListOf<CharacterModel>()
        for (model in myModels + heartModels) {
            if (model.id !in seen) {
                seen.add(model.id)
                result.add(model)
            }
        }
        return result
    }

    private fun buildModelLabel(model: CharacterModel): String {
        val charName = model.character?.name ?: "?"
        val variantName = model.name
        // Show variant name if it differs from character name
        val displayName = if (variantName != null && variantName != charName && variantName.isNotBlank()) {
            "$charName / $variantName"
        } else {
            charName
        }
        val mine = myModels.any { it.id == model.id }
        val prefix = if (mine) "★ " else ""
        return "$prefix$displayName"
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        guiGraphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF)

        when (state) {
            State.NOT_CONFIGURED -> {
                guiGraphics.drawCenteredString(font, "VRoid Hub is not configured.", width / 2, height / 2 - 20, 0xFF6666)
                guiGraphics.drawCenteredString(font, "Place vrmmod-vroidhub.json in config/", width / 2, height / 2, 0xAAAAAA)
                guiGraphics.drawCenteredString(font, "with clientId and clientSecret.", width / 2, height / 2 + 12, 0xAAAAAA)
            }

            State.LOADING, State.LOADING_MODELS -> {
                guiGraphics.drawCenteredString(font, "Loading...", width / 2, height / 2, 0xAAAAAA)
                if (userName.isNotBlank()) {
                    guiGraphics.drawCenteredString(font, "Logged in as: $userName", width / 2, 30, 0x66FF66)
                }
            }

            State.LOGIN -> {
                guiGraphics.drawCenteredString(font, "Login to VRoid Hub", width / 2, height / 2 - 60, 0xFFFFFF)
            }

            State.LOGGING_IN -> {
                guiGraphics.drawCenteredString(font, "Authenticating...", width / 2, height / 2, 0xAAAAAA)
            }

            State.LOGGED_IN -> {
                // Header
                if (userName.isNotBlank()) {
                    guiGraphics.drawString(font, "Logged in: $userName", 5, 22, 0x66FF66)
                }
                val allModels = getAllDisplayModels()
                guiGraphics.drawString(font, "Models: ${allModels.size} (★=mine)", 5, 34, 0xAAAAAA)

                // Right side: selected model details + license
                renderModelDetails(guiGraphics)
            }

            State.ERROR -> {
                guiGraphics.drawCenteredString(font, "Error: $errorMessage", width / 2, height / 2, 0xFF6666)
            }
        }
    }

    private fun renderModelDetails(guiGraphics: GuiGraphics) {
        val model = selectedModel ?: return
        val detailX = width / 2 + 5
        var y = 52

        guiGraphics.drawString(font, model.character?.name ?: "?", detailX, y, 0xFFFFFF)
        y += 12
        val variantName = model.name
        if (variantName != null && variantName != model.character?.name && variantName.isNotBlank()) {
            guiGraphics.drawString(font, "Variant: $variantName", detailX, y, 0xCCCCFF)
            y += 12
        }
        guiGraphics.drawString(font, "by ${model.character?.user?.name ?: "?"}", detailX, y, 0xAAAAAA)
        y += 12
        model.latest_character_model_version?.spec_version?.let {
            guiGraphics.drawString(font, "VRM $it", detailX, y, 0x888888)
            y += 12
        }
        val isMine = myModels.any { it.id == model.id }
        if (!isMine && !model.is_other_users_available) {
            guiGraphics.drawString(font, "Not available for other users", detailX, y, 0xFF4444)
            y += 12
        }
        y += 6

        val license = model.license
        if (license != null) {
            guiGraphics.drawString(font, "--- License ---", detailX, y, 0xFFFF00)
            y += 12
            y = drawLicense(guiGraphics, detailX, y, "Avatar use", license.characterization_allowed_user)
            y = drawLicense(guiGraphics, detailX, y, "Violence", license.violent_expression)
            y = drawLicense(guiGraphics, detailX, y, "Sexual", license.sexual_expression)
            y = drawLicense(guiGraphics, detailX, y, "Corp. commercial", license.corporate_commercial_use)
            y = drawLicense(guiGraphics, detailX, y, "Personal commercial", license.personal_commercial_use)
            y = drawLicense(guiGraphics, detailX, y, "Modification", license.modification)
            y = drawLicense(guiGraphics, detailX, y, "Redistribution", license.redistribution)
            drawLicense(guiGraphics, detailX, y, "Credit", license.credit)
        }
    }

    private fun drawLicense(guiGraphics: GuiGraphics, x: Int, y: Int, label: String, value: String): Int {
        val color = when (value) {
            "allow", "everyone", "unnecessary" -> 0x66FF66
            "disallow", "author" -> 0xFF6666
            else -> 0xCCCCCC
        }
        guiGraphics.drawString(font, "$label: $value", x, y, color)
        return y + 11
    }

    private fun exchangeToken(code: String) {
        val session = authSession ?: return
        CompletableFuture.supplyAsync {
            VRoidHubAuth.exchangeToken(vroidConfig, code, session)
        }.thenAccept { result ->
            Minecraft.getInstance().execute {
                result.onSuccess { token ->
                    VRoidHubAuth.saveToken(configDir, token)
                    VrmMod.logger.info("VRoid Hub login successful")
                    state = State.LOADING_MODELS
                    buildWidgets()
                    fetchUserAndModels(token.access_token)
                }.onFailure { e ->
                    VrmMod.logger.error("VRoid Hub login failed", e)
                    state = State.ERROR
                    errorMessage = e.message ?: "Unknown error"
                    buildWidgets()
                }
            }
        }
    }

    private fun fetchUserAndModels(accessToken: String) {
        CompletableFuture.supplyAsync {
            val account = VRoidHubApi.getAccount(accessToken).getOrNull()
            val hearts = VRoidHubApi.getHearts(accessToken).getOrElse { emptyList() }
            val mine = VRoidHubApi.getAccountCharacterModels(accessToken).getOrElse { emptyList() }
            Triple(account, hearts, mine)
        }.thenAccept { (account, hearts, mine) ->
            Minecraft.getInstance().execute {
                userName = account?.user_detail?.user?.name ?: ""
                heartModels = hearts
                myModels = mine
                state = State.LOGGED_IN
                selectedModel = null
                selectedIndex = -1
                scrollOffset = 0
                buildWidgets()
            }
        }
    }

    private fun onModelConfirmed(model: CharacterModel) {
        val modelId = model.id
        val configDir = Minecraft.getInstance().gameDirectory.resolve("config")
        val config = VrmModConfig.load(configDir)
        val newConfig = config.copy(vroidHubModelId = modelId)
        VrmModConfig.save(configDir, newConfig)
        VrmModClient.currentConfig = newConfig

        VrmMod.logger.info("Selected VRoid Hub model: {} ({})", model.character?.name, modelId)

        val player = Minecraft.getInstance().player
        if (player != null) {
            VrmModClient.loadVRoidHubModelFromScreen(player.uuid)
        }

        onClose()
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (state == State.LOGGED_IN) {
            val allModels = getAllDisplayModels()
            scrollOffset = (scrollOffset - scrollY.toInt()).coerceIn(0, maxOf(0, allModels.size - 1))
            buildWidgets()
            return true
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)
    }
}
