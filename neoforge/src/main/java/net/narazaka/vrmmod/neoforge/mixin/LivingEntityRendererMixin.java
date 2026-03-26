package net.narazaka.vrmmod.neoforge.mixin;

import net.narazaka.vrmmod.animation.PoseContext;
import net.narazaka.vrmmod.render.VrmPlayerManager;
import net.narazaka.vrmmod.render.VrmRenderer;
import net.narazaka.vrmmod.render.VrmState;
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

import net.narazaka.vrmmod.render.VrmRenderContext;

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

        // VRM is rendered in all camera modes including first-person

        VrmState state = VrmPlayerManager.INSTANCE.get(uuid);
        if (state == null) return;

        PoseContext poseContext = buildPoseContext(renderState);
        VrmRenderer.INSTANCE.render(state, poseContext, poseStack, bufferSource, packedLight, false);
        ci.cancel();
    }

    private static PoseContext buildPoseContext(PlayerRenderState renderState) {
        return new PoseContext(
                /* partialTick */       0f,
                /* limbSwing */         renderState.walkAnimationPos,
                /* limbSwingAmount */   renderState.walkAnimationSpeed,
                /* speedValue */        renderState.speedValue,
                /* isSneaking */        renderState.isCrouching,
                /* isSwimming */        renderState.isVisuallySwimming,
                /* swimAmount */        renderState.swimAmount,
                /* isFallFlying */      renderState.isFallFlying,
                /* isRiding */          renderState.isPassenger,
                /* isInWater */         renderState.isInWater,
                /* isOnGround */        VrmRenderContext.ON_GROUND.get(),
                /* isSwinging */        renderState.swinging,
                /* attackTime */        renderState.attackTime,
                /* isUsingItem */       renderState.isUsingItem,
                /* ticksUsingItem */    renderState.ticksUsingItem,
                /* isAutoSpinAttack */  renderState.isAutoSpinAttack,
                /* deathTime */         renderState.deathTime,
                /* headYaw */           renderState.yRot,
                /* headPitch */         renderState.xRot,
                /* bodyYaw */           renderState.bodyRot,
                /* entityX */           VrmRenderContext.ENTITY_X.get(),
                /* entityY */           VrmRenderContext.ENTITY_Y.get(),
                /* entityZ */           VrmRenderContext.ENTITY_Z.get(),
                /* mainHandItemTags */  VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
                /* hurtTime */          VrmRenderContext.HURT_TIME.get()
        );
    }
}
