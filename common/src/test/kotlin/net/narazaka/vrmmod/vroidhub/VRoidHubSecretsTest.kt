package net.narazaka.vrmmod.vroidhub

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class VRoidHubSecretsTest {

    private fun xorEncode(input: String, key: String): String {
        if (input.isEmpty()) return ""
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val encoded = input.toByteArray(Charsets.UTF_8).mapIndexed { i, b ->
            (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }.toByteArray()
        return Base64.getEncoder().encodeToString(encoded)
    }

    private fun xorDecode(encoded: String, key: String): String {
        if (encoded.isEmpty()) return ""
        val keyBytes = key.toByteArray(Charsets.UTF_8)
        val decoded = Base64.getDecoder().decode(encoded).mapIndexed { i, b ->
            (b.toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
        }.toByteArray()
        return String(decoded, Charsets.UTF_8)
    }

    @Test
    fun `xor round-trip preserves original string`() {
        val key = "vrmmod-obfuscation-key"
        val original = "test_secret_value_12345"
        val encoded = xorEncode(original, key)
        val decoded = xorDecode(encoded, key)
        assertEquals(original, decoded)
        assertFalse(encoded.contains(original))
    }

    @Test
    fun `xor encode of empty string returns empty string`() {
        val key = "vrmmod-obfuscation-key"
        assertEquals("", xorEncode("", key))
        assertEquals("", xorDecode("", key))
    }

    @Test
    fun `VRoidHubSecrets defaultConfig returns config without crashing`() {
        val config = VRoidHubSecrets.defaultConfig()
        assertNotNull(config)
        assertEquals(config.clientId.isNotBlank() && config.clientSecret.isNotBlank(), config.isAvailable)
    }
}
