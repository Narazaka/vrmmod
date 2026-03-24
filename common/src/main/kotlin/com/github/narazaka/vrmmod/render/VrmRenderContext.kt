package com.github.narazaka.vrmmod.render

import java.util.UUID

/**
 * Shared thread-local state for passing the player UUID from
 * PlayerRendererMixin (extractRenderState) to LivingEntityRendererMixin (render).
 */
object VrmRenderContext {
    @JvmField
    val CURRENT_PLAYER_UUID: ThreadLocal<UUID> = ThreadLocal()
}
