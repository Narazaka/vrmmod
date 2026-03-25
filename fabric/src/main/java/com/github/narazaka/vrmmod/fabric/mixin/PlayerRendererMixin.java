package com.github.narazaka.vrmmod.fabric.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.narazaka.vrmmod.render.VrmRenderContext;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
        // Capture interpolated entity position for SpringBone world tracking
        Vec3 pos = player.getPosition(partialTick);
        VrmRenderContext.ENTITY_X.set((float) pos.x);
        VrmRenderContext.ENTITY_Y.set((float) pos.y);
        VrmRenderContext.ENTITY_Z.set((float) pos.z);
        VrmRenderContext.ON_GROUND.set(player.onGround());
    }
}
