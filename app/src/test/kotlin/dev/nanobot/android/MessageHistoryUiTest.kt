package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageHistoryUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val messages = listOf(
        ChatMessage("u1", MessageRole.User, "first question"),
        ChatMessage("a1", MessageRole.Assistant, "assistant answer"),
        ChatMessage("u2", MessageRole.User, "second question"),
    )

    @Test
    fun `history lists only the user's messages`() {
        composeRule.setContent {
            MaterialTheme {
                MessageHistoryContent(messages = messages, onSelect = {})
            }
        }

        composeRule.onNodeWithText("My messages").assertExists()
        composeRule.onNodeWithText("first question").assertExists()
        composeRule.onNodeWithText("second question").assertExists()
        composeRule.onNodeWithText("assistant answer").assertDoesNotExist()
        composeRule.onNodeWithText("2 messages").assertExists()
    }

    @Test
    fun `search filters the user's messages`() {
        composeRule.setContent {
            MaterialTheme {
                MessageHistoryContent(messages = messages, onSelect = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("second")

        composeRule.onNodeWithText("second question").assertExists()
        composeRule.onNodeWithText("first question").assertDoesNotExist()
        composeRule.onNodeWithText("1 match").assertExists()
    }

    @Test
    fun `search without matches shows the empty hint`() {
        composeRule.setContent {
            MaterialTheme {
                MessageHistoryContent(messages = messages, onSelect = {})
            }
        }

        composeRule.onNode(hasSetTextAction()).performTextInput("zzz")

        composeRule.onNodeWithText("No messages match your search.").assertExists()
    }

    @Test
    fun `tapping an entry selects that message`() {
        var selected: ChatMessage? = null
        composeRule.setContent {
            MaterialTheme {
                MessageHistoryContent(
                    messages = messages,
                    onSelect = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("second question").performClick()

        composeRule.runOnIdle { assertEquals("u2", selected?.id) }
    }
}
