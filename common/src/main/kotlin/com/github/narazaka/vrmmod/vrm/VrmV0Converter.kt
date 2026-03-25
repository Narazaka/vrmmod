package com.github.narazaka.vrmmod.vrm

import com.google.gson.JsonArray
import com.google.gson.JsonObject

/**
 * Converts VRM 0.x JSON structures to VRM 1.0 format.
 *
 * VRM 0.x stores everything under a single "VRM" extension key.
 * This converter transforms each subsection (humanoid, expressions,
 * springBone, meta, firstPerson, lookAt) to match VRM 1.0's
 * VRMC_vrm / VRMC_springBone structure, so the existing v1 parser
 * can process them without modification.
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
