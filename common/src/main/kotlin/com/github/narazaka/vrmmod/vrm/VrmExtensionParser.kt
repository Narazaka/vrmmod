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
                    meshIndex = b.get("node")?.asInt ?: 0,
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
     * Helper to safely get a string from a JsonObject.
     */
    private fun JsonObject.getString(key: String, default: String = ""): String {
        val element = get(key) ?: return default
        return if (element.isJsonNull) default else element.asString
    }
}
