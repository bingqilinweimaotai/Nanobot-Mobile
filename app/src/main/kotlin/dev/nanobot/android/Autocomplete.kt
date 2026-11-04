package dev.nanobot.android

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

enum class AutocompleteTrigger { Command, Skill, Mention }

data class AutocompleteToken(
    val trigger: AutocompleteTrigger,
    val start: Int,
    val end: Int,
    val query: String,
)

/**
 * Finds the `/`, `$`, or `@` token under the cursor. The trigger must start the message or follow
 * whitespace, and the query cannot contain another trigger or whitespace.
 */
fun findAutocompleteToken(text: String, cursor: Int = text.length): AutocompleteToken? {
    val clamped = cursor.coerceIn(0, text.length)
    val before = text.substring(0, clamped)
    val triggers = listOf('/' to AutocompleteTrigger.Command, '$' to AutocompleteTrigger.Skill, '@' to AutocompleteTrigger.Mention)
    var best: AutocompleteToken? = null

    for ((marker, trigger) in triggers) {
        var index = before.lastIndexOf(marker)
        while (index >= 0) {
            val previous = if (index > 0) text[index - 1] else null
            if (previous == null || previous.isWhitespace()) {
                val query = before.substring(index + 1)
                val valid = query.none { it.isWhitespace() || it == '/' || it == '$' || it == '@' }
                if (valid) {
                    if (best == null || index > best.start) {
                        best = AutocompleteToken(trigger, index, clamped, query)
                    }
                    break
                }
            }
            index = if (index == 0) -1 else before.lastIndexOf(marker, index - 1)
        }
    }
    return best
}

fun replaceAutocompleteToken(text: String, token: AutocompleteToken, replacement: String): String =
    text.substring(0, token.start) + replacement + text.substring(token.end)

/**
 * Aligns the text field state with the draft after an external edit (e.g. picking a suggestion).
 * The caret moves to the end of the new text, so it no longer stays after the `/`, `$`, or `@`
 * trigger while the replacement is inserted around it.
 */
fun syncDraftField(current: TextFieldValue, draft: String): TextFieldValue =
    if (current.text == draft) current else TextFieldValue(draft, selection = TextRange(draft.length))
