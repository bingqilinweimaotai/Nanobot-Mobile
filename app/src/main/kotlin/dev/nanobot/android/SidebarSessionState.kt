package dev.nanobot.android

import dev.nanobot.protocol.SessionSummary

/**
 * Sidebar rows are the server's session list plus optimistic rows for chats the server has not
 * listed yet (a brand-new conversation whose first turn is still running). A local row is dropped
 * as soon as the server reports the same key, so the server's title and preview take over.
 */
internal fun mergeSidebarSessions(
    knownSessions: List<SessionSummary>,
    localSessions: Collection<SessionSummary>,
): List<SessionSummary> {
    if (localSessions.isEmpty()) return knownSessions
    val knownKeys = knownSessions.mapTo(HashSet()) { it.key }
    val pending = localSessions.filter { it.key !in knownKeys }
    return if (pending.isEmpty()) knownSessions else pending + knownSessions
}

/** Sidebar activity the server cannot report: locally running turns and unseen completions. */
internal data class SidebarRunState(
    val runningChatIds: Set<String> = emptySet(),
    val unreadChatIds: Set<String> = emptySet(),
)

/**
 * Reconciles local run tracking with a fresh server session list.
 *
 * A chat counts as finished when the server stops reporting `run_started_at`, or when a chat this
 * client started locally finally appears in the list without an active run. Finished chats that
 * are not open become unread; opening a chat clears its mark.
 */
internal fun reconcileSidebarRuns(
    previousSessions: List<SessionSummary>,
    serverSessions: List<SessionSummary>,
    runningChatIds: Set<String>,
    unreadChatIds: Set<String>,
    currentChatId: String?,
): SidebarRunState {
    val serverByChat = serverSessions
        .mapNotNull { session ->
            session.chatId.takeIf { it.isNotBlank() }?.let { it to session }
        }
        .toMap()
    val serverRunning = serverSessions
        .filter { it.runStartedAt != null }
        .mapTo(HashSet()) { it.chatId }

    val finished = mutableSetOf<String>()
    for (session in previousSessions) {
        val chatId = session.chatId
        if (chatId.isBlank() || session.runStartedAt == null) continue
        val current = serverByChat[chatId] ?: continue
        if (current.runStartedAt == null) finished += chatId
    }

    val stillRunning = runningChatIds.filterTo(mutableSetOf()) { chatId ->
        if (chatId in finished) return@filterTo false
        // The open chat settles through its own turn events; a refresh racing the send must not
        // hide the spinner before the first token arrives.
        if (chatId == currentChatId) return@filterTo true
        if (chatId in serverRunning) return@filterTo true
        // The server has not listed the chat yet; the local run is the only signal we have.
        if (chatId !in serverByChat) return@filterTo true
        // The server knows the chat and reports no active run, so the local run is over.
        finished += chatId
        false
    }

    val unread = unreadChatIds.toMutableSet()
    unread += finished.filter { it != currentChatId }
    currentChatId?.let(unread::remove)
    return SidebarRunState(runningChatIds = stillRunning, unreadChatIds = unread)
}
