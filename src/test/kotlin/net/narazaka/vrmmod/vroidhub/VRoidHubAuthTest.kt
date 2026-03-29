package net.narazaka.vrmmod.vroidhub

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class VRoidHubAuthTest {
    @Test
    fun `code verifier has valid length and characters`() {
        val verifier = VRoidHubAuth.generateCodeVerifier()
        assertTrue(verifier.length in 43..128, "Length: ${verifier.length}")
        assertTrue(verifier.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it in "-._~" },
            "Invalid characters in: $verifier")
    }

    @Test
    fun `code challenge is base64url without padding`() {
        val verifier = "test_verifier_string_that_is_long_enough_for_pkce"
        val challenge = VRoidHubAuth.generateCodeChallenge(verifier)
        assertFalse(challenge.contains('+'), "Should not contain +")
        assertFalse(challenge.contains('/'), "Should not contain /")
        assertFalse(challenge.contains('='), "Should not contain =")
        assertTrue(challenge.isNotEmpty())
    }

    @Test
    fun `authorize URL contains required parameters`() {
        val config = VRoidHubConfig(clientId = "test_id", clientSecret = "test_secret")
        val (url, session) = VRoidHubAuth.buildAuthorizeUrl(config)
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=test_id"))
        assertTrue(url.contains("redirect_uri=urn"))
        assertTrue(url.contains("code_challenge="))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("state="))
        assertTrue(session.codeVerifier.isNotEmpty())
        assertTrue(session.state.isNotEmpty())
    }
}
