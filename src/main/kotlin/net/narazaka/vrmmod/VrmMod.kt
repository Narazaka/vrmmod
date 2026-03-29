package net.narazaka.vrmmod

import net.narazaka.vrmmod.network.VrmModNetwork
import net.narazaka.vrmmod.network.VrmModServer
import dev.architectury.event.events.common.PlayerEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object VrmMod {
    const val MOD_ID = "vrmmod"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        VrmModNetwork.register()

        // Handle player disconnect on server side
        PlayerEvent.PLAYER_QUIT.register { player ->
            val server = player.server ?: return@register
            VrmModServer.handlePlayerDisconnect(player.uuid, server)
        }
    }
}
