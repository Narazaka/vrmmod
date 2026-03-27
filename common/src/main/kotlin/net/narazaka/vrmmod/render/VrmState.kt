package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.animation.AnimationConfig
import net.narazaka.vrmmod.animation.ExpressionController
import net.narazaka.vrmmod.animation.PoseProvider
import net.narazaka.vrmmod.animation.VanillaPoseProvider
import net.narazaka.vrmmod.physics.SpringBoneSimulator
import net.narazaka.vrmmod.vrm.VrmModel
import net.minecraft.resources.ResourceLocation

/**
 * Holds the parsed VRM model and its registered texture locations
 * for a single player.
 */
class VrmState(
    val model: VrmModel,
    val textureLocations: List<ResourceLocation>,
    val poseProvider: PoseProvider = VanillaPoseProvider(),
    val springBoneSimulator: SpringBoneSimulator? = null,
    val expressionController: ExpressionController = ExpressionController(),
    val animationConfig: AnimationConfig = AnimationConfig(),
    /** VRM model's eye height in MC blocks at rest pose (scaled). */
    val eyeHeight: Float = 1.62f,
    /** Rest-pose world matrices (computed once at model load). */
    val restPoseWorldMatrices: List<org.joml.Matrix4f> = emptyList(),
    /** Scale factor from rest-pose hips Y (cached at model load). */
    var cachedScale: Float = 0.9f,
) {
    /** Head descendant node indices (cached at model load). */
    val headDescendantNodes: Set<Int> = run {
        val headBone = model.humanoid.humanBones[net.narazaka.vrmmod.vrm.HumanBone.HEAD]
        if (headBone != null) {
            val descendants = mutableSetOf<Int>()
            fun dfs(idx: Int) {
                descendants.add(idx)
                for (child in model.skeleton.nodes.getOrNull(idx)?.childIndices ?: emptyList()) dfs(child)
            }
            dfs(headBone.nodeIndex)
            descendants.toSet()
        } else emptySet()
    }

    /** Head joint indices per skin (cached on first access). */
    val headJointIndicesCache: MutableMap<Int, Set<Int>> = mutableMapOf()
    /**
     * Eye position offset from entity feet in MC blocks, updated each frame.
     * Includes HEAD bone rotation effects but not walk animation translation.
     * Used by CameraMixin for VRM_VRM_CAMERA mode.
     */
    @Volatile
    var currentEyeOffset: org.joml.Vector3f = org.joml.Vector3f(0f, eyeHeight, 0f)
    @Volatile
    var rightHandMatrix: org.joml.Matrix4f? = null
    @Volatile
    var leftHandMatrix: org.joml.Matrix4f? = null
    @Volatile
    var lastRenderTimeNano: Long = 0L
}
