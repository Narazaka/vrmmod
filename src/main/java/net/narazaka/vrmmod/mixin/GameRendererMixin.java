package net.narazaka.vrmmod.mixin;

import net.narazaka.vrmmod.client.FirstPersonMode;
import net.narazaka.vrmmod.client.VrmModClient;
import net.narazaka.vrmmod.render.VrmPlayerManager;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Overrides raycast origin in VRM_VRM_CAMERA mode to match the custom camera position.
 * Without this, the crosshair aim and actual hit position diverge because vanilla
 * raycasts from Entity.getEyePosition() which differs from the VRM camera position.
 */
@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final
    private Minecraft minecraft;

    @Shadow @Final
    private Camera mainCamera;

    @Inject(method = "pick(F)V", at = @At("HEAD"), cancellable = true)
    private void vrmmod$pickFromCamera(float partialTick, CallbackInfo ci) {
        if (!minecraft.options.getCameraType().isFirstPerson()) return;
        if (VrmModClient.INSTANCE.getCurrentConfig().getFirstPersonMode() != FirstPersonMode.VRM_VRM_CAMERA) return;

        Entity entity = minecraft.getCameraEntity();
        if (entity == null || minecraft.level == null || minecraft.player == null) return;
        if (VrmPlayerManager.INSTANCE.get(minecraft.player.getUUID()) == null) return;

        // Use camera position as raycast origin instead of entity eye position
        Vec3 origin = mainCamera.getPosition();
        Vec3 direction = entity.getViewVector(partialTick);

        //? if HAS_INTERACTION_RANGE {
        double blockRange = minecraft.player.blockInteractionRange();
        double entityRange = minecraft.player.entityInteractionRange();
        //?} else {
        /*double blockRange = minecraft.gameMode.getPickRange();
        double entityRange = blockRange;*/
        //?}
        double maxRange = Math.max(blockRange, entityRange);
        double maxRangeSq = Mth.square(maxRange);

        // Block raycast
        Vec3 end = origin.add(direction.x * maxRange, direction.y * maxRange, direction.z * maxRange);
        HitResult blockHit = entity.level().clip(
                new ClipContext(origin, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity)
        );

        double blockDistSq = blockHit.getLocation().distanceToSqr(origin);
        if (blockHit.getType() != HitResult.Type.MISS) {
            maxRangeSq = blockDistSq;
            maxRange = Math.sqrt(blockDistSq);
        }

        // Entity raycast
        Vec3 entityEnd = origin.add(direction.x * maxRange, direction.y * maxRange, direction.z * maxRange);
        AABB searchBox = entity.getBoundingBox().expandTowards(direction.scale(maxRange)).inflate(1.0, 1.0, 1.0);
        //? if HAS_APPROXIMATE_NEAREST {
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                entity, origin, entityEnd, searchBox, EntitySelector.CAN_BE_PICKED, maxRangeSq
        );
        //?} else {
        /*EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                entity, origin, entityEnd, searchBox, e -> !e.isSpectator() && e.isPickable(), maxRangeSq
        );*/
        //?}

        HitResult result;
        if (entityHit != null && entityHit.getLocation().distanceToSqr(origin) < blockDistSq) {
            result = filterHitResult(entityHit, origin, entityRange);
        } else {
            result = filterHitResult(blockHit, origin, blockRange);
        }

        minecraft.hitResult = result;
        minecraft.crosshairPickEntity = result instanceof EntityHitResult ehr ? ehr.getEntity() : null;
        ci.cancel();
    }

    private static HitResult filterHitResult(HitResult hit, Vec3 origin, double range) {
        return net.narazaka.vrmmod.render.MixinHelper.filterHitResult(hit, origin, range);
    }
}
