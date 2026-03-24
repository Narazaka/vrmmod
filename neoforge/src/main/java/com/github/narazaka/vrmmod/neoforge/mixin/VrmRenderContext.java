package com.github.narazaka.vrmmod.neoforge.mixin;

import java.util.UUID;

/**
 * Shared thread-local state for passing the player UUID from
 * PlayerRendererMixin (extractRenderState) to LivingEntityRendererMixin (render).
 */
public final class VrmRenderContext {
    public static final ThreadLocal<UUID> CURRENT_PLAYER_UUID = new ThreadLocal<>();

    private VrmRenderContext() {}
}
