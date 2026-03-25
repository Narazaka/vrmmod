package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.client.FirstPersonMode;
import com.github.narazaka.vrmmod.client.VrmModClient;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla first-person hand rendering when VRM model is active.
 * Targets ItemInHandRenderer which handles the actual hand drawing in MC 1.21.4.
 */
@Mixin(ItemInHandRenderer.class)
public class HandRendererMixin {

    @Inject(method = "renderHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void vrmmod$cancelHandRendering(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, net.minecraft.client.player.LocalPlayer player, int light, CallbackInfo ci) {
        if (VrmModClient.INSTANCE.getCurrentConfig().getFirstPersonMode() == FirstPersonMode.VANILLA) return;
        var mc = Minecraft.getInstance();
        if (mc.player != null && VrmPlayerManager.INSTANCE.get(mc.player.getUUID()) != null) {
            ci.cancel();
        }
    }
}
