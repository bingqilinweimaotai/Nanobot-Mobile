package dev.nanobot.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Client -> server WebSocket frames used by this client. */
object Outbound {
    fun attach(chatId: String): String = buildJsonObject {
        put("type", "attach")
        put("chat_id", chatId)
    }.toString()

    fun newChat(): String = buildJsonObject {
        put("type", "new_chat")
    }.toString()

    fun forkChat(sourceChatId: String, beforeUserIndex: Int, title: String? = null): String =
        buildJsonObject {
            put("type", "fork_chat")
            put("source_chat_id", sourceChatId)
            put("before_user_index", beforeUserIndex)
            title?.trim()?.takeIf { it.isNotEmpty() }?.let { put("title", it) }
        }.toString()

    fun message(
        chatId: String,
        content: String,
        turnId: String,
        media: List<AttachmentReference> = emptyList(),
        cliApps: List<String> = emptyList(),
        mcpPresets: List<String> = emptyList(),
        workspaceScope: WorkspaceScope? = null,
    ): String = buildJsonObject {
        put("type", "message")
        put("chat_id", chatId)
        put("content", content)
        put("turn_id", turnId)
        put("webui", true)
        if (media.isNotEmpty()) {
            put(
                "media",
                buildJsonArray {
                    media.forEach { attachment ->
                        add(
                            buildJsonObject {
                                put("reference", attachment.reference)
                                attachment.name?.let { put("name", it) }
                            },
                        )
                    }
                },
            )
        }
        if (cliApps.isNotEmpty()) {
            put(
                "cli_apps",
                buildJsonArray {
                    cliApps.forEach { name -> add(buildJsonObject { put("name", name) }) }
                },
            )
        }
        if (mcpPresets.isNotEmpty()) {
            put(
                "mcp_presets",
                buildJsonArray {
                    mcpPresets.forEach { name -> add(buildJsonObject { put("name", name) }) }
                },
            )
        }
        if (workspaceScope != null) {
            put(
                "workspace_scope",
                buildJsonObject {
                    put("project_path", workspaceScope.projectPath)
                    put("access_mode", workspaceScope.accessMode)
                    workspaceScope.projectName?.let { put("project_name", it) }
                    workspaceScope.restrictToWorkspace?.let { put("restrict_to_workspace", it) }
                },
            )
        }
    }.toString()

    fun webUiRequest(
        requestId: String,
        action: String,
        payload: JsonObject = JsonObject(emptyMap()),
    ): String = buildJsonObject {
        put("type", "webui_request")
        put("request_id", requestId)
        put("action", action)
        put("payload", payload)
    }.toString()
}
