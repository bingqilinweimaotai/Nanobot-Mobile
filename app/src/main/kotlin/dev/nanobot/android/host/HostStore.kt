package dev.nanobot.android.host

import android.content.Context
import dev.nanobot.android.AppLocale
import dev.nanobot.android.SecretStore
import dev.nanobot.android.SettingsStore
import dev.nanobot.tunnel.PairingOffer
import java.net.URI
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Persists the user's saved gateways and the currently selected one.
 *
 * The first run migrates the old single-endpoint settings so existing installs keep working. Every
 * write rewrites one small JSON document; the list is expected to stay in the handful-of-hosts range.
 */
class HostStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("nanobot_hosts", Context.MODE_PRIVATE)
    private val secrets = SecretStore(appContext)
    private val legacy = SettingsStore(appContext)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
    private val lock = Any()

    init {
        synchronized(lock) { migrateIfNeeded() }
    }

    fun hosts(): List<HostProfile> = synchronized(lock) { state().hosts }

    fun currentHost(): HostProfile? = synchronized(lock) {
        val state = state()
        state.hosts.firstOrNull { it.id == state.currentId }
    }

    fun currentId(): String? = synchronized(lock) { state().currentId }

    fun upsert(host: HostProfile, makeCurrent: Boolean = false): HostProfile {
        synchronized(lock) {
            val state = state()
            val now = System.currentTimeMillis()
            val existing = state.hosts.firstOrNull { it.id == host.id }
            val saved = host.copy(
                createdAt = existing?.createdAt?.takeIf { it > 0 } ?: host.createdAt.takeIf { it > 0 } ?: now,
                lastUsedAt = now,
            )
            val hosts = state.hosts.map { if (it.id == saved.id) saved else it } +
                if (existing == null) listOf(saved) else emptyList()
            writeState(
                HostState(
                    currentId = if (makeCurrent) saved.id else state.currentId ?: saved.id,
                    hosts = hosts,
                ),
            )
            return saved
        }
    }

    fun remove(id: String) {
        synchronized(lock) {
            val state = state()
            val hosts = state.hosts.filterNot { it.id == id }
            val currentId = when {
                state.currentId != id -> state.currentId
                else -> hosts.firstOrNull()?.id
            }
            writeState(HostState(currentId = currentId, hosts = hosts))
            secrets.remove(gatewaySecretKey(id))
            secrets.remove(pairKeyKey(id))
        }
    }

    fun setCurrent(id: String) {
        synchronized(lock) {
            val state = state()
            if (state.hosts.none { it.id == id }) return
            writeState(state.copy(currentId = id))
        }
    }

    fun updateLastChat(id: String, chatId: String?) {
        synchronized(lock) {
            val state = state()
            if (state.hosts.none { it.id == id }) return
            val hosts = state.hosts.map {
                if (it.id == id) it.copy(lastChatId = chatId, lastUsedAt = System.currentTimeMillis()) else it
            }
            writeState(state.copy(hosts = hosts))
        }
    }

    fun gatewaySecret(id: String): String = secrets.read(gatewaySecretKey(id)).orEmpty()

    fun setGatewaySecret(id: String, value: String) {
        secrets.write(gatewaySecretKey(id), value)
    }

    fun pairKey(id: String): String = secrets.read(pairKeyKey(id)).orEmpty()

    fun setPairKey(id: String, value: String) {
        secrets.write(pairKeyKey(id), value)
    }

    /** Saves a scanned or pasted offer and makes it current. */
    fun saveOffer(offer: PairingOffer): HostProfile {
        val valid = offer.requireValid()
        val kind = if (valid.kind == PairingOffer.KIND_DIRECT) HostKind.Direct else HostKind.Relay
        val existing = synchronized(lock) {
            state().hosts.firstOrNull {
                when (kind) {
                    HostKind.Direct -> it.kind == HostKind.Direct && it.endpoint == valid.endpoint
                    HostKind.Relay -> it.kind == HostKind.Relay &&
                        it.relayUrl == valid.relay && it.serverId == valid.serverId
                }
            }
        }
        val host = (existing ?: HostProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            kind = kind,
            createdAt = System.currentTimeMillis(),
        )).fromOffer(valid).let {
            if (it.name.isBlank()) it.copy(name = valid.displayName) else it
        }
        setGatewaySecret(host.id, valid.secret)
        if (kind == HostKind.Relay) setPairKey(host.id, valid.pairKey)
        return upsert(host, makeCurrent = true)
    }

    fun saveDirect(name: String, endpoint: String, secret: String): HostProfile {
        val normalized = normalizeEndpoint(endpoint)
        require(normalized.isNotBlank()) { AppLocale.strings.enterGatewayAddress }
        val existing = synchronized(lock) {
            state().hosts.firstOrNull { it.kind == HostKind.Direct && it.endpoint == normalized }
        }
        val host = (existing ?: HostProfile(
            id = UUID.randomUUID().toString(),
            name = "",
            kind = HostKind.Direct,
            createdAt = System.currentTimeMillis(),
        )).copy(
            name = name.trim().ifBlank { existing?.name.orEmpty() }
                .ifBlank { hostName(normalized) },
            endpoint = normalized,
            gatewayPort = portOf(normalized),
        )
        setGatewaySecret(host.id, secret)
        return upsert(host, makeCurrent = true)
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        if (readState() == null) {
            val endpoint = normalizeEndpoint(legacy.endpoint)
            if (endpoint.isNotBlank()) {
                val host = HostProfile(
                    id = "legacy",
                    name = hostName(endpoint),
                    kind = HostKind.Direct,
                    endpoint = endpoint,
                    gatewayPort = portOf(endpoint),
                    lastChatId = legacy.lastChatId,
                    createdAt = System.currentTimeMillis(),
                    lastUsedAt = System.currentTimeMillis(),
                )
                secrets.write(gatewaySecretKey(host.id), legacy.secret)
                writeState(HostState(currentId = host.id, hosts = listOf(host)))
            }
        }
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
    }

    private fun state(): HostState = readState() ?: HostState()

    private fun readState(): HostState? {
        val raw = prefs.getString(KEY_STATE, null) ?: return null
        return runCatching { json.decodeFromString(HostState.serializer(), raw) }.getOrNull()
    }

    private fun writeState(value: HostState) {
        prefs.edit().putString(KEY_STATE, json.encodeToString(HostState.serializer(), value)).apply()
    }

    private fun gatewaySecretKey(id: String): String = "gateway:$id"

    private fun pairKeyKey(id: String): String = "pair:$id"

    private fun normalizeEndpoint(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "http://$trimmed"
        }
    }

    private fun hostName(endpoint: String): String =
        runCatching { URI(endpoint).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Gateway"

    private fun portOf(endpoint: String): Int? =
        runCatching {
            val uri = URI(endpoint)
            if (uri.port > 0) uri.port else if (uri.scheme == "https") 443 else 80
        }.getOrNull()

    @Serializable
    private data class HostState(
        val currentId: String? = null,
        val hosts: List<HostProfile> = emptyList(),
    )

    private companion object {
        const val KEY_STATE = "hosts"
        const val KEY_MIGRATED = "migrated"
    }
}
