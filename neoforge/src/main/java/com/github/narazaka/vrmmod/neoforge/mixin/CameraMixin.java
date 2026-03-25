package com.github.narazaka.vrmmod.neoforge.mixin;

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

        Vector3f eyeOffset = state.getCurrentEyeOffset();
        var pos = getPosition();

        double mcEyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double feetY = pos.y - mcEyeHeight;

        double bodyYawRad = Math.toRadians(
                mc.player.yBodyRotO + (mc.player.yBodyRot - mc.player.yBodyRotO) * partialTick
        );
        double angle = bodyYawRad + Math.PI;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double mz = -eyeOffset.z;
        double worldOffsetX = eyeOffset.x * cos - mz * sin;
        double worldOffsetZ = eyeOffset.x * sin + mz * cos;

        setPosition(
                pos.x + worldOffsetX,
                feetY + eyeOffset.y,
                pos.z + worldOffsetZ
        );
    }
}
