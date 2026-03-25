package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.client.FirstPersonMode;
import com.github.narazaka.vrmmod.client.VrmModClient;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla hand rendering when VRM model is active and not in vanilla mode.
 */
@Mixin(PlayerRenderer.class)
public class HandRendererMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void vrmmod$cancelRightHand(PoseStack poseStack, MultiBufferSource bufferSource, int light, ResourceLocation skin, boolean sleeve, CallbackInfo ci) {
        if (shouldCancelVanillaHand()) ci.cancel();
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void vrmmod$cancelLeftHand(PoseStack poseStack, MultiBufferSource bufferSource, int light, ResourceLocation skin, boolean sleeve, CallbackInfo ci) {
        if (shouldCancelVanillaHand()) ci.cancel();
    }

    private static boolean shouldCancelVanillaHand() {
        if (VrmModClient.INSTANCE.getCurrentConfig().getFirstPersonMode() == FirstPersonMode.VANILLA) return false;
        var mc = Minecraft.getInstance();
        return mc.player != null && VrmPlayerManager.INSTANCE.get(mc.player.getUUID()) != null;
    }
}
