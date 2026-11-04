package dev.nanobot.android

import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.WorkspaceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSearchTest {
    private val sessions = listOf(
        SessionSummary(
            key = "websocket:chat-1",
            title = "论文阅读",
            preview = "帮我读一下这篇论文",
            updatedAt = "2026-10-02",
            workspaceScope = WorkspaceScope(projectPath = "/papers"),
        ),
        SessionSummary(
            key = "websocket:chat-2",
            title = "Release notes",
            preview = "prepare the release",
            updatedAt = "2026-10-01",
        ),
        SessionSummary(
            key = "websocket:chat-3",
            title = "Third",
            preview = "unrelated",
            updatedAt = "2026-09-30",
        ),
    )

    @Test
    fun `matches text inside an assistant reply`() {
        val cached = mapOf(
            "chat-2" to listOf(
                ChatMessage("u1", MessageRole.User, "summarize the changes"),
                ChatMessage("a1", MessageRole.Assistant, "The transformer cache now reuses keys."),
            ),
        )

        val results = searchConversationHits(
            query = "transformer",
            sessions = sessions,
            titleOverrides = emptyMap(),
            cachedMessages = cached,
            liveChatId = null,
            liveMessages = emptyList(),
        )

        assertEquals(1, results.size)
        val hit = results.single()
        assertEquals("chat-2", hit.chatId)
        assertEquals(MessageRole.Assistant, hit.role)
        assertEquals("a1", hit.messageId)
        assertTrue(hit.preview.contains("transformer", ignoreCase = true))
    }

    @Test
    fun `matches a title without needing a cached transcript`() {
        val results = searchConversationHits(
            query = "论文",
            sessions = sessions,
            titleOverrides = emptyMap(),
            cachedMessages = emptyMap(),
            liveChatId = null,
            liveMessages = emptyList(),
        )

        assertEquals(1, results.size)
        assertEquals("chat-1", results.single().chatId)
        assertNull(results.single().role)
    }

    @Test
    fun `prefers live messages for the open chat`() {
        val cached = mapOf(
            "chat-2" to listOf(ChatMessage("old", MessageRole.User, "nothing relevant")),
        )
        val live = listOf(
            ChatMessage("live-a", MessageRole.Assistant, "streaming answer about widgets"),
        )

        val results = searchConversationHits(
            query = "widgets",
            sessions = sessions,
            titleOverrides = emptyMap(),
            cachedMessages = cached,
            liveChatId = "chat-2",
            liveMessages = live,
        )

        assertEquals("live-a", results.single().messageId)
    }

    @Test
    fun `requires every search term`() {
        val cached = mapOf(
            "chat-2" to listOf(
                ChatMessage("a1", MessageRole.Assistant, "the cache is warm"),
            ),
        )

        assertTrue(
            searchConversationHits(
                query = "cache cold",
                sessions = sessions,
                titleOverrides = emptyMap(),
                cachedMessages = cached,
                liveChatId = null,
                liveMessages = emptyList(),
            ).isEmpty(),
        )
    }

    @Test
    fun `scroll target falls back to text when cached ids shifted`() {
        val messages = listOf(
            ChatMessage("thread-user-0", MessageRole.User, "question"),
            ChatMessage("thread-assistant-1", MessageRole.Assistant, "the full answer"),
        )

        assertEquals(
            1,
            findSearchTarget(
                messages,
                SearchScrollTarget(messageId = "missing", text = "the full answer", role = MessageRole.Assistant),
            ),
        )
        assertEquals(
            0,
            findSearchTarget(
                messages,
                SearchScrollTarget(messageId = "thread-user-0", text = "", role = MessageRole.User),
            ),
        )
    }
}
