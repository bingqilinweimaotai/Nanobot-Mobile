package dev.nanobot.android

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownAndForkTest {
    @Test
    fun `markdown distinguishes headings lists code quotes and rules`() {
        val blocks = parseMarkdownBlocks(
            """
            ## 配置

            - **modelPreset**: `magpie`

            > runtime 262144

            ```
            val enabled = true
            ```

            ---
            """.trimIndent(),
        )

        assertIs<MarkdownBlock.Heading>(blocks[0])
        assertEquals(2, (blocks[0] as MarkdownBlock.Heading).level)
        assertIs<MarkdownBlock.ListItem>(blocks[1])
        assertEquals("•", (blocks[1] as MarkdownBlock.ListItem).marker)
        assertIs<MarkdownBlock.Quote>(blocks[2])
        assertIs<MarkdownBlock.Code>(blocks[3])
        assertEquals("val enabled = true", (blocks[3] as MarkdownBlock.Code).code)
        assertIs<MarkdownBlock.Rule>(blocks[4])
    }

    @Test
    fun `inline markdown applies bold and code spans`() {
        val text = inlineMarkdown("**bold** and `code`", Color.Blue, Color.LightGray)

        assertEquals("bold and code", text.text)
        assertTrue(text.spanStyles.any { it.item.fontWeight == FontWeight.Bold })
        assertTrue(text.spanStyles.any { it.item.fontFamily == FontFamily.Monospace })
    }

    @Test
    fun `fork accepts only the final assistant answer before the next user turn`() {
        val messages = listOf(
            message("u1", MessageRole.User, "first"),
            message("a1", MessageRole.Assistant, "old answer"),
            message("a2", MessageRole.Assistant, "final answer"),
            message("u2", MessageRole.User, "second"),
            message("a3", MessageRole.Assistant, "second answer"),
        )

        assertNull(forkUserIndex(messages, 1))
        assertEquals(1, forkUserIndex(messages, 2))
        assertEquals(2, forkUserIndex(messages, 4))
    }

    @Test
    fun `reasoning messages do not block or offer forks`() {
        val messages = listOf(
            message("u1", MessageRole.User, "question"),
            message("r1", MessageRole.Assistant, "thinking", reasoning = true),
            message("a1", MessageRole.Assistant, "answer"),
        )

        assertNull(forkUserIndex(messages, 1))
        assertEquals(1, forkUserIndex(messages, 2))
    }

    @Test
    fun `markdown parses a gfm table with alignment`() {
        val blocks = parseMarkdownBlocks(
            """
            | Name | Value |
            | :--- | ---: |
            | alpha | 1 |
            | `beta` | 2 |
            """.trimIndent(),
        )

        val table = assertIs<MarkdownBlock.Table>(blocks.single())
        assertEquals(listOf("Name", "Value"), table.headers)
        assertEquals(listOf(TableAlign.Left, TableAlign.Right), table.aligns)
        assertEquals(listOf("alpha", "1"), table.rows[0])
        assertEquals(listOf("`beta`", "2"), table.rows[1])
    }

    @Test
    fun `table rows without outer pipes still parse`() {
        val blocks = parseMarkdownBlocks(
            """
            Name | Value
            --- | :---:
            a | b
            """.trimIndent(),
        )

        val table = assertIs<MarkdownBlock.Table>(blocks.single())
        assertEquals(listOf("Name", "Value"), table.headers)
        assertEquals(listOf(TableAlign.Left, TableAlign.Center), table.aligns)
        assertEquals(listOf("a", "b"), table.rows.single())
    }

    @Test
    fun `pipe text without a delimiter row stays a paragraph`() {
        val blocks = parseMarkdownBlocks("a | b")

        assertIs<MarkdownBlock.Paragraph>(blocks.single())
    }

    @Test
    fun `stopped partial answers do not offer forks`() {
        val messages = listOf(
            ChatMessage("u1", MessageRole.User, "question"),
            ChatMessage("a1", MessageRole.Assistant, "half an answer", stopped = true),
        )

        assertNull(forkUserIndex(messages, 1))
    }

    private fun message(
        id: String,
        role: MessageRole,
        text: String,
        reasoning: Boolean = false,
    ) = ChatMessage(id = id, role = role, text = text, reasoning = reasoning)
}
