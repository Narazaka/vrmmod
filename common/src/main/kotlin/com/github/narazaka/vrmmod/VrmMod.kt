package com.github.narazaka.vrmmod

import org.slf4j.LoggerFactory

object VrmMod {
    const val MOD_ID = "vrmmod"
    val logger = LoggerFactory.getLogger(MOD_ID)

    fun init() {
        logger.info("VRM Mod initialized")
    }

    /**
     * Client-side initialization. Must only be called on the client.
     */
    fun initClient() {
        com.github.narazaka.vrmmod.client.VrmModClient.init()
    }
}
