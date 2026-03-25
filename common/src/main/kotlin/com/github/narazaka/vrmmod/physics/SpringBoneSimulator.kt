package com.github.narazaka.vrmmod.physics

import com.github.narazaka.vrmmod.vrm.ColliderShape
import com.github.narazaka.vrmmod.vrm.VrmSkeleton
import com.github.narazaka.vrmmod.vrm.VrmSpringBone
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f

/**
 * SpringBone simulator faithfully porting three-vrm's VRMSpringBoneJoint.ts update() algorithm.
 *
 * Tail positions (currentTail / prevTail) are stored in **center space**.
 * When center is absent, "center space" is entity-world space (translation only, no rotation).
 * This ensures entity movement generates correct inertia via the Verlet integration.
 *
 * Physics steps match three-vrm exactly:
 *   1. Compute worldSpaceBoneLength
 *   2. Transform boneAxis to world via initialLocalMatrix * parentMatrixWorld
 *   3. Verlet integration in center space, then convert to world for stiffness/gravity
 *   4. Length constraint in world space
 *   5. Collision in world space
 *   6. Store tails back to center space
 *   7. Compute rotation via fromUnitVectors in rest-pose local space
 *   8. Update bone matrix
 */
class SpringBoneSimulator(
    private val springBone: VrmSpringBone,
    private val skeleton: VrmSkeleton,
) {

    /**
     * Per-joint simulation state, matching three-vrm's VRMSpringBoneJoint fields.
     */
    private class JointState(
        /** Tail position in center space (or entity-world space if no center). */
        val currentTail: Vector3f = Vector3f(),
        /** Previous frame tail position in center space. */
        val prevTail: Vector3f = Vector3f(),
        /** Bone length in world space (distance from bone to child). */
        var worldSpaceBoneLength: Float = 0f,
        /** Rest-pose bone axis in local space (direction from bone to child). */
        val boneAxis: Vector3f = Vector3f(0f, 0f, 0f),
        /** Rest-pose local rotation of this bone (node.rotation). */
        val initialLocalRotation: Quaternionf = Quaternionf(),
        /** Rest-pose local matrix built from node TRS. */
        val initialLocalMatrix: Matrix4f = Matrix4f(),
    )

    private val jointStates: List<List<JointState>> = springBone.springs.map { spring ->
        spring.joints.map { JointState() }
    }

    private val parentOf: IntArray = buildParentLookup()
    private var initialized = false

    /** Previous frame entity world position. */
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

    /**
     * Builds centerToWorld and worldToCenter matrices.
     *
     * "World space" here means entity-world space = entityTranslation * modelSpace.
     * centerToWorld converts center space to this world space.
     * When center exists: centerToWorld = entityTranslation * centerModelMatrix.
     * When no center: centerToWorld = entityTranslation (tails stored in entity-world directly).
     */
    private fun buildCenterMatrices(
        centerNodeIndex: Int,
        worldMatrices: List<Matrix4f>,
        entityPos: Vector3f,
    ): Pair<Matrix4f, Matrix4f> {
        val entityTranslation = Matrix4f().translate(entityPos)
        val centerToWorld: Matrix4f
        if (centerNodeIndex >= 0) {
            val centerModelMatrix = worldMatrices.getOrNull(centerNodeIndex) ?: Matrix4f()
            centerToWorld = Matrix4f(entityTranslation).mul(centerModelMatrix)
        } else {
            centerToWorld = entityTranslation
        }
        val worldToCenter = Matrix4f(centerToWorld).invert()
        return Pair(centerToWorld, worldToCenter)
    }

    /**
     * Converts a model-space position to world space (entityTranslation * modelPos).
     */
    private fun modelToWorld(modelPos: Vector3f, entityPos: Vector3f): Vector3f {
        return Vector3f(modelPos).add(entityPos)
    }

    /**
     * Converts a model-space matrix to world space by prepending entity translation.
     */
    private fun modelMatrixToWorld(modelMatrix: Matrix4f, entityPos: Vector3f): Matrix4f {
        return Matrix4f().translate(entityPos).mul(modelMatrix)
    }

    fun initialize(worldMatrices: List<Matrix4f>, entityPos: Vector3f = Vector3f()) {
        prevEntityPos.set(entityPos)

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            val (_, worldToCenter) = buildCenterMatrices(centerNodeIndex, worldMatrices, entityPos)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val node = skeleton.nodes[nodeIndex]
                val state = jointStates[springIdx][jointIdx]

                // Store rest-pose local rotation
                state.initialLocalRotation.set(node.rotation)

                // Build rest-pose local matrix from node TRS
                state.initialLocalMatrix.identity()
                    .translate(node.translation)
                    .rotate(node.rotation)
                    .scale(node.scale)

                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue
                val headPos = Vector3f(); worldMatrix.getTranslation(headPos)

                // Compute tail position (child bone position in model space)
                val tailPos = computeTailPos(jointIdx, joints, worldMatrices, headPos, nodeIndex)

                // Step 1: worldSpaceBoneLength (in model space, which is our "world" before entity offset)
                state.worldSpaceBoneLength = headPos.distance(tailPos)

                // Compute boneAxis: direction from bone to child in bone's local space
                if (state.worldSpaceBoneLength > 1e-6f) {
                    // Get tail direction in bone's local space
                    val localTail = Vector3f(tailPos).sub(headPos)
                    val worldRot = Quaternionf(); worldMatrix.getNormalizedRotation(worldRot)
                    localTail.rotate(Quaternionf(worldRot).conjugate())
                    localTail.normalize()
                    state.boneAxis.set(localTail)
                } else {
                    // Fallback: point along Y in local space
                    state.boneAxis.set(0f, 1f, 0f)
                }

                // Store tail in center space (via entity-world space)
                val tailWorld = modelToWorld(tailPos, entityPos)
                val tailCenter = Vector3f(tailWorld)
                worldToCenter.transformPosition(tailCenter)
                state.currentTail.set(tailCenter)
                state.prevTail.set(tailCenter)
            }
        }
        initialized = true
    }

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

        prevEntityPos.set(entityPos)

        val rotationOverrides = mutableMapOf<Int, Quaternionf>()

        for (springIdx in springBone.springs.indices) {
            val spring = springBone.springs[springIdx]
            val joints = spring.joints
            val centerNodeIndex = spring.centerNodeIndex

            // Build center<->world matrices including entity position
            val (centerToWorld, worldToCenter) = buildCenterMatrices(centerNodeIndex, worldMatrices, entityPos)

            val colliders = resolveColliders(spring.colliderGroupIndices, worldMatrices, entityPos)

            for (jointIdx in joints.indices) {
                val joint = joints[jointIdx]
                val nodeIndex = joint.nodeIndex
                if (nodeIndex !in skeleton.nodes.indices) continue

                val state = jointStates[springIdx][jointIdx]
                val worldMatrix = worldMatrices.getOrNull(nodeIndex) ?: continue

                // == Step 1: worldSpaceBoneLength ==
                // Already computed at init; re-compute from current world matrices
                val headPosModel = Vector3f(); worldMatrix.getTranslation(headPosModel)
                val tailPosModel = computeTailPos(jointIdx, joints, worldMatrices, headPosModel, nodeIndex)
                state.worldSpaceBoneLength = headPosModel.distance(tailPosModel)

                // == Step 2: boneAxis to world space ==
                // worldSpaceBoneAxis = boneAxis.transformDirection(initialLocalMatrix).transformDirection(parentMatrixWorld)
                val parentNodeIdx = parentOf[nodeIndex]
                val parentMatrixWorld = if (parentNodeIdx >= 0) {
                    worldMatrices.getOrNull(parentNodeIdx) ?: Matrix4f()
                } else {
                    Matrix4f()
                }

                val worldSpaceBoneAxis = Vector3f(state.boneAxis)
                state.initialLocalMatrix.transformDirection(worldSpaceBoneAxis)
                parentMatrixWorld.transformDirection(worldSpaceBoneAxis)

                // == Step 3: Verlet integration ==
                // currentTail and prevTail are in center space
                // Inertia computed in center space
                val nextTail = Vector3f(state.currentTail)
                    .add(
                        Vector3f(state.currentTail).sub(state.prevTail)
                            .mul(1f - joint.dragForce)
                    )

                // Convert nextTail from center space to world space (entity-world)
                centerToWorld.transformPosition(nextTail)

                // In world space, add stiffness and gravity
                // stiffness uses worldSpaceBoneAxis (model-space direction)
                // but since our "world" = entity translation + model, and entity translation
                // doesn't rotate, worldSpaceBoneAxis in model space = worldSpaceBoneAxis in entity-world
                nextTail.add(Vector3f(worldSpaceBoneAxis).mul(joint.stiffness * deltaTime))
                nextTail.add(Vector3f(joint.gravityDir).mul(joint.gravityPower * deltaTime))

                // == Step 4: Length constraint (world space) ==
                // boneWorldPos = entity translation + model-space bone position
                val boneWorldPos = modelToWorld(headPosModel, entityPos)
                constrainLength(nextTail, boneWorldPos, state.worldSpaceBoneLength)

                // == Step 5: Collision (world space) ==
                resolveCollisions(nextTail, boneWorldPos, state.worldSpaceBoneLength, joint.hitRadius, colliders)

                // == Step 6: Store tails ==
                // prevTail = currentTail (already in center space)
                state.prevTail.set(state.currentTail)
                // currentTail = worldToCenter * nextTail
                val nextTailCenter = Vector3f(nextTail)
                worldToCenter.transformPosition(nextTailCenter)
                state.currentTail.set(nextTailCenter)

                // == Step 7: Rotation computation ==
                // worldSpaceInitialMatrixInv = inverse(parentMatrixWorld * initialLocalMatrix)
                val worldSpaceInitialMatrix = Matrix4f(parentMatrixWorld).mul(state.initialLocalMatrix)
                val worldSpaceInitialMatrixInv = Matrix4f(worldSpaceInitialMatrix).invert()

                // nextTail is in entity-world space, but rotation calculation needs model space
                // since parentMatrixWorld and initialLocalMatrix are in model space.
                // Convert nextTail back to model space for rotation calc.
                val nextTailModel = Vector3f(nextTail).sub(entityPos)

                // localNextDir = normalize(nextTailModel * worldSpaceInitialMatrixInv)
                // This transforms nextTail position to rest-pose local space
                val fromBoneToTail = Vector3f(nextTailModel).sub(headPosModel)
                val localNextDir = Vector3f(fromBoneToTail)
                worldSpaceInitialMatrixInv.transformDirection(localNextDir)
                if (localNextDir.length() > 1e-6f) {
                    localNextDir.normalize()
                } else {
                    continue
                }

                // bone.quaternion = fromUnitVectors(boneAxis, localNextDir).premultiply(initialLocalRotation)
                // premultiply(A) means result = A * original
                val fromUnitVec = fromToRotation(state.boneAxis, localNextDir)
                val newLocalRotation = Quaternionf(state.initialLocalRotation).mul(fromUnitVec)

                rotationOverrides[nodeIndex] = newLocalRotation
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

    // ── Collision ─────────────────────────────────────────────────────

    private sealed class ResolvedCollider {
        data class Sphere(val position: Vector3f, val radius: Float) : ResolvedCollider()
        data class Capsule(val start: Vector3f, val end: Vector3f, val radius: Float) : ResolvedCollider()
    }

    /**
     * Resolves colliders into entity-world space positions.
     */
    private fun resolveColliders(
        colliderGroupIndices: List<Int>,
        worldMatrices: List<Matrix4f>,
        entityPos: Vector3f,
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
                        worldPos.add(entityPos) // model -> entity-world
                        result.add(ResolvedCollider.Sphere(worldPos, shape.radius))
                    }
                    is ColliderShape.Capsule -> {
                        val worldOffset = Vector3f(shape.offset)
                        nodeWorld.transformPosition(worldOffset)
                        worldOffset.add(entityPos)
                        val worldTail = Vector3f(shape.tail)
                        nodeWorld.transformPosition(worldTail)
                        worldTail.add(entityPos)
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
         * Computes a quaternion that rotates unit vector [from] to unit vector [to].
         * Equivalent to Three.js Quaternion.setFromUnitVectors().
         */
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
