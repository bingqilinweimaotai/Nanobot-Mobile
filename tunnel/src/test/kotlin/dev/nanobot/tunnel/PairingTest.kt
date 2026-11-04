package dev.nanobot.tunnel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PairingTest {
    private val key = TunnelCrypto.encodeKey(TunnelCrypto.generateKey())

    @Test
    fun `relay offer round trips through a pairing link`() {
        val offer = PairingOffer(
            kind = PairingOffer.KIND_RELAY,
            name = "DESKTOP-ABC",
            relay = "wss://relay.example.com/control",
            serverId = "server-123",
            clientToken = "client-token",
            pairKey = key,
            gatewayPort = 18765,
            platform = "Windows",
        )

        val link = PairingLinks.encode(offer)
        assertTrue(link.startsWith("nanobot://pair?offer="))
        assertEquals(offer, PairingLinks.parse(link))
    }

    @Test
    fun `fragment offers and plain query links are accepted`() {
        val offer = PairingOffer(
            kind = PairingOffer.KIND_DIRECT,
            name = "Home PC",
            endpoint = "http://192.168.1.20:18765",
            secret = "secret",
            gatewayPort = 18765,
        )
        val payload = PairingLinks.encode(offer).substringAfter("offer=")
        assertEquals(offer, PairingLinks.parse("https://example.com/#offer=$payload"))
        assertEquals(offer, PairingLinks.parse("nanobot://pair#offer=$payload"))

        val query = PairingLinks.parse(
            "nanobot://pair?endpoint=http%3A%2F%2F10.0.0.2%3A9000&secret=s&name=Lab",
        )
        assertEquals("http://10.0.0.2:9000", query.endpoint)
        assertEquals("s", query.secret)
        assertEquals("Lab", query.name)
    }

    // nanobot-link prints this compact form so the terminal QR stays small enough to fit on screen.
    @Test
    fun `compact relay query links are accepted`() {
        val compact = "nanobot://pair?relay=ws://relay.example.com/nanobot&server=server-123" +
            "&token=client-token&key=$key&secret=s&name=Workshop+PC"
        val offer = PairingLinks.parse(compact)
        assertEquals(1, offer.v)
        assertEquals(PairingOffer.KIND_RELAY, offer.kind)
        assertEquals("ws://relay.example.com/nanobot", offer.relay)
        assertEquals("server-123", offer.serverId)
        assertEquals("client-token", offer.clientToken)
        assertEquals(key, offer.pairKey)
        assertEquals("s", offer.secret)
        assertEquals("Workshop PC", offer.name)
    }

    @Test
    fun `invalid offers are rejected`() {
        assertFailsWith<PairingException> {
            PairingLinks.parse("nanobot://pair?offer=not-base64")
        }
        assertFailsWith<PairingException> {
            PairingOffer(kind = PairingOffer.KIND_RELAY, relay = "https://example.com").requireValid()
        }
        assertFailsWith<PairingException> {
            PairingOffer(
                kind = PairingOffer.KIND_RELAY,
                relay = "wss://example.com/control",
                serverId = "s",
                clientToken = "t",
                pairKey = "short",
            ).requireValid()
        }
        assertFailsWith<PairingException> {
            PairingOffer(kind = PairingOffer.KIND_DIRECT, endpoint = "ftp://example.com").requireValid()
        }
    }

    @Test
    fun `gateway port is optional and validated`() {
        val offer = PairingOffer(
            kind = PairingOffer.KIND_RELAY,
            relay = "wss://example.com/control",
            serverId = "s",
            clientToken = "t",
            pairKey = key,
            gatewayPort = null,
        ).requireValid()
        assertEquals(null, offer.gatewayPort)

        assertFailsWith<PairingException> {
            offer.copy(gatewayPort = 70000).requireValid()
        }
    }
}
