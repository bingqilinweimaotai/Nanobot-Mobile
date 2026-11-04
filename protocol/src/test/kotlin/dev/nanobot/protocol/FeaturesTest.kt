package dev.nanobot.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeaturesTest {
    @Test
    fun `message carries media and tool mentions`() {
        val frame = NanobotJson.parseToJsonElement(
            Outbound.message(
                chatId = "chat-1",
                content = "look",
                turnId = "turn-1",
                media = listOf(AttachmentReference("ref-1", "photo.png")),
                cliApps = listOf("drawio"),
                mcpPresets = listOf("notion"),
            ),
        ) as JsonObject

        val media = frame["media"] as kotlinx.serialization.json.JsonArray
        assertEquals("ref-1", (media[0] as JsonObject)["reference"]?.toString()?.trim('"'))
        assertEquals("photo.png", (media[0] as JsonObject)["name"]?.toString()?.trim('"'))
        assertTrue(frame["cli_apps"] is kotlinx.serialization.json.JsonArray)
        assertTrue(frame["mcp_presets"] is kotlinx.serialization.json.JsonArray)
    }

    @Test
    fun `commands payload decodes lifecycle`() {
        val payload = NanobotJson.decodeFromString<CommandsResponse>(
            """{"commands":[{"command":"/new","title":"New chat","lifecycle":"finalize_active_turn"}]}""",
        )
        assertEquals(1, payload.commands.size)
        assertEquals("finalize_active_turn", payload.commands.first().lifecycle)
    }

    @Test
    fun `mcp payload decodes the WebUI management fields`() {
        val payload = NanobotJson.decodeFromString<McpPresetsResponse>(
            """
            {
              "proxy_configured": true,
              "installed_count": 1,
              "requires_restart": true,
              "presets": [{
                "name": "notion",
                "display_name": "Notion",
                "description": "Search Notion",
                "docs_url": "https://developers.notion.com",
                "transport": "streamableHttp",
                "auth": "oauth",
                "use_proxy": true,
                "install_supported": true,
                "installed": true,
                "configured": false,
                "status": "authorization_required",
                "required_fields": [{
                  "name": "token", "label": "Token", "secret": true,
                  "required": true, "configured": false
                }],
                "connection_summary": "https://mcp.notion.com/mcp",
                "enabled_tools": ["mcp_notion_search"],
                "tool_names": ["mcp_notion_search"]
              }],
              "last_action": {"ok": true, "message": "Updated tools for notion."}
            }
            """.trimIndent(),
        )

        val preset = payload.presets.single()
        assertEquals("Notion", preset.displayName)
        assertEquals("authorization_required", preset.status)
        assertEquals("oauth", preset.auth)
        assertTrue(preset.useProxy)
        assertTrue(preset.requiredFields.single().secret)
        assertEquals(listOf("mcp_notion_search"), preset.enabledTools)
        assertTrue(payload.requiresRestart)
        assertTrue(payload.lastAction?.ok == true)
    }

    @Test
    fun `automations decode the WebUI detail fields`() {
        val payload = NanobotJson.decodeFromString<AutomationsResponse>(
            """
            {
              "jobs": [{
                "id": "job-1",
                "name": "Daily report",
                "enabled": true,
                "protected": false,
                "delete_after_run": false,
                "created_at_ms": 1700000000000,
                "updated_at_ms": 1700000100000,
                "schedule": {"kind": "cron", "expr": "0 9 * * *", "tz": "Asia/Shanghai"},
                "payload": {"kind": "agent_turn", "message": "Summarize today"},
                "state": {
                  "next_run_at_ms": 1700003600000,
                  "last_run_at_ms": 1700000000000,
                  "last_status": "ok",
                  "pending": false,
                  "run_history": [{
                    "run_at_ms": 1700000000000, "status": "ok", "duration_ms": 4200
                  }]
                },
                "origin": {
                  "session_key": "websocket:chat-1",
                  "channel": "websocket",
                  "chat_id": "chat-1",
                  "title": "Daily report"
                }
              }]
            }
            """.trimIndent(),
        )

        val job = payload.jobs.single()
        assertEquals("Daily report", job.name)
        assertEquals("cron", job.schedule.kind)
        assertEquals("0 9 * * *", job.schedule.expr)
        assertEquals("Asia/Shanghai", job.schedule.tz)
        assertEquals("Summarize today", job.payload.message)
        assertEquals(1700003600000, job.state.nextRunAtMs)
        assertEquals("ok", job.state.lastStatus)
        assertEquals(4200, job.state.runHistory.single().durationMs)
        assertEquals("websocket:chat-1", job.origin?.sessionKey)
    }

    @Test
    fun `local triggers are recognised from the schedule or kind`() {
        val payload = NanobotJson.decodeFromString<AutomationsResponse>(
            """{"jobs":[{"id":"trg-1","name":"CI","kind":"local_trigger","schedule":{"kind":"local"}}]}""",
        )

        assertTrue(payload.jobs.single().isLocalTrigger)
    }

    @Test
    fun `automation chat picker decodes the binding revision`() {
        val payload = NanobotJson.decodeFromString<AutomationChatsResponse>(
            """
            {
              "revision": "rev-1",
              "current": {"id": "ws-1", "title": "Daily", "channel": "websocket"},
              "chats": [
                {"id": "ws-1", "title": "Daily", "channel": "websocket"},
                {"id": "ws-2", "title": "Old chat", "channel": "telegram", "unavailable": true}
              ]
            }
            """.trimIndent(),
        )

        assertEquals("rev-1", payload.revision)
        assertEquals("ws-1", payload.current?.id)
        assertEquals(2, payload.chats.size)
        assertTrue(payload.chats.last().unavailable)
    }

    @Test
    fun `automation updates omit untouched fields`() {
        val renamed = NanobotJson.encodeToJsonElement(
            AutomationUpdate(name = "Renamed"),
        ) as JsonObject
        assertEquals("Renamed", renamed["name"]?.toString()?.trim('"'))
        assertFalse(renamed.containsKey("message"))
        assertFalse(renamed.containsKey("schedule"))

        val scheduled = NanobotJson.encodeToJsonElement(
            AutomationUpdate(
                name = "Renamed",
                message = "Summarize",
                schedule = AutomationScheduleUpdate(
                    kind = "cron",
                    expr = "0 9 * * *",
                ),
            ),
        ) as JsonObject
        val schedule = scheduled["schedule"] as JsonObject
        assertEquals("cron", schedule["kind"]?.toString()?.trim('"'))
        assertEquals("0 9 * * *", schedule["expr"]?.toString()?.trim('"'))
        assertNull(schedule["tz"])
        assertNull(schedule["at_ms"])
        assertNull(schedule["every_ms"])
    }

    @Test
    fun `settings expose switchable model presets`() {
        val settings = NanobotJson.decodeFromString<GatewaySettings>(
            """
            {
              "agent": {"model": "openai/gpt-4.1", "model_preset": "fast", "context_window_tokens": 128000},
              "model_presets": [
                {
                  "name": "default", "label": "Default", "is_default": true,
                  "model": "openai/gpt-4.1", "context_window_tokens": 200000
                },
                {
                  "name": "fast", "active": true,
                  "model": "openai/gpt-4.1-mini", "provider": "openai",
                  "context_window_tokens": 128000, "reasoning_effort": "low"
                }
              ],
              "model_call_order": ["fast"]
            }
            """.trimIndent(),
        )

        assertEquals("fast", settings.agent.modelPreset)
        assertEquals(listOf("fast"), settings.modelCallOrder)
        val fast = settings.modelPresets.single { it.name == "fast" }
        assertEquals("openai/gpt-4.1-mini", fast.model)
        assertEquals(128000, fast.contextWindowTokens)
        assertEquals("low", fast.reasoningEffort)
        assertTrue(settings.modelPresets.single { it.name == "default" }.isDefault)
    }

    @Test
    fun `thread parser keeps raw body`() {
        val raw = """{"schemaVersion":1,"projection":"events","events":[]}"""
        val page = ThreadPageParser.parse(NanobotJson.parseToJsonElement(raw) as JsonObject, raw)
        assertEquals(raw, page.raw)
    }

    @Test
    fun `message carries workspace access mode`() {
        val frame = NanobotJson.parseToJsonElement(
            Outbound.message(
                chatId = "chat-1",
                content = "hello",
                turnId = "turn-1",
                workspaceScope = WorkspaceScope(
                    projectPath = "C:/workspace",
                    projectName = "workspace",
                    accessMode = "full",
                    restrictToWorkspace = false,
                ),
            ),
        ) as JsonObject
        val scope = frame["workspace_scope"] as JsonObject
        assertEquals("C:/workspace", scope["project_path"]?.toString()?.trim('"'))
        assertEquals("full", scope["access_mode"]?.toString()?.trim('"'))
    }

    @Test
    fun `sidebar state decodes both typed fields and the untouched document`() {
        val raw = """
            {
              "schema_version": 1,
              "pinned_keys": ["websocket:a"],
              "archived_keys": ["websocket:b"],
              "title_overrides": {"websocket:a": "Pinned chat"},
              "collapsed_groups": {"project:/work": true},
              "view": {"density": "compact", "show_archived": true, "sort": "manual"},
              "updated_at": "2026-11-01T08:39:14Z",
              "workbench": {"version": 1, "tabs": {"tab-1": {"paneKeys": ["websocket:a"]}}}
            }
        """.trimIndent()
        val state = SidebarState.fromJson(NanobotJson.parseToJsonElement(raw) as JsonObject)

        assertEquals(listOf("websocket:a"), state.pinnedKeys)
        assertEquals(listOf("websocket:b"), state.archivedKeys)
        assertEquals("Pinned chat", state.titleOverrides["websocket:a"])
        assertTrue(state.collapsedGroups.getValue("project:/work"))
        assertEquals("compact", state.view.density)
        assertTrue(state.view.showArchived)
        // The timestamp lets the client reject reads that predate a local edit.
        assertEquals("2026-11-01T08:39:14Z", state.updatedAt)
        // The full document survives so saving cannot erase the WebUI's workbench tabs.
        assertTrue(state.raw?.containsKey("workbench") == true)
    }
}
