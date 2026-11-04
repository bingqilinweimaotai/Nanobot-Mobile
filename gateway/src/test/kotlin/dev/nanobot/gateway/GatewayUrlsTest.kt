package dev.nanobot.gateway

import dev.nanobot.protocol.BootstrapResponse
import dev.nanobot.protocol.NanobotJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayUrlsTest {
    @Test
    fun `advertised ws url gets the token and client id`() {
        val boot = BootstrapResponse(
            token = "nbwt_test",
            wsUrl = "ws://127.0.0.1:8765/",
        )
        val url = buildWebSocketUrl("http://127.0.0.1:8765", boot, "android-1")
        assertEquals("ws://127.0.0.1:8765/?token=nbwt_test&client_id=android-1", url)
    }

    @Test
    fun `missing ws url derives wss from an https gateway`() {
        val boot = BootstrapResponse(
            token = "token value",
            wsPath = "/chat/ws",
        )
        val url = buildWebSocketUrl("https://nanobot.example.com", boot, "android 1")
        assertTrue(url.startsWith("wss://nanobot.example.com/chat/ws?"))
        assertTrue(url.contains("token=token%20value"))
        assertTrue(url.contains("client_id=android%201"))
    }

    @Test
    fun `bootstrap response tolerates unknown fields`() {
        val boot = NanobotJson.decodeFromString<BootstrapResponse>(
            """
            {
              "token": "t",
              "api_token": "a",
              "ws_path": "/",
              "ws_url": null,
              "expires_in": 300,
              "limits": {"max_frame_bytes": 37748736},
              "terminal": {"protocolVersion": 1, "gatewayId": "g",
                           "webui": {"capabilities": ["webui.core.v1"]}}
            }
            """.trimIndent(),
        )
        assertEquals("t", boot.token)
        assertEquals("a", boot.apiToken)
        assertEquals(listOf("webui.core.v1"), boot.terminal?.webui?.capabilities)
    }
}
