package net.narazaka.vrmmod.vrm

import org.joml.Vector3f

data class VrmSpringBone(
    val colliders: List<SpringBoneCollider> = emptyList(),
    val colliderGroups: List<SpringBoneColliderGroup> = emptyList(),
    val springs: List<Spring> = emptyList(),
)

data class SpringBoneCollider(
    val nodeIndex: Int,
    val shape: ColliderShape,
)

sealed class ColliderShape {
    data class Sphere(
        val offset: Vector3f = Vector3f(),
        val radius: Float = 0f,
    ) : ColliderShape()

    data class Capsule(
        val offset: Vector3f = Vector3f(),
        val radius: Float = 0f,
        val tail: Vector3f = Vector3f(),
    ) : ColliderShape()
}

data class SpringBoneColliderGroup(
    val name: String = "",
    val colliderIndices: List<Int> = emptyList(),
)

data class Spring(
    val name: String = "",
    val joints: List<SpringJoint> = emptyList(),
    val colliderGroupIndices: List<Int> = emptyList(),
    val centerNodeIndex: Int = -1,
)

data class SpringJoint(
    val nodeIndex: Int,
    val hitRadius: Float = 0f,
    val stiffness: Float = 1f,
    val gravityPower: Float = 0f,
    val gravityDir: Vector3f = Vector3f(0f, -1f, 0f),
    val dragForce: Float = 0.5f,
)
