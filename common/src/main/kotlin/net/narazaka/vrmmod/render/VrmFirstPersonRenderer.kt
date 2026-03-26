package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.PoseContext
import net.narazaka.vrmmod.client.FirstPersonMode
import net.narazaka.vrmmod.client.VrmModClient
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.LightTexture
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LightLayer

/**
 * Handles VRM model rendering in first-person view.
 *
 * In first-person, MC does not call the entity renderer for the local player.
 * This class hooks into the world rendering pipeline to manually render the
 * VRM model from the camera's perspective.
 */
object VrmFirstPersonRenderer {

    fun renderFirstPerson(
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTick: Float,
    ) {
        val mc = Minecraft.getInstance()
        if (!mc.options.getCameraType().isFirstPerson()) return
        if (VrmModClient.currentConfig.firstPersonMode == FirstPersonMode.VANILLA) return

        val player = mc.player ?: return
        val state = VrmPlayerManager.get(player.uuid) ?: return

        // Build PoseContext from player data
        val bodyRot = player.yBodyRotO + (player.yBodyRot - player.yBodyRotO) * partialTick
        val headYaw = player.yRotO + (player.yRot - player.yRotO) * partialTick
        val headPitch = player.xRotO + (player.xRot - player.xRotO) * partialTick
        val walkPos = player.walkAnimation.position(partialTick)
        val walkSpeed = player.walkAnimation.speed(partialTick)

        val poseContext = PoseContext(
            partialTick = partialTick,
            limbSwing = walkPos,
            limbSwingAmount = walkSpeed,
            speedValue = if (player.isSprinting) 1.0f else walkSpeed,
            isSneaking = player.isCrouching,
            isSwimming = player.isSwimming,
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
            entityX = (player.xOld + (player.x - player.xOld) * partialTick).toFloat(),
            entityY = (player.yOld + (player.y - player.yOld) * partialTick).toFloat(),
            entityZ = (player.zOld + (player.z - player.zOld) * partialTick).toFloat(),
            hurtTime = player.hurtTime.toFloat(),
        )

        // Compute light at player position
        val blockPos = BlockPos.containing(player.x, player.eyeY, player.z)
        val blockLight = player.level().getBrightness(LightLayer.BLOCK, blockPos)
        val skyLight = player.level().getBrightness(LightLayer.SKY, blockPos)
        val packedLight = LightTexture.pack(blockLight, skyLight)

        // Position the model at the player's render position (camera-relative)
        poseStack.pushPose()

        val camPos = mc.gameRenderer.mainCamera.position
        val renderX = (player.xOld + (player.x - player.xOld) * partialTick) - camPos.x
        val renderY = (player.yOld + (player.y - player.yOld) * partialTick) - camPos.y
        val renderZ = (player.zOld + (player.z - player.zOld) * partialTick) - camPos.z
        poseStack.translate(renderX, renderY, renderZ)

        VrmRenderer.render(state, poseContext, poseStack, bufferSource, packedLight, isFirstPerson = true)

        poseStack.popPose()
    }
}
