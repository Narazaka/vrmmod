package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.client.FirstPersonMode;
import com.github.narazaka.vrmmod.client.VrmModClient;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts camera Y position for VRM_VRM_CAMERA first-person mode.
 * Uses the rest-pose eye height computed at model load time.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract net.minecraft.world.phys.Vec3 getPosition();

    @Inject(method = "setup", at = @At("RETURN"))
    private void vrmmod$adjustCameraToVrmEye(BlockGetter level, Entity entity, boolean detached, boolean thirdPerson, float partialTick, CallbackInfo ci) {
        if (detached || thirdPerson) return;
        if (VrmModClient.INSTANCE.getCurrentConfig().getFirstPersonMode() != FirstPersonMode.VRM_VRM_CAMERA) return;

        var mc = Minecraft.getInstance();
        if (mc.player == null || entity != mc.player) return;

        VrmState state = VrmPlayerManager.INSTANCE.get(mc.player.getUUID());
        if (state == null) return;

        // Replace MC eye height with VRM eye height
        var pos = getPosition();
        double mcEyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double vrmEyeHeight = state.getEyeHeight();
        double yOffset = vrmEyeHeight - mcEyeHeight;

        setPosition(pos.x, pos.y + yOffset, pos.z);
    }
}
