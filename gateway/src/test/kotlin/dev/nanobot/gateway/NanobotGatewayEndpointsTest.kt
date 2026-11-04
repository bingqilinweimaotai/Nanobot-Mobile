package dev.nanobot.gateway

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Covers the REST and mutation endpoints the app uses for settings, skills, MCP, automations, and
 * chat attach. The existing integration test drives the conversation flow; these are the other
 * half of the gateway surface, where a wrong action name or renamed response field only shows up
 * at runtime.
 */
class NanobotGatewayEndpointsTest {
    @Test
    fun `settings skills and cli apps decode their payloads`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val requests = CopyOnWriteArrayList<JsonObject>()
            server.enqueue(bootstrap(server))
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    requestListener(requests) { action, _ ->
                        when (action) {
                            "settings.agent.update" -> SETTINGS
                            else -> "{}"
                        }
                    },
                ),
            )
            server.enqueue(MockResponse().setBody(SKILLS))
            server.enqueue(MockResponse().setBody(SKILL_DETAIL))
            server.enqueue(MockResponse().setBody(CLI_APPS))
            server.enqueue(MockResponse().setBody(SETTINGS))

            val gateway = NanobotGateway(GatewayConfig(server.url("/").toString().trimEnd('/'), "secret", "test"))
            gateway.connect()
            awaitReady(gateway)
            val boot = take(server)
            assertEquals("/webui/bootstrap", boot.path)
            assertEquals("secret", boot.getHeader("X-Nanobot-Auth"))
            assertTrue(take(server).path.orEmpty().startsWith("/?token=ws"))

            // Upload limits come from bootstrap, not a client constant.
            assertEquals(8L * 1024 * 1024, gateway.maxAttachmentBytes("image/png"))
            assertEquals(20L * 1024 * 1024, gateway.maxAttachmentBytes("video/mp4"))

            val skills = gateway.fetchSkills()
            assertEquals("/api/webui/skills", take(server).path)
            assertEquals("git", skills.single().name)
            assertTrue(skills.single().deletable)
            assertEquals("missing bin git", skills.single().unavailableReason)

            val detail = gateway.fetchSkillDetail("git helpers")
            assertEquals("/api/webui/skills/git%20helpers", take(server).path)
            assertTrue(detail.requirements.hasMissing)
            assertEquals("brew install git", detail.installOptions.single().command)
            assertEquals("# Git", detail.rawMarkdown)

            val apps = gateway.fetchCliApps()
            assertEquals("/api/settings/cli-apps?installed_only=1", take(server).path)
            assertEquals("Codex", apps.single().displayName)
            assertTrue(apps.single().installed)

            val settings = gateway.fetchSettings()
            assertEquals("/api/settings", take(server).path)
            assertEquals("fast", settings.agent.modelPreset)
            assertEquals(200_000, settings.agent.contextWindowTokens)
            assertEquals(listOf("fast"), settings.modelCallOrder)
            assertEquals("fast", settings.modelPresets.single().name)
            assertEquals("Anthropic", settings.providers.single().label)
            assertEquals("0.9.0", settings.version?.current)

            gateway.updateAgentSettings(buildJsonObject { put("model", "claude-sonnet-5-5") })
            val update = requests.single { it.action() == "settings.agent.update" }
            assertEquals("claude-sonnet-5-5", update.payload()["model"]?.jsonPrimitive?.content)

            gateway.deleteSkill("git")
            val delete = requests.single { it.action() == "skill.delete" }
            assertEquals("git", delete.payload()["name"]?.jsonPrimitive?.content)

            gateway.close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `mcp actions and oauth flows use the documented actions`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val requests = CopyOnWriteArrayList<JsonObject>()
            server.enqueue(bootstrap(server))
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    requestListener(requests) { action, _ ->
                        when {
                            action == "settings.mcp.oauth_start" -> OAUTH
                            action == "settings.mcp.oauth_complete" -> OAUTH
                            action == "settings.mcp.oauth_cancel" -> OAUTH
                            action.startsWith("settings.mcp.") -> MCP_PRESETS
                            else -> "{}"
                        }
                    },
                ),
            )
            server.enqueue(MockResponse().setBody(MCP_PRESETS))
            server.enqueue(MockResponse().setBody(OAUTH))

            val gateway = NanobotGateway(GatewayConfig(server.url("/").toString().trimEnd('/'), "", "test"))
            gateway.connect()
            awaitReady(gateway)
            take(server)
            take(server)

            val presets = gateway.fetchMcpPresets()
            assertEquals("/api/settings/mcp-presets", take(server).path)
            val notion = presets.presets.single()
            assertEquals("notion", notion.name)
            assertTrue(notion.needsConnectionForm)
            assertEquals("token", notion.requiredFields.single().name)
            assertEquals(listOf("search"), notion.enabledTools)
            assertTrue(presets.proxyConfigured)
            assertEquals("reloaded", presets.hotReload?.message)

            gateway.mcpPresetAction("install", "notion", mapOf("token" to "secret", "empty" to ""))
            val install = requests.single { it.action() == "settings.mcp.install" }.payload()
            assertEquals("notion", install["name"]?.jsonPrimitive?.content)
            assertEquals("secret", install["token"]?.jsonPrimitive?.content)
            assertTrue("empty" !in install, "blank credential values must be omitted")

            gateway.updateMcpTools("notion", listOf("search", "fetch"))
            val tools = requests.single { it.action() == "settings.mcp.tools" }.payload()
            assertEquals(
                listOf("search", "fetch"),
                tools["enabled_tools"]?.jsonArray?.map { it.jsonPrimitive.content },
            )

            val started = gateway.startMcpOAuth("notion", reset = true)
            val start = requests.single { it.action() == "settings.mcp.oauth_start" }.payload()
            assertEquals("notion", start["name"]?.jsonPrimitive?.content)
            assertEquals(true, start["reset"]?.jsonPrimitive?.content?.toBoolean())
            assertEquals("flow-1", started.flowId)
            assertEquals("https://auth.example/1", started.authorizationUrl)

            val status = gateway.fetchMcpOAuthStatus("flow-1")
            assertEquals("/api/settings/mcp-oauth/status?flow_id=flow-1", take(server).path)
            assertEquals("pending", status.status)

            gateway.completeMcpOAuth("flow-1", "nanobot://oauth?code=abc")
            val complete = requests.single { it.action() == "settings.mcp.oauth_complete" }.payload()
            assertEquals("flow-1", complete["flow_id"]?.jsonPrimitive?.content)
            assertEquals("nanobot://oauth?code=abc", complete["callback_url"]?.jsonPrimitive?.content)

            gateway.cancelMcpOAuth("flow-1")
            val cancel = requests.single { it.action() == "settings.mcp.oauth_cancel" }.payload()
            assertEquals("flow-1", cancel["flow_id"]?.jsonPrimitive?.content)

            gateway.close()
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `automation actions and chat attach use the documented frames`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val requests = CopyOnWriteArrayList<JsonObject>()
            val stopped = CompletableDeferred<JsonObject>()
            server.enqueue(bootstrap(server))
            server.enqueue(
                MockResponse().withWebSocketUpgrade(
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            webSocket.send("""{"event":"ready","chat_id":"chat-1","client_id":"test"}""")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val frame = NanobotJson.parseToJsonElement(text) as JsonObject
                            when (frame["type"]?.jsonPrimitive?.content) {
                                "attach" -> webSocket.send(
                                    """{"event":"attached","chat_id":"${frame["chat_id"]?.jsonPrimitive?.content}"}""",
                                )

                                "message" -> stopped.complete(frame)

                                "webui_request" -> {
                                    requests += frame
                                    val id = frame["request_id"]!!.jsonPrimitive.content
                                    webSocket.send(
                                        """{"event":"webui_response","request_id":"$id","ok":true,"result":{}}""",
                                    )
                                }
                            }
                        }
                    },
                ),
            )
            server.enqueue(MockResponse().setBody(AUTOMATIONS))
            server.enqueue(MockResponse().setBody(AUTOMATION_CHATS))

            val gateway = NanobotGateway(GatewayConfig(server.url("/").toString().trimEnd('/'), "", "test"))
            gateway.connect()
            awaitReady(gateway)
            take(server)
            take(server)

            val jobs = gateway.fetchAutomations()
            assertEquals("/api/webui/automations", take(server).path)
            val job = jobs.single()
            assertTrue(job.isLocalTrigger)
            assertEquals("0 9 * * *", job.schedule.expr)
            assertEquals("Asia/Shanghai", job.schedule.tz)
            assertEquals(123L, job.state.nextRunAtMs)
            assertEquals("rev-1", job.chatBindingRevision)

            val chats = gateway.fetchAutomationChats("job-1")
            assertEquals("/api/webui/automations/chats?id=job-1", take(server).path)
            assertEquals("rev-7", chats.revision)
            assertEquals("chat-1", chats.current?.id)
            assertTrue(chats.chats.single { it.id == "chat-3" }.unavailable)

            gateway.runAutomation("job-1")
            gateway.setAutomationEnabled("job-1", true)
            gateway.setAutomationEnabled("job-1", false)
            gateway.deleteAutomation("job-1")
            assertEquals(
                listOf(
                    "automation.run",
                    "automation.enable",
                    "automation.disable",
                    "automation.delete",
                ),
                requests.map { it.action() },
            )
            assertTrue(requests.all { it.payload()["id"]?.jsonPrimitive?.content == "job-1" })

            gateway.attach("chat-2")
            val state = gateway.state.first { it is GatewayState.Ready } as GatewayState.Ready
            assertEquals("chat-2", state.chatId)

            gateway.sendStop("chat-2", "turn-42")
            val stop = withTimeout(5_000) { stopped.await() }
            assertEquals("/stop", stop["content"]?.jsonPrimitive?.content)
            assertEquals("turn-42", stop["turn_id"]?.jsonPrimitive?.content)
            assertEquals("chat-2", stop["chat_id"]?.jsonPrimitive?.content)

            gateway.close()
        } finally {
            server.shutdown()
        }
    }

    private suspend fun awaitReady(gateway: NanobotGateway) {
        withTimeout(5_000) { gateway.state.first { it is GatewayState.Ready } }
    }

    private fun bootstrap(server: MockWebServer): MockResponse {
        val wsUrl = server.url("/").toString().replaceFirst("http://", "ws://")
        return MockResponse().setBody(
            """{"token":"ws","api_token":"api","ws_url":"$wsUrl","expires_in":300,
               "limits":{"attachments":{"max_count":4,"max_file_bytes":8388608,
                 "max_total_bytes":25165824}}}""",
        )
    }

    private fun requestListener(
        requests: MutableList<JsonObject>,
        result: (String, JsonObject) -> String,
    ) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send("""{"event":"ready","chat_id":"chat-1","client_id":"test"}""")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = NanobotJson.parseToJsonElement(text) as JsonObject
            if (frame["type"]?.jsonPrimitive?.content != "webui_request") return
            requests += frame
            val id = frame["request_id"]!!.jsonPrimitive.content
            val reply = result(frame.action(), frame.payload())
            webSocket.send("""{"event":"webui_response","request_id":"$id","ok":true,"result":$reply}""")
        }
    }

    private fun take(server: MockWebServer) =
        server.takeRequest(5, TimeUnit.SECONDS) ?: error("the gateway made no request")

    private fun JsonObject.action(): String = this["action"]!!.jsonPrimitive.content

    private fun JsonObject.payload(): JsonObject = this["payload"]!!.jsonObject

    private companion object {
        val NanobotJson = dev.nanobot.protocol.NanobotJson

        const val SKILLS = """
            {"skills":[{"name":"git","description":"Git helpers","source":"workspace",
              "enabled":true,"deletable":true,"available":false,
              "unavailable_reason":"missing bin git"}]}
        """

        const val SKILL_DETAIL = """
            {"name":"git","description":"Git helpers","enabled":true,"deletable":true,
             "requirements":{"bins":["git"],"env":[],"missing_bins":["git"],"missing_env":[]},
             "install_options":[{"id":"brew","kind":"command","label":"brew","command":"brew install git"}],
             "raw_markdown":"# Git"}
        """

        const val CLI_APPS = """
            {"apps":[{"name":"codex","display_name":"Codex","category":"coding",
              "installed":true,"available":true,"status":"ready"}]}
        """

        const val SETTINGS = """
            {"agent":{"model":"claude-sonnet-5-5","provider":"anthropic","model_preset":"fast",
              "max_tokens":4096,"context_window_tokens":200000,"timezone":"Asia/Shanghai"},
             "runtime":{"unified_session":true},
             "model_presets":[{"name":"fast","active":true,"model":"claude-haiku-5-5",
               "context_window_tokens":200000}],
             "model_call_order":["fast"],
             "providers":[{"name":"anthropic","label":"Anthropic","configured":true}],
             "version":{"current":"0.9.0"}}
        """

        const val MCP_PRESETS = """
            {"presets":[{"name":"notion","display_name":"Notion","transport":"streamableHttp",
              "installed":true,"configured":false,"status":"configured",
              "required_fields":[{"name":"token","label":"API token","secret":true,
                "required":true,"configured":false}],
              "tool_names":["search"],"enabled_tools":["search"],"tool_count":1}],
             "proxy_configured":true,"installed_count":1,"requires_restart":false,
             "hot_reload":{"ok":true,"message":"reloaded"}}
        """

        const val OAUTH = """
            {"flow_id":"flow-1","name":"notion","status":"pending","expires_in":300,
             "authorization_url":"https://auth.example/1"}
        """

        const val AUTOMATIONS = """
            {"jobs":[{"id":"job-1","name":"Daily","enabled":true,"kind":"local_trigger",
              "schedule":{"kind":"cron","expr":"0 9 * * *","tz":"Asia/Shanghai"},
              "payload":{"message":"Summarize","kind":"text"},
              "state":{"next_run_at_ms":123,"last_status":"ok"},
              "chat_binding_revision":"rev-1"}]}
        """

        const val AUTOMATION_CHATS = """
            {"revision":"rev-7","current":{"id":"chat-1","title":"Main","channel":"websocket"},
             "chats":[{"id":"chat-2","title":"Second","channel":"websocket"},
               {"id":"chat-3","title":"Gone","channel":"websocket","unavailable":true}]}
        """
    }
}
