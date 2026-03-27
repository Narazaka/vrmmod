package net.narazaka.vrmmod.fabric.mixin;

import net.narazaka.vrmmod.client.FirstPersonMode;
import net.narazaka.vrmmod.client.VrmModClient;
import net.narazaka.vrmmod.render.VrmPlayerManager;
import net.narazaka.vrmmod.render.VrmState;
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
 * The camera is positioned at the VRM model's eye position (HEAD bone +
 * lookAt.offsetFromHeadBone), which follows HEAD rotation so the neck
 * interior stays hidden when looking around.
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

        // currentEyeOffset is the eye position relative to entity feet, in MC blocks.
        // It includes HEAD bone rotation effects (XZ offset from looking around).
        Vector3f eyeOffset = state.getCurrentEyeOffset();

        // MC camera is at entity position + eyeHeight.
        // Replace with VRM eye position.
        var pos = getPosition();

        // Entity feet position = current camera pos - MC eye height
        double mcEyeHeight = mc.player.getEyeHeight(mc.player.getPose());
        double feetY = pos.y - mcEyeHeight;

        // Transform eye XZ offset from VRM model space to MC world space.
        // VrmRenderer applies: rotateY(-bodyYaw) + scale
        double bodyYawRad = Math.toRadians(
                mc.player.yBodyRotO + (mc.player.yBodyRot - mc.player.yBodyRotO) * partialTick
        );
        double cos = Math.cos(bodyYawRad);
        double sin = Math.sin(bodyYawRad);
        double worldOffsetX = eyeOffset.x * cos - eyeOffset.z * sin;
        double worldOffsetZ = eyeOffset.x * sin + eyeOffset.z * cos;

        setPosition(
                pos.x + worldOffsetX,
                feetY + eyeOffset.y,
                pos.z + worldOffsetZ
        );
    }
}
