package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.animation.PoseContext;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderer;
import com.github.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
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

    /**
     * Capture player UUID during extractRenderState (which has access to the entity).
     * render() only receives PlayerRenderState, not the entity.
     */
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        this.vrmmod$currentPlayerUUID = player.getUUID();
    }

    /**
     * Intercept render() inherited from LivingEntityRenderer.
     * PlayerRenderer does NOT override render(), so we target the parent signature
     * with LivingEntityRenderState (type-erased).
     */
    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            LivingEntityRenderState livingRenderState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(livingRenderState instanceof PlayerRenderState renderState)) {
            return;
        }

        UUID uuid = this.vrmmod$currentPlayerUUID;
        if (uuid == null) return;

        // Skip VRM rendering for the LOCAL player in first-person view only
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson()
                && mc.player != null && uuid.equals(mc.player.getUUID())) {
            return;
        }

        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight);
        ci.cancel();
    }

    private static PoseContext buildPoseContext(PlayerRenderState renderState) {
        float headYaw = renderState.yRot - renderState.bodyRot;
        float headPitch = renderState.xRot;
        boolean isSwinging = renderState.attackTime > 0f;
        boolean isSprinting = renderState.speedValue > 0.9f;

        return new PoseContext(
                0f,
                renderState.walkAnimationPos,
                renderState.walkAnimationSpeed,
                isSwinging,
                renderState.isCrouching,
                isSprinting,
                renderState.isVisuallySwimming,
                renderState.isFallFlying,
                renderState.isPassenger,
                headYaw,
                headPitch
        );
    }
}
