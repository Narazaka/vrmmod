package com.github.narazaka.vrmmod.animation

import com.github.narazaka.vrmmod.vrm.HumanBone
import com.google.gson.Gson
import de.javagl.jgltf.model.AccessorFloatData
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Parses VRMA (VRM Animation) files into [AnimationClip] instances.
 *
 * A single .vrma file may contain multiple glTF animations, each of which
 * becomes a separate [AnimationClip].
 */
object VrmaParser {

    private val gson = Gson()

    /**
     * Parses a .vrma file from the given [inputStream] and returns a list of [AnimationClip].
     */
    fun parse(inputStream: InputStream): List<AnimationClip> {
        val bytes = inputStream.readBytes()

        // High-level model for animation channels/samplers
        val model = GltfModelReader().readWithoutReferences(ByteArrayInputStream(bytes))

        // Low-level: read raw glTF JSON to access VRMC_vrm_animation extension
        val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltf = GltfReaderV2().read(ByteArrayInputStream(jsonBytes))

        // Build nodeIndex -> HumanBone mapping from VRMC_vrm_animation
        val nodeToHumanBone = buildNodeToHumanBoneMap(gltf.extensions)

        // Build nodeModel name -> nodeIndex mapping
        val nodeNameToIndex = mutableMapOf<String, Int>()
        for ((index, nodeModel) in model.nodeModels.withIndex()) {
            val name = nodeModel.name
            if (name != null) {
                nodeNameToIndex[name] = index
            }
        }

        val clips = mutableListOf<AnimationClip>()

        for (animModel in model.animationModels) {
            val trackMap = mutableMapOf<HumanBone, MutableBoneTrackBuilder>()
            var maxTime = 0f

            for (channel in animModel.channels) {
                val nodeName = channel.nodeModel?.name ?: continue
                val nodeIndex = nodeNameToIndex[nodeName] ?: continue
                val humanBone = nodeToHumanBone[nodeIndex] ?: continue
                val path = channel.path ?: continue

                val sampler = channel.sampler ?: continue
                val inputData = sampler.input?.accessorData as? AccessorFloatData ?: continue
                val outputData = sampler.output?.accessorData as? AccessorFloatData ?: continue

                val builder = trackMap.getOrPut(humanBone) { MutableBoneTrackBuilder() }

                when (path) {
                    "rotation" -> {
                        for (i in 0 until inputData.numElements) {
                            val time = inputData.get(i, 0)
                            if (time > maxTime) maxTime = time
                            val qx = outputData.get(i, 0)
                            val qy = outputData.get(i, 1)
                            val qz = outputData.get(i, 2)
                            val qw = outputData.get(i, 3)
                            builder.rotationKeyframes.add(
                                RotationKeyframe(time, Quaternionf(qx, qy, qz, qw)),
                            )
                        }
                    }
                    "translation" -> {
                        for (i in 0 until inputData.numElements) {
                            val time = inputData.get(i, 0)
                            if (time > maxTime) maxTime = time
                            val tx = outputData.get(i, 0)
                            val ty = outputData.get(i, 1)
                            val tz = outputData.get(i, 2)
                            builder.translationKeyframes.add(
                                TranslationKeyframe(time, Vector3f(tx, ty, tz)),
                            )
                        }
                    }
                }
            }

            val tracks = trackMap.mapValues { (_, builder) ->
                BoneTrack(
                    rotationKeyframes = builder.rotationKeyframes.toList(),
                    translationKeyframes = builder.translationKeyframes.toList(),
                )
            }

            val clipName = animModel.name ?: "animation_${clips.size}"
            clips.add(AnimationClip(name = clipName, duration = maxTime, tracks = tracks))
        }

        return clips
    }

    /**
     * Builds a mapping from glTF node index to [HumanBone] using the
     * VRMC_vrm_animation extension's humanoid.humanBones block.
     */
    private fun buildNodeToHumanBoneMap(extensions: Map<String, Any>?): Map<Int, HumanBone> {
        if (extensions == null) return emptyMap()
        val vrmAnimExt = extensions["VRMC_vrm_animation"] ?: return emptyMap()
        val json = gson.toJsonTree(vrmAnimExt).asJsonObject

        val humanoid = json.getAsJsonObject("humanoid") ?: return emptyMap()
        val humanBones = humanoid.getAsJsonObject("humanBones") ?: return emptyMap()

        val result = mutableMapOf<Int, HumanBone>()
        for ((key, value) in humanBones.entrySet()) {
            val bone = HumanBone.fromVrmName(key) ?: continue
            val nodeIndex = value.asJsonObject?.get("node")?.asInt ?: continue
            result[nodeIndex] = bone
        }
        return result
    }

    private class MutableBoneTrackBuilder {
        val rotationKeyframes = mutableListOf<RotationKeyframe>()
        val translationKeyframes = mutableListOf<TranslationKeyframe>()
    }
}
