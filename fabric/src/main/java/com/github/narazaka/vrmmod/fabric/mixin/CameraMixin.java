package com.github.narazaka.vrmmod.fabric.mixin;

import com.github.narazaka.vrmmod.client.FirstPersonMode;
import com.github.narazaka.vrmmod.client.VrmModClient;
import com.github.narazaka.vrmmod.render.VrmPlayerManager;
import com.github.narazaka.vrmmod.render.VrmState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adjusts camera position for VRM_VRM_CAMERA first-person mode.
 *
 * Y: MC camera Y + (VRM eye height - MC eye height) offset
 * XZ: animated HEAD bone XZ offset rotated to world space
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

        var pos = getPosition();
        Vector3f eyeOffset = state.getCurrentEyeOffset();

        // Y: add the difference between VRM eye height and MC eye height.
        // This preserves MC's sneaking camera drop while adjusting to VRM model height.
        double mcEyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double yDelta = state.getEyeHeight() - mcEyeHeight;

        // XZ: animated HEAD offset, rotated from model space to world space by bodyYaw
        double bodyYawRad = Math.toRadians(
                mc.player.yBodyRotO + (mc.player.yBodyRot - mc.player.yBodyRotO) * partialTick
        );
        double cos = Math.cos(-bodyYawRad);
        double sin = Math.sin(-bodyYawRad);
        // Z-flip: model space Z is flipped relative to MC
        double worldOffsetX = eyeOffset.x * cos - (-eyeOffset.z) * sin;
        double worldOffsetZ = eyeOffset.x * sin + (-eyeOffset.z) * cos;

        setPosition(
                pos.x + worldOffsetX,
                pos.y + yDelta,
                pos.z + worldOffsetZ
        );
    }
}
