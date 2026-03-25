package com.github.narazaka.vrmmod.physics

import com.github.narazaka.vrmmod.vrm.ColliderShape
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import com.github.narazaka.vrmmod.vrm.VrmSpringBone
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * SpringBone simulator faithfully porting three-vrm's VRMSpringBoneJoint.ts.
 *
 * worldMatrices passed to this class must include entity world position
 * (i.e. they are true world-space matrices, not model-space).
 * This matches three-vrm where bone.matrixWorld includes the scene position.
 *
 * Tails are stored in center space. When center is absent, tails are in world space.
 */
class SpringBoneSimulator(
    private val springBone: VrmSpringBone,
    private val skeleton: VrmSkeleton,
) {

    private class JointState(
        val currentTail: Vector3f = Vector3f(),
        val prevTail: Vector3f = Vector3f(),
        var worldSpaceBoneLength: Float = 0f,
        val boneAxis: Vector3f = Vector3f(0f, 0f, 0f),
        val initialLocalRotation: Quaternionf = Quaternionf(),
        val initialLocalMatrix: Matrix4f = Matrix4f(),
    )

    private val jointStates: List<List<JointState>> = springBone.springs.map { spring ->
        spring.joints.map { JointState() }
    }

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

    private fun getCenterToWorld(centerNodeIndex: Int, worldMatrices: List<Matrix4f>): Matrix4f {
        return if (centerNodeIndex >= 0) {
            worldMatrices.getOrNull(centerNodeIndex) ?: Matrix4f()
        } else {
            Matrix4f()
        }
    }

    private fun getWorldToCenter(centerNodeIndex: Int, worldMatrices: List<Matrix4f>): Matrix4f {
        return Matrix4f(getCenterToWorld(centerNodeIndex, worldMatrices)).invert()
    }

    fun initialize(worldMatrices: List<Matrix4f>) {
        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val worldToCenter = getWorldToCenter(spring.centerNodeIndex, worldMatrices)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val node = skeleton.nodes[nodeIndex]
                val state = jointStates[springIdx][jointIdx]

                state.initialLocalRotation.set(node.rotation)
                state.initialLocalMatrix.identity()
                    .translate(node.translation)
                    .rotate(node.rotation)
                    .scale(node.scale)

                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue
                val headPos = Vector3f(); worldMatrix.getTranslation(headPos)
                val tailPos = computeTailPos(jointIdx, joints, worldMatrices, headPos, nodeIndex)

                state.worldSpaceBoneLength = headPos.distance(tailPos)

                if (state.worldSpaceBoneLength > 1e-6f) {
                    val localTail = Vector3f(tailPos).sub(headPos)
                    val worldRot = Quaternionf(); worldMatrix.getNormalizedRotation(worldRot)
                    localTail.rotate(Quaternionf(worldRot).conjugate())
                    localTail.normalize()
                    state.boneAxis.set(localTail)
                } else {
                    state.boneAxis.set(0f, 1f, 0f)
                }

                // three-vrm: bone.localToWorld(initialLocalChildPosition).applyMatrix4(worldToCenter)
                val tailCenter = Vector3f(tailPos)
                worldToCenter.transformPosition(tailCenter)
                state.currentTail.set(tailCenter)
                state.prevTail.set(tailCenter)
            }
        }
        initialized = true
    }

    fun update(
        worldMatrices: List<Matrix4f>,
        deltaTime: Float,
    ): Map<Int, Quaternionf> {
        if (!initialized) {
            initialize(worldMatrices)
            return emptyMap()
        }

        // Make a mutable copy of worldMatrices so we can update them
        // as joints are processed (matching three-vrm's bone.updateMatrix/matrixWorld)
        val matrices = worldMatrices.map { Matrix4f(it) }.toMutableList()

        val rotationOverrides = mutableMapOf<Int, Quaternionf>()

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            val centerToWorld = getCenterToWorld(centerNodeIndex, matrices)
            val worldToCenter = Matrix4f(centerToWorld).invert()
            val colliders = resolveColliders(spring.colliderGroupIndices, matrices)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val state = jointStates[springIdx][jointIdx]
                val worldMatrix = matrices.getOrNull(nodeIndex) ?: continue

                // == Step 1: worldSpaceBoneLength ==
                val headPos = Vector3f(); worldMatrix.getTranslation(headPos)
                val tailPos = computeTailPos(jointIdx, joints, matrices, headPos, nodeIndex)
                state.worldSpaceBoneLength = headPos.distance(tailPos)

                // == Step 2: boneAxis to world space ==
                // three-vrm: boneAxis.transformDirection(initialLocalMatrix).transformDirection(parentMatrixWorld)
                val parentNodeIdx = parentOf[nodeIndex]
                val parentMatrixWorld = if (parentNodeIdx >= 0) {
                    matrices.getOrNull(parentNodeIdx) ?: Matrix4f()
                } else {
                    Matrix4f()
                }

                val worldSpaceBoneAxis = Vector3f(state.boneAxis)
                state.initialLocalMatrix.transformDirection(worldSpaceBoneAxis)
                parentMatrixWorld.transformDirection(worldSpaceBoneAxis)

                // == Step 3: Verlet integration ==
                // three-vrm: inertia in center space, then centerToWorld, then stiffness/gravity in world
                val nextTail = Vector3f(state.currentTail)
                    .add(
                        Vector3f(state.currentTail).sub(state.prevTail)
                            .mul(1f - joint.dragForce)
                    )

                // Convert from center space to world space
                centerToWorld.transformPosition(nextTail)

                // Stiffness and gravity in world space
                nextTail.add(Vector3f(worldSpaceBoneAxis).mul(joint.stiffness * deltaTime))
                nextTail.add(Vector3f(joint.gravityDir).mul(joint.gravityPower * deltaTime))

                // == Step 4: Length constraint (world space) ==
                constrainLength(nextTail, headPos, state.worldSpaceBoneLength)

                // == Step 5: Collision (world space) ==
                resolveCollisions(nextTail, headPos, state.worldSpaceBoneLength, joint.hitRadius, colliders)

                // == Step 6: Store tails ==
                // three-vrm: prevTail = currentTail; currentTail = worldToCenter * nextTail
                state.prevTail.set(state.currentTail)
                val nextTailCenter = Vector3f(nextTail)
                worldToCenter.transformPosition(nextTailCenter)
                state.currentTail.set(nextTailCenter)

                // == Step 7: Rotation ==
                // three-vrm: worldSpaceInitialMatrixInv = inverse(parentMatrixWorld * initialLocalMatrix)
                // bone.quaternion = fromUnitVectors(boneAxis, nextTail * worldSpaceInitialMatrixInv).premultiply(initialLocalRotation)
                val worldSpaceInitialMatrix = Matrix4f(parentMatrixWorld).mul(state.initialLocalMatrix)
                val worldSpaceInitialMatrixInv = Matrix4f(worldSpaceInitialMatrix).invert()

                val localNextDir = Vector3f(nextTail).sub(headPos)
                worldSpaceInitialMatrixInv.transformDirection(localNextDir)
                if (localNextDir.length() < 1e-6f) continue
                localNextDir.normalize()

                // premultiply(initialLocalRotation) = initialLocalRotation * fromUnitVectors
                val fromUnitVec = fromToRotation(state.boneAxis, localNextDir)
                val newLocalRotation = Quaternionf(state.initialLocalRotation).mul(fromUnitVec)

                rotationOverrides[nodeIndex] = newLocalRotation

                // == Step 8: Update bone matrix (three-vrm: bone.updateMatrix, bone.matrixWorld) ==
                // Rebuild local matrix with new rotation and update worldMatrix
                // so subsequent joints in the chain see updated parent transforms.
                val node = skeleton.nodes[nodeIndex]
                val newLocalMatrix = Matrix4f()
                    .translate(node.translation)
                    .rotate(newLocalRotation)
                    .scale(node.scale)
                val newWorldMatrix = Matrix4f(parentMatrixWorld).mul(newLocalMatrix)
                matrices[nodeIndex].set(newWorldMatrix)

                // Update children's worldMatrices too
                updateChildWorldMatrices(nodeIndex, matrices)
            }
        }

        return rotationOverrides
    }

    /**
     * Recursively update children's worldMatrices after a parent's worldMatrix changed.
     */
    private fun updateChildWorldMatrices(nodeIndex: Int, matrices: MutableList<Matrix4f>) {
        val node = skeleton.nodes[nodeIndex]
        for (childIdx in node.childIndices) {
            if (childIdx !in skeleton.nodes.indices) continue
            val childNode = skeleton.nodes[childIdx]
            val childLocal = Matrix4f()
                .translate(childNode.translation)
                .rotate(childNode.rotation)
                .scale(childNode.scale)
            matrices[childIdx].set(Matrix4f(matrices[nodeIndex]).mul(childLocal))
            updateChildWorldMatrices(childIdx, matrices)
        }
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
