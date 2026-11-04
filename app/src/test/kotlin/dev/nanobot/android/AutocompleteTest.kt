package dev.nanobot.android

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AutocompleteTest {
    @Test
    fun `slash command at start is detected and replaced`() {
        val token = findAutocompleteToken("/ne")!!
        assertEquals(AutocompleteTrigger.Command, token.trigger)
        assertEquals(0, token.start)
        assertEquals("ne", token.query)
        assertEquals("/new ", replaceAutocompleteToken("/ne", token, "/new "))
    }

    @Test
    fun `inline mention after whitespace is detected`() {
        val text = "hello @pla"
        val token = findAutocompleteToken(text)!!
        assertEquals(AutocompleteTrigger.Mention, token.trigger)
        assertEquals(6, token.start)
        assertEquals("pla", token.query)
        assertEquals("hello ", replaceAutocompleteToken(text, token, ""))
    }

    @Test
    fun `skill trigger is detected`() {
        val token = findAutocompleteToken("\$ai")!!
        assertEquals(AutocompleteTrigger.Skill, token.trigger)
        assertEquals("ai", token.query)
    }

    @Test
    fun `slash inside a word is not a command`() {
        assertNull(findAutocompleteToken("hello/world"))
    }

    @Test
    fun `completed command with a space closes the menu`() {
        assertNull(findAutocompleteToken("/new "))
    }

    @Test
    fun `picking a suggestion moves the caret past the replacement`() {
        val before = TextFieldValue("/ne", selection = TextRange(1))
        val after = syncDraftField(before, "/new ")
        assertEquals("/new ", after.text)
        assertEquals(TextRange(5), after.selection)
    }

    @Test
    fun `typing keeps the caret where the user put it`() {
        val current = TextFieldValue("hello", selection = TextRange(2))
        assertSame(current, syncDraftField(current, "hello"))
    }
}
