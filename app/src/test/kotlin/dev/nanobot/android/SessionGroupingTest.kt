package dev.nanobot.android

import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.WorkspaceScope
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionGroupingTest {
    @Test
    fun `default workspace conversations share one localized topic group`() {
        val defaultPath = """C:\Users\test\.nanobot\workspace"""
        val groups = groupSessionsForSidebar(
            sessions = listOf(
                session("websocket:a", defaultPath, updatedAt = "2026-10-01"),
                session("websocket:b", """E:\workspaces\nanobot""", updatedAt = "2026-10-03"),
                session("websocket:c", "", updatedAt = "2026-10-02"),
            ),
            defaultWorkspacePath = defaultPath,
            projectNameOverrides = emptyMap(),
            topicGroupLabel = "主题",
        )

        assertEquals(listOf("nanobot", "主题"), groups.map { it.label })
        assertEquals(listOf("websocket:b"), groups[0].sessions.map { it.key })
        assertEquals(listOf("websocket:c", "websocket:a"), groups[1].sessions.map { it.key })
    }

    @Test
    fun `project names are matched case sensitively like the WebUI`() {
        val groups = groupSessionsForSidebar(
            sessions = listOf(
                session("websocket:a", "/Paper/reading", updatedAt = "2026-10-02"),
                session("websocket:b", "/Paper/Reading", updatedAt = "2026-10-01"),
            ),
            defaultWorkspacePath = null,
            projectNameOverrides = emptyMap(),
            topicGroupLabel = "Topics",
        )

        assertEquals(listOf("reading", "Reading"), groups.map { it.label })
    }

    @Test
    fun `project overrides replace the directory label`() {
        val groups = groupSessionsForSidebar(
            sessions = listOf(session("websocket:a", "/Paper/reading", updatedAt = "2026-10-01")),
            defaultWorkspacePath = null,
            projectNameOverrides = mapOf("/Paper/reading" to "论文阅读"),
            topicGroupLabel = "Topics",
        )

        assertEquals("论文阅读", groups.single().label)
    }

    @Test
    fun `color blocks use the first Latin letter or CJK character`() {
        assertEquals("N", workspaceInitial("nanobot"))
        assertEquals("论", workspaceInitial("论文阅读"))
        assertEquals("?", workspaceInitial("   "))
    }

    @Test
    fun `the same group always receives the same accent color`() {
        assertEquals(workspaceAccentColor("workspace:chats"), workspaceAccentColor("workspace:chats"))
    }

    @Test
    fun `pinned conversations lead their project and archived ones stay hidden`() {
        val groups = groupSessionsForSidebar(
            sessions = listOf(
                session("websocket:a", "/work/alpha", updatedAt = "2026-10-01T10:00:00Z"),
                session("websocket:b", "/work/alpha", updatedAt = "2026-10-02T10:00:00Z"),
                session("websocket:c", "/work/alpha", updatedAt = "2026-10-03T10:00:00Z"),
            ),
            defaultWorkspacePath = null,
            projectNameOverrides = emptyMap(),
            topicGroupLabel = "Topics",
            pinnedKeys = setOf("websocket:a"),
            archivedKeys = setOf("websocket:c"),
        )

        assertEquals(listOf("websocket:a", "websocket:b"), groups.single().sessions.map { it.key })
    }

    @Test
    fun `archived conversations appear at the end when archived are shown`() {
        val groups = groupSessionsForSidebar(
            sessions = listOf(
                session("websocket:a", "/work/alpha", updatedAt = "2026-10-01T10:00:00Z"),
                session("websocket:b", "/work/alpha", updatedAt = "2026-10-02T10:00:00Z"),
            ),
            defaultWorkspacePath = null,
            projectNameOverrides = emptyMap(),
            topicGroupLabel = "Topics",
            archivedKeys = setOf("websocket:b"),
            showArchived = true,
        )

        assertEquals(listOf("websocket:a", "websocket:b"), groups.single().sessions.map { it.key })
    }

    @Test
    fun `conversations without projects group by pinned and date buckets`() {
        val now = java.time.Instant.parse("2026-10-03T12:00:00Z").toEpochMilli()
        val groups = groupSessionsForSidebar(
            sessions = listOf(
                session("websocket:pinned", "", updatedAt = "2026-09-01T10:00:00Z"),
                session("websocket:today", "", updatedAt = "2026-10-03T09:00:00Z"),
                session("websocket:yesterday", "", updatedAt = "2026-10-02T09:00:00Z"),
                session("websocket:earlier", "", updatedAt = "2026-09-20T09:00:00Z"),
                session("websocket:archived", "", updatedAt = "2026-10-01T09:00:00Z"),
            ),
            defaultWorkspacePath = null,
            projectNameOverrides = emptyMap(),
            topicGroupLabel = "Topics",
            pinnedKeys = setOf("websocket:pinned"),
            archivedKeys = setOf("websocket:archived"),
            showArchived = true,
            labels = SidebarGroupLabels(
                pinned = "Pinned",
                today = "Today",
                yesterday = "Yesterday",
                earlier = "Earlier",
                archived = "Archived",
            ),
            nowMillis = now,
        )

        assertEquals(
            listOf("Pinned", "Today", "Yesterday", "Earlier", "Archived"),
            groups.map { it.label },
        )
        assertEquals(listOf("websocket:pinned"), groups[0].sessions.map { it.key })
        assertEquals(listOf("websocket:today"), groups[1].sessions.map { it.key })
        assertEquals(listOf("websocket:yesterday"), groups[2].sessions.map { it.key })
        assertEquals(listOf("websocket:earlier"), groups[3].sessions.map { it.key })
        assertEquals(listOf("websocket:archived"), groups[4].sessions.map { it.key })
    }

    private fun session(key: String, path: String, updatedAt: String) = SessionSummary(
        key = key,
        workspaceScope = if (path.isBlank()) null else WorkspaceScope(projectPath = path),
        updatedAt = updatedAt,
    )
}
