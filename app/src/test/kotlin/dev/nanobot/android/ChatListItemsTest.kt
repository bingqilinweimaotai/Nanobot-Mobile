package dev.nanobot.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ChatListItemsTest {
    private fun message(
        id: String,
        role: MessageRole = MessageRole.Assistant,
        reasoning: Boolean = false,
        streaming: Boolean = false,
    ) = ChatMessage(
        id,
        role,
        "text $id",
        streaming = streaming,
        reasoning = reasoning,
    )

    @Test
    fun `assistant segments between user messages share one turn row`() {
        val messages = listOf(
            message("u1", role = MessageRole.User),
            message("a1"),
            message("r1", reasoning = true),
            message("a2"),
            message("r2", reasoning = true),
            message("u2", role = MessageRole.User),
            message("a3"),
        )

        val items = chatListItems(messages)

        assertEquals(
            listOf("u1", "assistant-turn-a1", "u2", "assistant-turn-a3"),
            items.map { it.key },
        )
        val turn = assertIs<ChatListItem.AssistantTurn>(items[1])
        assertEquals(listOf("a1", "r1", "a2", "r2"), turn.messages.map { it.id })
        assertEquals(1, turn.startIndex)
    }

    @Test
    fun `a system row ends the turn instead of joining it`() {
        val messages = listOf(
            message("a1"),
            message("s1", role = MessageRole.System),
            message("a2"),
        )

        val items = chatListItems(messages)

        assertEquals(listOf("assistant-turn-a1", "s1", "assistant-turn-a2"), items.map { it.key })
    }

    @Test
    fun `search targets map to the row that renders them`() {
        val messages = listOf(
            message("u1", role = MessageRole.User),
            message("a1"),
            message("r1", reasoning = true),
            message("a2"),
        )
        val items = chatListItems(messages)

        assertEquals(0, chatListIndexForMessage(items, 0))
        assertEquals(1, chatListIndexForMessage(items, 1))
        assertEquals(1, chatListIndexForMessage(items, 2))
        assertEquals(1, chatListIndexForMessage(items, 3))
        assertEquals(-1, chatListIndexForMessage(items, 99))
    }

    @Test
    fun `message ids map to the row that renders them`() {
        val messages = listOf(
            message("u1", role = MessageRole.User),
            message("r1", reasoning = true),
            message("r2", reasoning = true),
            message("a1"),
            message("u2", role = MessageRole.User),
        )

        // The whole assistant turn is one row, and the next user message is the row after it.
        assertEquals(2, chatListIndexForMessageId(messages, "u2"))
        assertEquals(1, chatListIndexForMessageId(messages, "r2"))
        assertEquals(1, chatListIndexForMessageId(messages, "a1"))
        assertEquals(-1, chatListIndexForMessageId(messages, "missing"))
    }

    @Test
    fun `a running reload keeps the last thought active`() {
        val messages = listOf(message("u1", role = MessageRole.User), message("r1", reasoning = true))

        val marked = messages.markRunningReasoning()

        assertTrue(marked.last().streaming)
        assertFalse(marked.first().streaming)
    }

    @Test
    fun `a finished reload leaves the last answer alone`() {
        val messages = listOf(
            message("r1", reasoning = true),
            message("a1"),
        )

        val marked = messages.markRunningReasoning()

        assertFalse(marked.last().streaming)
    }
}
