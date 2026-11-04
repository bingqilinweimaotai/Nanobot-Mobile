package dev.nanobot.protocol

import kotlinx.serialization.json.JsonObject

/** Parses the `projection: "events"` response of `GET /api/sessions/{key}/webui-thread`. */
object ThreadPageParser {
    fun parse(root: JsonObject, raw: String? = null): ThreadPage {
        require(root.intOrNull("schemaVersion") != null) { "webui-thread response is missing schemaVersion" }
        require(root.textOrNull("projection") == "events") { "unsupported webui-thread projection" }
        val events = root.arrayOrNull("events")
            ?.mapNotNull { element ->
                (element as? JsonObject)?.let(InboundEvents::decode)
            }
            .orEmpty()
        val page = root.objectOrNull("page")
        return ThreadPage(
            events = events,
            hasMoreBefore = page?.booleanOrNull("has_more_before") ?: false,
            beforeCursor = page?.textOrNull("before_cursor"),
            raw = raw,
            activeTurnId = root.textOrNull("active_turn_id")?.takeIf { it.isNotBlank() },
            hasPendingToolCalls = root.booleanOrNull("has_pending_tool_calls") ?: false,
        )
    }
}
