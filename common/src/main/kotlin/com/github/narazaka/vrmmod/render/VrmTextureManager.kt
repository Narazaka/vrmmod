package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.vrm.VrmTexture
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.ResourceLocation
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages DynamicTexture registration and cleanup for VRM model textures.
 * Must be called on the render thread for register/unregister operations.
 */
object VrmTextureManager {
    private val registeredTextures = ConcurrentHashMap<String, Pair<ResourceLocation, DynamicTexture>>()

    /**
     * Register VRM textures for a player and return the ResourceLocations.
     * Must be called on the render thread.
     */
    fun registerTextures(playerUUID: UUID, textures: List<VrmTexture>): List<ResourceLocation> {
        val textureManager = Minecraft.getInstance().textureManager
        return textures.mapIndexed { idx, vrmTex ->
            val key = "${playerUUID}_$idx"
            registeredTextures.getOrPut(key) {
                val nativeImage = NativeImage.read(ByteArrayInputStream(vrmTex.imageData))
                val dynamicTexture = DynamicTexture(nativeImage)
                val location = ResourceLocation.fromNamespaceAndPath(
                    VrmMod.MOD_ID, "vrm_tex/${playerUUID}/$idx"
                )
                textureManager.register(location, dynamicTexture)
                Pair(location, dynamicTexture)
            }.first
        }
    }

    /**
     * Unregister and release all textures for a player.
     */
    fun unregisterTextures(playerUUID: UUID) {
        val keysToRemove = registeredTextures.keys.filter { it.startsWith("${playerUUID}_") }
        keysToRemove.forEach { key ->
            registeredTextures.remove(key)?.let { (_, texture) ->
                texture.close()
            }
        }
    }

    fun clear() {
        registeredTextures.values.forEach { (_, texture) -> texture.close() }
        registeredTextures.clear()
    }
}
