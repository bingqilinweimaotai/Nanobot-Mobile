package dev.nanobot.gateway

data class GatewayConfig(
    /** Gateway origin, for example `http://10.0.2.2:8765`. */
    val baseUrl: String,
    /** `channels.websocket.tokenIssueSecret` (or a static token). */
    val secret: String,
    /** Stable per-install identifier used for `allowFrom`. */
    val clientId: String,
)

sealed interface GatewayState {
    data object Disconnected : GatewayState
    data object Connecting : GatewayState
    data class Ready(
        val chatId: String,
        val capabilities: Set<String>,
    ) : GatewayState

    data class Reconnecting(
        val attempt: Int,
        val reason: String,
    ) : GatewayState

    data class Failed(val message: String) : GatewayState
}

class GatewayException(
    override val message: String,
    val statusCode: Int? = null,
) : Exception(message)
