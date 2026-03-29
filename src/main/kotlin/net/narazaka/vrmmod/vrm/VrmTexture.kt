package net.narazaka.vrmmod.vrm

/**
 * A texture image extracted from a VRM file.
 */
data class VrmTexture(
    val index: Int,
    val name: String = "",
    val imageData: ByteArray = byteArrayOf(),
    val mimeType: String = "image/png",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VrmTexture) return false
        return index == other.index &&
            name == other.name &&
            imageData.contentEquals(other.imageData) &&
            mimeType == other.mimeType
    }

    override fun hashCode(): Int {
        var result = index
        result = 31 * result + name.hashCode()
        result = 31 * result + imageData.contentHashCode()
        return result
    }
}
