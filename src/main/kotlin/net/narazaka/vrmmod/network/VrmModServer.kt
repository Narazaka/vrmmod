package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.NormalMode
import dev.architectury.networking.NetworkManager
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object VrmModServer {
    data class PlayerModelInfo(
        val vroidHubModelId: String,
        val multiplayLicenseId: String?,
        val scale: Float,
        val normalMode: NormalMode,
    )

    private val playerModels = ConcurrentHashMap<UUID, PlayerModelInfo>()

    fun handleModelAnnounce(player: ServerPlayer, payload: ModelAnnouncePayload) {
        val uuid = player.uuid
        val isFirstAnnounce = !playerModels.containsKey(uuid)

        if (payload.vroidHubModelId == null) {
            playerModels.remove(uuid)
            VrmMod.logger.info("Player {} cleared VRM model", player.gameProfile.name)
        } else {
            val info = PlayerModelInfo(payload.vroidHubModelId, payload.multiplayLicenseId, payload.scale, payload.normalMode)
            playerModels[uuid] = info
            VrmMod.logger.info("Player {} announced VRM model: {}", player.gameProfile.name, payload.vroidHubModelId)
        }

        // Broadcast this player's model to all other players
        val broadcastPayload = PlayerModelPayload(
            playerUUID = uuid,
            vroidHubModelId = payload.vroidHubModelId,
            multiplayLicenseId = payload.multiplayLicenseId,
            scale = payload.scale,
            normalMode = payload.normalMode,
        )
        for (otherPlayer in player.server.playerList.players) {
            if (otherPlayer.uuid != uuid) {
                //? if HAS_CUSTOM_PAYLOAD {
                NetworkManager.sendToPlayer(otherPlayer, broadcastPayload)
                //?} else {
                /*NetworkManager.sendToPlayer(otherPlayer, PlayerModelPayload.PACKET_ID) { buf -> PlayerModelPayload.encode(buf, broadcastPayload) }*/
                //?}
            }
        }

        // On first announce only: send existing players' models to the newly connected player
        if (isFirstAnnounce) {
            for ((existingUuid, info) in playerModels) {
                if (existingUuid != uuid) {
                    val existingPayload = PlayerModelPayload(
                        playerUUID = existingUuid,
                        vroidHubModelId = info.vroidHubModelId,
                        multiplayLicenseId = info.multiplayLicenseId,
                        scale = info.scale,
                        normalMode = info.normalMode,
                    )
                    //? if HAS_CUSTOM_PAYLOAD {
                    NetworkManager.sendToPlayer(player, existingPayload)
                    //?} else {
                    /*NetworkManager.sendToPlayer(player, PlayerModelPayload.PACKET_ID) { buf -> PlayerModelPayload.encode(buf, existingPayload) }*/
                    //?}
                }
            }
        }
    }

    fun handlePlayerDisconnect(playerUUID: UUID, server: net.minecraft.server.MinecraftServer) {
        val removed = playerModels.remove(playerUUID)
        if (removed != null) {
            VrmMod.logger.info("Player {} disconnected, broadcasting model removal", playerUUID)
            val clearPayload = PlayerModelPayload(
                playerUUID = playerUUID,
                vroidHubModelId = null,
                multiplayLicenseId = null,
                scale = 1.0f,
            )
            for (player in server.playerList.players) {
                //? if HAS_CUSTOM_PAYLOAD {
                NetworkManager.sendToPlayer(player, clearPayload)
                //?} else {
                /*NetworkManager.sendToPlayer(player, PlayerModelPayload.PACKET_ID) { buf -> PlayerModelPayload.encode(buf, clearPayload) }*/
                //?}
            }
        }
    }

    fun clear() {
        playerModels.clear()
    }
}
