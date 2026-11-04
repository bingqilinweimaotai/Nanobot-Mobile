package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MessageUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `assistant markdown is rendered without source markers`() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    ## 配置

                    - **modelPreset**: `magpie`

                    ```
                    val enabled = true
                    ```
                    """.trimIndent(),
                )
            }
        }

        composeRule.onNodeWithText("配置").assertExists()
        composeRule.onNodeWithText("modelPreset: magpie").assertExists()
        composeRule.onNodeWithText("**modelPreset**").assertDoesNotExist()
        composeRule.onNodeWithText("val enabled = true").assertExists()
    }

    @Test
    fun `assistant actions expose copy and fork icons`() {
        var forkedAt: Int? = null
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = ChatMessage("a1", MessageRole.Assistant, "answer"),
                    forkUserIndex = 2,
                    onFork = { forkedAt = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Copy").assertExists()
        composeRule.onNodeWithContentDescription("Fork from here").performClick()
        composeRule.runOnIdle { assertEquals(2, forkedAt) }

        val rootWidth = composeRule.onRoot().getUnclippedBoundsInRoot().let { it.right - it.left }
        val copyLeft = composeRule.onNodeWithContentDescription("Copy")
            .getUnclippedBoundsInRoot()
            .left
        assertTrue(copyLeft < rootWidth / 2)
    }

    @Test
    fun `copy waits until the answer finishes streaming`() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = ChatMessage(
                        "streaming",
                        MessageRole.Assistant,
                        "half an answer",
                        streaming = true,
                    ),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Copy").assertDoesNotExist()
    }

    @Test
    fun `markdown renders tables without pipe syntax`() {
        composeRule.setContent {
            MaterialTheme {
                MarkdownText(
                    """
                    | Name | Value |
                    | :--- | ---: |
                    | alpha | 1 |
                    | beta | 2 |
                    """.trimIndent(),
                )
            }
        }

        composeRule.onNodeWithText("Name").assertExists()
        composeRule.onNodeWithText("Value").assertExists()
        composeRule.onNodeWithText("alpha").assertExists()
        composeRule.onNodeWithText("2").assertExists()
        composeRule.onNodeWithText("| Name | Value |").assertDoesNotExist()
    }

    @Test
    fun `copy icon confirms briefly then returns to copy`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = ChatMessage("copy-test", MessageRole.Assistant, "answer"),
                    forkUserIndex = null,
                    onFork = {},
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Copy").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Copied").assertExists()

        composeRule.mainClock.advanceTimeBy(900)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Copied").assertExists()

        composeRule.mainClock.advanceTimeBy(110)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithContentDescription("Copy").assertExists()
        composeRule.onNodeWithContentDescription("Copied").assertDoesNotExist()
    }
}
