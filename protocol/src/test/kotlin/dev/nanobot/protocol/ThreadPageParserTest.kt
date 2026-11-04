package dev.nanobot.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ThreadPageParserTest {
    @Test
    fun `parses projected events and pagination`() {
        val page = ThreadPageParser.parse(
            NanobotJson.parseToJsonElement(
                """
                {
                  "schemaVersion": 1,
                  "projection": "events",
                  "events": [
                    {"event":"user_message","chat_id":"c","text":"hi","turn_id":"t1","starts_turn":true},
                    {"event":"message","chat_id":"c","text":"hello","turn_id":"t1"},
                    {"event":"turn_end","chat_id":"c","turn_id":"t1","usage":{"total_tokens":3}}
                  ],
                  "page": {"has_more_before": true, "before_cursor": "cursor-1"},
                  "active_turn_id": "t2",
                  "has_pending_tool_calls": true
                }
                """.trimIndent(),
            ).let { assertIs<kotlinx.serialization.json.JsonObject>(it) },
        )

        assertEquals(3, page.events.size)
        assertIs<InboundEvent.UserMessage>(page.events[0])
        assertIs<InboundEvent.Message>(page.events[1])
        assertIs<InboundEvent.TurnEnd>(page.events[2])
        assertTrue(page.hasMoreBefore)
        assertEquals("cursor-1", page.beforeCursor)
        assertEquals("t2", page.activeTurnId)
        assertTrue(page.hasPendingToolCalls)
    }

    @Test
    fun `a settled thread has no active turn`() {
        val page = ThreadPageParser.parse(
            NanobotJson.parseToJsonElement(
                """{"schemaVersion":3,"projection":"events","events":[]}""",
            ).let { assertIs<kotlinx.serialization.json.JsonObject>(it) },
        )

        assertEquals(null, page.activeTurnId)
        assertTrue(!page.hasPendingToolCalls)
    }
}
