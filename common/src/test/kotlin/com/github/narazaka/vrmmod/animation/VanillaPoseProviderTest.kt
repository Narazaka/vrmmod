package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

class VanillaPoseProviderTest {

    private val provider = VanillaPoseProvider()
    private val skeleton = VrmSkeleton() // empty skeleton is fine for pose computation

    private fun idleContext() = PoseContext(
        partialTick = 0f,
        limbSwing = 0f,
        limbSwingAmount = 0f,
        isSwinging = false,
        isSneaking = false,
        isSprinting = false,
        isSwimming = false,
        isFallFlying = false,
        isRiding = false,
        headYaw = 0f,
        headPitch = 0f,
    )

    // ---- Idle ----

    @Test
    fun `idle returns non-empty map`() {
        val poses = provider.computePose(skeleton, idleContext())
        assertTrue(poses.isNotEmpty(), "Idle pose should contain at least head")
        assertNotNull(poses[HumanBone.HEAD], "Idle should have HEAD pose")
    }

    @Test
    fun `idle head has identity-like rotation when yaw and pitch are zero`() {
        val poses = provider.computePose(skeleton, idleContext())
        val headRot = poses[HumanBone.HEAD]!!.rotation
        // Should be close to identity
        assertTrue(abs(headRot.x) < 1e-4f, "Head X rotation should be ~0")
        assertTrue(abs(headRot.y) < 1e-4f, "Head Y rotation should be ~0")
        assertTrue(abs(headRot.z) < 1e-4f, "Head Z rotation should be ~0")
        assertTrue(abs(headRot.w - 1f) < 1e-4f, "Head W should be ~1")
    }

    // ---- Head follows yaw/pitch ----

    @Test
    fun `head follows yaw`() {
        val ctx = idleContext().copy(headYaw = 45f)
        val poses = provider.computePose(skeleton, ctx)
        val headRot = poses[HumanBone.HEAD]!!.rotation
        // Y component should be non-zero for yaw
        assertTrue(abs(headRot.y) > 0.1f, "Head should have Y rotation for yaw, got ${headRot.y}")
    }

    @Test
    fun `head follows pitch`() {
        val ctx = idleContext().copy(headPitch = 30f)
        val poses = provider.computePose(skeleton, ctx)
        val headRot = poses[HumanBone.HEAD]!!.rotation
        // X component should be non-zero for pitch
        assertTrue(abs(headRot.x) > 0.1f, "Head should have X rotation for pitch, got ${headRot.x}")
    }

    // ---- Walking has leg rotation ----

    @Test
    fun `walking has leg rotation`() {
        val ctx = idleContext().copy(limbSwing = 1.0f, limbSwingAmount = 1.0f)
        val poses = provider.computePose(skeleton, ctx)

        val rightLeg = poses[HumanBone.RIGHT_UPPER_LEG]
        val leftLeg = poses[HumanBone.LEFT_UPPER_LEG]
        assertNotNull(rightLeg, "Walking should have right leg pose")
        assertNotNull(leftLeg, "Walking should have left leg pose")

        // Legs should have opposite X rotations
        assertTrue(abs(rightLeg!!.rotation.x) > 0.01f, "Right leg should rotate during walk")
        assertTrue(abs(leftLeg!!.rotation.x) > 0.01f, "Left leg should rotate during walk")
    }

    @Test
    fun `walking legs are opposite`() {
        val ctx = idleContext().copy(limbSwing = 1.0f, limbSwingAmount = 1.0f)
        val poses = provider.computePose(skeleton, ctx)

        val rightLeg = poses[HumanBone.RIGHT_UPPER_LEG]!!.rotation
        val leftLeg = poses[HumanBone.LEFT_UPPER_LEG]!!.rotation

        // They should have opposite X rotation signs (approximately)
        assertTrue(
            rightLeg.x * leftLeg.x < 0 || (abs(rightLeg.x) < 1e-4f && abs(leftLeg.x) < 1e-4f),
            "Legs should swing in opposite directions: right=${rightLeg.x}, left=${leftLeg.x}",
        )
    }

    // ---- Arms swing during walk ----

    @Test
    fun `walking has arm rotation`() {
        val ctx = idleContext().copy(limbSwing = 1.0f, limbSwingAmount = 1.0f)
        val poses = provider.computePose(skeleton, ctx)

        assertNotNull(poses[HumanBone.RIGHT_UPPER_ARM], "Walking should move right arm")
        assertNotNull(poses[HumanBone.LEFT_UPPER_ARM], "Walking should move left arm")
    }

    // ---- Attack swing ----

    @Test
    fun `swing attack moves right arm down`() {
        val ctx = idleContext().copy(isSwinging = true)
        val poses = provider.computePose(skeleton, ctx)

        val rightArm = poses[HumanBone.RIGHT_UPPER_ARM]
        assertNotNull(rightArm, "Swing should affect right arm")
        // Should have significant negative X rotation (arm swung down)
        assertTrue(rightArm!!.rotation.x < -0.1f, "Right arm should swing down, got ${rightArm.rotation.x}")
    }

    // ---- Sneaking ----

    @Test
    fun `sneaking leans spine forward`() {
        val ctx = idleContext().copy(isSneaking = true)
        val poses = provider.computePose(skeleton, ctx)

        val spine = poses[HumanBone.SPINE]
        assertNotNull(spine, "Sneaking should affect spine")
        assertTrue(spine!!.rotation.x > 0.1f, "Spine should lean forward when sneaking")
    }

    // ---- Swimming ----

    @Test
    fun `swimming rotates hips forward`() {
        val ctx = idleContext().copy(isSwimming = true, limbSwing = 1f, limbSwingAmount = 0.5f)
        val poses = provider.computePose(skeleton, ctx)

        val hips = poses[HumanBone.HIPS]
        assertNotNull(hips, "Swimming should affect hips")
        assertTrue(hips!!.rotation.x > 0.5f, "Hips should pitch forward when swimming")
    }

    // ---- Elytra ----

    @Test
    fun `elytra pitches hips forward`() {
        val ctx = idleContext().copy(isFallFlying = true)
        val poses = provider.computePose(skeleton, ctx)

        val hips = poses[HumanBone.HIPS]
        assertNotNull(hips, "Elytra should affect hips")
        assertTrue(hips!!.rotation.x > 0.5f, "Hips should pitch forward during elytra flight")
    }

    // ---- Riding ----

    @Test
    fun `riding spreads and bends legs`() {
        val ctx = idleContext().copy(isRiding = true)
        val poses = provider.computePose(skeleton, ctx)

        assertNotNull(poses[HumanBone.RIGHT_UPPER_LEG], "Riding should affect right leg")
        assertNotNull(poses[HumanBone.LEFT_UPPER_LEG], "Riding should affect left leg")
        assertNotNull(poses[HumanBone.RIGHT_LOWER_LEG], "Riding should affect right knee")
        assertNotNull(poses[HumanBone.LEFT_LOWER_LEG], "Riding should affect left knee")
    }
}
