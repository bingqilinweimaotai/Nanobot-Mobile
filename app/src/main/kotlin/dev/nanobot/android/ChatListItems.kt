package dev.nanobot.android

/**
 * One row in the transcript: a user/system message, or one assistant turn.
 *
 * An assistant turn is every assistant message between two user or system
 * rows — answer slices and reasoning steps alike. The turn renders as one
 * continuous answer body with a single activity strip above it, so a model
 * that alternates text and reasoning inside one turn no longer produces a
 * stack of answer bubbles, action rows, and "Thought" headers.
 */
internal sealed interface ChatListItem {
    val key: String

    data class Message(val message: ChatMessage, val index: Int) : ChatListItem {
        override val key: String get() = message.id
    }

    data class AssistantTurn(
        val messages: List<ChatMessage>,
        val startIndex: Int,
    ) : ChatListItem {
        override val key: String get() = "assistant-turn-${messages.first().id}"
    }
}

internal fun chatListItems(messages: List<ChatMessage>): List<ChatListItem> {
    val items = mutableListOf<ChatListItem>()
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        if (message.role != MessageRole.Assistant) {
            items += ChatListItem.Message(message, index)
            index += 1
            continue
        }
        val start = index
        while (index < messages.size && messages[index].role == MessageRole.Assistant) index += 1
        items += ChatListItem.AssistantTurn(
            messages = messages.subList(start, index).toList(),
            startIndex = start,
        )
    }
    return items
}

/**
 * A reloaded thread carries no streaming flags. If its turn is still running, keep the last
 * reasoning bubble visibly active instead of showing a completed check until the next step.
 */
internal fun List<ChatMessage>.markRunningReasoning(): List<ChatMessage> {
    val last = lastOrNull() ?: return this
    if (!last.reasoning || last.streaming) return this
    return toMutableList().also { it[it.lastIndex] = last.copy(streaming = true) }
}

/** Maps a transcript message index to the row that renders it. */
internal fun chatListIndexForMessage(
    items: List<ChatListItem>,
    messageIndex: Int,
): Int = items.indexOfFirst { item ->
    when (item) {
        is ChatListItem.Message -> item.index == messageIndex
        is ChatListItem.AssistantTurn ->
            messageIndex >= item.startIndex &&
                messageIndex < item.startIndex + item.messages.size
    }
}

/** Maps a message id to the transcript row that renders it, or -1 when it is gone. */
internal fun chatListIndexForMessageId(messages: List<ChatMessage>, messageId: String): Int {
    val messageIndex = messages.indexOfFirst { it.id == messageId }
    if (messageIndex < 0) return -1
    return chatListIndexForMessage(chatListItems(messages), messageIndex)
}
