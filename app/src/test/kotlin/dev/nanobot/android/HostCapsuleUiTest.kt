package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nanobot.android.host.HostKind
import dev.nanobot.android.host.HostProfile
import dev.nanobot.android.tunnel.TunnelState
import dev.nanobot.gateway.GatewayState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HostCapsuleUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val relayHost = HostProfile(
        id = "pc-1",
        name = "Work PC",
        kind = HostKind.Relay,
        relayUrl = "wss://relay.example.com/control",
        serverId = "abcdef123456",
        clientToken = "token",
    )

    private val directHost = HostProfile(
        id = "lan-1",
        name = "Studio",
        kind = HostKind.Direct,
        endpoint = "http://192.168.1.20:9123",
    )

    @Test
    fun `shows the current host and its tunnel state`() {
        val state = UiState(
            hosts = listOf(relayHost),
            currentHostId = relayHost.id,
            connection = GatewayState.Ready(chatId = "chat-1", capabilities = emptySet()),
            tunnelState = TunnelState.Reconnecting(attempt = 3, reason = "relay down"),
        )
        composeRule.setContent {
            MaterialTheme {
                HostCapsule(state = state, onOpen = {})
            }
        }
        composeRule.onNodeWithText("Work PC").assertExists()
        composeRule.onNodeWithText("relay reconnecting (3)").assertExists()
    }

    @Test
    fun `a direct host ignores the tunnel state`() {
        val state = UiState(
            hosts = listOf(directHost),
            currentHostId = directHost.id,
            connection = GatewayState.Ready(chatId = "chat-1", capabilities = emptySet()),
            tunnelState = TunnelState.Failed("stale relay state"),
        )
        composeRule.setContent {
            MaterialTheme {
                HostCapsule(state = state, onOpen = {})
            }
        }
        composeRule.onNodeWithText("Studio").assertExists()
        composeRule.onNodeWithText("connected").assertExists()
    }

    @Test
    fun `tapping the capsule opens the host switcher`() {
        var opened = 0
        val state = UiState(hosts = listOf(relayHost), currentHostId = relayHost.id)
        composeRule.setContent {
            MaterialTheme {
                HostCapsule(state = state, onOpen = { opened += 1 })
            }
        }
        composeRule.onNodeWithText("Hosts").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `tapping the gear opens settings`() {
        var opened = 0
        val state = UiState(hosts = listOf(relayHost), currentHostId = relayHost.id)
        composeRule.setContent {
            MaterialTheme {
                HostCapsule(state = state, onOpen = {}, onSettings = { opened += 1 })
            }
        }
        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertEquals(1, opened)
    }
}
