package net.narazaka.vrmmod.render

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.vrm.VrmTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages DynamicTexture registration and cleanup for VRM model textures.
 *
 * Each player's VRM textures are registered with the Minecraft TextureManager
 * under unique ResourceLocations and cached for reuse.
 */
object VrmTextureManager {

    private val registeredTextures = ConcurrentHashMap<String, ResourceLocation>()

    /**
     * Registers the given VRM textures as DynamicTextures with the Minecraft
     * TextureManager.
     *
     * Must be called on the render thread.
     *
     * @param playerUUID the UUID of the player owning the VRM model
     * @param textures the list of VRM textures to register
     * @return a list of ResourceLocations corresponding to each registered texture
     */
    fun registerTextures(playerUUID: UUID, textures: List<VrmTexture>): List<ResourceLocation> {
        val textureManager = Minecraft.getInstance().textureManager
        return textures.mapIndexed { idx, vrmTexture ->
            val key = "$playerUUID/$idx"
            registeredTextures.getOrPut(key) {
                val nativeImage = NativeImage.read(ByteArrayInputStream(vrmTexture.imageData))
                try {
                    val dynamicTexture = DynamicTexture(nativeImage)
                    val location = ResourceLocation.fromNamespaceAndPath(
                        VrmMod.MOD_ID,
                        "vrm_tex/$playerUUID/$idx",
                    )
                    textureManager.register(location, dynamicTexture)
                    location
                } catch (e: Exception) {
                    nativeImage.close()
                    throw e
                }
            }
        }
    }

    /**
     * Unregisters and releases all textures associated with the given player.
     *
     * @param playerUUID the UUID of the player whose textures should be removed
     */
    fun unregisterTextures(playerUUID: UUID) {
        val textureManager = Minecraft.getInstance().textureManager
        val prefix = "$playerUUID/"
        val keysToRemove = registeredTextures.keys.filter { it.startsWith(prefix) }
        for (key in keysToRemove) {
            val location = registeredTextures.remove(key)
            if (location != null) {
                textureManager.release(location)
            }
        }
    }

    /**
     * Removes and releases all registered VRM textures.
     */
    fun clear() {
        val textureManager = Minecraft.getInstance().textureManager
        for ((_, location) in registeredTextures) {
            textureManager.release(location)
        }
        registeredTextures.clear()
    }
}
