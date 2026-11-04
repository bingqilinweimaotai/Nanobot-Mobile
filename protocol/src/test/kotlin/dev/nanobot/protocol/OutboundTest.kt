package dev.nanobot.protocol

import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class OutboundTest {
    @Test
    fun `message frame carries chat content and turn id`() {
        val json = NanobotJson.parseToJsonElement(Outbound.message("chat-1", "hello", "turn-1")) as JsonObject
        assertEquals(
            mapOf(
                "type" to "message",
                "chat_id" to "chat-1",
                "content" to "hello",
                "turn_id" to "turn-1",
                "webui" to "true",
            ),
            json.mapValues { it.value.toString().trim('"') },
        )
    }

    @Test
    fun `attach and new chat use typed envelopes`() {
        assertEquals(
            "attach",
            (NanobotJson.parseToJsonElement(Outbound.attach("chat-1")) as JsonObject)["type"]?.toString()?.trim('"'),
        )
        assertEquals(
            "new_chat",
            (NanobotJson.parseToJsonElement(Outbound.newChat()) as JsonObject)["type"]?.toString()?.trim('"'),
        )
    }

    @Test
    fun `fork frame carries source index and optional title`() {
        val json = NanobotJson.parseToJsonElement(
            Outbound.forkChat("chat-1", 2, "Fork: First"),
        ) as JsonObject
        assertEquals(
            mapOf(
                "type" to "fork_chat",
                "source_chat_id" to "chat-1",
                "before_user_index" to "2",
                "title" to "Fork: First",
            ),
            json.mapValues { it.value.toString().trim('"') },
        )
    }
}
