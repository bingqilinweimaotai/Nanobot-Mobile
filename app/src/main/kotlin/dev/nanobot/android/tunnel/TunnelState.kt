package dev.nanobot.android.tunnel

/** Health of the outbound relay connection. Direct hosts never create a tunnel. */
sealed interface TunnelState {
    data object Stopped : TunnelState
    data object Connecting : TunnelState
    data object Ready : TunnelState
    data class Reconnecting(val attempt: Int, val reason: String) : TunnelState
    data class Failed(val reason: String) : TunnelState
}

data class RelayConfig(
    val controlUrl: String,
    val serverId: String,
    val clientToken: String,
    /** Base64url AES-GCM key from the pairing offer. */
    val pairKey: String,
)
