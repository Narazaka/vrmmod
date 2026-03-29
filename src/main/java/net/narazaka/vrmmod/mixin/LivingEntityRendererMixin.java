package net.narazaka.vrmmod.mixin;

import net.narazaka.vrmmod.animation.PoseContext;
import net.narazaka.vrmmod.render.VrmPlayerManager;
import net.narazaka.vrmmod.render.VrmRenderer;
import net.narazaka.vrmmod.render.VrmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;
import net.narazaka.vrmmod.render.MixinHelper;

import java.util.UUID;

//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
//?} else {
/*import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.player.AbstractClientPlayer;*/
//?}

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    //? if HAS_RENDER_STATE {
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

        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = MixinHelper.buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        if (state.getAnimationConfig().getHeldItemThirdPerson()) {
            VrmRenderer.INSTANCE.renderHeldItems(
                    state,
                    renderState.rightHandItem,
                    renderState.leftHandItem,
                    poseStack,
                    bufferSource,
                    packedLight,
                    (float) Math.toRadians(renderState.bodyRot),
                    state.getAnimationConfig()
            );
        }
        VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void vrmmod$clearContext(
            LivingEntityRenderState livingRenderState,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (livingRenderState instanceof PlayerRenderState) {
            VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        }
    }
    //?} else {
    /*
    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), cancellable = true)
    private void vrmmod$onRender(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (!(entity instanceof AbstractClientPlayer player)) return;

        UUID uuid = player.getUUID();
        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = MixinHelper.buildPoseContextFromEntity(player, entityYaw, partialTick);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        // Held item rendering not yet implemented for pre-1.21.2
        VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        ci.cancel();
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void vrmmod$clearContext(
            LivingEntity entity,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            CallbackInfo ci
    ) {
        if (entity instanceof AbstractClientPlayer) {
            VrmRenderContext.CURRENT_PLAYER_UUID.remove();
        }
    }
    */
    //?}

}
