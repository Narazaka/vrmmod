package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.PoseContext
import net.minecraft.client.renderer.entity.state.PlayerRenderState
import net.minecraft.world.InteractionHand

object MixinHelper {
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
}
