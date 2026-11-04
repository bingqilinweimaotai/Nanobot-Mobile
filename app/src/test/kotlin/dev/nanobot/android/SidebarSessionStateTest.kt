package dev.nanobot.android

import dev.nanobot.protocol.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarSessionStateTest {
    @Test
    fun `an optimistic row stays above the server list until it is confirmed`() {
        val merged = mergeSidebarSessions(
            knownSessions = listOf(session("old")),
            localSessions = listOf(session("new")),
        )

        assertEquals(listOf("websocket:new", "websocket:old"), merged.map { it.key })
    }

    @Test
    fun `the server copy replaces an optimistic row with the same key`() {
        val merged = mergeSidebarSessions(
            knownSessions = listOf(session("new", title = "Real title")),
            localSessions = listOf(session("new")),
        )

        assertEquals(1, merged.size)
        assertEquals("Real title", merged.single().title)
    }

    @Test
    fun `a locally running chat stays running until the server lists it`() {
        val state = reconcileSidebarRuns(
            previousSessions = emptyList(),
            serverSessions = emptyList(),
            runningChatIds = setOf("chat-a"),
            unreadChatIds = emptySet(),
            currentChatId = "chat-b",
        )

        assertEquals(setOf("chat-a"), state.runningChatIds)
        assertTrue(state.unreadChatIds.isEmpty())
    }

    @Test
    fun `a locally running chat that finishes while elsewhere becomes unread`() {
        val state = reconcileSidebarRuns(
            previousSessions = emptyList(),
            serverSessions = listOf(session("chat-a")),
            runningChatIds = setOf("chat-a"),
            unreadChatIds = emptySet(),
            currentChatId = "chat-b",
        )

        assertTrue(state.runningChatIds.isEmpty())
        assertEquals(setOf("chat-a"), state.unreadChatIds)
    }

    @Test
    fun `a turn that ends in the open chat is not marked unread`() {
        val state = reconcileSidebarRuns(
            previousSessions = listOf(session("chat-a", running = true)),
            serverSessions = listOf(session("chat-a")),
            runningChatIds = setOf("chat-a"),
            unreadChatIds = emptySet(),
            currentChatId = "chat-a",
        )

        assertTrue(state.runningChatIds.isEmpty())
        assertTrue(state.unreadChatIds.isEmpty())
    }

    @Test
    fun `the server run flag marks a finished background chat unread`() {
        val state = reconcileSidebarRuns(
            previousSessions = listOf(session("chat-a", running = true)),
            serverSessions = listOf(session("chat-a")),
            runningChatIds = emptySet(),
            unreadChatIds = emptySet(),
            currentChatId = "chat-b",
        )

        assertEquals(setOf("chat-a"), state.unreadChatIds)
    }

    @Test
    fun `a run still reported by the server keeps its spinner`() {
        val state = reconcileSidebarRuns(
            previousSessions = listOf(session("chat-a", running = true)),
            serverSessions = listOf(session("chat-a", running = true)),
            runningChatIds = setOf("chat-a"),
            unreadChatIds = emptySet(),
            currentChatId = "chat-b",
        )

        assertEquals(setOf("chat-a"), state.runningChatIds)
        assertFalse("chat-a" in state.unreadChatIds)
    }

    @Test
    fun `opening a chat clears its unread mark`() {
        val state = reconcileSidebarRuns(
            previousSessions = emptyList(),
            serverSessions = listOf(session("chat-a")),
            runningChatIds = emptySet(),
            unreadChatIds = setOf("chat-a", "chat-b"),
            currentChatId = "chat-a",
        )

        assertEquals(setOf("chat-b"), state.unreadChatIds)
    }

    private fun session(
        chatId: String,
        title: String = "",
        running: Boolean = false,
    ) = SessionSummary(
        key = "websocket:$chatId",
        title = title,
        runStartedAt = if (running) 1_760_000_000_000 else null,
    )
}
