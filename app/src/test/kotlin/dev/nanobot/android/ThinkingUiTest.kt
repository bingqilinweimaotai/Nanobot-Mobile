package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThinkingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun reasoningMessage(
        text: String,
        streaming: Boolean = false,
        stopped: Boolean = false,
        id: String = "reasoning-1",
    ) = ChatMessage(
        id = id,
        role = MessageRole.Assistant,
        text = text,
        streaming = streaming,
        reasoning = true,
        stopped = stopped,
    )

    @Test
    fun `reasoning starts as a one line preview and expands on tap`() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = reasoningMessage("alpha **bold** omega"),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thought").assertExists()
        composeRule.onNodeWithText("alpha bold omega").assertExists()
        composeRule.onNodeWithTag(REASONING_BODY_TAG).assertDoesNotExist()

        composeRule.onNodeWithTag(REASONING_HEADER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(REASONING_BODY_TAG).assertExists()
        composeRule.onNodeWithText("alpha **bold** omega").assertExists()

        composeRule.onNodeWithTag(REASONING_HEADER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(REASONING_BODY_TAG).assertDoesNotExist()
    }

    @Test
    fun `a live thought spins the dotted cat and keeps the preview current`() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = reasoningMessage("checking the workspace", streaming = true),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thinking…").assertExists()
        composeRule.onNodeWithText("checking the workspace").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a finished thought swaps the spinner for a check`() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = reasoningMessage("done thinking"),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thought").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithTag(CAT_SPINNER_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a stopped thought keeps a resting cat`() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = reasoningMessage("interrupted", stopped = true),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thinking · stopped").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_TAG, useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `consecutive reasoning steps share one folded header`() {
        composeRule.setContent {
            MaterialTheme {
                ReasoningBlock(
                    listOf(
                        reasoningMessage("**step one** full", id = "r1"),
                        reasoningMessage("**step two** full", id = "r2"),
                        reasoningMessage("**step three** full", id = "r3"),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Thought").assertExists()
        composeRule.onNodeWithText("· 3 steps").assertExists()
        composeRule.onNodeWithText("step three full").assertExists()
        composeRule.onNodeWithTag(REASONING_BODY_TAG).assertDoesNotExist()

        composeRule.onNodeWithTag(REASONING_HEADER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(REASONING_BODY_TAG).assertExists()
        composeRule.onNodeWithText("step one full").assertExists()
        composeRule.onNodeWithText("step two full").assertExists()
        // The header preview repeats the latest step's one-line preview.
        composeRule.onAllNodesWithText("step three full").assertCountEquals(2)

        composeRule.onNodeWithText("step one full").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("**step one** full").assertExists()
    }

    @Test
    fun `the preview strips markdown and collapses whitespace`() {
        assertEquals(
            "Hello world",
            compactReasoningPreview("**Hello**\n\n[world](https://example.com)"),
        )
        assertEquals("one two three", compactReasoningPreview("one\n\ntwo\tthree"))
    }

    @Test
    fun `the preview boundary never splits a surrogate pair`() {
        val text = "x".repeat(511) + "🚀 tail"
        assertEquals("x".repeat(511) + "…", reasoningPreview(text))
    }

    @Test
    fun `the working row uses the cat instead of a generic spinner`() {
        composeRule.setContent { MaterialTheme { WorkingRow() } }

        composeRule.onNodeWithText("Working…").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `one turn folds its text slices and reasoning into one block`() {
        composeRule.setContent {
            MaterialTheme {
                AssistantTurnBlock(
                    messages = listOf(
                        ChatMessage("a1", MessageRole.Assistant, "first paragraph"),
                        reasoningMessage("step one", id = "r1"),
                        ChatMessage("a2", MessageRole.Assistant, "second paragraph"),
                        reasoningMessage("step two", id = "r2"),
                    ),
                    isLive = false,
                    forkUserIndex = 1,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("first paragraph").assertExists()
        composeRule.onNodeWithText("second paragraph").assertExists()
        // One activity strip and one action row for the whole turn, not one per segment.
        composeRule.onAllNodesWithText("Thought").assertCountEquals(1)
        composeRule.onNodeWithText("· 2 steps").assertExists()
        composeRule.onAllNodesWithContentDescription("Copy").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("Fork from here").assertExists()

        // The strip sits above the answer, never between its paragraphs.
        val headerTop = composeRule.onNodeWithTag(REASONING_HEADER_TAG)
            .getUnclippedBoundsInRoot().top
        val bodyTop = composeRule.onNodeWithText("first paragraph")
            .getUnclippedBoundsInRoot().top
        assertTrue(headerTop < bodyTop)
    }

    @Test
    fun `a live turn keeps its actions hidden`() {
        composeRule.setContent {
            MaterialTheme {
                AssistantTurnBlock(
                    messages = listOf(
                        ChatMessage("a1", MessageRole.Assistant, "partial answer"),
                        reasoningMessage("still thinking", streaming = true, id = "r1"),
                    ),
                    isLive = true,
                    forkUserIndex = 1,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thinking…").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertExists()
        composeRule.onAllNodesWithContentDescription("Copy").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Fork from here").assertCountEquals(0)
    }

    @Test
    fun `a settled turn shows a check even though the turn row still exists`() {
        composeRule.setContent {
            MaterialTheme {
                AssistantTurnBlock(
                    messages = listOf(
                        ChatMessage("a1", MessageRole.Assistant, "final answer"),
                        reasoningMessage("finished thinking", id = "r1"),
                    ),
                    isLive = false,
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithText("Thought").assertExists()
        composeRule.onNodeWithTag(CAT_SPINNER_ACTIVE_TAG, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Copy").assertExists()
    }
}
