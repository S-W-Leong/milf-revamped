package ai.milf.client.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientSecurityTest {
    @Test
    fun debugAllowsEmulatorCleartextWebsocket() {
        val security = ClientSecurity(
            isDebugBuild = true,
            defaultBackendUrl = "ws://10.0.2.2:8765",
            deviceToken = "dev-token"
        )

        val securedUrl = security.secureWebSocketUrl("ws://10.0.2.2:8765")

        assertEquals("ws://10.0.2.2:8765?token=dev-token", securedUrl)
    }

    @Test
    fun debugAllowsLocalCleartextWebsocketHosts() {
        val security = ClientSecurity(
            isDebugBuild = true,
            defaultBackendUrl = "ws://127.0.0.1:8765",
            deviceToken = "dev-token"
        )

        assertEquals("ws://localhost:8765?token=dev-token", security.secureWebSocketUrl("ws://localhost:8765"))
        assertEquals("ws://127.0.0.1:8765?token=dev-token", security.secureWebSocketUrl("ws://127.0.0.1:8765"))
    }

    @Test
    fun releaseRejectsCleartextWebsocketBeforeConnection() {
        val security = ClientSecurity(
            isDebugBuild = false,
            defaultBackendUrl = "wss://backend.example/ws",
            deviceToken = "release-token"
        )

        val error = assertThrows(ClientSecurity.SecurityException::class.java) {
            security.secureWebSocketUrl("ws://10.0.2.2:8765")
        }

        assertEquals("Cleartext websocket URLs are only allowed for local debug builds", error.message)
    }

    @Test
    fun acceptsSecureWebsocketInRelease() {
        val security = ClientSecurity(
            isDebugBuild = false,
            defaultBackendUrl = "wss://backend.example/ws",
            deviceToken = "release-token"
        )

        val securedUrl = security.secureWebSocketUrl("wss://backend.example/ws")

        assertEquals("wss://backend.example/ws?token=release-token", securedUrl)
    }

    @Test
    fun appendsTokenAfterExistingQueryAndBeforeFragment() {
        val security = ClientSecurity(
            isDebugBuild = false,
            defaultBackendUrl = "wss://backend.example/ws",
            deviceToken = "token with spaces"
        )

        val securedUrl = security.secureWebSocketUrl("wss://backend.example/ws?lang=en#session")

        assertEquals("wss://backend.example/ws?lang=en&token=token%20with%20spaces#session", securedUrl)
    }

    @Test
    fun missingTokenIsRejectedWithSanitizedMessage() {
        val security = ClientSecurity(
            isDebugBuild = true,
            defaultBackendUrl = "ws://10.0.2.2:8765",
            deviceToken = "  "
        )

        val error = assertThrows(ClientSecurity.SecurityException::class.java) {
            security.secureWebSocketUrl("ws://10.0.2.2:8765")
        }

        assertEquals("Device token is required before connecting", error.message)
        assertFalse(error.message.orEmpty().contains("token=", ignoreCase = true))
    }

    @Test
    fun invalidSchemeIsRejected() {
        val security = ClientSecurity(
            isDebugBuild = true,
            defaultBackendUrl = "ws://10.0.2.2:8765",
            deviceToken = "dev-token"
        )

        val error = assertThrows(ClientSecurity.SecurityException::class.java) {
            security.secureWebSocketUrl("http://10.0.2.2:8765")
        }

        assertTrue(error.message.orEmpty().contains("websocket", ignoreCase = true))
    }
}
