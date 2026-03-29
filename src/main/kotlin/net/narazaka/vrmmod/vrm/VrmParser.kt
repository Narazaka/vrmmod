package net.narazaka.vrmmod.vrm

import de.javagl.jgltf.model.AccessorModel
import de.javagl.jgltf.model.GltfModel
import de.javagl.jgltf.model.MeshPrimitiveModel
import de.javagl.jgltf.model.NodeModel
import de.javagl.jgltf.model.io.GltfModelReader
import de.javagl.jgltf.model.io.RawGltfData
import de.javagl.jgltf.model.io.RawGltfDataReader
import de.javagl.jgltf.model.io.v2.GltfReaderV2
import de.javagl.jgltf.model.AccessorFloatData
import de.javagl.jgltf.model.AccessorByteData
import de.javagl.jgltf.model.AccessorShortData
import de.javagl.jgltf.model.AccessorIntData
import de.javagl.jgltf.model.v2.MaterialModelV2
import de.javagl.jgltf.impl.v2.GlTF
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.io.ByteArrayInputStream
import java.io.InputStream
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

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

        // Detect VRM version and parse extensions
        val extensions = gltf.extensions
        val isV1 = extensions?.containsKey("VRMC_vrm") == true
        val isV0 = !isV1 && extensions?.containsKey("VRM") == true

        val vrmJson: JsonObject?
        val springBoneExtension: Any?

        if (isV0) {
            // VRM 0.x: convert to v1 format
            val v0Extension = extensions!!["VRM"]
            val v0Json = VrmExtensionParser.toJsonObject(v0Extension!!)
            val nodeChildren = buildNodeChildrenMap(model)
            val meshToNodeMap = buildMeshToNodeMap(model)
            val converted = VrmV0Converter.convertAll(v0Json, nodeChildren, meshToNodeMap)
            vrmJson = converted.vrmcVrm
            springBoneExtension = converted.vrmcSpringBone
        } else {
            // VRM 1.0: use directly
            val vrmExtension = extensions?.get("VRMC_vrm")
            vrmJson = vrmExtension?.let { VrmExtensionParser.toJsonObject(it) }
            springBoneExtension = extensions?.get("VRMC_springBone")
        }

        val meta = vrmJson?.let { VrmExtensionParser.parseMeta(it) } ?: VrmMeta(name = "")
        val humanoid = vrmJson?.let { VrmExtensionParser.parseHumanoid(it) } ?: VrmHumanoid()

        // Build node -> mesh index mapping from high-level model
        val nodeToMeshIndex = buildNodeToMeshMap(model)

        // Parse expressions, resolving node indices to mesh indices
        val rawExpressions = VrmExtensionParser.parseExpressions(vrmJson)
        val expressions = rawExpressions.map { expr ->
            expr.copy(
                morphTargetBinds = expr.morphTargetBinds.map { bind ->
                    bind.copy(nodeIndex = nodeToMeshIndex[bind.nodeIndex] ?: -1)
                }
            )
        }

        // Parse firstPerson annotations, resolving node indices to mesh indices
        val rawFirstPerson = VrmExtensionParser.parseFirstPerson(vrmJson)
        val firstPersonAnnotations = rawFirstPerson.mapKeys { (nodeIdx, _) ->
            nodeToMeshIndex[nodeIdx] ?: -1
        }.filterKeys { it >= 0 }

        // Parse lookAt offset
        val lookAtOffset = VrmExtensionParser.parseLookAtOffset(vrmJson)

        // Parse VRMC_springBone extension
        val springBone = VrmExtensionParser.parseSpringBone(springBoneExtension)

        // Parse VRMC_materials_mtoon from per-material extensions
        val mtoonMaterials = VrmExtensionParser.parseMtoonMaterials(gltf.materials)

        // Extract geometry and skeleton from high-level model
        // Pass raw glTF data for sparse accessor resolution
        val meshes = extractMeshes(model, gltf, rawData)
        val skeleton = extractSkeleton(model)
        val textures = extractTextures(model)

        var vrmModel = VrmModel(
            meta = meta,
            humanoid = humanoid,
            meshes = meshes,
            skeleton = skeleton,
            textures = textures,
            expressions = expressions,
            springBone = springBone,
            mtoonMaterials = mtoonMaterials,
            firstPersonAnnotations = firstPersonAnnotations,
            lookAtOffsetFromHeadBone = lookAtOffset,
        )

        // VRM 0.x coordinate conversion: Z- forward → Z+ forward
        if (isV0) {
            vrmModel = VrmV0Converter.convertCoordinates(vrmModel)
        }

        // Compute normalized bone info from the final (possibly v0-converted) skeleton.
        // This follows three-vrm's VRMHumanoidRig._setupTransforms():
        // pre-compute parentWorldRotation and boneRotation for each humanoid bone.
        val normalizedBoneInfo = VrmModel.computeNormalizedBoneInfo(
            vrmModel.humanoid, vrmModel.skeleton
        )
        return vrmModel.copy(normalizedBoneInfo = normalizedBoneInfo)
    }

    /**
     * Builds a mapping from glTF node index to mesh model list index.
     */
    private fun buildNodeToMeshMap(model: GltfModel): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for ((nodeIdx, nodeModel) in model.nodeModels.withIndex()) {
            val meshModels = nodeModel.meshModels
            if (meshModels.isNotEmpty()) {
                val meshIdx = model.meshModels.indexOf(meshModels[0])
                if (meshIdx >= 0) {
                    result[nodeIdx] = meshIdx
                }
            }
        }
        return result
    }

    private fun extractMeshes(model: GltfModel, gltf: GlTF, rawData: RawGltfData): List<VrmMesh> {
        val imageModels = model.imageModels
        val materialModels = model.materialModels
        val binaryData = rawData.binaryData

        // Build mesh index -> skin index mapping from raw glTF nodes.
        // Each node can reference a mesh and a skin; we need to associate them.
        val meshToSkinIndex = mutableMapOf<Int, Int>()
        for (node in gltf.nodes ?: emptyList()) {
            val meshIdx = node.mesh ?: continue
            val skinIdx = node.skin ?: continue
            meshToSkinIndex[meshIdx] = skinIdx
        }

        // Build a mapping from raw glTF mesh index to its morph target accessor indices.
        // This allows us to resolve sparse accessors that JglTF fails to handle.
        val rawMeshes = gltf.meshes ?: emptyList()

        return model.meshModels.mapIndexed { meshModelIndex, meshModel ->
            val rawMesh = rawMeshes.getOrNull(meshModelIndex)
            val rawPrimitives = rawMesh?.primitives ?: emptyList()

            VrmMesh(
                name = meshModel.name ?: "",
                primitives = meshModel.meshPrimitiveModels.mapIndexed { primIndex, prim ->
                    val rawPrim = rawPrimitives.getOrNull(primIndex)
                    extractPrimitive(prim, materialModels, imageModels, rawPrim, gltf, binaryData)
                },
                skinIndex = meshToSkinIndex[meshModelIndex] ?: -1,
            )
        }
    }

    private fun extractPrimitive(
        primitive: MeshPrimitiveModel,
        materialModels: List<de.javagl.jgltf.model.MaterialModel>,
        imageModels: List<de.javagl.jgltf.model.ImageModel>,
        rawPrimitive: de.javagl.jgltf.impl.v2.MeshPrimitive?,
        gltf: GlTF,
        binaryData: ByteBuffer?,
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

        // Extract morph targets, resolving sparse accessors from raw glTF data
        val rawTargets = rawPrimitive?.targets ?: emptyList()
        val morphTargets = primitive.targets.mapIndexed { targetIdx, target ->
            val rawTarget = rawTargets.getOrNull(targetIdx)
            val posAccessor = target["POSITION"]
            val normAccessor = target["NORMAL"]

            val posDeltas = if (posAccessor != null && posAccessor.accessorData != null) {
                readFloatAccessor(posAccessor)
            } else if (rawTarget != null && binaryData != null) {
                // JglTF failed to resolve this accessor (likely sparse) - resolve manually
                val accIdx = rawTarget["POSITION"]
                if (accIdx != null) {
                    resolveSparseAccessor(accIdx, gltf, binaryData)
                } else floatArrayOf()
            } else floatArrayOf()

            val normDeltas = if (normAccessor != null && normAccessor.accessorData != null) {
                readFloatAccessor(normAccessor)
            } else if (rawTarget != null && binaryData != null) {
                val accIdx = rawTarget["NORMAL"]
                if (accIdx != null) {
                    resolveSparseAccessor(accIdx, gltf, binaryData)
                } else floatArrayOf()
            } else floatArrayOf()

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

        // Resolve alphaMode from material
        val alphaMode = primitive.materialModel?.let { mat ->
            if (mat is MaterialModelV2) {
                when (mat.alphaMode) {
                    MaterialModelV2.AlphaMode.MASK -> AlphaMode.MASK
                    MaterialModelV2.AlphaMode.BLEND -> AlphaMode.BLEND
                    else -> AlphaMode.OPAQUE
                }
            } else AlphaMode.OPAQUE
        } ?: AlphaMode.OPAQUE

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
            alphaMode = alphaMode,
            morphTargets = morphTargets,
        )
    }

    /**
     * Resolves a sparse accessor from the raw glTF binary data.
     *
     * Sparse accessors store only non-zero values at specified indices.
     * JglTF doesn't resolve these for morph targets, so we do it manually.
     * The result is a full float array with zeros at non-sparse positions.
     */
    private fun resolveSparseAccessor(
        accessorIndex: Int,
        gltf: GlTF,
        binaryData: ByteBuffer,
    ): FloatArray {
        val accessors = gltf.accessors ?: return floatArrayOf()
        val accessor = accessors.getOrNull(accessorIndex) ?: return floatArrayOf()
        val count = accessor.count ?: return floatArrayOf()
        val type = accessor.type ?: return floatArrayOf()

        val numComponents = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            else -> return floatArrayOf()
        }

        val result = FloatArray(count * numComponents)

        // If the accessor has a bufferView, read the base data first
        val bufferViewIndex = accessor.bufferView
        if (bufferViewIndex != null) {
            val accessorByteOffset = accessor.byteOffset ?: accessor.defaultByteOffset() ?: 0
            val baseData = readBufferViewFloats(bufferViewIndex, gltf, binaryData, count * numComponents, accessorByteOffset, numComponents)
            baseData.copyInto(result)
        }

        // Apply sparse data on top
        val sparse = accessor.sparse ?: return result
        val sparseCount = sparse.count ?: return result
        val sparseIndices = sparse.indices ?: return result
        val sparseValues = sparse.values ?: return result

        val indicesBvIndex = sparseIndices.bufferView ?: return result
        val indicesByteOffset = sparseIndices.byteOffset ?: sparseIndices.defaultByteOffset() ?: 0
        val indicesComponentType = sparseIndices.componentType ?: return result

        val valuesBvIndex = sparseValues.bufferView ?: return result
        val valuesByteOffset = sparseValues.byteOffset ?: sparseValues.defaultByteOffset() ?: 0

        val bufferViews = gltf.bufferViews ?: return result

        // Read sparse indices
        val indicesBv = bufferViews.getOrNull(indicesBvIndex) ?: return result
        val indicesBvOffset = (indicesBv.byteOffset ?: indicesBv.defaultByteOffset() ?: 0) + indicesByteOffset
        val indexArray = IntArray(sparseCount)
        val indicesBuf = binaryData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        indicesBuf.position(indicesBvOffset)
        for (i in 0 until sparseCount) {
            indexArray[i] = when (indicesComponentType) {
                5121 -> indicesBuf.get().toInt() and 0xFF // UNSIGNED_BYTE
                5123 -> indicesBuf.short.toInt() and 0xFFFF // UNSIGNED_SHORT
                5125 -> indicesBuf.int // UNSIGNED_INT
                else -> 0
            }
        }

        // Read sparse values (always FLOAT for morph target POSITION/NORMAL)
        val valuesBv = bufferViews.getOrNull(valuesBvIndex) ?: return result
        val valuesBvOffset = (valuesBv.byteOffset ?: valuesBv.defaultByteOffset() ?: 0) + valuesByteOffset
        val valuesBuf = binaryData.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        valuesBuf.position(valuesBvOffset)
        for (i in 0 until sparseCount) {
            val targetIndex = indexArray[i]
            for (c in 0 until numComponents) {
                result[targetIndex * numComponents + c] = valuesBuf.float
            }
        }

        return result
    }

    /**
     * Reads float values from a buffer view, respecting byteStride.
     *
     * @param numComponents number of float components per element (e.g. 3 for VEC3)
     *   Used to determine element size for stride calculation. When 0, reads
     *   sequentially (legacy tightly-packed behavior).
     */
    private fun readBufferViewFloats(
        bufferViewIndex: Int,
        gltf: GlTF,
        binaryData: ByteBuffer,
        count: Int,
        accessorByteOffset: Int = 0,
        numComponents: Int = 0,
    ): FloatArray {
        val bufferViews = gltf.bufferViews ?: return floatArrayOf()
        val bv = bufferViews.getOrNull(bufferViewIndex) ?: return floatArrayOf()
        val offset = (bv.byteOffset ?: bv.defaultByteOffset() ?: 0) + accessorByteOffset
        val buf = binaryData.duplicate().order(ByteOrder.LITTLE_ENDIAN)

        val byteStride = bv.byteStride ?: 0
        val elementByteSize = if (numComponents > 0) numComponents * 4 else 0
        val hasStride = byteStride > 0 && elementByteSize > 0 && byteStride != elementByteSize

        if (!hasStride) {
            // Tightly packed: read sequentially
            buf.position(offset)
            val result = FloatArray(count)
            for (i in 0 until count) {
                result[i] = buf.float
            }
            return result
        }

        // Interleaved: respect byteStride between elements
        val numElements = count / numComponents
        val result = FloatArray(count)
        for (elem in 0 until numElements) {
            buf.position(offset + elem * byteStride)
            for (comp in 0 until numComponents) {
                result[elem * numComponents + comp] = buf.float
            }
        }
        return result
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
        val meshModels = model.meshModels
        val allNodes = nodeModels.mapIndexed { index, nodeModel ->
            nodeModelToVrmNode(nodeModel, nodeModels, meshModels)
        }

        // Root nodes: scene root nodes
        val rootNodeIndices = model.sceneModels.firstOrNull()?.nodeModels?.map { root ->
            nodeModels.indexOf(root)
        } ?: emptyList()

        // Skin data: read ALL skins (glTF allows multiple skins for different meshes)
        val skins = model.skinModels.map { skinModel ->
            val jointNodeIndices = skinModel.joints.map { joint ->
                nodeModels.indexOf(joint)
            }
            val inverseBindMatrices = extractInverseBindMatrices(skinModel)
            VrmSkin(jointNodeIndices = jointNodeIndices, inverseBindMatrices = inverseBindMatrices)
        }

        return VrmSkeleton(
            nodes = allNodes,
            rootNodeIndices = rootNodeIndices,
            skins = skins,
        )
    }

    private fun nodeModelToVrmNode(nodeModel: NodeModel, allNodes: List<NodeModel>, allMeshModels: List<de.javagl.jgltf.model.MeshModel> = emptyList()): VrmNode {
        val translation = nodeModel.translation?.let { Vector3f(it[0], it[1], it[2]) }
            ?: Vector3f(0f, 0f, 0f)
        val rotation = nodeModel.rotation?.let { Quaternionf(it[0], it[1], it[2], it[3]) }
            ?: Quaternionf(0f, 0f, 0f, 1f)
        val scale = nodeModel.scale?.let { Vector3f(it[0], it[1], it[2]) }
            ?: Vector3f(1f, 1f, 1f)

        val childIndices = nodeModel.children.map { child ->
            allNodes.indexOf(child)
        }

        // Mesh index: find which mesh this node references in the global mesh list
        val meshIndex = nodeModel.meshModels.firstOrNull()?.let { mesh ->
            allMeshModels.indexOf(mesh)
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

    private fun buildNodeChildrenMap(model: GltfModel): Map<Int, List<Int>> {
        val nodeModels = model.nodeModels
        val result = mutableMapOf<Int, List<Int>>()
        for ((idx, node) in nodeModels.withIndex()) {
            result[idx] = node.children.map { child -> nodeModels.indexOf(child) }
        }
        return result
    }

    private fun buildMeshToNodeMap(model: GltfModel): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()
        for ((nodeIdx, nodeModel) in model.nodeModels.withIndex()) {
            val meshModels = nodeModel.meshModels
            if (meshModels.isNotEmpty()) {
                val meshIdx = model.meshModels.indexOf(meshModels[0])
                if (meshIdx >= 0 && meshIdx !in result) {
                    result[meshIdx] = nodeIdx
                }
            }
        }
        return result
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
