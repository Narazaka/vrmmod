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
 * Physics runs entirely in **model space**. Entity movement (jumps, walking)
 * is tracked via a position delta applied to prevTail each frame, so that
 * the Verlet inertia term picks up the motion.
 *
 * When a spring has a center node, tails are stored in center-model-space
 * so that center movement does not generate spurious inertia.
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

    private val parentOf: IntArray = buildParentLookup()
    private var initialized = false

    /** Previous frame entity world position (for computing movement delta). */
    private val prevEntityPos = Vector3f()

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

    fun initialize(worldMatrices: List<Matrix4f>, entityPos: Vector3f = Vector3f()) {
        prevEntityPos.set(entityPos)

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            val centerInv = if (centerNodeIndex >= 0) {
                worldMatrices.getOrNull(centerNodeIndex)?.let { Matrix4f(it).invert() }
            } else null

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val node = skeleton.nodes[nodeIndex]
                val state = jointStates[springIdx][jointIdx]
                state.initialLocalRotation.set(node.rotation)

                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue
                val headPos = Vector3f(); worldMatrix.getTranslation(headPos)

                val tailPos = computeTailPos(jointIdx, joints, worldMatrices, headPos, nodeIndex)
                state.boneLength = headPos.distance(tailPos)

                if (state.boneLength > 1e-6f) {
                    val localTail = Vector3f(tailPos).sub(headPos)
                    val worldRot = Quaternionf(); worldMatrix.getNormalizedRotation(worldRot)
                    localTail.rotate(Quaternionf(worldRot).conjugate())
                    localTail.normalize()
                    state.boneAxis.set(localTail)
                }

                // Store in center space or model space
                if (centerInv != null) {
                    val stored = Vector3f(tailPos)
                    centerInv.transformPosition(stored)
                    state.currentTail.set(stored)
                    state.prevTail.set(stored)
                } else {
                    state.currentTail.set(tailPos)
                    state.prevTail.set(tailPos)
                }
            }
        }
        initialized = true
    }

    /**
     * @param worldMatrices model-space world matrices
     * @param deltaTime seconds per frame
     * @param entityPos entity's absolute world position (for inertia from movement)
     */
    /**
     * @param worldMatrices model-space world matrices
     * @param deltaTime seconds per frame
     * @param entityPos entity's absolute world position (for inertia from movement)
     * @param modelScale uniform scale factor (world blocks -> model units)
     */
    fun update(
        worldMatrices: List<Matrix4f>,
        deltaTime: Float,
        entityPos: Vector3f = Vector3f(),
        modelScale: Float = 1f,
    ): Map<Int, Quaternionf> {
        if (!initialized) {
            initialize(worldMatrices, entityPos)
            return emptyMap()
        }

        // Entity movement delta converted to model space units.
        // entityDelta is in world blocks; divide by modelScale to get model-space distance.
        val entityDelta = Vector3f(entityPos).sub(prevEntityPos)
        if (modelScale > 1e-6f) {
            entityDelta.div(modelScale)
        }
        prevEntityPos.set(entityPos)

        val rotationOverrides = mutableMapOf<Int, Quaternionf>()

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            val centerWorld = if (centerNodeIndex >= 0) {
                worldMatrices.getOrNull(centerNodeIndex)
            } else null
            val centerInv = centerWorld?.let { Matrix4f(it).invert() }

            val colliders = resolveColliders(spring.colliderGroupIndices, worldMatrices)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val state = jointStates[springIdx][jointIdx]
                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue

                val headPos = Vector3f(); worldMatrix.getTranslation(headPos)

                // Restore tails to model space
                val currentTail: Vector3f
                val prevTail: Vector3f
                if (centerWorld != null) {
                    currentTail = Vector3f(state.currentTail)
                    centerWorld.transformPosition(currentTail)
                    prevTail = Vector3f(state.prevTail)
                    centerWorld.transformPosition(prevTail)
                    // Center space absorbs center node movement but not entity movement.
                    // Apply entity delta to create inertia from entity motion.
                    prevTail.sub(entityDelta)
                } else {
                    currentTail = Vector3f(state.currentTail)
                    prevTail = Vector3f(state.prevTail)
                    // Entity movement creates inertia: the tail was at a world position
                    // that has shifted by entityDelta. In model space headPos doesn't
                    // move, but the tail "lags behind". Subtract entityDelta from
                    // prevTail so inertia = currentTail - (prevTail - entityDelta)
                    // = (currentTail - prevTail) + entityDelta, picking up entity motion.
                    prevTail.sub(entityDelta)
                }

                // Parent rotation (model space)
                val parentNodeIdx = parentOf[nodeIndex]
                val parentWorldRot = if (parentNodeIdx >= 0) {
                    val r = Quaternionf()
                    worldMatrices.getOrNull(parentNodeIdx)?.getNormalizedRotation(r)
                    r
                } else {
                    Quaternionf()
                }

                // Stiffness direction (rest tail direction in model space)
                val restDir = Vector3f(state.boneAxis)
                val restRot = Quaternionf(parentWorldRot).mul(state.initialLocalRotation)
                restDir.rotate(restRot)

                // Verlet integration (all in model space)
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

                // Collision
                resolveCollisions(nextTail, headPos, state.boneLength, joint.hitRadius, colliders)

                // Store tails
                if (centerInv != null) {
                    val storedNext = Vector3f(nextTail)
                    centerInv.transformPosition(storedNext)
                    val storedCur = Vector3f(currentTail)
                    centerInv.transformPosition(storedCur)
                    state.prevTail.set(storedCur)
                    state.currentTail.set(storedNext)
                } else {
                    state.prevTail.set(currentTail)
                    state.currentTail.set(nextTail)
                }

                // Compute rotation (UniVRM world-space approach, but in model space)
                val actualDir = Vector3f(nextTail).sub(headPos)
                if (actualDir.length() < 1e-6f) continue

                val worldRestRot = Quaternionf(parentWorldRot).mul(state.initialLocalRotation)
                val newWorldRot = Quaternionf(fromToRotation(restDir, actualDir)).mul(worldRestRot)

                val invParentWorldRot = Quaternionf(parentWorldRot).conjugate()
                val newLocalRot = Quaternionf(invParentWorldRot).mul(newWorldRot)

                rotationOverrides[nodeIndex] = newLocalRot
            }
        }

        return rotationOverrides
    }

    private fun computeTailPos(
        jointIdx: Int,
        joints: List<com.github.narazaka.vrmmod.vrm.SpringJoint>,
        worldMatrices: List<Matrix4f>,
        headPos: Vector3f,
        nodeIndex: Int,
    ): Vector3f {
        if (jointIdx + 1 < joints.size) {
            val nextNodeIndex = joints[jointIdx + 1].nodeIndex
            val nextWorld = worldMatrices.getOrNull(nextNodeIndex)
            if (nextWorld != null) {
                val p = Vector3f(); nextWorld.getTranslation(p); return p
            }
        }
        return computeTailFromChildren(nodeIndex, worldMatrices, headPos)
    }

    private fun computeTailFromChildren(
        nodeIndex: Int,
        worldMatrices: List<Matrix4f>,
        headPos: Vector3f,
    ): Vector3f {
        val node = skeleton.nodes[nodeIndex]
        for (childIdx in node.childIndices) {
            val childWorld = worldMatrices.getOrNull(childIdx) ?: continue
            val childPos = Vector3f(); childWorld.getTranslation(childPos)
            if (childPos.distance(headPos) > 1e-6f) return childPos
        }
        val worldMatrix = worldMatrices[nodeIndex]
        val worldRot = Quaternionf(); worldMatrix.getNormalizedRotation(worldRot)
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
        fun fromToRotation(from: Vector3f, to: Vector3f): Quaternionf {
            val fromN = Vector3f(from).normalize()
            val toN = Vector3f(to).normalize()
            val dot = fromN.dot(toN)
            if (dot > 0.999999f) return Quaternionf()
            if (dot < -0.999999f) {
                var axis = Vector3f(1f, 0f, 0f).cross(fromN)
                if (axis.length() < 1e-6f) axis = Vector3f(0f, 1f, 0f).cross(fromN)
                axis.normalize()
                return Quaternionf().rotationAxis(Math.PI.toFloat(), axis.x, axis.y, axis.z)
            }
            val cross = Vector3f(fromN).cross(toN)
            return Quaternionf(cross.x, cross.y, cross.z, 1f + dot).normalize()
        }
    }
}
