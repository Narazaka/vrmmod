package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderer;
import com.github.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts PlayerRenderer.render() to substitute VRM model rendering
 * when a VRM model is loaded for the player.
 */
@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            LivingEntityRenderState livingRenderState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        // The render state is actually a PlayerRenderState when called from PlayerRenderer
        if (!(livingRenderState instanceof PlayerRenderState renderState)) {
            return;
        }

        // Don't render VRM in first-person view
        if (Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            return;
        }

        // Look up the player entity from the render state's entity ID
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Entity entity = mc.level.getEntity(renderState.id);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }

        // Check if a VRM model is loaded for this player
        VrmState state = VrmPlayerManager.INSTANCE.get(player);
        if (state == null) {
            return; // No VRM model; fall back to vanilla rendering
        }

        // Render the VRM model and cancel vanilla rendering
        VrmRenderer.INSTANCE.render(state, poseStack, bufferSource, packedLight);
        ci.cancel();
    }
}
