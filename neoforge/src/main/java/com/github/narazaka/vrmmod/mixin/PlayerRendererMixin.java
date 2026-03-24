package com.github.narazaka.vrmmod.mixin;

import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderer;
import com.github.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {
    @Unique
    private UUID vrmmod$currentPlayerUUID;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        this.vrmmod$currentPlayerUUID = player.getUUID();
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(PlayerRenderState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        UUID uuid = this.vrmmod$currentPlayerUUID;
        if (uuid == null) return;

        // Skip VRM rendering for the local player in first-person view
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()
                && mc.player != null && uuid.equals(mc.player.getUUID())) {
            return;
        }

        VrmState vrmState = VrmPlayerManager.INSTANCE.get(uuid);
        if (vrmState == null) return;

        VrmRenderer.INSTANCE.render(vrmState, poseStack, bufferSource, packedLight);
        ci.cancel();
    }
}
