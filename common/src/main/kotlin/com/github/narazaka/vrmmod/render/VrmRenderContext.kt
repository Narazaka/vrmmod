package com.github.narazaka.vrmmod.render

import java.util.UUID

/**
 * Shared thread-local state for passing data from
 * PlayerRendererMixin (extractRenderState) to LivingEntityRendererMixin (render).
 */
object VrmRenderContext {
    @JvmField
    val CURRENT_PLAYER_UUID: ThreadLocal<UUID> = ThreadLocal()

    @JvmField
    val ENTITY_X: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ENTITY_Y: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ENTITY_Z: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
    @JvmField
    val ON_GROUND: ThreadLocal<Boolean> = ThreadLocal.withInitial { true }
    @JvmField
    val HURT_TIME: ThreadLocal<Float> = ThreadLocal.withInitial { 0f }
}
