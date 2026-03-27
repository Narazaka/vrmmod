package net.narazaka.vrmmod.vroidhub

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class VRoidHubConfigTest {
    @Test
    fun `load returns built-in default when JSON file does not exist`(@TempDir tempDir: Path) {
        val config = VRoidHubConfig.load(tempDir)
        assertEquals(VRoidHubSecrets.defaultClientId, config.clientId)
        assertEquals(VRoidHubSecrets.defaultClientSecret, config.clientSecret)
    }

    @Test
    fun `load uses JSON values when file exists with valid values`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("vrmmod-vroidhub-secrets.json").toFile()
        file.writeText("""{"clientId": "custom_id", "clientSecret": "custom_secret"}""")
        val config = VRoidHubConfig.load(tempDir)
        assertEquals("custom_id", config.clientId)
        assertEquals("custom_secret", config.clientSecret)
    }

    @Test
    fun `load falls back to default when JSON is malformed`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("vrmmod-vroidhub-secrets.json").toFile()
        file.writeText("not valid json {{{")
        val config = VRoidHubConfig.load(tempDir)
        assertEquals(VRoidHubSecrets.defaultClientId, config.clientId)
    }

    @Test
    fun `load falls back to default when JSON has empty values`(@TempDir tempDir: Path) {
        val file = tempDir.resolve("vrmmod-vroidhub-secrets.json").toFile()
        file.writeText("""{"clientId": "", "clientSecret": ""}""")
        val config = VRoidHubConfig.load(tempDir)
        assertEquals(VRoidHubSecrets.defaultClientId, config.clientId)
    }
}
