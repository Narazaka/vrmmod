package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import dev.architectury.networking.NetworkManager
import net.minecraft.server.level.ServerPlayer

object VrmModNetwork {
    fun register() {
        VrmMod.logger.info("Registering VRM mod network packets")

        // Register C2S receiver (server handles ModelAnnounce)
        NetworkManager.registerReceiver(
            NetworkManager.c2s(),
            ModelAnnouncePayload.TYPE,
            ModelAnnouncePayload.CODEC,
        ) { payload, context ->
            context.queue {
                val player = context.player
                if (player is ServerPlayer) {
                    VrmModServer.handleModelAnnounce(player, payload)
                }
            }
        }

        // Register S2C receiver (client handles PlayerModel)
        NetworkManager.registerReceiver(
            NetworkManager.s2c(),
            PlayerModelPayload.TYPE,
            PlayerModelPayload.CODEC,
        ) { payload, context ->
            context.queue {
                MultiplayModelHandler.handlePlayerModel(payload)
            }
        }
    }
}
