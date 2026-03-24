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
 * Follows the UniVRM reference implementation.
 */
class SpringBoneSimulator(
    private val springBone: VrmSpringBone,
    private val skeleton: VrmSkeleton,
) {

    private class JointState(
        val currentTail: Vector3f = Vector3f(),
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
     */
    fun initialize(worldMatrices: List<Matrix4f>) {
        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
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

                state.currentTail.set(tailPos)
                state.prevTail.set(tailPos)
            }
        }
        initialized = true
    }

    /**
     * Updates the simulation by one step and returns rotation overrides per node.
     *
     * @param worldMatrices current world matrices for all nodes
     * @param deltaTime time step in seconds
     * @return map of nodeIndex to rotation override (in local space)
     */
    fun update(worldMatrices: List<Matrix4f>, deltaTime: Float): Map<Int, Quaternionf> {
        if (!initialized) {
            initialize(worldMatrices)
            return emptyMap()
        }

        val rotationOverrides = mutableMapOf<Int, Quaternionf>()

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints

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

                // 2a: Inertia (Verlet)
                val inertia = Vector3f(state.currentTail).sub(state.prevTail)
                    .mul(1f - joint.dragForce)

                // 2b: Stiffness force
                val stiffnessDir = Vector3f(state.boneAxis)
                stiffnessDir.rotate(worldRot)
                stiffnessDir.mul(joint.stiffness * deltaTime)

                // 2c: External force (gravity)
                val externalForce = Vector3f(joint.gravityDir).mul(joint.gravityPower * deltaTime)

                // 2d: Next tail
                val nextTail = Vector3f(state.currentTail)
                    .add(inertia)
                    .add(stiffnessDir)
                    .add(externalForce)

                // 2e: Length constraint
                constrainLength(nextTail, headPos, state.boneLength)

                // 2f: Collider collision
                resolveCollisions(nextTail, headPos, state.boneLength, joint.hitRadius, colliders)

                // 2g: Update state
                state.prevTail.set(state.currentTail)
                state.currentTail.set(nextTail)

                // 2h: Compute rotation override
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
