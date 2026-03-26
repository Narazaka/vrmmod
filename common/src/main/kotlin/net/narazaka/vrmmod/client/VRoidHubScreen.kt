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
        LOADING,        // Checking token status
        NOT_CONFIGURED, // No vrmmod-vroidhub.json
        LOGIN,          // Show login button + code input
        LOGGING_IN,     // Exchanging code for token
        LOGGED_IN,      // Show model list
        LOADING_MODELS, // Fetching hearts list
        ERROR,          // Show error message
    }

    private var state = State.LOADING
    private var errorMessage = ""
    private var userName = ""

    private var vroidConfig = VRoidHubConfig()
    private var authSession: AuthSession? = null
    private var codeInput: EditBox? = null

    private var models: List<CharacterModel> = emptyList()
    private var selectedModel: CharacterModel? = null
    private var modelListScrollOffset = 0

    private val configDir get() = Minecraft.getInstance().gameDirectory.resolve("config").toPath()
    private val gameDir get() = Minecraft.getInstance().gameDirectory.toPath()

    override fun init() {
        vroidConfig = VRoidHubConfig.load(configDir)

        if (!vroidConfig.isAvailable) {
            state = State.NOT_CONFIGURED
            addCloseButton()
            return
        }

        // Check saved token
        val token = VRoidHubAuth.loadToken(configDir)
        if (token != null && !token.isExpired) {
            state = State.LOADING_MODELS
            addCloseButton()
            fetchUserAndModels(token.accessToken)
        } else if (token != null) {
            // Try refresh
            state = State.LOADING
            CompletableFuture.supplyAsync {
                VRoidHubAuth.refreshToken(vroidConfig, token.refreshToken)
            }.thenAccept { result ->
                Minecraft.getInstance().execute {
                    result.onSuccess { newToken ->
                        VRoidHubAuth.saveToken(configDir, newToken)
                        state = State.LOADING_MODELS
                        rebuildWidgets()
                        fetchUserAndModels(newToken.access_token)
                    }.onFailure {
                        showLoginState()
                    }
                }
            }
        } else {
            showLoginState()
        }
    }

    private fun showLoginState() {
        state = State.LOGIN
        authSession = VRoidHubAuth.buildAuthorizeUrl(vroidConfig).let { (_, session) -> session }
        rebuildWidgets()
    }

    override fun rebuildWidgets() {
        clearWidgets()

        when (state) {
            State.LOGIN -> {
                val (url, session) = VRoidHubAuth.buildAuthorizeUrl(vroidConfig)
                authSession = session

                // Login button
                addRenderableWidget(
                    Button.builder(Component.literal("Open VRoid Hub Login")) { _ ->
                        Util.getPlatform().openUri(url)
                    }.bounds(width / 2 - 100, height / 2 - 40, 200, 20).build()
                )

                // Code input
                codeInput = EditBox(font, width / 2 - 100, height / 2, 200, 20, Component.literal("Code")).also {
                    it.setHint(Component.literal("Paste authorization code here"))
                    it.setMaxLength(256)
                    addRenderableWidget(it)
                }

                // Submit button
                addRenderableWidget(
                    Button.builder(Component.literal("Authenticate")) { _ ->
                        val code = codeInput?.value?.trim() ?: ""
                        if (code.isNotBlank() && authSession != null) {
                            state = State.LOGGING_IN
                            rebuildWidgets()
                            exchangeToken(code)
                        }
                    }.bounds(width / 2 - 100, height / 2 + 30, 200, 20).build()
                )

                addCloseButton()
            }

            State.LOGGED_IN -> {
                // Model list buttons (simple vertical list)
                val listTop = 50
                val itemHeight = 22
                val visibleCount = (height - 120) / itemHeight
                val displayModels = models.filter { it.is_downloadable }

                for ((i, model) in displayModels.withIndex().drop(modelListScrollOffset).take(visibleCount)) {
                    val label = "${model.character?.name ?: "?"} - ${model.character?.user?.name ?: "?"}"
                    val y = listTop + (i - modelListScrollOffset) * itemHeight
                    addRenderableWidget(
                        Button.builder(Component.literal(label.take(40))) { _ ->
                            selectedModel = model
                            rebuildWidgets()
                        }.bounds(5, y, width / 2 - 10, 20).build()
                    )
                }

                // Use model button
                if (selectedModel != null) {
                    addRenderableWidget(
                        Button.builder(Component.literal("Use this model (agree to license)")) { _ ->
                            onModelConfirmed(selectedModel!!)
                        }.bounds(width / 2 - 150, height - 28, 150, 20).build()
                    )
                }

                // Logout button
                addRenderableWidget(
                    Button.builder(Component.literal("Logout")) { _ ->
                        val token = VRoidHubAuth.loadToken(configDir)
                        if (token != null) {
                            CompletableFuture.runAsync {
                                VRoidHubAuth.revokeToken(vroidConfig, token.accessToken)
                            }
                        }
                        VRoidHubAuth.deleteToken(configDir)
                        state = State.LOGIN
                        rebuildWidgets()
                    }.bounds(width / 2 + 10, height - 28, 80, 20).build()
                )

                addCloseButton()
            }

            else -> addCloseButton()
        }
    }

    private fun addCloseButton() {
        addRenderableWidget(
            Button.builder(Component.literal("Close")) { _ -> onClose() }
                .bounds(width - 65, height - 28, 60, 20).build()
        )
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        super.render(guiGraphics, mouseX, mouseY, partialTick)

        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF)

        when (state) {
            State.NOT_CONFIGURED -> {
                guiGraphics.drawCenteredString(font, "VRoid Hub is not configured.", width / 2, height / 2 - 20, 0xFF6666)
                guiGraphics.drawCenteredString(font, "Place vrmmod-vroidhub.json in config/ with clientId and clientSecret.", width / 2, height / 2, 0xAAAAAA)
            }

            State.LOADING -> {
                guiGraphics.drawCenteredString(font, "Loading...", width / 2, height / 2, 0xAAAAAA)
            }

            State.LOGIN -> {
                guiGraphics.drawCenteredString(font, "Login to VRoid Hub", width / 2, height / 2 - 60, 0xFFFFFF)
            }

            State.LOGGING_IN -> {
                guiGraphics.drawCenteredString(font, "Authenticating...", width / 2, height / 2, 0xAAAAAA)
            }

            State.LOADING_MODELS -> {
                guiGraphics.drawCenteredString(font, "Loading models...", width / 2, height / 2, 0xAAAAAA)
                if (userName.isNotBlank()) {
                    guiGraphics.drawCenteredString(font, "Logged in as: $userName", width / 2, 30, 0x66FF66)
                }
            }

            State.LOGGED_IN -> {
                if (userName.isNotBlank()) {
                    guiGraphics.drawString(font, "Logged in as: $userName", 5, 30, 0x66FF66)
                }
                guiGraphics.drawString(font, "Favorites (${models.count { it.is_downloadable }} models)", 5, 40, 0xAAAAAA)

                // Right side: selected model details + license
                val model = selectedModel
                if (model != null) {
                    val detailX = width / 2 + 5
                    var y = 50

                    guiGraphics.drawString(font, model.character?.name ?: "?", detailX, y, 0xFFFFFF)
                    y += 12
                    guiGraphics.drawString(font, "by ${model.character?.user?.name ?: "?"}", detailX, y, 0xAAAAAA)
                    y += 12
                    model.latest_character_model_version?.spec_version?.let {
                        guiGraphics.drawString(font, "VRM $it", detailX, y, 0x888888)
                        y += 12
                    }
                    y += 6

                    val license = model.license
                    if (license != null) {
                        guiGraphics.drawString(font, "--- License ---", detailX, y, 0xFFFF00)
                        y += 12
                        y = drawLicenseField(guiGraphics, detailX, y, "Avatar use", license.characterization_allowed_user)
                        y = drawLicenseField(guiGraphics, detailX, y, "Violence", license.violent_expression)
                        y = drawLicenseField(guiGraphics, detailX, y, "Sexual", license.sexual_expression)
                        y = drawLicenseField(guiGraphics, detailX, y, "Corp. commercial", license.corporate_commercial_use)
                        y = drawLicenseField(guiGraphics, detailX, y, "Personal commercial", license.personal_commercial_use)
                        y = drawLicenseField(guiGraphics, detailX, y, "Modification", license.modification)
                        y = drawLicenseField(guiGraphics, detailX, y, "Redistribution", license.redistribution)
                        drawLicenseField(guiGraphics, detailX, y, "Credit", license.credit)
                    }
                }
            }

            State.ERROR -> {
                guiGraphics.drawCenteredString(font, "Error: $errorMessage", width / 2, height / 2, 0xFF6666)
            }
        }
    }

    private fun drawLicenseField(guiGraphics: GuiGraphics, x: Int, y: Int, label: String, value: String): Int {
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
                    rebuildWidgets()
                    fetchUserAndModels(token.access_token)
                }.onFailure { e ->
                    VrmMod.logger.error("VRoid Hub login failed", e)
                    state = State.ERROR
                    errorMessage = e.message ?: "Unknown error"
                    rebuildWidgets()
                }
            }
        }
    }

    private fun fetchUserAndModels(accessToken: String) {
        CompletableFuture.supplyAsync {
            val account = VRoidHubApi.getAccount(accessToken).getOrNull()
            val hearts = VRoidHubApi.getHearts(accessToken).getOrElse { emptyList() }
            Pair(account, hearts)
        }.thenAccept { (account, hearts) ->
            Minecraft.getInstance().execute {
                userName = account?.user_detail?.user?.name ?: ""
                models = hearts
                state = State.LOGGED_IN
                rebuildWidgets()
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

        // Trigger download and load
        val player = Minecraft.getInstance().player
        if (player != null) {
            VrmModClient.loadVRoidHubModelFromScreen(player.uuid)
        }

        onClose()
    }

    override fun onClose() {
        minecraft?.setScreen(parent)
    }
}
