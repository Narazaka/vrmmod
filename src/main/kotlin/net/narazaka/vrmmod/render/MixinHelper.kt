package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.PoseContext
//? if HAS_RENDER_STATE {
import net.minecraft.client.renderer.entity.state.PlayerRenderState
//?}
import net.minecraft.world.InteractionHand

object MixinHelper {

    //? if HAS_RENDER_STATE {
    @JvmStatic
    fun buildPoseContext(renderState: PlayerRenderState): PoseContext {
        return PoseContext(
            partialTick = 0f,
            limbSwing = renderState.walkAnimationPos,
            limbSwingAmount = renderState.walkAnimationSpeed,
            speedValue = renderState.speedValue,
            isSneaking = renderState.isCrouching,
            isSwimming = renderState.isVisuallySwimming,
            swimAmount = renderState.swimAmount,
            isFallFlying = renderState.isFallFlying,
            isRiding = renderState.isPassenger,
            isInWater = renderState.isInWater,
            isOnGround = VrmRenderContext.ON_GROUND.get(),
            isSwinging = renderState.swinging,
            attackTime = renderState.attackTime,
            isUsingItem = renderState.isUsingItem,
            ticksUsingItem = renderState.ticksUsingItem,
            isAutoSpinAttack = renderState.isAutoSpinAttack,
            deathTime = renderState.deathTime,
            headYaw = renderState.yRot,
            headPitch = renderState.xRot,
            bodyYaw = renderState.bodyRot,
            entityX = VrmRenderContext.ENTITY_X.get(),
            entityY = VrmRenderContext.ENTITY_Y.get(),
            entityZ = VrmRenderContext.ENTITY_Z.get(),
            mainHandItemTags = VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
            offHandItemTags = VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
            isOffHandSwing = renderState.attackArm != renderState.mainArm,
            isOffHandUse = renderState.useItemHand != InteractionHand.MAIN_HAND,
            isLeftHanded = renderState.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT,
            hurtTime = VrmRenderContext.HURT_TIME.get(),
        )
    }
    //?} else {
    /*@JvmStatic
    fun buildPoseContextFromEntity(
        player: net.minecraft.client.player.AbstractClientPlayer,
        entityYaw: Float,
        partialTick: Float,
    ): PoseContext {
        val bodyRot = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick
        val headYaw = player.yRotO + (player.yRot - player.yRotO) * partialTick
        val headPitch = player.xRotO + (player.xRot - player.xRotO) * partialTick
        return PoseContext(
            partialTick = partialTick,
            limbSwing = player.walkAnimation.position(partialTick),
            limbSwingAmount = player.walkAnimation.speed(partialTick),
            speedValue = if (player.isSprinting) 1.0f else player.walkAnimation.speed(partialTick),
            isSneaking = player.isCrouching,
            isSwimming = player.isVisuallySwimming,
            swimAmount = player.getSwimAmount(partialTick),
            isFallFlying = player.isFallFlying,
            isRiding = player.isPassenger,
            isInWater = player.isInWater,
            isOnGround = player.onGround(),
            isSwinging = player.swinging,
            attackTime = player.getAttackAnim(partialTick),
            isUsingItem = player.isUsingItem,
            ticksUsingItem = player.ticksUsingItem,
            isAutoSpinAttack = player.isAutoSpinAttack,
            deathTime = player.deathTime.toFloat(),
            headYaw = headYaw - bodyRot,
            headPitch = headPitch,
            bodyYaw = bodyRot,
            entityX = VrmRenderContext.ENTITY_X.get(),
            entityY = VrmRenderContext.ENTITY_Y.get(),
            entityZ = VrmRenderContext.ENTITY_Z.get(),
            mainHandItemTags = VrmRenderContext.MAIN_HAND_ITEM_TAGS.get(),
            offHandItemTags = VrmRenderContext.OFF_HAND_ITEM_TAGS.get(),
            isOffHandSwing = player.swingingArm != InteractionHand.MAIN_HAND,
            isOffHandUse = player.usedItemHand != InteractionHand.MAIN_HAND,
            isLeftHanded = player.mainArm == net.minecraft.world.entity.HumanoidArm.LEFT,
            hurtTime = player.hurtTime.toFloat(),
        )
    }*/
    //?}

    @JvmStatic
    fun filterHitResult(
        hit: net.minecraft.world.phys.HitResult,
        origin: net.minecraft.world.phys.Vec3,
        range: Double,
    ): net.minecraft.world.phys.HitResult {
        val loc = hit.location
        if (!loc.closerThan(origin, range)) {
            //? if HAS_APPROXIMATE_NEAREST {
            val dir = net.minecraft.core.Direction.getApproximateNearest(
                loc.x - origin.x, loc.y - origin.y, loc.z - origin.z,
            )
            //?} else {
            /*val dir = net.minecraft.core.Direction.getNearest(
                (loc.x - origin.x).toFloat(), (loc.y - origin.y).toFloat(), (loc.z - origin.z).toFloat(),
            )*/
            //?}
            return net.minecraft.world.phys.BlockHitResult.miss(
                //? if HAS_BLOCKPOS_CONTAINING {
                loc, dir, net.minecraft.core.BlockPos.containing(loc),
                //?} else {
                /*loc, dir, net.minecraft.core.BlockPos(net.minecraft.util.Mth.floor(loc.x), net.minecraft.util.Mth.floor(loc.y), net.minecraft.util.Mth.floor(loc.z)),*/
                //?}
            )
        }
        return hit
    }
}
