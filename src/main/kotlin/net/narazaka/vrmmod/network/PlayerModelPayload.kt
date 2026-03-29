package net.narazaka.vrmmod.network

import net.narazaka.vrmmod.VrmMod
import net.narazaka.vrmmod.animation.NormalMode
//? if HAS_CUSTOM_PAYLOAD {
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
//?} else {
/*import net.minecraft.network.FriendlyByteBuf*/
//?}
import net.minecraft.resources.ResourceLocation
import java.util.UUID

//? if HAS_CUSTOM_PAYLOAD {
data class PlayerModelPayload(
    val playerUUID: UUID,
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
    val normalMode: NormalMode = NormalMode.AUTO,
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<PlayerModelPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<PlayerModelPayload>(
            //? if HAS_RESOURCE_LOCATION_FACTORY {
            ResourceLocation.fromNamespaceAndPath(VrmMod.MOD_ID, "player_model")
            //?} else {
            /*ResourceLocation(VrmMod.MOD_ID, "player_model")*/
            //?}
        )
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, PlayerModelPayload> =
            object : StreamCodec<RegistryFriendlyByteBuf, PlayerModelPayload> {
                override fun decode(buf: RegistryFriendlyByteBuf): PlayerModelPayload {
                    val uuid = buf.readUUID()
                    val hasModelId = buf.readBoolean()
                    val vroidHubModelId = if (hasModelId) buf.readUtf() else null
                    val hasLicenseId = buf.readBoolean()
                    val multiplayLicenseId = if (hasLicenseId) buf.readUtf() else null
                    val scale = buf.readFloat()
                    val normalMode = NormalMode.entries[buf.readByte().toInt().coerceIn(0, NormalMode.entries.size - 1)]
                    return PlayerModelPayload(uuid, vroidHubModelId, multiplayLicenseId, scale, normalMode)
                }

                override fun encode(buf: RegistryFriendlyByteBuf, payload: PlayerModelPayload) {
                    buf.writeUUID(payload.playerUUID)
                    buf.writeBoolean(payload.vroidHubModelId != null)
                    payload.vroidHubModelId?.let { buf.writeUtf(it) }
                    buf.writeBoolean(payload.multiplayLicenseId != null)
                    payload.multiplayLicenseId?.let { buf.writeUtf(it) }
                    buf.writeFloat(payload.scale)
                    buf.writeByte(payload.normalMode.ordinal)
                }
            }
    }
}
//?} else {
/*data class PlayerModelPayload(
    val playerUUID: UUID,
    val vroidHubModelId: String?,
    val multiplayLicenseId: String?,
    val scale: Float,
    val normalMode: NormalMode = NormalMode.AUTO,
) {
    companion object {
        val PACKET_ID = ResourceLocation(VrmMod.MOD_ID, "player_model")

        fun decode(buf: FriendlyByteBuf): PlayerModelPayload {
            val uuid = buf.readUUID()
            val hasModelId = buf.readBoolean()
            val vroidHubModelId = if (hasModelId) buf.readUtf() else null
            val hasLicenseId = buf.readBoolean()
            val multiplayLicenseId = if (hasLicenseId) buf.readUtf() else null
            val scale = buf.readFloat()
            val normalMode = NormalMode.entries[buf.readByte().toInt().coerceIn(0, NormalMode.entries.size - 1)]
            return PlayerModelPayload(uuid, vroidHubModelId, multiplayLicenseId, scale, normalMode)
        }

        fun encode(buf: FriendlyByteBuf, payload: PlayerModelPayload) {
            buf.writeUUID(payload.playerUUID)
            buf.writeBoolean(payload.vroidHubModelId != null)
            payload.vroidHubModelId?.let { buf.writeUtf(it) }
            buf.writeBoolean(payload.multiplayLicenseId != null)
            payload.multiplayLicenseId?.let { buf.writeUtf(it) }
            buf.writeFloat(payload.scale)
            buf.writeByte(payload.normalMode.ordinal)
        }
    }
}*/
//?}
