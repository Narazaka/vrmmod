package net.narazaka.vrmmod.animation

import net.narazaka.vrmmod.vrm.HumanBone
import com.google.gson.Gson
import de.javagl.jgltf.model.AccessorFloatData
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Parses VRMA (VRM Animation) files into [AnimationClip] instances.
 *
 * A single .vrma file may contain multiple glTF animations, each of which
 * becomes a separate [AnimationClip].
 *
 * Rotation keyframes are normalized at parse time following three-vrm's algorithm:
 *   normalizedRot = parentWorldRot * localRot * inverse(selfWorldRot)
 * This removes the vrma file's rest-pose dependency so that the normalized
 * rotations can be applied to any VRM model by simply pre-multiplying the
 * model's own rest rotation.
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

        // Compute world matrices for the vrma skeleton
        val nodeCount = model.nodeModels.size
        val vrmaNodes = buildVrmaNodes(model.nodeModels)
        val parentOf = buildParentLookup(vrmaNodes)
        val worldMatrices = computeVrmaWorldMatrices(vrmaNodes, parentOf, model.sceneModels.firstOrNull()?.nodeModels?.mapNotNull { nodeNameToIndex[it.name] } ?: emptyList())

        // Extract world rotations for normalization
        val worldRotations = Array(nodeCount) { i ->
            val rot = Quaternionf()
            worldMatrices[i].getNormalizedRotation(rot)
            rot
        }

        // Find hips node and compute rest hips height from world position
        val hipsNodeIndex = nodeToHumanBone.entries.find { it.value == HumanBone.HIPS }?.key
        val restHipsHeight = if (hipsNodeIndex != null && hipsNodeIndex in 0 until nodeCount) {
            val hipsWorldPos = Vector3f()
            worldMatrices[hipsNodeIndex].getTranslation(hipsWorldPos)
            hipsWorldPos.y
        } else {
            0f
        }

        // Compute hips parent world matrix for translation normalization
        val hipsParentWorldMatrix = if (hipsNodeIndex != null) {
            val hipsParent = parentOf[hipsNodeIndex]
            if (hipsParent >= 0) Matrix4f(worldMatrices[hipsParent]) else Matrix4f()
        } else {
            Matrix4f()
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
                        // Get world rotations for normalization
                        val selfWorldRot = worldRotations[nodeIndex]
                        val selfWorldRotInv = Quaternionf(selfWorldRot).invert()
                        val parentWorldRot = if (parentOf[nodeIndex] >= 0) {
                            worldRotations[parentOf[nodeIndex]]
                        } else {
                            Quaternionf() // identity
                        }

                        for (i in 0 until inputData.numElements) {
                            val time = inputData.get(i, 0)
                            if (time > maxTime) maxTime = time
                            val qx = outputData.get(i, 0)
                            val qy = outputData.get(i, 1)
                            val qz = outputData.get(i, 2)
                            val qw = outputData.get(i, 3)

                            // Normalize: parentWorldRot * localRot * inverse(selfWorldRot)
                            val localRot = Quaternionf(qx, qy, qz, qw)
                            val normalizedRot = Quaternionf(parentWorldRot)
                                .mul(localRot)
                                .mul(selfWorldRotInv)

                            builder.rotationKeyframes.add(
                                RotationKeyframe(time, normalizedRot),
                            )
                        }
                    }
                    "translation" -> {
                        // Only hips should have translation; normalize with parent world matrix
                        // normalizedTranslation = hipsParentWorldMatrix * localTranslation
                        // We extract only the rotation part of the parent world matrix for direction,
                        // and apply it as a rotation to the translation vector.
                        val parentMatrix = if (humanBone == HumanBone.HIPS) {
                            hipsParentWorldMatrix
                        } else {
                            Matrix4f()
                        }
                        val parentRot = Quaternionf()
                        parentMatrix.getNormalizedRotation(parentRot)

                        for (i in 0 until inputData.numElements) {
                            val time = inputData.get(i, 0)
                            if (time > maxTime) maxTime = time
                            val tx = outputData.get(i, 0)
                            val ty = outputData.get(i, 1)
                            val tz = outputData.get(i, 2)

                            // Normalize translation by rotating with parent world rotation
                            val localTranslation = Vector3f(tx, ty, tz)
                            val normalizedTranslation = parentRot.transform(localTranslation)

                            builder.translationKeyframes.add(
                                TranslationKeyframe(time, normalizedTranslation),
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
            clips.add(AnimationClip(name = clipName, duration = maxTime, tracks = tracks, restHipsHeight = restHipsHeight))
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

    /**
     * Lightweight node representation for vrma world matrix computation.
     */
    private data class VrmaNode(
        val translation: Vector3f,
        val rotation: Quaternionf,
        val scale: Vector3f,
        val childIndices: List<Int>,
    )

    /**
     * Extracts TRS and child indices from JglTF NodeModels.
     */
    private fun buildVrmaNodes(nodeModels: List<de.javagl.jgltf.model.NodeModel>): List<VrmaNode> {
        return nodeModels.map { nm ->
            val t = nm.translation ?: floatArrayOf(0f, 0f, 0f)
            val r = nm.rotation ?: floatArrayOf(0f, 0f, 0f, 1f)
            val s = nm.scale ?: floatArrayOf(1f, 1f, 1f)
            VrmaNode(
                translation = Vector3f(t[0], t[1], t[2]),
                rotation = Quaternionf(r[0], r[1], r[2], r[3]),
                scale = Vector3f(s[0], s[1], s[2]),
                childIndices = nm.children?.mapNotNull { child ->
                    nodeModels.indexOf(child).takeIf { it >= 0 }
                } ?: emptyList(),
            )
        }
    }

    /**
     * Builds a parent lookup array (nodeIndex -> parentIndex, -1 for roots).
     */
    private fun buildParentLookup(nodes: List<VrmaNode>): IntArray {
        val parentOf = IntArray(nodes.size) { -1 }
        for (i in nodes.indices) {
            for (child in nodes[i].childIndices) {
                if (child in nodes.indices) {
                    parentOf[child] = i
                }
            }
        }
        return parentOf
    }

    /**
     * Computes world matrices for the vrma skeleton nodes via BFS.
     */
    private fun computeVrmaWorldMatrices(
        nodes: List<VrmaNode>,
        parentOf: IntArray,
        rootIndices: List<Int>,
    ): List<Matrix4f> {
        val worldMatrices = ArrayList<Matrix4f>(nodes.size)
        for (i in nodes.indices) {
            worldMatrices.add(Matrix4f())
        }

        val visited = BooleanArray(nodes.size)
        val queue = ArrayDeque<Int>()

        // Enqueue explicit roots
        for (root in rootIndices) {
            queue.addLast(root)
        }
        // Also enqueue orphan nodes
        for (i in nodes.indices) {
            if (parentOf[i] == -1 && i !in rootIndices) {
                queue.addLast(i)
            }
        }

        while (queue.isNotEmpty()) {
            val idx = queue.removeFirst()
            if (visited[idx]) continue
            visited[idx] = true

            val node = nodes[idx]
            val local = Matrix4f()
                .translate(node.translation)
                .rotate(node.rotation)
                .scale(node.scale)

            val parent = parentOf[idx]
            if (parent >= 0 && visited[parent]) {
                worldMatrices[idx].set(worldMatrices[parent]).mul(local)
            } else {
                worldMatrices[idx].set(local)
            }

            for (child in node.childIndices) {
                queue.addLast(child)
            }
        }

        return worldMatrices
    }

    private class MutableBoneTrackBuilder {
        val rotationKeyframes = mutableListOf<RotationKeyframe>()
        val translationKeyframes = mutableListOf<TranslationKeyframe>()
    }
}
