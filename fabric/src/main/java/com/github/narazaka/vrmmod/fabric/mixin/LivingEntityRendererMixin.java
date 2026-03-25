package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.animation.PoseContext;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmRenderer;
import com.github.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.github.narazaka.vrmmod.render.VrmRenderContext;

import java.util.UUID;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

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

        UUID uuid = VrmRenderContext.CURRENT_PLAYER_UUID.get();
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
        // yRot appears to already be relative to body in MC 1.21.4 render state
        float headYaw = renderState.yRot;
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
                headPitch,
                renderState.bodyRot,
                VrmRenderContext.ENTITY_X.get(),
                VrmRenderContext.ENTITY_Y.get(),
                VrmRenderContext.ENTITY_Z.get(),
                VrmRenderContext.ON_GROUND.get(),
                VrmRenderContext.HURT_TIME.get()
        );
    }
}
