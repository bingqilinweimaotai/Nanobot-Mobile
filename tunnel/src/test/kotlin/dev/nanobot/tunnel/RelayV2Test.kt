package dev.nanobot.tunnel

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RelayV2Test {
    @Test
    fun controlUrlAddsTheControlPath() {
        assertEquals(
            "wss://relay.example.com/control",
            RelayV2.controlUrl("wss://relay.example.com"),
        )
        assertEquals(
            "ws://127.0.0.1:8787/control",
            RelayV2.controlUrl("ws://127.0.0.1:8787/"),
        )
    }

    @Test
    fun controlUrlKeepsAMountPrefix() {
        assertEquals(
            "wss://relay.example.com/nanobot/control",
            RelayV2.controlUrl("wss://relay.example.com/nanobot"),
        )
        assertEquals(
            "wss://relay.example.com/nanobot/control",
            RelayV2.controlUrl("wss://relay.example.com/nanobot/"),
        )
        assertEquals(
            "wss://relay.example.com/nanobot/control",
            RelayV2.controlUrl("wss://relay.example.com/nanobot/control"),
        )
    }

    @Test
    fun controlUrlRejectsPlainHttp() {
        assertFailsWith<IllegalArgumentException> {
            RelayV2.controlUrl("https://relay.example.com")
        }
    }

    @Test
    fun dataUrlSitsNextToControl() {
        assertEquals(
            "wss://relay.example.com/nanobot/data?serverId=srv&role=client",
            RelayV2.dataUrl("wss://relay.example.com/nanobot/control", "srv", "client"),
        )
    }

    @Test
    fun frameHeaderIsNineBigEndianBytes() {
        val encoded = RelayV2.encodeFrame(
            RelayV2.FRAME_DATA,
            0x0102030405060708L,
            byteArrayOf(9, 8),
        )
        assertContentEquals(
            byteArrayOf(
                RelayV2.FRAME_DATA,
                1, 2, 3, 4, 5, 6, 7, 8,
                9, 8,
            ),
            encoded,
        )
    }

    @Test
    fun frameRoundTrip() {
        val payload = ByteArray(1024) { (it % 251).toByte() }
        val frame = RelayV2.decodeFrame(RelayV2.encodeFrame(RelayV2.FRAME_DATA, 42L, payload))
        requireNotNull(frame)
        assertEquals(RelayV2.FRAME_DATA, frame.type)
        assertEquals(42L, frame.streamId)
        assertContentEquals(payload, frame.payload)
    }

    @Test
    fun controlFramesHaveEmptyPayloads() {
        val frame = RelayV2.decodeFrame(RelayV2.encodeFrame(RelayV2.FRAME_ACK, 7L))
        requireNotNull(frame)
        assertEquals(RelayV2.FRAME_ACK, frame.type)
        assertEquals(0, frame.payload.size)
    }

    @Test
    fun closeFrameCarriesTheReason() {
        val frame = RelayV2.decodeFrame(RelayV2.closeFrame(3L, RelayV2.CLOSE_AGENT_OFFLINE))
        requireNotNull(frame)
        assertEquals(RelayV2.FRAME_CLOSE, frame.type)
        assertEquals(3L, frame.streamId)
        assertContentEquals(byteArrayOf(RelayV2.CLOSE_AGENT_OFFLINE), frame.payload)
    }

    @Test
    fun truncatedFramesAreRejected() {
        assertNull(RelayV2.decodeFrame(ByteArray(8)))
        assertNull(RelayV2.decodeFrame(ByteArray(0)))
    }

    @Test
    fun oversizedFramesAreRejected() {
        assertNull(RelayV2.decodeFrame(ByteArray(RelayV2.MAX_FRAME_SIZE + 1)))
    }

    @Test
    fun controlMessagesRoundTrip() {
        val message = RelayV2.Control(
            t = RelayV2.TYPE_HELLO,
            v = RelayV2.VERSION,
            role = RelayV2.ROLE_CLIENT,
            serverId = "srv",
            token = "tok",
        )
        val decoded = RelayV2.decodeControl(RelayV2.encodeControl(message))
        assertEquals(message, decoded)
    }

    @Test
    fun controlMessagesUseTheRelayFieldNames() {
        val text = RelayV2.encodeControl(
            RelayV2.Control(
                t = RelayV2.TYPE_PRESENCE,
                agent = true,
                sid = "session",
            ),
        )
        // The relay and nanobot-link read these exact keys.
        assertEquals(true, text.contains("\"t\":\"presence\""))
        assertEquals(true, text.contains("\"agent\":true"))
        assertEquals(true, text.contains("\"sid\":\"session\""))
        assertEquals(false, text.contains("serverId"))
    }

    @Test
    fun malformedControlMessagesAreIgnored() {
        assertNull(RelayV2.decodeControl("not json"))
        assertNull(RelayV2.decodeControl("""{"v":2}"""))
    }
}
