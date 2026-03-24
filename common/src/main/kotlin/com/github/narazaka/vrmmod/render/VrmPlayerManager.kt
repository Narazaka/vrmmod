package com.github.narazaka.vrmmod.render

import com.github.narazaka.vrmmod.VrmMod
import com.github.narazaka.vrmmod.vrm.VrmParser
import net.minecraft.client.Minecraft
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the mapping of player UUIDs to loaded VRM model states.
 * Handles async loading and cleanup.
 */
object VrmPlayerManager {
    private val states = ConcurrentHashMap<UUID, VrmState>()
    private val loading = ConcurrentHashMap<UUID, CompletableFuture<*>>()

    fun get(playerUUID: UUID): VrmState? {
        return states[playerUUID]
    }

    /**
     * Load a VRM model for a player from a local file.
     * Parsing is done on a worker thread; texture registration is
     * scheduled on the render thread via [Minecraft.execute].
     */
    fun loadLocal(playerUUID: UUID, file: File) {
        if (loading.containsKey(playerUUID)) return

        val future = CompletableFuture.supplyAsync {
            VrmParser.parse(file.inputStream())
        }.thenAcceptAsync({ model ->
            val texLocations = VrmTextureManager.registerTextures(playerUUID, model.textures)
            states[playerUUID] = VrmState(model = model, textureLocations = texLocations)
            loading.remove(playerUUID)
            VrmMod.logger.info("VRM model loaded for $playerUUID (${model.meta.name})")
        }, { runnable ->
            Minecraft.getInstance().execute(runnable)
        }).exceptionally { e ->
            VrmMod.logger.error("Failed to load VRM model for $playerUUID", e)
            loading.remove(playerUUID)
            null
        }

        loading[playerUUID] = future
    }

    fun unload(playerUUID: UUID) {
        states.remove(playerUUID)
        VrmTextureManager.unregisterTextures(playerUUID)
        loading.remove(playerUUID)?.cancel(false)
    }

    fun clear() {
        states.clear()
        loading.values.forEach { it.cancel(false) }
        loading.clear()
        VrmTextureManager.clear()
    }
}
