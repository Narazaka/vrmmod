package com.github.narazaka.vrmmod.neoforge.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Captures the player UUID during extractRenderState so that
 * LivingEntityRendererMixin can use it during render().
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    /**
     * Capture player UUID during extractRenderState (which has access to the entity).
     * render() only receives PlayerRenderState, not the entity.
     * Store it in a ThreadLocal so LivingEntityRendererMixin can read it.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
    }
}
