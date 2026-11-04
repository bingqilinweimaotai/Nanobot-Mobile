package dev.nanobot.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InboundEventsTest {
    @Test
    fun `ready carries chat id upload and capabilities`() {
        val event = InboundEvents.decode(
            """
            {
              "event": "ready",
              "chat_id": "chat-1",
              "client_id": "android-1",
              "upload": {"path": "/api/attachments", "token": "upload-token"},
              "terminal": {"webui": {"capabilities": ["webui.core.v1", "webui.attachments.binary.v1"]}}
            }
            """.trimIndent(),
        )

        val ready = assertIs<InboundEvent.Ready>(event)
        assertEquals("chat-1", ready.chatId)
        assertEquals("android-1", ready.clientId)
        assertEquals("/api/attachments", ready.upload?.path)
        assertEquals("upload-token", ready.upload?.token)
        assertTrue("webui.attachments.binary.v1" in ready.capabilities)
    }

    @Test
    fun `delta and message acceptance keep stream and turn ids`() {
        val delta = InboundEvents.decode(
            """{"event":"delta","chat_id":"c","text":"Hello","stream_id":"s1"}""",
        )
        assertEquals(InboundEvent.Delta("c", "Hello", "s1"), delta)

        val accepted = InboundEvents.decode(
            """{"event":"message_accepted","chat_id":"c","turn_id":"t1"}""",
        )
        assertEquals(InboundEvent.MessageAccepted("c", "t1"), accepted)
    }

    @Test
    fun `turn end decodes usage`() {
        val event = InboundEvents.decode(
            """
            {
              "event": "turn_end",
              "chat_id": "c",
              "turn_id": "t1",
              "latency_ms": 1200,
              "outcome": "completed",
              "usage": {"prompt_tokens": 10, "completion_tokens": 20, "total_tokens": 30}
            }
            """.trimIndent(),
        )

        val end = assertIs<InboundEvent.TurnEnd>(event)
        assertEquals("t1", end.turnId)
        assertEquals(30, end.usage?.totalTokens)
        assertEquals("completed", end.outcome)
    }

    @Test
    fun `turn end carries the context window the composer ring needs`() {
        val event = InboundEvents.decode(
            """
            {
              "event": "turn_end",
              "chat_id": "c",
              "turn_id": "t1",
              "context_window_tokens": 200000,
              "usage": {"context_tokens": 42000}
            }
            """.trimIndent(),
        )

        val end = assertIs<InboundEvent.TurnEnd>(event)
        assertEquals(200000, end.contextWindowTokens)
        assertEquals(42000, end.usage?.contextTokens)
    }

    @Test
    fun `turn model update carries the active preset and window`() {
        val event = InboundEvents.decode(
            """
            {
              "event": "turn_model_updated",
              "chat_id": "c",
              "model_name": "openai/gpt-4.1-mini",
              "model_preset": "fast",
              "context_window_tokens": 128000
            }
            """.trimIndent(),
        )

        assertEquals(
            InboundEvent.TurnModelUpdated(
                chatId = "c",
                modelName = "openai/gpt-4.1-mini",
                modelPreset = "fast",
                contextWindowTokens = 128000,
            ),
            event,
        )
    }

    @Test
    fun `runtime model update carries the new gateway default`() {
        val event = InboundEvents.decode(
            """
            {
              "event": "runtime_model_updated",
              "model_name": "openai/gpt-4.1-mini",
              "model_preset": "fast"
            }
            """.trimIndent(),
        )

        assertEquals(
            InboundEvent.RuntimeModelUpdated(
                modelName = "openai/gpt-4.1-mini",
                modelPreset = "fast",
            ),
            event,
        )
    }

    @Test
    fun `runtime model update without a preset falls back to the default`() {
        val event = InboundEvents.decode(
            """{"event":"runtime_model_updated","model_name":"openai/gpt-4.1-mini"}""",
        )

        assertEquals(
            InboundEvent.RuntimeModelUpdated(
                modelName = "openai/gpt-4.1-mini",
                modelPreset = null,
            ),
            event,
        )
    }

    @Test
    fun `unknown events stay inspectable instead of failing`() {
        val event = InboundEvents.decode("""{"event":"future_event","chat_id":"c"}""")
        val unknown = assertIs<InboundEvent.Unknown>(event)
        assertEquals("future_event", unknown.event)
        assertEquals("c", unknown.raw["chat_id"]?.toString()?.trim('"'))
    }

    @Test
    fun `invalid json becomes an unknown event`() {
        assertIs<InboundEvent.Unknown>(InboundEvents.decode("not json"))
    }
}
