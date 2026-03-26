package net.narazaka.vrmmod.neoforge.mixin;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.narazaka.vrmmod.render.VrmRenderContext;

@Mixin(PlayerRenderer.class)
public class PlayerRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;F)V",
            at = @At("HEAD"))
    private void vrmmod$capturePlayer(AbstractClientPlayer player, PlayerRenderState state, float partialTick, CallbackInfo ci) {
        VrmRenderContext.CURRENT_PLAYER_UUID.set(player.getUUID());
        Vec3 pos = player.getPosition(partialTick);
        VrmRenderContext.ENTITY_X.set((float) pos.x);
        VrmRenderContext.ENTITY_Y.set((float) pos.y);
        VrmRenderContext.ENTITY_Z.set((float) pos.z);
        VrmRenderContext.ON_GROUND.set(player.onGround());
        VrmRenderContext.HURT_TIME.set((float) player.hurtTime);
        var mainHandItem = player.getMainHandItem();
        VrmRenderContext.IS_HOLDING_WEAPON.set(
                mainHandItem.is(ItemTags.SWORDS) || mainHandItem.is(ItemTags.AXES) ||
                mainHandItem.is(ItemTags.PICKAXES) || mainHandItem.is(ItemTags.SHOVELS) ||
                mainHandItem.is(ItemTags.HOES)
        );
        VrmRenderContext.IS_HOLDING_ITEM.set(!mainHandItem.isEmpty());
    }
}
