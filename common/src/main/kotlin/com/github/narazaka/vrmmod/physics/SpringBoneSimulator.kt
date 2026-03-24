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
 * Follows the UniVRM reference implementation, including center space support.
 *
 * When a spring has a `centerNodeIndex`, tail positions (currentTail/prevTail) are
 * stored in the center node's local space. This ensures that movement of the center
 * node does not produce spurious inertia. At the start of each update, positions are
 * converted to model space for physics, then converted back for storage.
 *
 * When no center is specified (centerNodeIndex < 0), an external `modelToWorld` matrix
 * is used so that entity-level movement (jumping, walking) generates appropriate inertia.
 */
class SpringBoneSimulator(
    private val springBone: VrmSpringBone,
    private val skeleton: VrmSkeleton,
) {

    private class JointState(
        /** Tail position stored in center space (or world space if no center). */
        val currentTail: Vector3f = Vector3f(),
        /** Previous tail position stored in center space (or world space if no center). */
        val prevTail: Vector3f = Vector3f(),
        var boneLength: Float = 0f,
        val boneAxis: Vector3f = Vector3f(0f, 1f, 0f),
        val initialLocalRotation: Quaternionf = Quaternionf(),
    )

    private val jointStates: List<List<JointState>> = springBone.springs.map { spring ->
        spring.joints.map { JointState() }
    }

    private var initialized = false

    /**
     * Initializes joint states from the current world matrices (rest pose).
     *
     * @param worldMatrices model-space world matrices for all nodes
     * @param modelToWorld transform from model space to world space (entity transform)
     */
    fun initialize(worldMatrices: List<Matrix4f>, modelToWorld: Matrix4f = Matrix4f()) {
        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            // Compute center inverse if center is specified
            val centerInverse: Matrix4f? = if (centerNodeIndex >= 0) {
                val centerWorld = worldMatrices.getOrNull(centerNodeIndex)
                centerWorld?.let { Matrix4f(it).invert() }
            } else {
                null
            }

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

                // Tail position: next joint's world pos, or computed from children
                val tailPos = if (jointIdx + 1 < joints.size) {
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

                val boneLength = headPos.distance(tailPos)
                state.boneLength = boneLength

                // Bone axis in local space
                if (boneLength > 1e-6f) {
                    val localTail = Vector3f(tailPos).sub(headPos)
                    val worldRot = Quaternionf()
                    worldMatrix.getNormalizedRotation(worldRot)
                    val invWorldRot = Quaternionf(worldRot).conjugate()
                    localTail.rotate(invWorldRot)
                    localTail.normalize()
                    state.boneAxis.set(localTail)
                } else {
                    state.boneAxis.set(0f, 1f, 0f)
                }

                // Store tail in appropriate space
                if (centerInverse != null) {
                    // Store in center space
                    val tailInCenter = Vector3f(tailPos)
                    centerInverse.transformPosition(tailInCenter)
                    state.currentTail.set(tailInCenter)
                    state.prevTail.set(tailInCenter)
                } else {
                    // Store in world space (using modelToWorld)
                    val tailInWorld = Vector3f(tailPos)
                    modelToWorld.transformPosition(tailInWorld)
                    state.currentTail.set(tailInWorld)
                    state.prevTail.set(tailInWorld)
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
     * @param modelToWorld transform from model space to entity world space;
     *   used for springs without a center node so entity movement creates inertia
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

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            // Compute center transforms if center is specified
            val centerWorld: Matrix4f?
            val centerInverse: Matrix4f?
            if (centerNodeIndex >= 0) {
                val cw = worldMatrices.getOrNull(centerNodeIndex)
                centerWorld = cw
                centerInverse = cw?.let { Matrix4f(it).invert() }
            } else {
                centerWorld = null
                centerInverse = null
            }

            // Inverse of modelToWorld for converting back from world to model space
            val worldToModel = Matrix4f(modelToWorld).invert()

            val colliders = resolveColliders(spring.colliderGroupIndices, worldMatrices)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val state = jointStates[springIdx][jointIdx]
                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue

                val headPos = Vector3f()
                worldMatrix.getTranslation(headPos)

                val worldRot = Quaternionf()
                worldMatrix.getNormalizedRotation(worldRot)

                // Convert stored tail positions to model space for physics
                val currentTail: Vector3f
                val prevTail: Vector3f
                if (centerWorld != null) {
                    // Stored in center space -> convert to model space
                    currentTail = Vector3f(state.currentTail)
                    centerWorld.transformPosition(currentTail)
                    prevTail = Vector3f(state.prevTail)
                    centerWorld.transformPosition(prevTail)
                } else {
                    // Stored in entity world space -> convert to model space
                    currentTail = Vector3f(state.currentTail)
                    worldToModel.transformPosition(currentTail)
                    prevTail = Vector3f(state.prevTail)
                    worldToModel.transformPosition(prevTail)
                }

                // 2a: Inertia (Verlet integration with drag)
                val inertia = Vector3f(currentTail).sub(prevTail)
                    .mul(1f - joint.dragForce)

                // 2b: Stiffness force (UniVRM style):
                // parentRotation * localRotation * boneAxis * stiffness * deltaTime
                val stiffnessDir = Vector3f(state.boneAxis)
                val parentRot = Quaternionf(worldRot)
                val restRot = Quaternionf(parentRot).mul(state.initialLocalRotation)
                stiffnessDir.rotate(restRot)
                val stiffnessForce = stiffnessDir
                    .mul(joint.stiffness * deltaTime * state.boneLength)

                // 2c: External force (gravity), scaled by deltaTime
                val externalForce = Vector3f(joint.gravityDir)
                    .mul(joint.gravityPower * deltaTime * state.boneLength)

                // 2d: Next tail
                val nextTail = Vector3f(currentTail)
                    .add(inertia)
                    .add(stiffnessForce)
                    .add(externalForce)

                // 2e: Length constraint
                constrainLength(nextTail, headPos, state.boneLength)

                // 2f: Collider collision
                resolveCollisions(nextTail, headPos, state.boneLength, joint.hitRadius, colliders)

                // 2g: Store tail back in appropriate space
                if (centerInverse != null) {
                    // Convert model space -> center space for storage
                    val nextInCenter = Vector3f(nextTail)
                    centerInverse.transformPosition(nextInCenter)
                    val curInCenter = Vector3f(currentTail)
                    centerInverse.transformPosition(curInCenter)
                    state.prevTail.set(curInCenter)
                    state.currentTail.set(nextInCenter)
                } else {
                    // Convert model space -> entity world space for storage
                    val nextInWorld = Vector3f(nextTail)
                    modelToWorld.transformPosition(nextInWorld)
                    val curInWorld = Vector3f(currentTail)
                    modelToWorld.transformPosition(curInWorld)
                    state.prevTail.set(curInWorld)
                    state.currentTail.set(nextInWorld)
                }

                // 2h: Compute rotation override (in model space)
                val invWorldRot = Quaternionf(worldRot).conjugate()
                val localNextTail = Vector3f(nextTail).sub(headPos).rotate(invWorldRot)
                if (localNextTail.length() > 1e-6f) {
                    localNextTail.normalize()
                }

                val fromTo = fromToRotation(state.boneAxis, localNextTail)
                val finalRotation = Quaternionf(fromTo).mul(state.initialLocalRotation)
                rotationOverrides[nodeIndex] = finalRotation
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

    private fun resolveColliders(
        colliderGroupIndices: List<Int>,
        worldMatrices: List<Matrix4f>,
    ): List<ResolvedCollider> {
        val result = mutableListOf<ResolvedCollider>()
        for (groupIdx in colliderGroupIndices) {
            val group = springBone.colliderGroups.getOrNull(groupIdx) ?: continue
            for (colliderIdx in group.colliderIndices) {
                val collider = springBone.colliders.getOrNull(colliderIdx) ?: continue
                val nodeWorld = worldMatrices.getOrNull(collider.nodeIndex) ?: continue
                when (val shape = collider.shape) {
                    is ColliderShape.Sphere -> {
                        val worldPos = Vector3f(shape.offset)
                        nodeWorld.transformPosition(worldPos)
                        result.add(ResolvedCollider.Sphere(worldPos, shape.radius))
                    }
                    is ColliderShape.Capsule -> {
                        val worldOffset = Vector3f(shape.offset)
                        nodeWorld.transformPosition(worldOffset)
                        val worldTail = Vector3f(shape.tail)
                        nodeWorld.transformPosition(worldTail)
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
