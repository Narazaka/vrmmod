package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderContext;
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
 * Suppresses vanilla hand rendering when VRM model is active.
 */
@Mixin(PlayerRenderer.class)
public class HandRendererMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    private void vrmmod$cancelRightHand(PoseStack poseStack, MultiBufferSource bufferSource, int light, ResourceLocation skin, boolean sleeve, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && VrmPlayerManager.INSTANCE.get(mc.player.getUUID()) != null) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    private void vrmmod$cancelLeftHand(PoseStack poseStack, MultiBufferSource bufferSource, int light, ResourceLocation skin, boolean sleeve, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (mc.player != null && VrmPlayerManager.INSTANCE.get(mc.player.getUUID()) != null) {
            ci.cancel();
        }
    }
}
