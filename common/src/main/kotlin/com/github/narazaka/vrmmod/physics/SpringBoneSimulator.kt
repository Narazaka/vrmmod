package com.github.narazaka.vrmmod.physics

import com.github.narazaka.vrmmod.vrm.ColliderShape
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import com.github.narazaka.vrmmod.vrm.VrmSpringBone
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * Verlet integration-based SpringBone simulator conforming to VRMC_springBone 1.0.
 *
 * Follows the UniVRM reference implementation:
 * - Parent bone world rotation used for stiffness direction
 * - Entity world space for physics (modelToWorld applied)
 * - Center node space for tail storage when center is specified
 * - World-space rotation calculation matching UniVRM
 */
class SpringBoneSimulator(
    private val springBone: VrmSpringBone,
    private val skeleton: VrmSkeleton,
) {

    private class JointState(
        /** Tail position stored in center-entity-world space (or entity-world space if no center). */
        val currentTail: Vector3f = Vector3f(),
        /** Previous tail position stored in center-entity-world space (or entity-world space if no center). */
        val prevTail: Vector3f = Vector3f(),
        var boneLength: Float = 0f,
        val boneAxis: Vector3f = Vector3f(0f, 1f, 0f),
        val initialLocalRotation: Quaternionf = Quaternionf(),
    )

    private val jointStates: List<List<JointState>> = springBone.springs.map { spring ->
        spring.joints.map { JointState() }
    }

    /** parentOf[nodeIndex] = parent node index, or -1 if root */
    private val parentOf: IntArray = buildParentLookup()

    private var initialized = false

    private fun buildParentLookup(): IntArray {
        val parent = IntArray(skeleton.nodes.size) { -1 }
        for ((nodeIndex, node) in skeleton.nodes.withIndex()) {
            for (childIdx in node.childIndices) {
                if (childIdx in skeleton.nodes.indices) {
                    parent[childIdx] = nodeIndex
                }
            }
        }
        return parent
    }

    /**
     * Initializes joint states from the current world matrices (rest pose).
     *
     * @param worldMatrices model-space world matrices for all nodes
     * @param modelToWorld transform from model space to entity world space
     */
    fun initialize(worldMatrices: List<Matrix4f>, modelToWorld: Matrix4f = Matrix4f()) {
        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            // Precompute center entity-world matrix if center exists
            val centerEntityWorld = if (centerNodeIndex >= 0) {
                val centerModel = worldMatrices.getOrNull(centerNodeIndex)
                if (centerModel != null) Matrix4f(modelToWorld).mul(centerModel) else null
            } else null
            val centerEntityWorldInv = centerEntityWorld?.let { Matrix4f(it).invert() }

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val node = skeleton.nodes[nodeIndex]
                val state = jointStates[springIdx][jointIdx]
                state.initialLocalRotation.set(node.rotation)

                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue
                val headPos = Vector3f()
                worldMatrix.getTranslation(headPos)

                // Tail position in model space: next joint's world pos, or computed from children
                val tailModelPos = if (jointIdx + 1 < joints.size) {
                    val nextNodeIndex = joints[jointIdx + 1].nodeIndex
                    val nextWorld = worldMatrices.getOrNull(nextNodeIndex)
                    if (nextWorld != null) {
                        val p = Vector3f()
                        nextWorld.getTranslation(p)
                        p
                    } else {
                        computeTailFromChildren(nodeIndex, worldMatrices, headPos)
                    }
                } else {
                    computeTailFromChildren(nodeIndex, worldMatrices, headPos)
                }

                val boneLength = headPos.distance(tailModelPos)
                state.boneLength = boneLength

                // Bone axis in local space
                if (boneLength > 1e-6f) {
                    val localTail = Vector3f(tailModelPos).sub(headPos)
                    val worldRot = Quaternionf()
                    worldMatrix.getNormalizedRotation(worldRot)
                    val invWorldRot = Quaternionf(worldRot).conjugate()
                    localTail.rotate(invWorldRot)
                    localTail.normalize()
                    state.boneAxis.set(localTail)
                } else {
                    state.boneAxis.set(0f, 1f, 0f)
                }

                // Transform tail to entity world space
                val tailEntityWorld = Vector3f(tailModelPos)
                modelToWorld.transformPosition(tailEntityWorld)

                // Store in appropriate space
                if (centerEntityWorldInv != null) {
                    // Store in center-entity-world local space
                    val stored = Vector3f(tailEntityWorld)
                    centerEntityWorldInv.transformPosition(stored)
                    state.currentTail.set(stored)
                    state.prevTail.set(stored)
                } else {
                    // Store in entity world space
                    state.currentTail.set(tailEntityWorld)
                    state.prevTail.set(tailEntityWorld)
                }
            }
        }
        initialized = true
    }

    /**
     * Updates the simulation by one step and returns rotation overrides per node.
     *
     * @param worldMatrices current model-space world matrices for all nodes
     * @param deltaTime time step in seconds
     * @param modelToWorld transform from model space to entity world space
     * @return map of nodeIndex to rotation override (in local space)
     */
    fun update(
        worldMatrices: List<Matrix4f>,
        deltaTime: Float,
        modelToWorld: Matrix4f = Matrix4f(),
    ): Map<Int, Quaternionf> {
        if (!initialized) {
            initialize(worldMatrices, modelToWorld)
            return emptyMap()
        }

        val rotationOverrides = mutableMapOf<Int, Quaternionf>()

        // Extract modelToWorld rotation once
        val modelToWorldRot = Quaternionf()
        modelToWorld.getNormalizedRotation(modelToWorldRot)

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            // Compute center entity-world matrix if center exists
            val centerEntityWorld = if (centerNodeIndex >= 0) {
                val centerModel = worldMatrices.getOrNull(centerNodeIndex)
                if (centerModel != null) Matrix4f(modelToWorld).mul(centerModel) else null
            } else null
            val centerEntityWorldInv = centerEntityWorld?.let { Matrix4f(it).invert() }

            // Resolve colliders in entity world space
            val colliders = resolveColliders(spring.colliderGroupIndices, worldMatrices, modelToWorld)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val state = jointStates[springIdx][jointIdx]
                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue

                // Head position in entity world space
                val headModelPos = Vector3f()
                worldMatrix.getTranslation(headModelPos)
                val headPos = Vector3f(headModelPos)
                modelToWorld.transformPosition(headPos)

                // Restore tails to entity world space
                val currentTail: Vector3f
                val prevTail: Vector3f
                if (centerEntityWorld != null) {
                    currentTail = Vector3f(state.currentTail)
                    centerEntityWorld.transformPosition(currentTail)
                    prevTail = Vector3f(state.prevTail)
                    centerEntityWorld.transformPosition(prevTail)
                } else {
                    currentTail = Vector3f(state.currentTail)
                    prevTail = Vector3f(state.prevTail)
                }

                // Get parent rotation in entity world space
                val parentNodeIdx = parentOf[nodeIndex]
                val parentWorldRot = if (parentNodeIdx >= 0) {
                    val parentModelRot = Quaternionf()
                    worldMatrices.getOrNull(parentNodeIdx)?.getNormalizedRotation(parentModelRot)
                    Quaternionf(modelToWorldRot).mul(parentModelRot)
                } else {
                    Quaternionf(modelToWorldRot)
                }

                // Stiffness direction: rest tail direction in entity world space
                // restDir = parentWorldRot * initialLocalRotation * boneAxis
                val restDir = Vector3f(state.boneAxis)
                val restRot = Quaternionf(parentWorldRot).mul(state.initialLocalRotation)
                restDir.rotate(restRot)

                // Verlet integration
                val inertia = Vector3f(currentTail).sub(prevTail)
                    .mul(1f - joint.dragForce)

                val stiffnessForce = Vector3f(restDir)
                    .mul(joint.stiffness * deltaTime)

                val externalForce = Vector3f(joint.gravityDir)
                    .mul(joint.gravityPower * deltaTime)

                val nextTail = Vector3f(currentTail)
                    .add(inertia)
                    .add(stiffnessForce)
                    .add(externalForce)

                // Length constraint
                constrainLength(nextTail, headPos, state.boneLength)

                // Collider collision (in entity world space)
                resolveCollisions(nextTail, headPos, state.boneLength, joint.hitRadius, colliders)

                // Store tails
                if (centerEntityWorldInv != null) {
                    val storedNext = Vector3f(nextTail)
                    centerEntityWorldInv.transformPosition(storedNext)
                    val storedCur = Vector3f(currentTail)
                    centerEntityWorldInv.transformPosition(storedCur)
                    state.prevTail.set(storedCur)
                    state.currentTail.set(storedNext)
                } else {
                    state.prevTail.set(currentTail)
                    state.currentTail.set(nextTail)
                }

                // Compute rotation (UniVRM world-space approach)
                // restDir already computed above
                val actualDir = Vector3f(nextTail).sub(headPos)
                if (actualDir.length() < 1e-6f) continue

                val worldRestRot = Quaternionf(parentWorldRot).mul(state.initialLocalRotation)
                val newWorldRot = Quaternionf(fromToRotation(restDir, actualDir)).mul(worldRestRot)

                // Convert to local: newLocalRot = inv(parentWorldRot) * newWorldRot
                val invParentWorldRot = Quaternionf(parentWorldRot).conjugate()
                val newLocalRot = Quaternionf(invParentWorldRot).mul(newWorldRot)

                rotationOverrides[nodeIndex] = newLocalRot
            }
        }

        return rotationOverrides
    }

    private fun computeTailFromChildren(
        nodeIndex: Int,
        worldMatrices: List<Matrix4f>,
        headPos: Vector3f,
    ): Vector3f {
        val node = skeleton.nodes[nodeIndex]
        for (childIdx in node.childIndices) {
            val childWorld = worldMatrices.getOrNull(childIdx) ?: continue
            val childPos = Vector3f()
            childWorld.getTranslation(childPos)
            if (childPos.distance(headPos) > 1e-6f) {
                return childPos
            }
        }
        // Fallback: extend 0.07 units along local Y
        val worldMatrix = worldMatrices[nodeIndex]
        val worldRot = Quaternionf()
        worldMatrix.getNormalizedRotation(worldRot)
        val localUp = Vector3f(0f, 0.07f, 0f).rotate(worldRot)
        return Vector3f(headPos).add(localUp)
    }

    private sealed class ResolvedCollider {
        data class Sphere(val position: Vector3f, val radius: Float) : ResolvedCollider()
        data class Capsule(val start: Vector3f, val end: Vector3f, val radius: Float) : ResolvedCollider()
    }

    /**
     * Resolves colliders into entity world space positions.
     */
    private fun resolveColliders(
        colliderGroupIndices: List<Int>,
        worldMatrices: List<Matrix4f>,
        modelToWorld: Matrix4f,
    ): List<ResolvedCollider> {
        val result = mutableListOf<ResolvedCollider>()
        for (groupIdx in colliderGroupIndices) {
            val group = springBone.colliderGroups.getOrNull(groupIdx) ?: continue
            for (colliderIdx in group.colliderIndices) {
                val collider = springBone.colliders.getOrNull(colliderIdx) ?: continue
                val nodeWorld = worldMatrices.getOrNull(collider.nodeIndex) ?: continue
                // Compute entity-world transform for this collider node
                val nodeEntityWorld = Matrix4f(modelToWorld).mul(nodeWorld)
                when (val shape = collider.shape) {
                    is ColliderShape.Sphere -> {
                        val worldPos = Vector3f(shape.offset)
                        nodeEntityWorld.transformPosition(worldPos)
                        result.add(ResolvedCollider.Sphere(worldPos, shape.radius))
                    }
                    is ColliderShape.Capsule -> {
                        val worldOffset = Vector3f(shape.offset)
                        nodeEntityWorld.transformPosition(worldOffset)
                        val worldTail = Vector3f(shape.tail)
                        nodeEntityWorld.transformPosition(worldTail)
                        result.add(ResolvedCollider.Capsule(worldOffset, worldTail, shape.radius))
                    }
                }
            }
        }
        return result
    }

    private fun constrainLength(nextTail: Vector3f, headPos: Vector3f, boneLength: Float) {
        val diff = Vector3f(nextTail).sub(headPos)
        val diffLen = diff.length()
        if (diffLen > 1e-6f) {
            diff.normalize().mul(boneLength)
            nextTail.set(headPos).add(diff)
        }
    }

    private fun resolveCollisions(
        nextTail: Vector3f,
        headPos: Vector3f,
        boneLength: Float,
        hitRadius: Float,
        colliders: List<ResolvedCollider>,
    ) {
        for (collider in colliders) {
            when (collider) {
                is ResolvedCollider.Sphere -> {
                    pushOutSphere(nextTail, collider.position, hitRadius + collider.radius)
                    constrainLength(nextTail, headPos, boneLength)
                }
                is ResolvedCollider.Capsule -> {
                    pushOutCapsule(nextTail, collider.start, collider.end, hitRadius + collider.radius)
                    constrainLength(nextTail, headPos, boneLength)
                }
            }
        }
    }

    private fun pushOutSphere(point: Vector3f, center: Vector3f, totalRadius: Float) {
        val diff = Vector3f(point).sub(center)
        val dist = diff.length()
        if (dist < totalRadius && dist > 1e-6f) {
            diff.normalize().mul(totalRadius)
            point.set(center).add(diff)
        }
    }

    private fun pushOutCapsule(point: Vector3f, start: Vector3f, end: Vector3f, totalRadius: Float) {
        val segDir = Vector3f(end).sub(start)
        val segLen = segDir.length()
        if (segLen < 1e-6f) {
            pushOutSphere(point, start, totalRadius)
            return
        }
        segDir.normalize()
        val toPoint = Vector3f(point).sub(start)
        val t = toPoint.dot(segDir).coerceIn(0f, segLen)
        val closestPoint = Vector3f(start).add(Vector3f(segDir).mul(t))
        pushOutSphere(point, closestPoint, totalRadius)
    }

    companion object {
        /**
         * Computes a quaternion that rotates vector [from] to vector [to].
         */
        fun fromToRotation(from: Vector3f, to: Vector3f): Quaternionf {
            val fromN = Vector3f(from).normalize()
            val toN = Vector3f(to).normalize()

            val dot = fromN.dot(toN)
            if (dot > 0.999999f) {
                return Quaternionf()
            }
            if (dot < -0.999999f) {
                var axis = Vector3f(1f, 0f, 0f).cross(fromN)
                if (axis.length() < 1e-6f) {
                    axis = Vector3f(0f, 1f, 0f).cross(fromN)
                }
                axis.normalize()
                return Quaternionf().rotationAxis(Math.PI.toFloat(), axis.x, axis.y, axis.z)
            }

            val cross = Vector3f(fromN).cross(toN)
            val w = 1f + dot
            return Quaternionf(cross.x, cross.y, cross.z, w).normalize()
        }
    }
}
