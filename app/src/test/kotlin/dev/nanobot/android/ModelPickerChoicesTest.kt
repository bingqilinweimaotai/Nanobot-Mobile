package dev.nanobot.android

import dev.nanobot.protocol.GatewayModelPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelPickerChoicesTest {
    @Test
    fun `picker hides the built-in default preset`() {
        val presets = listOf(
            GatewayModelPreset(name = "default", isDefault = true, model = "anthropic/claude-opus-4-5"),
            GatewayModelPreset(name = "magpie", model = "auto/dfmodel"),
            GatewayModelPreset(name = "luna", model = "openai-codex/gpt-6-luna"),
        )

        assertEquals(
            listOf("magpie", "luna"),
            modelPickerChoices(presets).map { it.name },
        )
    }

    @Test
    fun `picker hides a default-named preset even without the flag`() {
        val presets = listOf(
            GatewayModelPreset(name = "default", model = "legacy/model"),
            GatewayModelPreset(name = "fast", model = "openai/gpt-4.1"),
        )

        assertEquals(listOf("fast"), modelPickerChoices(presets).map { it.name })
    }
}
