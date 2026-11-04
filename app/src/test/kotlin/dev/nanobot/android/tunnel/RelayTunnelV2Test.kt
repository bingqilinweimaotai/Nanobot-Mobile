package dev.nanobot.android.tunnel

import dev.nanobot.tunnel.RelayV2
import dev.nanobot.tunnel.TunnelCrypto
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config

/**
 * Drives the real [RelayTunnel] against a fake relay that speaks protocol v2. The fake relay also
 * plays the PC side: it acknowledges a stream and echoes decrypted bytes back, which is exactly what
 * nanobot-link does when it reaches the gateway.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RelayTunnelV2Test {
    @Test
    fun forwardsLocalTcpThroughTheRelay() = runBlocking {
        val key = TunnelCrypto.generateKey()
        val relay = FakeRelay(key, acceptStreams = true)
        relay.start()
        val tunnel = startTunnel(relay, key)
        try {
            withTimeout(TIMEOUT_MS) { tunnel.state.first { it is TunnelState.Ready } }

            openLocal(tunnel).use { socket ->
                socket.getOutputStream().apply {
                    write("ping".toByteArray())
                    flush()
                }
                assertEquals("ping", readExactly(socket, 4))
            }

            assertTrue("the relay never saw a data channel", relay.sawDataChannel.get())
            assertEquals("client", relay.helloRole.get())
        } finally {
            tunnel.stop()
            relay.stop()
        }
    }

    @Test
    fun closesTheLocalSocketWhenThePcIsOffline() = runBlocking {
        val key = TunnelCrypto.generateKey()
        val relay = FakeRelay(key, acceptStreams = false)
        relay.start()
        val tunnel = startTunnel(relay, key)
        try {
            withTimeout(TIMEOUT_MS) { tunnel.state.first { it is TunnelState.Ready } }

            openLocal(tunnel).use { socket ->
                socket.getOutputStream().apply {
                    write("ping".toByteArray())
                    flush()
                }
                // The relay answered OPEN with CLOSE(agent offline); the phone must give up rather
                // than hold the HTTP connection until it times out.
                assertEquals(-1, socket.getInputStream().read())
            }
        } finally {
            tunnel.stop()
            relay.stop()
        }
    }

    @Test
    fun writesQueuedDataBeforeClosingWhenThePcClosesTheStream() = runBlocking {
        val key = TunnelCrypto.generateKey()
        val payload = ByteArray(BURST_FRAMES * BURST_CHUNK) { (it % 251).toByte() }
        val relay = FakeRelay(key, acceptStreams = true, burstOnOpen = payload)
        relay.start()
        val tunnel = startTunnel(relay, key)
        try {
            withTimeout(TIMEOUT_MS) { tunnel.state.first { it is TunnelState.Ready } }

            openLocal(tunnel).use { socket ->
                // Read nothing until the PC has sent every byte and closed the stream. The
                // response is then sitting in the tunnel's buffers when CLOSE arrives, which is
                // exactly the moment the old code closed the local socket and lost the body.
                assertTrue("the fake relay did not finish the burst", relay.awaitBurst(TIMEOUT_MS))
                Thread.sleep(300)
                val received = socket.getInputStream().readBytes()
                assertEquals(payload.size, received.size)
                assertArrayEquals(payload, received)
            }
        } finally {
            tunnel.stop()
            relay.stop()
        }
    }

    private fun startTunnel(relay: FakeRelay, key: ByteArray): RelayTunnel {
        val tunnel = RelayTunnel(
            RelayConfig(
                controlUrl = "ws://127.0.0.1:${relay.awaitPort()}",
                serverId = "test-server",
                clientToken = "test-token",
                pairKey = TunnelCrypto.encodeKey(key),
            ),
        )
        tunnel.start()
        return tunnel
    }

    private fun openLocal(tunnel: RelayTunnel): Socket {
        val port = URI(tunnel.baseUrl).port
        return Socket().apply {
            connect(InetSocketAddress("127.0.0.1", port), TIMEOUT_MS.toInt())
            soTimeout = TIMEOUT_MS.toInt()
        }
    }

    private fun readExactly(socket: Socket, count: Int): String {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = socket.getInputStream().read(buffer, read, count - read)
            if (n < 0) break
            read += n
        }
        return String(buffer, 0, read)
    }

    private class FakeRelay(
        private val key: ByteArray,
        private val acceptStreams: Boolean,
        private val burstOnOpen: ByteArray? = null,
    ) : WebSocketServer(InetSocketAddress("127.0.0.1", 0)) {
        private val started = CountDownLatch(1)
        private val burstSent = CountDownLatch(1)
        private val boundPort = AtomicReference<Int>()
        private val control = AtomicReference<WebSocket?>()
        private val data = AtomicReference<WebSocket?>()

        val sawDataChannel = AtomicBoolean(false)
        val helloRole = AtomicReference<String>()

        fun awaitPort(): Int {
            check(started.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) { "the fake relay did not start" }
            return boundPort.get() ?: error("the fake relay did not bind a port")
        }

        fun awaitBurst(timeoutMs: Long): Boolean = burstSent.await(timeoutMs, TimeUnit.MILLISECONDS)

        override fun onStart() {
            boundPort.set(port)
            started.countDown()
        }

        override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
            when {
                handshake.resourceDescriptor.startsWith("/control") -> control.set(conn)
                handshake.resourceDescriptor.startsWith("/data") -> {
                    check(handshake.getFieldValue(RelayV2.SESSION_HEADER) == SESSION_ID) {
                        "the data channel arrived without its session id"
                    }
                    data.set(conn)
                    sawDataChannel.set(true)
                }
                else -> conn.close()
            }
        }

        override fun onMessage(conn: WebSocket, message: String) {
            val control = RelayV2.decodeControl(message) ?: return
            if (control.t != RelayV2.TYPE_HELLO) return
            helloRole.set(control.role)
            conn.send(RelayV2.encodeControl(RelayV2.Control(t = RelayV2.TYPE_READY, sid = SESSION_ID)))
        }

        override fun onMessage(conn: WebSocket, message: ByteBuffer) {
            val bytes = ByteArray(message.remaining())
            message.get(bytes)
            val frame = RelayV2.decodeFrame(bytes) ?: return
            when (frame.type) {
                RelayV2.FRAME_OPEN ->
                    if (acceptStreams) {
                        send(RelayV2.FRAME_ACK, frame.streamId)
                        burstOnOpen?.let { burst ->
                            var offset = 0
                            while (offset < burst.size) {
                                val end = minOf(offset + BURST_CHUNK, burst.size)
                                send(
                                    RelayV2.FRAME_DATA,
                                    frame.streamId,
                                    TunnelCrypto.encrypt(key, burst.copyOfRange(offset, end)),
                                )
                                offset = end
                            }
                            send(
                                RelayV2.FRAME_CLOSE,
                                frame.streamId,
                                byteArrayOf(RelayV2.CLOSE_NORMAL),
                            )
                            burstSent.countDown()
                        }
                    } else {
                        send(
                            RelayV2.FRAME_CLOSE,
                            frame.streamId,
                            byteArrayOf(RelayV2.CLOSE_AGENT_OFFLINE),
                        )
                    }

                RelayV2.FRAME_DATA -> {
                    val plaintext = TunnelCrypto.decrypt(key, frame.payload)
                    send(RelayV2.FRAME_DATA, frame.streamId, TunnelCrypto.encrypt(key, plaintext))
                }

                else -> Unit
            }
        }

        private fun send(type: Byte, streamId: Long, payload: ByteArray = ByteArray(0)) {
            data.get()?.send(RelayV2.encodeFrame(type, streamId, payload))
        }

        override fun onClose(conn: WebSocket, code: Int, reason: String, remote: Boolean) = Unit

        override fun onError(conn: WebSocket?, ex: Exception) = Unit

        private companion object {
            const val SESSION_ID = "test-session"
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
        const val BURST_CHUNK = 16 * 1024
        const val BURST_FRAMES = 64
    }
}
