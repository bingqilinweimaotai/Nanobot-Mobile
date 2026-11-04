package dev.nanobot.gateway

import dev.nanobot.protocol.AutomationScheduleUpdate
import dev.nanobot.protocol.AutomationUpdate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class NanobotGatewayIntegrationTest {
    @Test
    fun `bootstrap ready send and new chat`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val wsUrl = server.url("/").toString().replaceFirst("http://", "ws://")
            val requests = java.util.concurrent.CopyOnWriteArrayList<JsonObject>()
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "token": "ws-token",
                      "api_token": "api-token",
                      "ws_path": "/",
                      "ws_url": "$wsUrl",
                      "expires_in": 300,
                      "terminal": {"webui": {"capabilities": ["webui.core.v1"]}}
                    }
                    """.trimIndent(),
                ),
            )
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(
                                """{"event":"ready","chat_id":"chat-1","client_id":"test","upload":{"path":"/api/attachments","token":"upload-token"},"terminal":{"webui":{"capabilities":["webui.core.v1"]}}}""",
                            )
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val frame = NanobotJson.parseToJsonElement(text) as JsonObject
                            when (frame["type"]?.jsonPrimitive?.content) {
                                "message" -> {
                                    val turnId = frame["turn_id"]?.jsonPrimitive?.content.orEmpty()
                                    assertTrue(frame["webui"]?.jsonPrimitive?.content == "true")
                                    webSocket.send(
                                        """{"event":"message_accepted","chat_id":"chat-1","turn_id":"$turnId"}""",
                                    )
                                }

                                "new_chat" -> webSocket.send(
                                    """{"event":"attached","chat_id":"chat-2"}""",
                                )

                                "fork_chat" -> {
                                    assertTrue(frame["source_chat_id"]?.jsonPrimitive?.content == "chat-1")
                                    assertTrue(frame["before_user_index"]?.jsonPrimitive?.content == "2")
                                    webSocket.send(
                                        """{"event":"attached","chat_id":"chat-3"}""",
                                    )
                                }

                                "webui_request" -> {
                                    requests += frame
                                    val requestId = frame["request_id"]?.jsonPrimitive?.content.orEmpty()
                                    val result = if (frame["action"]?.jsonPrimitive?.content ==
                                        "workspace.resolve_project"
                                    ) {
                                        """{"path":"/home/me/projects","name":"projects"}"""
                                    } else {
                                        "{}"
                                    }
                                    webSocket.send(
                                        """{"event":"webui_response","request_id":"$requestId","ok":true,"result":$result}""",
                                    )
                                }
                            }
                        }
                    },
                ),
            )

            val gateway = NanobotGateway(
                GatewayConfig(
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    secret = "test-secret",
                    clientId = "test-client",
                ),
            )
            gateway.connect()
            val ready = withTimeout(5_000) {
                gateway.state.first { it is GatewayState.Ready }
            } as GatewayState.Ready
            assertEquals("chat-1", ready.chatId)
            assertTrue("webui.core.v1" in ready.capabilities)

            server.enqueue(
                MockResponse().setBody(
                    """{"sessions":[{"key":"websocket:chat-1","title":"First","preview":"hi"}]}""",
                ),
            )
            val sessions = gateway.listSessions()
            assertEquals(1, sessions.size)
            assertEquals("chat-1", sessions.first().chatId)

            server.enqueue(
                MockResponse().setResponseCode(201).setBody("""{"reference":"ref-1"}"""),
            )
            val attachment = gateway.uploadAttachment("note.txt", "text/plain", "hello".toByteArray())
            assertEquals("ref-1", attachment.reference)

            server.enqueue(
                MockResponse().setBody("""{"commands":[{"command":"/new","title":"New chat"}]}"""),
            )
            assertEquals("/new", gateway.fetchCommands().first().command)

            gateway.setSkillEnabled("demo", false)

            gateway.updateModelConfiguration(
                buildJsonObject {
                    put("name", "fast")
                    put("model", "claude-haiku-5-5")
                    put("provider", "anthropic")
                    put("max_tokens", 4096)
                },
            )
            val modelMutation = requests.first {
                it["action"]?.jsonPrimitive?.content == "settings.model_configuration.update"
            }
            val modelPayload = modelMutation["payload"]!!.jsonObject
            assertEquals("fast", modelPayload["name"]?.jsonPrimitive?.content)
            assertEquals("claude-haiku-5-5", modelPayload["model"]?.jsonPrimitive?.content)
            assertEquals("4096", modelPayload["max_tokens"]?.jsonPrimitive?.content)

            gateway.updateRuntimeConfig(
                buildJsonObject { put("agents.defaults.unified_session", true) },
            )
            val runtimeMutation = requests.first {
                it["action"]?.jsonPrimitive?.content == "settings.runtime_config.update"
            }
            val values = runtimeMutation["payload"]!!.jsonObject["values"]!!.jsonObject
            assertEquals(
                "true",
                values["agents.defaults.unified_session"]?.jsonPrimitive?.content,
            )

            gateway.updateModelCallOrder(listOf("fast", "default"))
            val orderMutation = requests.first {
                it["action"]?.jsonPrimitive?.content == "settings.model_call_order.update"
            }
            assertEquals(
                JsonArray(listOf(JsonPrimitive("fast"), JsonPrimitive("default"))),
                orderMutation["payload"]!!.jsonObject["order"],
            )

            gateway.updateAutomation(
                "job-1",
                AutomationUpdate(
                    name = "Weekly",
                    message = "Summarize the week",
                    schedule = AutomationScheduleUpdate(
                        kind = "cron",
                        expr = "0 9 * * 1",
                        tz = "Asia/Shanghai",
                    ),
                ),
            )
            val updateMutation = requests.first {
                it["action"]?.jsonPrimitive?.content == "automation.update"
            }
            val updatePayload = updateMutation["payload"]!!.jsonObject
            assertEquals("job-1", updatePayload["id"]?.jsonPrimitive?.content)
            val updateValues = updatePayload["values"]!!.jsonObject
            assertEquals("Weekly", updateValues["name"]?.jsonPrimitive?.content)
            assertEquals("Summarize the week", updateValues["message"]?.jsonPrimitive?.content)
            val updateSchedule = updateValues["schedule"]!!.jsonObject
            assertEquals("cron", updateSchedule["kind"]?.jsonPrimitive?.content)
            assertEquals("0 9 * * 1", updateSchedule["expr"]?.jsonPrimitive?.content)
            assertEquals("Asia/Shanghai", updateSchedule["tz"]?.jsonPrimitive?.content)

            gateway.changeAutomationChat("job-1", "ws-2", "rev-1", "Summarize the week")
            val chatMutation = requests.first {
                it["action"]?.jsonPrimitive?.content == "automation.change_chat"
            }
            val chatPayload = chatMutation["payload"]!!.jsonObject
            assertEquals("job-1", chatPayload["id"]?.jsonPrimitive?.content)
            val chatValues = chatPayload["values"]!!.jsonObject
            assertEquals("ws-2", chatValues["target_id"]?.jsonPrimitive?.content)
            assertEquals("rev-1", chatValues["revision"]?.jsonPrimitive?.content)
            assertEquals("Summarize the week", chatValues["message"]?.jsonPrimitive?.content)

            val turnId = gateway.sendMessage("chat-1", "hello")
            assertTrue(turnId.isNotBlank())

            assertEquals("chat-2", gateway.newChat())

            server.enqueue(
                MockResponse().setResponseCode(404).setBody("webui thread not found"),
            )
            assertTrue(gateway.fetchThread("chat-2").events.isEmpty())

            assertEquals("chat-3", gateway.forkChat("chat-1", 2, "Fork: First"))

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "host": {"name": "DESKTOP-1", "platform": "Linux"},
                      "default_scope": {"project_path": "/home/me"},
                      "favorite_projects": [{"name": "projects", "path": "/home/me/projects"}],
                      "recent_projects": [{"name": "notes", "path": "/home/me/notes"}],
                      "controls": {"can_change_project": true, "can_browse_directories": true}
                    }
                    """.trimIndent(),
                ),
            )
            val workspaces = gateway.fetchWorkspaces()
            assertEquals("DESKTOP-1", workspaces.host?.name)
            assertEquals("/home/me/projects", workspaces.favoriteProjects.single().path)
            assertEquals("/home/me/notes", workspaces.recentProjects.single().path)
            assertTrue(workspaces.controls.canBrowseDirectories)

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "path": "/home/me",
                      "parent": "/home",
                      "entries": [{"name": "projects", "path": "/home/me/projects"}],
                      "truncated": false,
                      "host": "DESKTOP-1",
                      "platform": "Linux"
                    }
                    """.trimIndent(),
                ),
            )
            val directories = gateway.fetchWorkspaceDirectories("/home/me", query = "proj")
            assertEquals("/home", directories.parent)
            assertEquals("projects", directories.entries.single().name)

            val resolved = gateway.resolveWorkspaceProject("/home/me/projects")
            assertEquals("/home/me/projects", resolved.path)
            assertEquals("projects", resolved.name)

            gateway.close()
        } finally {
            server.shutdown()
            server.close()
        }
    }

    @Test
    fun `sidebar updates round-trip the whole document and deletes report blockers`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val wsUrl = server.url("/").toString().replaceFirst("http://", "ws://")
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "token": "ws-token",
                      "api_token": "api-token",
                      "ws_path": "/",
                      "ws_url": "$wsUrl",
                      "expires_in": 300
                    }
                    """.trimIndent(),
                ),
            )
            var sentPinned: List<String> = emptyList()
            var sentWorkbench = false
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send(
                                """{"event":"ready","chat_id":"chat-1","client_id":"test"}""",
                            )
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val frame = NanobotJson.parseToJsonElement(text) as JsonObject
                            if (frame["type"]?.jsonPrimitive?.content != "webui_request") return
                            val requestId = frame["request_id"]?.jsonPrimitive?.content.orEmpty()
                            val action = frame["action"]?.jsonPrimitive?.content.orEmpty()
                            val payload = frame["payload"] as JsonObject
                            val result = when (action) {
                                "sidebar.update" -> {
                                    val state = payload["state"] as JsonObject
                                    sentPinned = (state["pinned_keys"] as JsonArray)
                                        .map { it.jsonPrimitive.content }
                                    sentWorkbench = state.containsKey("workbench")
                                    """{
                                      "schema_version":1,
                                      "pinned_keys":["websocket:chat-1"],
                                      "archived_keys":[],
                                      "title_overrides":{},
                                      "collapsed_groups":{},
                                      "view":{"density":"comfortable","show_archived":false,"sort":"updated_desc"},
                                      "workbench":{"version":1,"tabs":{}}
                                    }"""
                                }

                                "session.delete" -> {
                                    assertTrue(payload["key"]?.jsonPrimitive?.content == "websocket:b")
                                    """{"deleted":false,"blocked_by_automations":true,"automations":[{"id":"job-1","name":"Daily"}]}"""
                                }

                                else -> "{}"
                            }
                            webSocket.send(
                                """{"event":"webui_response","request_id":"$requestId","ok":true,"result":$result}""",
                            )
                        }
                    },
                ),
            )

            val gateway = NanobotGateway(
                GatewayConfig(
                    baseUrl = server.url("/").toString().trimEnd('/'),
                    secret = "",
                    clientId = "test-client",
                ),
            )
            gateway.connect()
            withTimeout(5_000) { gateway.state.first { it is GatewayState.Ready } }

            val document = NanobotJson.parseToJsonElement(
                """{
                  "schema_version":1,
                  "pinned_keys":[],
                  "archived_keys":[],
                  "title_overrides":{},
                  "collapsed_groups":{},
                  "view":{"density":"comfortable","show_archived":false,"sort":"updated_desc"},
                  "workbench":{"version":1,"tabs":{"tab-1":{"paneKeys":["websocket:chat-1"]}}}
                }""",
            ) as JsonObject
            val updated = document.withPinned("websocket:chat-1")
            val saved = gateway.updateSidebarState(updated)
            assertEquals(listOf("websocket:chat-1"), sentPinned)
            assertTrue(sentWorkbench)
            assertEquals(listOf("websocket:chat-1"), saved.pinnedKeys)
            assertTrue(saved.raw?.containsKey("workbench") == true)

            val blocked = gateway.deleteSession("websocket:b")
            assertTrue(blocked.blockedByAutomations)
            assertEquals("Daily", blocked.automations.single().name)
            gateway.close()
        } finally {
            server.shutdown()
            server.close()
        }
    }

    @Test
    fun `fetch media resolves urls through the current gateway`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val base = server.url("/").toString().trimEnd('/')
            server.enqueue(
                MockResponse().setBody("""{"token":"ws","api_token":"api","expires_in":300}"""),
            )
            server.enqueue(MockResponse().setBody("relative"))
            server.enqueue(MockResponse().setBody("absolute"))
            val gateway = NanobotGateway(GatewayConfig(base, "secret", "test"))

            assertEquals("relative", String(gateway.fetchMedia("/api/media/a?x=1")))
            assertEquals("absolute", String(gateway.fetchMedia("http://old-host:9999/api/media/b?y=2")))

            assertEquals("/webui/bootstrap", server.takeRequest().path)
            val first = server.takeRequest()
            assertEquals("/api/media/a?x=1", first.path)
            assertEquals("Bearer api", first.getHeader("Authorization"))
            val second = server.takeRequest()
            assertEquals("/api/media/b?y=2", second.path)
            assertEquals("Bearer api", second.getHeader("Authorization"))
            gateway.close()
        } finally {
            server.shutdown()
        }
    }

    private fun JsonObject.withPinned(key: String): JsonObject =
        JsonObject(this + ("pinned_keys" to JsonArray(listOf(JsonPrimitive(key)))))

    private companion object {
        val NanobotJson = dev.nanobot.protocol.NanobotJson
    }
}
