package dev.nanobot.android.host

import dev.nanobot.android.AppLocale
import dev.nanobot.tunnel.PairingOffer
import kotlinx.serialization.Serializable

@Serializable
enum class HostKind { Direct, Relay }

/**
 * One saved gateway.
 *
 * Secrets and the relay pair key deliberately live in [SecretStore] instead of this model, so the
 * host list can be stored as ordinary preferences without ever containing a credential.
 */
@Serializable
data class HostProfile(
    val id: String,
    val name: String,
    val kind: HostKind,
    /** Direct mode: `http://host:port`. */
    val endpoint: String = "",
    /** Relay mode: the full control URL, for example `wss://relay.example.com/control`. */
    val relayUrl: String = "",
    val serverId: String = "",
    val clientToken: String = "",
    val gatewayPort: Int? = null,
    val platform: String = "",
    val lastChatId: String? = null,
    val createdAt: Long = 0L,
    val lastUsedAt: Long = 0L,
) {
    val displayName: String
        get() = name.trim().ifBlank {
            when (kind) {
                HostKind.Direct -> endpoint.substringAfter("://").substringBefore('/').ifBlank { AppLocale.strings.gatewayHostDefault }
                HostKind.Relay -> serverId.take(12).ifBlank { AppLocale.strings.gatewayHostDefault }
            }
        }

    val targetLabel: String
        get() = when (kind) {
            HostKind.Direct -> endpoint.substringAfter("://").substringBefore('/').ifBlank { endpoint }
            HostKind.Relay -> serverId.take(12).ifBlank { relayUrl }
        }

    fun fromOffer(offer: PairingOffer): HostProfile = copy(
        name = name.ifBlank { offer.displayName },
        kind = if (offer.kind == PairingOffer.KIND_DIRECT) HostKind.Direct else HostKind.Relay,
        endpoint = offer.endpoint,
        relayUrl = offer.relay,
        serverId = offer.serverId,
        clientToken = offer.clientToken,
        gatewayPort = offer.gatewayPort,
        platform = offer.platform,
    )
}
