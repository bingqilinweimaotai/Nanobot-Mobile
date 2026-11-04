package dev.nanobot.android

import dev.nanobot.protocol.SessionSummary

/**
 * One result in the drawer's conversation search. [role] is null when only the session title or
 * preview matched; otherwise [messageText] is the user or assistant message that matched.
 */
data class ConversationSearchHit(
    val chatId: String,
    val sessionKey: String,
    val title: String,
    val preview: String,
    val role: MessageRole? = null,
    val messageId: String? = null,
    val messageText: String? = null,
) {
    /** The bubble the transcript should reveal after opening this result. */
    val scrollTarget: SearchScrollTarget?
        get() = messageText?.takeIf { it.isNotBlank() }?.let { text ->
            SearchScrollTarget(messageId = messageId, text = text, role = role)
        }
}

/** A message the transcript should scroll to after a search result is opened. */
data class SearchScrollTarget(
    val messageId: String?,
    val text: String,
    val role: MessageRole?,
)

/**
 * Searches conversations the way the WebUI does: titles and previews first, then the messages
 * already held on this device. Both user and assistant text is searched, so an answer is findable
 * even when the question does not mention the term.
 */
internal fun searchConversationHits(
    query: String,
    sessions: List<SessionSummary>,
    titleOverrides: Map<String, String>,
    cachedMessages: Map<String, List<ChatMessage>>,
    liveChatId: String?,
    liveMessages: List<ChatMessage>,
    limit: Int = 30,
): List<ConversationSearchHit> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (terms.isEmpty()) return emptyList()

    val results = mutableListOf<ConversationSearchHit>()
    for (session in sessions.sortedByDescending { it.updatedAt.orEmpty() }) {
        if (session.chatId.isBlank()) continue
        val title = sessionDisplayTitle(session, titleOverrides)
        val preview = visibleSessionPreview(session.preview)
        val header = "$title $preview".lowercase()
        if (terms.all { header.contains(it) }) {
            results += ConversationSearchHit(
                chatId = session.chatId,
                sessionKey = session.key,
                title = title,
                preview = preview,
            )
        } else {
            val messages = if (session.chatId == liveChatId && liveMessages.isNotEmpty()) {
                liveMessages
            } else {
                cachedMessages[session.chatId].orEmpty()
            }
            val match = messages.asReversed().firstOrNull { message ->
                (message.role == MessageRole.User || message.role == MessageRole.Assistant) &&
                    !message.reasoning &&
                    message.text.isNotBlank() &&
                    terms.all { message.text.lowercase().contains(it) }
            }
            if (match != null) {
                results += ConversationSearchHit(
                    chatId = session.chatId,
                    sessionKey = session.key,
                    title = title,
                    preview = searchExcerpt(match.text, terms.first()),
                    role = match.role,
                    messageId = match.id,
                    messageText = match.text,
                )
            }
        }
        if (results.size >= limit) break
    }
    return results
}

/** A one-line excerpt around the first matching term, like the WebUI's search preview. */
internal fun searchExcerpt(text: String, term: String): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.isEmpty()) return ""
    val index = flat.lowercase().indexOf(term.lowercase()).takeIf { it >= 0 } ?: 0
    val start = (index - 40).coerceAtLeast(0)
    val end = (index + 120).coerceAtMost(flat.length)
    return buildString {
        if (start > 0) append('…')
        append(flat.substring(start, end))
        if (end < flat.length) append('…')
    }
}

/**
 * Finds the bubble a search result points at. Cached ids can shift when the gateway returns a
 * newer transcript window, so fall back to role and text.
 */
internal fun findSearchTarget(
    messages: List<ChatMessage>,
    target: SearchScrollTarget,
): Int {
    target.messageId?.let { id ->
        val byId = messages.indexOfFirst { it.id == id }
        if (byId >= 0) return byId
    }
    val role = target.role
    val text = target.text.trim()
    if (text.isBlank()) return -1
    val exact = messages.indexOfFirst {
        it.text.trim() == text && (role == null || it.role == role)
    }
    if (exact >= 0) return exact
    val head = text.take(80)
    return messages.indexOfFirst {
        it.text.contains(head) && (role == null || it.role == role)
    }
}
