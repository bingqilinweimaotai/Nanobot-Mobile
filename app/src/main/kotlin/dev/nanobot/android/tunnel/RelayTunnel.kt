package dev.nanobot.android.tunnel

import dev.nanobot.android.AppLocale
import dev.nanobot.tunnel.RelayV2
import dev.nanobot.tunnel.TunnelCrypto
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * Exposes a loopback HTTP/WebSocket endpoint and forwards every TCP stream to nanobot-link on the
 * PC through an end-to-end encrypted relay.
 *
 * The existing [dev.nanobot.gateway.NanobotGateway] is pointed at [baseUrl], so bootstrap, REST and
 * WebSocket traffic all take the tunnel without protocol changes. The relay only forwards encrypted
 * bytes; nanobot-link decides where they go (always its own local gateway port).
 *
 * Protocol v2 keeps two WebSocket connections per phone instead of one per stream: a control
 * channel and a multiplexed data channel. Every accepted local TCP connection becomes one stream on
 * that data channel.
 */
class RelayTunnel(private val config: RelayConfig) {
    private val key = TunnelCrypto.decodeKey(config.pairKey)

    /**
     * The relay may have been given as a bare origin or as a mounted path such as
     * `wss://host/nanobot`. Both URLs derive from this one normalized value, so a mounted relay
     * keeps `/nanobot/data` next to `/nanobot/control`.
     */
    private val controlUrl = RelayV2.controlUrl(config.controlUrl)
    private val dataUrl = RelayV2.dataUrl(controlUrl, config.serverId, RelayV2.ROLE_CLIENT)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<TunnelState>(TunnelState.Stopped)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private lateinit var server: ServerSocket
    private val sessions = ConcurrentHashMap<Long, Session>()
    private val nextStreamId = AtomicLong(1)

    @Volatile
    private var running = false

    /** The current multiplexed data channel, or null while reconnecting. */
    @Volatile
    private var dataSocket: WebSocket? = null

    @Volatile
    private var lastFailure: String = AppLocale.strings.relayUnreachable

    /** Set when the relay rejects our credentials; retrying cannot fix that. */
    @Volatile
    private var fatal: Boolean = false

    /** Available as soon as [start] returns; the gateway can connect before the relay is ready. */
    val baseUrl: String
        get() = "http://127.0.0.1:${server.localPort}"

    fun start() {
        if (running) return
        running = true
        // Bind IPv4 explicitly. InetAddress.getLoopbackAddress() returns ::1 on Android, while
        // baseUrl advertises 127.0.0.1, so the gateway would be unable to reach the tunnel.
        server = ServerSocket(0, 64, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        // A small loopback window stops an upload from disappearing into kernel buffers in one
        // burst; together with the relay queue cap it keeps HTTP write progress close to reality.
        runCatching { server.receiveBufferSize = 64 * 1024 }
        scope.launch { acceptLoop() }
        scope.launch { relayLoop() }
    }

    fun stop() {
        if (!running && _state.value is TunnelState.Stopped) return
        running = false
        dataSocket?.close(1000, "client stopped")
        dataSocket = null
        closeAllSessions("client stopped")
        runCatching { server.close() }
        _state.value = TunnelState.Stopped
        scope.cancel()
    }

    private suspend fun acceptLoop() {
        while (running && scope.isActive) {
            val socket = try {
                server.accept()
            } catch (_: IOException) {
                break
            }
            scope.launch { handleSocket(socket) }
        }
    }

    /**
     * Connects, serves until either WebSocket dies, then backs off. Streams never outlive a relay
     * connection: the relay drops their state when the channel closes, so the local sockets must go
     * too, and the caller (the gateway) retries.
     */
    private suspend fun relayLoop() {
        var attempt = 0
        while (running && scope.isActive) {
            _state.value = if (attempt == 0) {
                TunnelState.Connecting
            } else {
                TunnelState.Reconnecting(attempt, lastFailure)
            }
            val failure = try {
                serveOnce()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.message ?: AppLocale.strings.relayConnectionFailed
            }
            closeAllSessions("relay disconnected")
            dataSocket = null
            if (!running) return
            if (fatal) {
                _state.value = TunnelState.Failed(lastFailure)
                return
            }
            attempt += 1
            lastFailure = failure
            _state.value = TunnelState.Reconnecting(attempt, failure)
            delay(backoffMs(attempt))
        }
    }

    /** Runs one relay session and returns the reason it ended. */
    private suspend fun serveOnce(): String {
        val active = AtomicBoolean(true)
        val ready = CompletableDeferred<String>()
        val closed = CompletableDeferred<String>()
        val refusal = AtomicReference<String?>(null)

        fun finish(reason: String) {
            if (active.get()) closed.complete(reason)
        }

        val control = http.newWebSocket(
            Request.Builder().url(controlUrl).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (!active.get()) {
                        webSocket.cancel()
                        return
                    }
                    webSocket.send(
                        RelayV2.encodeControl(
                            RelayV2.Control(
                                t = RelayV2.TYPE_HELLO,
                                v = RelayV2.VERSION,
                                role = RelayV2.ROLE_CLIENT,
                                serverId = config.serverId,
                                token = config.clientToken,
                            ),
                        ),
                    )
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (!active.get()) return
                    val message = RelayV2.decodeControl(text) ?: return
                    when (message.t) {
                        RelayV2.TYPE_READY -> message.sid?.let(ready::complete)
                        RelayV2.TYPE_ERROR -> {
                            if (message.code == RelayV2.ERROR_AUTH) fatal = true
                            lastFailure = message.message ?: AppLocale.strings.relayRefusedPhone
                            refusal.set(lastFailure)
                            finish(lastFailure)
                        }
                        // presence tells us whether the PC is online, but streams already report
                        // that through CLOSE(agent offline); nothing to act on here.
                        else -> Unit
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    finish(reason.ifBlank { AppLocale.strings.relayClosedConnection })
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    finish(t.message ?: AppLocale.strings.relayUnreachable)
                }
            },
        )

        var data: WebSocket? = null
        try {
            val sid = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { ready.await() }
                ?: return refusal.get() ?: AppLocale.strings.relayDidNotAcceptPhone

            val dataReady = CompletableDeferred<Unit>()
            data = http.newWebSocket(
                Request.Builder()
                    .url(dataUrl)
                    .header(RelayV2.SESSION_HEADER, sid)
                    .build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        if (!active.get()) {
                            webSocket.cancel()
                            return
                        }
                        dataSocket = webSocket
                        dataReady.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        if (!active.get()) return
                        val frame = RelayV2.decodeFrame(bytes.toByteArray())
                        if (frame == null) {
                            finish(AppLocale.strings.relayInvalidFrame)
                            return
                        }
                        when (frame.type) {
                            RelayV2.FRAME_ACK -> sessions[frame.streamId]?.accepted()
                            RelayV2.FRAME_DATA -> sessions[frame.streamId]?.receive(frame.payload)
                            RelayV2.FRAME_CLOSE ->
                                // The PC closes a stream right after the last DATA frame of a
                                // response. Let pumpToSocket write what is already queued before
                                // the local socket goes away, or the response tail is truncated.
                                sessions.remove(frame.streamId)?.close(
                                    AppLocale.strings.pcClosedStream,
                                    notifyRelay = false,
                                    drain = true,
                                )
                            // The relay never sends OPEN or WINDOW to a client.
                            else -> Unit
                        }
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(1000, null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        finish(reason.ifBlank { AppLocale.strings.relayClosedDataChannel })
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        finish(t.message ?: AppLocale.strings.relayDataChannelFailed)
                    }
                },
            )

            val opened = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { dataReady.await() } != null
            if (!opened) return AppLocale.strings.relayDidNotOpenDataChannel

            lastFailure = ""
            _state.value = TunnelState.Ready
            return closed.await()
        } finally {
            active.set(false)
            dataSocket = null
            control.cancel()
            data?.cancel()
        }
    }

    private suspend fun handleSocket(socket: Socket) {
        try {
            socket.soTimeout = 15_000
            if (!awaitReady()) {
                socket.close()
                return
            }

            val id = nextStreamId.getAndIncrement()
            val session = Session(id, socket)
            sessions[id] = session
            scope.launch { session.pumpToSocket() }

            if (!sendFrame(RelayV2.FRAME_OPEN, id)) {
                session.close(AppLocale.strings.relayNotConnected, notifyRelay = false)
                return
            }
            if (!session.awaitAccepted()) {
                session.close(AppLocale.strings.pcDidNotAcceptStream, notifyRelay = true)
                return
            }
            session.pumpToRelay()
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private suspend fun awaitReady(): Boolean {
        if (_state.value is TunnelState.Ready) return true
        if (_state.value is TunnelState.Failed) return false
        // The gateway retries; waiting keeps a connection that arrived during a brief relay
        // reconnect from failing the whole turn.
        return withTimeoutOrNull(CONNECT_WAIT_MS) {
            while (_state.value !is TunnelState.Ready) {
                if (_state.value is TunnelState.Failed) return@withTimeoutOrNull false
                delay(100)
            }
            true
        } == true
    }

    private fun sendFrame(type: Byte, streamId: Long, payload: ByteArray = EMPTY): Boolean {
        val socket = dataSocket ?: return false
        val frame = RelayV2.encodeFrame(type, streamId, payload)
        return runCatching { socket.send(frame.toByteString()) }.getOrDefault(false)
    }

    private fun closeAllSessions(reason: String) {
        // Use ConcurrentHashMap.forEach: its weakly consistent traversal does not go through an
        // iterator that can throw when stream coroutines remove a session at the same time.
        val pending = ArrayList<Session>(sessions.size)
        sessions.forEach { _, session -> pending.add(session) }
        pending.forEach { it.close(reason, notifyRelay = false) }
        sessions.clear()
    }

    /**
     * One local TCP connection. The PC is the one that dials the gateway, so this side waits for
     * ACK before reading the socket: a phone that never gets an ACK never spends memory on a
     * request that cannot be delivered.
     */
    private inner class Session(
        private val id: Long,
        private val socket: Socket,
    ) {
        private val incoming = Channel<ByteArray>(capacity = 64)
        private val decision = CompletableDeferred<Boolean>()
        private val closed = AtomicBoolean(false)

        fun accepted() {
            decision.complete(true)
        }

        suspend fun awaitAccepted(): Boolean =
            withTimeoutOrNull(OPEN_TIMEOUT_MS) { decision.await() } == true

        /** Called on the WebSocket reader thread with one encrypted DATA payload. */
        fun receive(payload: ByteArray) {
            val plaintext = runCatching { TunnelCrypto.decrypt(key, payload) }.getOrElse {
                close(AppLocale.strings.pcSentUndecryptableFrame, notifyRelay = true)
                return
            }
            if (incoming.trySend(plaintext).isFailure) {
                close(AppLocale.strings.phoneTooSlow, notifyRelay = true)
            }
        }

        /**
         * Waits while the relay's outbound queue is full. Without this, an upload is read from the
         * loopback socket as fast as the phone can copy it and queued in memory, so HTTP write
         * progress reaches 100% long before the bytes cross the relay.
         */
        private suspend fun awaitRelayCapacity() {
            while (!closed.get()) {
                val relay = dataSocket ?: return
                if (relay.queueSize() < MAX_QUEUED_BYTES) return
                delay(5)
            }
        }

        /** Reads the local socket and forwards it to the PC. Runs until either side closes. */
        suspend fun pumpToRelay() {
            val buffer = ByteArray(16 * 1024)
            try {
                socket.soTimeout = 0
                val input = socket.getInputStream()
                while (!closed.get()) {
                    awaitRelayCapacity()
                    if (closed.get()) break
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    val packet = TunnelCrypto.encrypt(key, buffer.copyOf(read))
                    if (!sendFrame(RelayV2.FRAME_DATA, id, packet)) {
                        close(AppLocale.strings.relayTooSlow, notifyRelay = false)
                        return
                    }
                }
            } catch (_: IOException) {
                // Fall through to close.
            } finally {
                close(AppLocale.strings.phoneClosedStream, notifyRelay = true)
            }
        }

        /** Writes frames from the PC into the local socket. */
        suspend fun pumpToSocket() {
            try {
                val output = socket.getOutputStream()
                // Keep draining after close() flips the flag. The channel is closed by close(),
                // which means no more DATA can arrive, but everything already queued still has to
                // reach the local socket before it is shut.
                while (true) {
                    val plaintext = incoming.receiveCatching().getOrNull() ?: break
                    output.write(plaintext)
                    output.flush()
                }
            } catch (_: IOException) {
                // Fall through to close.
            } finally {
                // Always close here, even when close() ran first: a draining close deliberately
                // leaves the socket open for this loop.
                runCatching { socket.close() }
                close(AppLocale.strings.pcClosedStream, notifyRelay = false)
            }
        }

        fun close(reason: String, notifyRelay: Boolean, drain: Boolean = false) {
            if (!closed.compareAndSet(false, true)) return
            sessions.remove(id)
            decision.complete(false)
            incoming.close()
            android.util.Log.d("RelayTunnel", "stream $id closed: $reason")
            if (notifyRelay) {
                sendFrame(RelayV2.FRAME_CLOSE, id, byteArrayOf(RelayV2.CLOSE_NORMAL))
            }
            // A draining close is completed by pumpToSocket once every queued frame is written.
            if (!drain) runCatching { socket.close() }
        }
    }

    private fun backoffMs(attempt: Int): Long {
        val steps = longArrayOf(500, 1_000, 2_000, 4_000, 8_000, 15_000, 30_000)
        return steps[min(attempt - 1, steps.lastIndex)] + Random.nextLong(0, 400)
    }

    private companion object {
        val EMPTY = ByteArray(0)
        const val MAX_QUEUED_BYTES = 256 * 1024L
        const val CONNECT_WAIT_MS = 12_000L
        const val OPEN_TIMEOUT_MS = 12_000L
        const val HANDSHAKE_TIMEOUT_MS = 12_000L
    }
}
