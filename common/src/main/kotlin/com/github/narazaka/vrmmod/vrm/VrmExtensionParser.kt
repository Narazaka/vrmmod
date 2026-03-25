package com.github.narazaka.vrmmod.vrm

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/**
 * Parses VRM 1.0 extension JSON (VRMC_vrm) into data model classes.
 *
 * The extension data arrives as a [LinkedHashMap] from JglTF's GltfAssetV2,
 * which is converted to [JsonObject] via Gson for structured access.
 */
object VrmExtensionParser {

    private val gson = Gson()

    /**
     * Converts a raw extension map (from JglTF) to a Gson [JsonObject].
     */
    fun toJsonObject(extensionMap: Any): JsonObject {
        val jsonElement = gson.toJsonTree(extensionMap)
        return jsonElement.asJsonObject
    }

    /**
     * Parses the "meta" block of VRMC_vrm.
     */
    fun parseMeta(json: JsonObject): VrmMeta {
        val meta = json.getAsJsonObject("meta") ?: return VrmMeta(name = "")
        return VrmMeta(
            name = meta.getString("name", ""),
            version = meta.getString("version", ""),
            authors = meta.getAsJsonArray("authors")
                ?.map { it.asString }
                ?: emptyList(),
            copyrightInformation = meta.getString("copyrightInformation", ""),
            licenseUrl = meta.getString("licenseUrl", ""),
        )
    }

    /**
     * Parses the "humanoid" block of VRMC_vrm.
     */
    fun parseHumanoid(json: JsonObject): VrmHumanoid {
        val humanoid = json.getAsJsonObject("humanoid") ?: return VrmHumanoid()
        val humanBones = humanoid.getAsJsonObject("humanBones") ?: return VrmHumanoid()

        val boneMap = mutableMapOf<HumanBone, HumanBoneNode>()
        for ((key, value) in humanBones.entrySet()) {
            val bone = HumanBone.fromVrmName(key) ?: continue
            val nodeObj = value.asJsonObject
            val nodeIndex = nodeObj.get("node")?.asInt ?: continue
            boneMap[bone] = HumanBoneNode(nodeIndex = nodeIndex)
        }

        return VrmHumanoid(humanBones = boneMap)
    }

    /**
     * Parses the "expressions" block of VRMC_vrm.
     * VRM 1.0 has preset expressions and custom expressions.
     */
    fun parseExpressions(json: JsonObject?): List<VrmExpression> {
        if (json == null) return emptyList()
        val expressions = json.getAsJsonObject("expressions") ?: return emptyList()

        val result = mutableListOf<VrmExpression>()

        // Preset expressions
        val preset = expressions.getAsJsonObject("preset")
        if (preset != null) {
            for ((name, value) in preset.entrySet()) {
                val expr = parseExpression(name, name, value)
                result.add(expr)
            }
        }

        // Custom expressions
        val custom = expressions.getAsJsonObject("custom")
        if (custom != null) {
            for ((name, value) in custom.entrySet()) {
                val expr = parseExpression(name, "", value)
                result.add(expr)
            }
        }

        return result
    }

    private fun parseExpression(name: String, preset: String, element: JsonElement): VrmExpression {
        val obj = element.asJsonObject
        val morphTargetBinds = obj.getAsJsonArray("morphTargetBinds")
            ?.map { bind ->
                val b = bind.asJsonObject
                MorphTargetBind(
                    meshIndex = b.get("mesh")?.asInt ?: 0,
                    morphTargetIndex = b.get("index")?.asInt ?: 0,
                    weight = b.get("weight")?.asFloat ?: 0f,
                )
            }
            ?: emptyList()

        return VrmExpression(
            name = name,
            preset = preset,
            morphTargetBinds = morphTargetBinds,
        )
    }

    /**
     * Parses VRMC_materials_mtoon from the raw glTF materials list.
     *
     * Each material in the glTF may have an `extensions.VRMC_materials_mtoon` block.
     * This extracts shadeColorFactor, shadingShiftFactor, and shadingToonyFactor.
     *
     * @param rawMaterials the list of raw glTF material objects (from GltfAssetV2.materials)
     */
    fun parseMtoonMaterials(rawMaterials: List<Any?>?): List<VrmMtoonMaterial> {
        if (rawMaterials == null) return emptyList()
        val result = mutableListOf<VrmMtoonMaterial>()
        for ((index, rawMat) in rawMaterials.withIndex()) {
            if (rawMat == null) continue
            val matJson = toJsonObject(rawMat)
            val extensions = matJson.getAsJsonObject("extensions") ?: continue
            val mtoonRaw = extensions.get("VRMC_materials_mtoon") ?: continue
            if (!mtoonRaw.isJsonObject) continue
            val mtoon = mtoonRaw.asJsonObject

            val shadeColorFactor = mtoon.getAsJsonArray("shadeColorFactor")?.let { arr ->
                org.joml.Vector3f(
                    arr.get(0).asFloat,
                    arr.get(1).asFloat,
                    arr.get(2).asFloat,
                )
            } ?: org.joml.Vector3f(0f, 0f, 0f)

            val shadingShiftFactor = mtoon.get("shadingShiftFactor")?.asFloat ?: 0f
            val shadingToonyFactor = mtoon.get("shadingToonyFactor")?.asFloat ?: 0.9f

            result.add(
                VrmMtoonMaterial(
                    materialIndex = index,
                    shadeColorFactor = shadeColorFactor,
                    shadingShiftFactor = shadingShiftFactor,
                    shadingToonyFactor = shadingToonyFactor,
                )
            )
        }
        return result
    }

    /**
     * Parses the VRMC_springBone extension into [VrmSpringBone].
     */
    fun parseSpringBone(extensionMap: Any?): VrmSpringBone {
        if (extensionMap == null) return VrmSpringBone()
        val json = toJsonObject(extensionMap)

        val colliders = json.getAsJsonArray("colliders")?.map { element ->
            val obj = element.asJsonObject
            val nodeIndex = obj.get("node")?.asInt ?: 0
            val shapeObj = obj.getAsJsonObject("shape")
            val shape = parseColliderShape(shapeObj)
            SpringBoneCollider(nodeIndex = nodeIndex, shape = shape)
        } ?: emptyList()

        val colliderGroups = json.getAsJsonArray("colliderGroups")?.map { element ->
            val obj = element.asJsonObject
            val name = obj.getString("name", "")
            val colliderIndices = obj.getAsJsonArray("colliders")
                ?.map { it.asInt }
                ?: emptyList()
            SpringBoneColliderGroup(name = name, colliderIndices = colliderIndices)
        } ?: emptyList()

        val springs = json.getAsJsonArray("springs")?.map { element ->
            val obj = element.asJsonObject
            val name = obj.getString("name", "")
            val centerNodeIndex = obj.get("center")?.let {
                if (it.isJsonNull) -1 else it.asInt
            } ?: -1
            val joints = obj.getAsJsonArray("joints")?.map { jointEl ->
                val j = jointEl.asJsonObject
                SpringJoint(
                    nodeIndex = j.get("node")?.asInt ?: 0,
                    hitRadius = j.get("hitRadius")?.asFloat ?: 0f,
                    stiffness = j.get("stiffness")?.asFloat ?: 1f,
                    gravityPower = j.get("gravityPower")?.asFloat ?: 0f,
                    gravityDir = j.get("gravityDir")?.let { parseVector3f(it) }
                        ?: org.joml.Vector3f(0f, -1f, 0f),
                    dragForce = j.get("dragForce")?.asFloat ?: 0.5f,
                )
            } ?: emptyList()
            val colliderGroupIndices = obj.getAsJsonArray("colliderGroups")
                ?.map { it.asInt }
                ?: emptyList()
            Spring(
                name = name,
                joints = joints,
                colliderGroupIndices = colliderGroupIndices,
                centerNodeIndex = centerNodeIndex,
            )
        } ?: emptyList()

        return VrmSpringBone(
            colliders = colliders,
            colliderGroups = colliderGroups,
            springs = springs,
        )
    }

    private fun parseColliderShape(shapeObj: JsonObject?): ColliderShape {
        if (shapeObj == null) return ColliderShape.Sphere()

        val sphereEl = shapeObj.get("sphere")
        if (sphereEl != null && sphereEl.isJsonObject) {
            val sphereObj = sphereEl.asJsonObject
            return ColliderShape.Sphere(
                offset = parseVector3f(sphereObj.get("offset")),
                radius = sphereObj.get("radius")?.asFloat ?: 0f,
            )
        }

        val capsuleEl = shapeObj.get("capsule")
        if (capsuleEl != null && capsuleEl.isJsonObject) {
            val capsuleObj = capsuleEl.asJsonObject
            return ColliderShape.Capsule(
                offset = parseVector3f(capsuleObj.get("offset")),
                radius = capsuleObj.get("radius")?.asFloat ?: 0f,
                tail = parseVector3f(capsuleObj.get("tail")),
            )
        }

        return ColliderShape.Sphere()
    }

    /**
     * Parses a Vector3f from either a JSON array [x,y,z] or a JSON object {x,y,z}.
     */
    private fun parseVector3f(element: JsonElement?): org.joml.Vector3f {
        if (element == null) return org.joml.Vector3f()
        if (element.isJsonArray) {
            val arr = element.asJsonArray
            return org.joml.Vector3f(
                arr.getOrNull(0)?.asFloat ?: 0f,
                arr.getOrNull(1)?.asFloat ?: 0f,
                arr.getOrNull(2)?.asFloat ?: 0f,
            )
        }
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            return org.joml.Vector3f(
                obj.get("x")?.asFloat ?: 0f,
                obj.get("y")?.asFloat ?: 0f,
                obj.get("z")?.asFloat ?: 0f,
            )
        }
        return org.joml.Vector3f()
    }

    private fun com.google.gson.JsonArray.getOrNull(index: Int): JsonElement? {
        return if (index in 0 until size()) get(index) else null
    }

    /**
     * Helper to safely get a string from a JsonObject.
     */
    private fun JsonObject.getString(key: String, default: String = ""): String {
        val element = get(key) ?: return default
        return if (element.isJsonNull) default else element.asString
    }
}
