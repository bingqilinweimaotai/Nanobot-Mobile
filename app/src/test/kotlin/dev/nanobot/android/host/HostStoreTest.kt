package dev.nanobot.android.host

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nanobot.android.SettingsStore
import dev.nanobot.tunnel.PairingOffer
import dev.nanobot.tunnel.TunnelCrypto
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class HostStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("nanobot_hosts", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("nanobot", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `migrates the legacy single endpoint into a direct host`() {
        SettingsStore(context).apply {
            endpoint = "http://192.168.1.9:9123"
            lastChatId = "chat-legacy"
        }
        val store = HostStore(context)
        val current = store.currentHost()
        assertNotNull(current)
        assertEquals(HostKind.Direct, current.kind)
        assertEquals("http://192.168.1.9:9123", current.endpoint)
        assertEquals("chat-legacy", current.lastChatId)
        assertEquals("192.168.1.9", current.displayName)
        assertTrue(store.gatewaySecret(current.id).isEmpty())

        // A second store must not migrate twice or mint a second host.
        assertEquals(1, HostStore(context).hosts().size)
    }

    @Test
    fun `saving a pairing offer makes it current`() {
        val offer = PairingOffer(
            kind = PairingOffer.KIND_DIRECT,
            name = "Desk PC",
            endpoint = "http://192.168.1.9:9123",
            gatewayPort = 9123,
        )
        val store = HostStore(context)
        val host = store.saveOffer(offer)

        assertEquals("Desk PC", host.displayName)
        assertEquals(HostKind.Direct, host.kind)
        assertEquals("http://192.168.1.9:9123", host.endpoint)
        assertEquals(9123, host.gatewayPort)
        assertEquals(host.id, store.currentHost()?.id)
        assertTrue(context.getSharedPreferences("nanobot_hosts", Context.MODE_PRIVATE)
            .getString("hosts", "").orEmpty().contains("Desk PC"))
    }

    @Test
    fun `a relay offer maps onto a relay host profile`() {
        val offer = PairingOffer(
            kind = PairingOffer.KIND_RELAY,
            name = "Desk PC",
            relay = "wss://relay.example.com/control",
            serverId = "server-1",
            clientToken = "client-token",
            pairKey = TunnelCrypto.encodeKey(TunnelCrypto.generateKey()),
            gatewayPort = 9123,
            platform = "linux",
        ).requireValid()
        val host = HostProfile(id = "host-1", name = "", kind = HostKind.Direct).fromOffer(offer)
        assertEquals(HostKind.Relay, host.kind)
        assertEquals("Desk PC", host.displayName)
        assertEquals("wss://relay.example.com/control", host.relayUrl)
        assertEquals("server-1", host.serverId)
        assertEquals("client-token", host.clientToken)
        assertEquals(9123, host.gatewayPort)
    }

    @Test
    fun `saving the same direct endpoint twice updates instead of duplicating`() {
        val store = HostStore(context)
        val first = store.saveDirect("One", "http://10.0.0.4:9123", "")
        val second = store.saveDirect("Renamed", "10.0.0.4:9123", "")
        assertEquals(first.id, second.id)
        assertEquals(1, store.hosts().size)
        assertEquals("Renamed", store.currentHost()?.displayName)
    }

    @Test
    fun `direct hosts with the same address are deduplicated`() {
        val store = HostStore(context)
        val first = store.saveDirect("One", "http://10.0.0.4:9123", "")
        val second = store.saveDirect("Two", "10.0.0.4:9123", "")
        assertEquals(first.id, second.id)
        assertEquals("http://10.0.0.4:9123", second.endpoint)
        assertEquals(1, store.hosts().size)
    }

    @Test
    fun `removing the current host falls back to another host`() {
        val store = HostStore(context)
        val first = store.saveDirect("One", "http://10.0.0.4:9123", "")
        val second = store.saveDirect("Two", "http://10.0.0.5:9123", "")
        assertEquals(second.id, store.currentHost()?.id)

        store.remove(second.id)
        assertEquals(first.id, store.currentId())
        assertNull(store.hosts().firstOrNull { it.id == second.id })
    }

    @Test
    fun `updating the last chat keeps the host list intact`() {
        val store = HostStore(context)
        val host = store.saveDirect("One", "http://10.0.0.4:9123", "")
        store.updateLastChat(host.id, "chat-42")
        assertEquals("chat-42", store.currentHost()?.lastChatId)
        assertEquals(1, store.hosts().size)
    }
}
