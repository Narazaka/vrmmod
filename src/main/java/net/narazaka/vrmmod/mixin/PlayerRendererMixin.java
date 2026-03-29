package net.narazaka.vrmmod.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;

//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
//?}

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    //? if HAS_RENDER_STATE {
    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        capturePlayerData(player, partialTick);
    }
    //?} else {
    /*@Inject(method = "render(Lnet/minecraft/client/player/AbstractClientPlayer;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, float entityYaw, float partialTick,
            com.mojang.blaze3d.vertex.PoseStack poseStack,
            net.minecraft.client.renderer.MultiBufferSource bufferSource,
            int packedLight, CallbackInfo ci) {
        capturePlayerData(player, partialTick);
    }*/
    //?}

    private static void capturePlayerData(AbstractClientPlayer player, float partialTick) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
        Vec3 pos = player.getPosition(partialTick);
        VrmRenderContext.ENTITY_X.set((float) pos.x);
        VrmRenderContext.ENTITY_Y.set((float) pos.y);
        VrmRenderContext.ENTITY_Z.set((float) pos.z);
        VrmRenderContext.ON_GROUND.set(player.onGround());
        VrmRenderContext.HURT_TIME.set((float) player.hurtTime);

        var mainHandItem = player.getMainHandItem();
        List<String> tags = new ArrayList<>();
        mainHandItem.getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                tags.add(loc.getPath());
            } else {
                tags.add(loc.toString());
            }
        });
        VrmRenderContext.MAIN_HAND_ITEM_TAGS.set(tags);

        var offHandItem = player.getOffhandItem();
        List<String> offTags = new ArrayList<>();
        offHandItem.getTags().forEach(tagKey -> {
            var loc = tagKey.location();
            if (loc.getNamespace().equals("minecraft")) {
                offTags.add(loc.getPath());
            } else {
                offTags.add(loc.toString());
            }
        });
        VrmRenderContext.OFF_HAND_ITEM_TAGS.set(offTags);
    }
}
