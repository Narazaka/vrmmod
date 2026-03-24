package com.github.narazaka.vrmmod.vrm

import de.javagl.jgltf.model.AccessorModel
import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.MeshPrimitiveModel
import de.javagl.jgltf.model.NodeModel
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import de.javagl.jgltf.model.AccessorFloatData
import de.javagl.jgltf.model.AccessorByteData
import de.javagl.jgltf.model.AccessorShortData
import de.javagl.jgltf.model.AccessorIntData
import de.javagl.jgltf.model.v2.MaterialModelV2
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Orchestrates JglTF and [VrmExtensionParser] to produce a complete [VrmModel]
 * from a VRM file [InputStream].
 */
object VrmParser {

    /**
     * Parses a `.vrm` file from the given input stream into a [VrmModel].
     *
     * Reads the stream bytes once and uses them to build both the high-level
     * [GltfModel] (meshes, skins, nodes, textures) and the raw [GltfAssetV2]
     * (for VRM extension JSON).
     */
    fun parse(inputStream: InputStream): VrmModel {
        val bytes = inputStream.readBytes()

        // High-level model for mesh/skin/node/texture data
        val modelReader = GltfModelReader()
        val model = modelReader.readWithoutReferences(ByteArrayInputStream(bytes))

        // Low-level: read raw glTF JSON to access VRM extensions
        val rawData = RawGltfDataReader.read(ByteArrayInputStream(bytes))
        val jsonBuf = rawData.jsonData
        val jsonBytes = ByteArray(jsonBuf.remaining())
        jsonBuf.get(jsonBytes)
        val gltfReader = GltfReaderV2()
        val gltf = gltfReader.read(ByteArrayInputStream(jsonBytes))

        // Parse VRM extensions
        val extensions = gltf.extensions
        val vrmExtension = extensions?.get("VRMC_vrm")
        val vrmJson = vrmExtension?.let { VrmExtensionParser.toJsonObject(it) }

        val meta = vrmJson?.let { VrmExtensionParser.parseMeta(it) } ?: VrmMeta(name = "")
        val humanoid = vrmJson?.let { VrmExtensionParser.parseHumanoid(it) } ?: VrmHumanoid()
        val expressions = VrmExtensionParser.parseExpressions(vrmJson)

        // Extract geometry and skeleton from high-level model
        val meshes = extractMeshes(model)
        val skeleton = extractSkeleton(model)
        val textures = extractTextures(model)

        return VrmModel(
            meta = meta,
            humanoid = humanoid,
            meshes = meshes,
            skeleton = skeleton,
            textures = textures,
            expressions = expressions,
        )
    }

    private fun extractMeshes(model: GltfModel): List<VrmMesh> {
        val imageModels = model.imageModels
        val materialModels = model.materialModels
        return model.meshModels.map { meshModel ->
            VrmMesh(
                name = meshModel.name ?: "",
                primitives = meshModel.meshPrimitiveModels.map {
                    extractPrimitive(it, materialModels, imageModels)
                },
            )
        }
    }

    private fun extractPrimitive(
        primitive: MeshPrimitiveModel,
        materialModels: List<de.javagl.jgltf.model.MaterialModel>,
        imageModels: List<de.javagl.jgltf.model.ImageModel>,
    ): VrmPrimitive {
        val positionAccessor = primitive.attributes["POSITION"]
        val normalAccessor = primitive.attributes["NORMAL"]
        val texCoordAccessor = primitive.attributes["TEXCOORD_0"]
        val jointsAccessor = primitive.attributes["JOINTS_0"]
        val weightsAccessor = primitive.attributes["WEIGHTS_0"]
        val indicesAccessor = primitive.indices

        val positions = positionAccessor?.let { readFloatAccessor(it) } ?: floatArrayOf()
        val vertexCount = if (positionAccessor != null) {
            positionAccessor.count
        } else {
            0
        }

        val normals = normalAccessor?.let { readFloatAccessor(it) } ?: floatArrayOf()
        val texCoords = texCoordAccessor?.let { readFloatAccessor(it) } ?: floatArrayOf()
        val joints = jointsAccessor?.let { readIntAccessor(it) } ?: intArrayOf()
        val weights = weightsAccessor?.let { readFloatAccessor(it) } ?: floatArrayOf()
        val indices = indicesAccessor?.let { readIntAccessor(it) } ?: intArrayOf()

        val morphTargets = primitive.targets.map { target ->
            val posDeltas = target["POSITION"]?.let { readFloatAccessor(it) } ?: floatArrayOf()
            val normDeltas = target["NORMAL"]?.let { readFloatAccessor(it) } ?: floatArrayOf()
            VrmMorphTarget(positionDeltas = posDeltas, normalDeltas = normDeltas)
        }

        // Resolve material index in the global material list
        val materialIndex = primitive.materialModel?.let { mat ->
            materialModels.indexOf(mat)
        } ?: -1

        // Resolve image index: material -> baseColorTexture -> imageModel -> index in imageModels
        val imageIndex = primitive.materialModel?.let { mat ->
            if (mat is MaterialModelV2) {
                val textureModel = mat.baseColorTexture
                val imageModel = textureModel?.imageModel
                if (imageModel != null) imageModels.indexOf(imageModel) else -1
            } else {
                -1
            }
        } ?: -1

        return VrmPrimitive(
            positions = positions,
            normals = normals,
            texCoords = texCoords,
            joints = joints,
            weights = weights,
            indices = indices,
            vertexCount = vertexCount,
            materialIndex = materialIndex,
            imageIndex = imageIndex,
            morphTargets = morphTargets,
        )
    }

    private fun readFloatAccessor(accessor: AccessorModel): FloatArray {
        val data = accessor.accessorData
        if (data is AccessorFloatData) {
            val numElements = data.numElements
            val numComponents = data.numComponentsPerElement
            val result = FloatArray(numElements * numComponents)
            for (i in 0 until numElements) {
                for (j in 0 until numComponents) {
                    result[i * numComponents + j] = data.get(i, j)
                }
            }
            return result
        }
        return floatArrayOf()
    }

    private fun readIntAccessor(accessor: AccessorModel): IntArray {
        val data = accessor.accessorData
        val numElements: Int
        val numComponents: Int
        return when (data) {
            is AccessorByteData -> {
                numElements = data.numElements
                numComponents = data.numComponentsPerElement
                IntArray(numElements * numComponents) { idx ->
                    data.getInt(idx / numComponents, idx % numComponents)
                }
            }
            is AccessorShortData -> {
                numElements = data.numElements
                numComponents = data.numComponentsPerElement
                IntArray(numElements * numComponents) { idx ->
                    data.getInt(idx / numComponents, idx % numComponents)
                }
            }
            is AccessorIntData -> {
                numElements = data.numElements
                numComponents = data.numComponentsPerElement
                IntArray(numElements * numComponents) { idx ->
                    data.get(idx / numComponents, idx % numComponents)
                }
            }
            is AccessorFloatData -> {
                // indices can sometimes be stored as float (unusual but handle it)
                numElements = data.numElements
                numComponents = data.numComponentsPerElement
                IntArray(numElements * numComponents) { idx ->
                    data.get(idx / numComponents, idx % numComponents).toInt()
                }
            }
            else -> intArrayOf()
        }
    }

    private fun extractSkeleton(model: GltfModel): VrmSkeleton {
        val nodeModels = model.nodeModels
        val allNodes = nodeModels.mapIndexed { index, nodeModel ->
            nodeModelToVrmNode(nodeModel, nodeModels)
        }

        // Root nodes: scene root nodes
        val rootNodeIndices = model.sceneModels.firstOrNull()?.nodeModels?.map { root ->
            nodeModels.indexOf(root)
        } ?: emptyList()

        // Skin data
        val skin = model.skinModels.firstOrNull()
        val jointNodeIndices = skin?.joints?.map { joint ->
            nodeModels.indexOf(joint)
        } ?: emptyList()

        val inverseBindMatrices = skin?.let { extractInverseBindMatrices(it) } ?: emptyList()

        return VrmSkeleton(
            nodes = allNodes,
            rootNodeIndices = rootNodeIndices,
            jointNodeIndices = jointNodeIndices,
            inverseBindMatrices = inverseBindMatrices,
        )
    }

    private fun nodeModelToVrmNode(nodeModel: NodeModel, allNodes: List<NodeModel>): VrmNode {
        val translation = nodeModel.translation?.let { Vector3f(it[0], it[1], it[2]) }
            ?: Vector3f(0f, 0f, 0f)
        val rotation = nodeModel.rotation?.let { Quaternionf(it[0], it[1], it[2], it[3]) }
            ?: Quaternionf(0f, 0f, 0f, 1f)
        val scale = nodeModel.scale?.let { Vector3f(it[0], it[1], it[2]) }
            ?: Vector3f(1f, 1f, 1f)

        val childIndices = nodeModel.children.map { child ->
            allNodes.indexOf(child)
        }

        // Mesh index: find which mesh this node references
        val meshIndex = nodeModel.meshModels.firstOrNull()?.let { mesh ->
            // Find the mesh's index in the global mesh list
            // NodeModel doesn't directly expose this, use -1 as default
            -1
        } ?: -1

        return VrmNode(
            name = nodeModel.name ?: "",
            translation = translation,
            rotation = rotation,
            scale = scale,
            childIndices = childIndices,
            meshIndex = meshIndex,
        )
    }

    private fun extractInverseBindMatrices(skin: de.javagl.jgltf.model.SkinModel): List<Matrix4f> {
        val accessor = skin.inverseBindMatrices ?: return emptyList()
        val data = accessor.accessorData
        if (data is AccessorFloatData) {
            val numElements = data.numElements
            return (0 until numElements).map { i ->
                val m = Matrix4f()
                // glTF stores matrices in column-major order
                m.set(
                    data.get(i, 0), data.get(i, 1), data.get(i, 2), data.get(i, 3),
                    data.get(i, 4), data.get(i, 5), data.get(i, 6), data.get(i, 7),
                    data.get(i, 8), data.get(i, 9), data.get(i, 10), data.get(i, 11),
                    data.get(i, 12), data.get(i, 13), data.get(i, 14), data.get(i, 15),
                )
                m
            }
        }
        return emptyList()
    }

    private fun extractTextures(model: GltfModel): List<VrmTexture> {
        return model.imageModels.mapIndexed { index, imageModel ->
            val imageData = imageModel.imageData
            val bytes = ByteArray(imageData.capacity())
            imageData.rewind()
            imageData.get(bytes)

            VrmTexture(
                index = index,
                name = imageModel.uri ?: "image_$index",
                imageData = bytes,
                mimeType = imageModel.mimeType ?: "image/png",
            )
        }
    }
}
