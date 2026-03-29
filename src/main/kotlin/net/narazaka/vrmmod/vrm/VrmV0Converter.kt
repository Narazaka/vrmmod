package net.narazaka.vrmmod.vrm

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.joml.Matrix4f
import org.joml.Vector3f

/**
 * Converts VRM 0.x to VRM 1.0 format.
 *
 * Two layers of conversion:
 * 1. **JSON conversion** (`convertAll`): Restructures the VRM extension JSON
 *    from v0 schema to v1 schema (humanoid, expressions, springBone, etc.).
 * 2. **Coordinate conversion** (`convertCoordinates`): Transforms parsed geometry
 *    from v0 coordinate space (Z- forward) to v1 coordinate space (Z+ forward).
 *
 * The coordinate difference between v0 and v1 is a 180° rotation around Y,
 * which is equivalent to negating both X and Z components of all spatial data.
 * This is a proper rotation (det = 1), not a reflection, so winding order
 * and handedness are preserved.
 *
 * The transformation matrix M = diag(-1, 1, -1, 1) is applied to:
 * - Vertex positions and normals (vec3: negate x,z)
 * - Morph target deltas (vec3: negate x,z)
 * - Skeleton node translations (vec3: negate x,z)
 * - Inverse bind matrices (similarity transform: M * IBM * M)
 * - SpringBone collider offsets (vec3: negate x,z)
 * - SpringBone gravity directions (vec3: negate x,z)
 */
object VrmV0Converter {

    data class V0ConversionResult(
        val vrmcVrm: JsonObject,
        val vrmcSpringBone: JsonObject,
    )

    /** v0 thumb bone name -> v1 thumb bone name */
    private val thumbBoneNameMap = mapOf(
        "leftThumbProximal" to "leftThumbMetacarpal",
        "leftThumbIntermediate" to "leftThumbProximal",
        "rightThumbProximal" to "rightThumbMetacarpal",
        "rightThumbIntermediate" to "rightThumbProximal",
    )

    /** v0 preset name -> v1 preset name */
    private val presetNameMap = mapOf(
        "a" to "aa",
        "e" to "ee",
        "i" to "ih",
        "o" to "oh",
        "u" to "ou",
        "joy" to "happy",
        "angry" to "angry",
        "sorrow" to "sad",
        "fun" to "relaxed",
        "blink" to "blink",
        "blink_l" to "blinkLeft",
        "blink_r" to "blinkRight",
        "lookup" to "lookUp",
        "lookdown" to "lookDown",
        "lookleft" to "lookLeft",
        "lookright" to "lookRight",
        "neutral" to "neutral",
    )

    /** v0 firstPersonFlag (PascalCase) -> v1 type (camelCase) */
    private val firstPersonFlagMap = mapOf(
        "Auto" to "auto",
        "FirstPersonOnly" to "firstPersonOnly",
        "ThirdPersonOnly" to "thirdPersonOnly",
        "Both" to "both",
    )

    /**
     * Converts v0 humanoid (array of {bone, node}) to
     * v1 humanoid (object {boneName: {node: N}}).
     */
    fun convertHumanoid(v0Json: JsonObject): JsonObject {
        val result = JsonObject()
        val v0Humanoid = v0Json.getAsJsonObject("humanoid") ?: return result
        val v0Bones = v0Humanoid.getAsJsonArray("humanBones") ?: return result

        val v1Bones = JsonObject()
        for (entry in v0Bones) {
            val obj = entry.asJsonObject
            val boneName = obj.get("bone")?.asString ?: continue
            val nodeIndex = obj.get("node")?.asInt ?: continue

            val v1Name = thumbBoneNameMap[boneName] ?: boneName
            val boneObj = JsonObject()
            boneObj.addProperty("node", nodeIndex)
            v1Bones.add(v1Name, boneObj)
        }

        val v1Humanoid = JsonObject()
        v1Humanoid.add("humanBones", v1Bones)
        result.add("humanoid", v1Humanoid)
        return result
    }

    /**
     * Converts v0 blendShapeMaster to v1 expressions.
     *
     * @param meshToNodeMap glTF mesh index -> node index (needed because v0 binds
     *   reference mesh indices, but v1 references node indices)
     */
    fun convertExpressions(v0Json: JsonObject, meshToNodeMap: Map<Int, Int> = emptyMap()): JsonObject {
        val result = JsonObject()
        val blendShapeMaster = v0Json.getAsJsonObject("blendShapeMaster") ?: return result
        val groups = blendShapeMaster.getAsJsonArray("blendShapeGroups") ?: return result

        val preset = JsonObject()
        val custom = JsonObject()

        for (element in groups) {
            val group = element.asJsonObject
            val v0PresetName = group.get("presetName")?.asString
            val groupName = group.get("name")?.asString ?: continue

            val v1Name = v0PresetName?.let { presetNameMap[it] }

            val expr = JsonObject()

            // Convert isBinary
            val isBinary = group.get("isBinary")?.asBoolean ?: false
            if (isBinary) expr.addProperty("isBinary", true)

            // Convert binds -> morphTargetBinds
            val binds = group.getAsJsonArray("binds")
            if (binds != null && binds.size() > 0) {
                val morphTargetBinds = JsonArray()
                for (bind in binds) {
                    val b = bind.asJsonObject
                    val meshIdx = b.get("mesh")?.asInt ?: continue
                    val morphIdx = b.get("index")?.asInt ?: continue
                    val weight = (b.get("weight")?.asFloat ?: 100f) * 0.01f

                    // v0 uses mesh index, v1 uses node index
                    val nodeIdx = meshToNodeMap[meshIdx] ?: meshIdx

                    val v1Bind = JsonObject()
                    v1Bind.addProperty("node", nodeIdx)
                    v1Bind.addProperty("index", morphIdx)
                    v1Bind.addProperty("weight", weight)
                    morphTargetBinds.add(v1Bind)
                }
                expr.add("morphTargetBinds", morphTargetBinds)
            }

            if (v1Name != null) {
                preset.add(v1Name, expr)
            } else {
                custom.add(groupName, expr)
            }
        }

        val expressions = JsonObject()
        expressions.add("preset", preset)
        if (custom.size() > 0) {
            expressions.add("custom", custom)
        }
        result.add("expressions", expressions)
        return result
    }

    /**
     * Converts v0 meta to v1 meta.
     */
    fun convertMeta(v0Json: JsonObject): JsonObject {
        val result = JsonObject()
        val v0Meta = v0Json.getAsJsonObject("meta") ?: return result

        val v1Meta = JsonObject()
        v1Meta.addProperty("name", v0Meta.get("title")?.asString ?: "")
        v1Meta.addProperty("version", v0Meta.get("version")?.asString ?: "")

        val authors = JsonArray()
        v0Meta.get("author")?.asString?.let { authors.add(it) }
        v1Meta.add("authors", authors)

        v1Meta.addProperty("copyrightInformation", v0Meta.get("contactInformation")?.asString ?: "")
        v1Meta.addProperty("licenseUrl", v0Meta.get("otherLicenseUrl")?.asString ?: "")

        result.add("meta", v1Meta)
        return result
    }

    /**
     * Converts v0 firstPerson mesh annotations.
     *
     * @param meshToNodeMap glTF mesh index -> node index (v0 uses mesh indices)
     */
    fun convertFirstPerson(v0Json: JsonObject, meshToNodeMap: Map<Int, Int> = emptyMap()): JsonObject {
        val result = JsonObject()
        val v0Fp = v0Json.getAsJsonObject("firstPerson") ?: return result

        val v1Fp = JsonObject()
        val v0Annotations = v0Fp.getAsJsonArray("meshAnnotations")
        if (v0Annotations != null) {
            val v1Annotations = JsonArray()
            for (element in v0Annotations) {
                val ann = element.asJsonObject
                val v1Ann = JsonObject()
                // v0 uses "mesh" (mesh index), v1 uses "node" (node index)
                val meshIdx = ann.get("mesh")?.asInt ?: 0
                v1Ann.addProperty("node", meshToNodeMap[meshIdx] ?: meshIdx)
                val flag = ann.get("firstPersonFlag")?.asString ?: "Auto"
                v1Ann.addProperty("type", firstPersonFlagMap[flag] ?: "auto")
                v1Annotations.add(v1Ann)
            }
            v1Fp.add("meshAnnotations", v1Annotations)
        }

        result.add("firstPerson", v1Fp)
        return result
    }

    /**
     * Converts v0 firstPersonBoneOffset to v1 lookAt.offsetFromHeadBone.
     */
    fun convertLookAt(v0Json: JsonObject): JsonObject {
        val result = JsonObject()
        val v0Fp = v0Json.getAsJsonObject("firstPerson") ?: return result

        val v1LookAt = JsonObject()
        val offset = v0Fp.getAsJsonObject("firstPersonBoneOffset")
        if (offset != null) {
            val arr = JsonArray()
            arr.add(offset.get("x")?.asFloat ?: 0f)
            arr.add(offset.get("y")?.asFloat ?: 0.06f)
            arr.add(offset.get("z")?.asFloat ?: 0f)
            v1LookAt.add("offsetFromHeadBone", arr)
        }

        result.add("lookAt", v1LookAt)
        return result
    }

    /**
     * Converts v0 secondaryAnimation to v1 VRMC_springBone format.
     *
     * @param nodeChildren map of node index -> list of child node indices (from glTF)
     */
    fun convertSpringBone(v0Json: JsonObject, nodeChildren: Map<Int, List<Int>>): JsonObject {
        val result = JsonObject()
        val v0Anim = v0Json.getAsJsonObject("secondaryAnimation") ?: return result

        // Convert collider groups -> individual colliders + collider groups
        val v0ColliderGroups = v0Anim.getAsJsonArray("colliderGroups")
        val v1Colliders = JsonArray()
        val v1ColliderGroups = JsonArray()
        var colliderIndex = 0

        if (v0ColliderGroups != null) {
            for (element in v0ColliderGroups) {
                val v0Group = element.asJsonObject
                val nodeIdx = v0Group.get("node")?.asInt ?: continue
                val v0Colliders = v0Group.getAsJsonArray("colliders") ?: continue

                val indices = JsonArray()
                for (col in v0Colliders) {
                    val colObj = col.asJsonObject
                    val v1Collider = JsonObject()
                    v1Collider.addProperty("node", nodeIdx)

                    val shape = JsonObject()
                    val sphere = JsonObject()
                    val colOffset = colObj.getAsJsonObject("offset")
                    if (colOffset != null) {
                        val offsetArr = JsonArray()
                        offsetArr.add(colOffset.get("x")?.asFloat ?: 0f)
                        offsetArr.add(colOffset.get("y")?.asFloat ?: 0f)
                        offsetArr.add(colOffset.get("z")?.asFloat ?: 0f)
                        sphere.add("offset", offsetArr)
                    }
                    sphere.addProperty("radius", colObj.get("radius")?.asFloat ?: 0f)
                    shape.add("sphere", sphere)
                    v1Collider.add("shape", shape)

                    v1Colliders.add(v1Collider)
                    indices.add(colliderIndex)
                    colliderIndex++
                }

                val group = JsonObject()
                group.add("colliders", indices)
                v1ColliderGroups.add(group)
            }
        }

        // Convert bone groups -> springs
        val v0BoneGroups = v0Anim.getAsJsonArray("boneGroups")
        val v1Springs = JsonArray()

        if (v0BoneGroups != null) {
            for (element in v0BoneGroups) {
                val group = element.asJsonObject
                val stiffness = group.get("stiffiness")?.asFloat
                    ?: group.get("stiffness")?.asFloat ?: 1f
                val gravityPower = group.get("gravityPower")?.asFloat ?: 0f
                val gravityDir = group.getAsJsonObject("gravityDir")
                val dragForce = group.get("dragForce")?.asFloat ?: 0.5f
                val hitRadius = group.get("hitRadius")?.asFloat ?: 0f
                val center = group.get("center")?.asInt ?: -1
                val rootBones = group.getAsJsonArray("bones") ?: continue
                val colliderGroupIndices = group.getAsJsonArray("colliderGroups")

                for (rootBoneEl in rootBones) {
                    val rootBone = rootBoneEl.asInt
                    val chains = buildJointChains(rootBone, nodeChildren)

                    for (chain in chains) {
                        val v1Spring = JsonObject()
                        v1Spring.addProperty("name", group.get("comment")?.asString ?: "")
                        if (center >= 0) {
                            v1Spring.addProperty("center", center)
                        }

                        val joints = JsonArray()
                        for (nodeIdx in chain) {
                            val joint = JsonObject()
                            joint.addProperty("node", nodeIdx)
                            joint.addProperty("hitRadius", hitRadius)
                            joint.addProperty("stiffness", stiffness)
                            joint.addProperty("gravityPower", gravityPower)
                            if (gravityDir != null) {
                                val dir = JsonObject()
                                dir.addProperty("x", gravityDir.get("x")?.asFloat ?: 0f)
                                dir.addProperty("y", gravityDir.get("y")?.asFloat ?: -1f)
                                dir.addProperty("z", gravityDir.get("z")?.asFloat ?: 0f)
                                joint.add("gravityDir", dir)
                            }
                            joint.addProperty("dragForce", dragForce)
                            joints.add(joint)
                        }
                        v1Spring.add("joints", joints)

                        if (colliderGroupIndices != null && colliderGroupIndices.size() > 0) {
                            v1Spring.add("colliderGroups", colliderGroupIndices.deepCopy())
                        }

                        v1Springs.add(v1Spring)
                    }
                }
            }
        }

        result.add("colliders", v1Colliders)
        result.add("colliderGroups", v1ColliderGroups)
        result.add("springs", v1Springs)
        return result
    }

    /**
     * Converts the entire v0 VRM extension to v1 format.
     *
     * @param v0Json the "VRM" extension root object
     * @param nodeChildren node index -> child indices (from glTF nodes)
     * @param meshToNodeMap glTF mesh index -> node index (for expression/firstPerson bind conversion)
     * @return pair of (VRMC_vrm JSON, VRMC_springBone JSON)
     */
    fun convertAll(
        v0Json: JsonObject,
        nodeChildren: Map<Int, List<Int>>,
        meshToNodeMap: Map<Int, Int> = emptyMap(),
    ): V0ConversionResult {
        val vrmcVrm = JsonObject()

        // Merge each converted section into the VRMC_vrm object
        val meta = convertMeta(v0Json)
        meta.getAsJsonObject("meta")?.let { vrmcVrm.add("meta", it) }

        val humanoid = convertHumanoid(v0Json)
        humanoid.getAsJsonObject("humanoid")?.let { vrmcVrm.add("humanoid", it) }

        val expressions = convertExpressions(v0Json, meshToNodeMap)
        expressions.getAsJsonObject("expressions")?.let { vrmcVrm.add("expressions", it) }

        val firstPerson = convertFirstPerson(v0Json, meshToNodeMap)
        firstPerson.getAsJsonObject("firstPerson")?.let { vrmcVrm.add("firstPerson", it) }

        val lookAt = convertLookAt(v0Json)
        lookAt.getAsJsonObject("lookAt")?.let { vrmcVrm.add("lookAt", it) }

        // SpringBone is a separate extension in v1
        val vrmcSpringBone = convertSpringBone(v0Json, nodeChildren)

        return V0ConversionResult(vrmcVrm, vrmcSpringBone)
    }

    // ========================================================================
    // Coordinate conversion: v0 (Z- forward) → v1 (Z+ forward)
    // ========================================================================

    /**
     * Transforms a parsed VrmModel from VRM 0.x coordinate space to VRM 1.0.
     *
     * VRM 0.x models face Z- while VRM 1.0 models face Z+. Empirically verified
     * by comparing the same model exported in both formats: all spatial data
     * (positions, normals, translations) has X and Z negated between v0 and v1.
     *
     * This is equivalent to a 180° rotation around Y: M = diag(-1, 1, -1, 1).
     * Applied as:
     * - vec3 data: negate X and Z
     * - matrices: similarity transform M * matrix * M
     */
    fun convertCoordinates(model: VrmModel): VrmModel {
        val meshes = model.meshes.map { mesh ->
            mesh.copy(primitives = mesh.primitives.map { prim ->
                prim.copy(
                    positions = flipXZ(prim.positions),
                    normals = flipXZ(prim.normals),
                    morphTargets = prim.morphTargets.map { mt ->
                        VrmMorphTarget(
                            positionDeltas = flipXZ(mt.positionDeltas),
                            normalDeltas = flipXZ(mt.normalDeltas),
                        )
                    },
                )
            })
        }

        val nodes = model.skeleton.nodes.map { node ->
            node.copy(translation = flipXZ(node.translation))
        }

        val skins = model.skeleton.skins.map { skin ->
            skin.copy(inverseBindMatrices = skin.inverseBindMatrices.map { similarityTransformM(it) })
        }

        val skeleton = model.skeleton.copy(nodes = nodes, skins = skins)

        val springBone = convertSpringBoneCoordinates(model.springBone)

        return model.copy(meshes = meshes, skeleton = skeleton, springBone = springBone)
    }

    /**
     * Transforms SpringBone spatial data from v0 to v1 coordinates.
     *
     * Collider offsets/tails: UniVRM exports these in a mixed coordinate space where
     * Z already matches v1 convention, but X is negated. Empirically verified by
     * comparing the same model's v0/v1 exports: offset X is opposite, Z is identical.
     * Therefore only X is negated (NOT flipXZ).
     *
     * Gravity directions: model-space direction vectors, use full flipXZ.
     * (Typically (0,-1,0) so unaffected, but correct in principle.)
     */
    private fun convertSpringBoneCoordinates(springBone: VrmSpringBone): VrmSpringBone {
        val colliders = springBone.colliders.map { collider ->
            val newShape = when (val shape = collider.shape) {
                is ColliderShape.Sphere -> ColliderShape.Sphere(
                    offset = flipX(shape.offset),
                    radius = shape.radius,
                )
                is ColliderShape.Capsule -> ColliderShape.Capsule(
                    offset = flipX(shape.offset),
                    radius = shape.radius,
                    tail = flipX(shape.tail),
                )
            }
            collider.copy(shape = newShape)
        }

        val springs = springBone.springs.map { spring ->
            spring.copy(joints = spring.joints.map { joint ->
                joint.copy(gravityDir = flipXZ(joint.gravityDir))
            })
        }

        return springBone.copy(colliders = colliders, springs = springs)
    }

    /** Negate X and Z components of a Vector3f. */
    private fun flipXZ(v: Vector3f): Vector3f = Vector3f(-v.x, v.y, -v.z)

    /** Negate X component only. Used for VRM 0.x collider offsets (mixed coordinate space). */
    private fun flipX(v: Vector3f): Vector3f = Vector3f(-v.x, v.y, v.z)

    /**
     * Negate X and Z components in a vec3 array (stride=3).
     * Returns a new array; the original is not modified.
     */
    private fun flipXZ(arr: FloatArray): FloatArray {
        if (arr.isEmpty()) return arr
        val result = arr.copyOf()
        var i = 0
        while (i + 2 < result.size) {
            result[i] = -result[i]           // X
            // result[i + 1] unchanged       // Y
            result[i + 2] = -result[i + 2]   // Z
            i += 3
        }
        return result
    }

    /**
     * Applies similarity transform M * matrix * M where M = diag(-1, 1, -1, 1).
     *
     * This negates elements where exactly one of (row, col) is in {0, 2}:
     * - (0,1), (0,3), (1,0), (1,2), (2,1), (2,3), (3,0), (3,2) are negated
     * - All other elements are unchanged
     */
    private fun similarityTransformM(ibm: Matrix4f): Matrix4f {
        val m = Matrix4f(ibm)
        // Row 0: negate cols 1, 3
        m.m01(-m.m01()); m.m03(-m.m03())
        // Row 1: negate cols 0, 2
        m.m10(-m.m10()); m.m12(-m.m12())
        // Row 2: negate cols 1, 3
        m.m21(-m.m21()); m.m23(-m.m23())
        // Row 3: negate cols 0, 2
        m.m30(-m.m30()); m.m32(-m.m32())
        return m
    }

    // ========================================================================
    // SpringBone chain building
    // ========================================================================

    /**
     * Enumerates all root-to-leaf paths from a root node.
     * v0 SpringBone affects the root bone and ALL descendants.
     * v1 springs are strictly linear chains, so each root-to-leaf path
     * becomes a separate Spring.
     *
     * Example: root=0, 0->[1,4], 1->[2], 4->[5]
     * Returns: [[0,1,2], [0,4,5]]
     */
    private fun buildJointChains(rootNode: Int, nodeChildren: Map<Int, List<Int>>): List<List<Int>> {
        val results = mutableListOf<List<Int>>()

        fun dfs(node: Int, path: MutableList<Int>) {
            path.add(node)
            val children = nodeChildren[node] ?: emptyList()
            if (children.isEmpty()) {
                // Leaf node: record the path
                results.add(path.toList())
            } else {
                for (child in children) {
                    dfs(child, path)
                }
            }
            path.removeAt(path.size - 1)
        }

        dfs(rootNode, mutableListOf())
        return results
    }
}
