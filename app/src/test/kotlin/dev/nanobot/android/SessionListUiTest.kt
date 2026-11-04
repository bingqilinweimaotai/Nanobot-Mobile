package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nanobot.protocol.SessionSummary
import dev.nanobot.protocol.WorkspaceScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SessionListUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `workspace headers collapse and expand their conversations`() {
        val collapsedGroups = mutableStateOf(emptySet<String>())

        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(
                        defaultWorkspaceScope = WorkspaceScope(projectPath = "/default"),
                        sessions = listOf(
                            SessionSummary(
                                key = "websocket:project-chat",
                                title = "初次问候与协助请求",
                                preview = "你好",
                                updatedAt = "2026-10-01",
                                workspaceScope = WorkspaceScope(projectPath = "/Paper/reading"),
                            ),
                        ),
                        collapsedGroups = collapsedGroups.value,
                    ),
                    onSelect = {},
                    onToggleGroup = { groupId ->
                        collapsedGroups.value = if (groupId in collapsedGroups.value) {
                            collapsedGroups.value - groupId
                        } else {
                            collapsedGroups.value + groupId
                        }
                    },
                )
            }
        }

        composeRule.onNodeWithText("reading").assertExists()
        composeRule.onNodeWithText("初次问候与协助请求").assertExists()

        composeRule.onNodeWithText("reading").performClick()
        composeRule.onNodeWithText("初次问候与协助请求").assertDoesNotExist()

        composeRule.onNodeWithText("reading").performClick()
        composeRule.onNodeWithText("初次问候与协助请求").assertExists()
    }

    @Test
    fun `only running sessions show a spinner`() {
        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(
                        sessions = listOf(
                            SessionSummary(
                                key = "websocket:running-chat",
                                title = "Running chat",
                                runStartedAt = 1_760_000_000_000,
                            ),
                            SessionSummary(
                                key = "websocket:idle-chat",
                                title = "Idle chat",
                            ),
                        ),
                    ),
                    onSelect = {},
                    onToggleGroup = {},
                )
            }
        }

        composeRule.onNodeWithText("Running chat").assertExists()
        composeRule.onNodeWithText("Idle chat").assertExists()
        composeRule.onAllNodesWithContentDescription("Running").assertCountEquals(1)
    }

    @Test
    fun `only conversations with unseen answers show the green marker`() {
        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(
                        sessions = listOf(
                            SessionSummary(
                                key = "websocket:done-chat",
                                title = "Done chat",
                            ),
                            SessionSummary(
                                key = "websocket:idle-chat",
                                title = "Idle chat",
                            ),
                        ),
                        unreadChatIds = setOf("done-chat"),
                    ),
                    onSelect = {},
                    onToggleGroup = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Unread").assertCountEquals(1)
    }

    @Test
    fun `a running conversation shows the spinner instead of the green marker`() {
        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(
                        sessions = listOf(
                            SessionSummary(
                                key = "websocket:running-chat",
                                title = "Running chat",
                                runStartedAt = 1_760_000_000_000,
                            ),
                        ),
                        unreadChatIds = setOf("running-chat"),
                    ),
                    onSelect = {},
                    onToggleGroup = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Unread").assertCountEquals(0)
        composeRule.onAllNodesWithContentDescription("Running").assertCountEquals(1)
    }

    @Test
    fun `session action menu pins and deletes a conversation`() {
        var pinned: String? = null
        var deleted: List<SessionSummary>? = null
        val session = SessionSummary(key = "websocket:chat-a", title = "Chat A")

        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(sessions = listOf(session)),
                    onSelect = {},
                    onToggleGroup = {},
                    onTogglePin = { pinned = it },
                    onRequestDelete = { deleted = it },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Conversation actions for Chat A").performClick()
        composeRule.onNodeWithText("Pin").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals("websocket:chat-a", pinned) }

        composeRule.onNodeWithContentDescription("Conversation actions for Chat A").performClick()
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(listOf("websocket:chat-a"), deleted?.map { it.key })
        }
    }

    @Test
    fun `only pinned sessions show the pin marker`() {
        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(
                        sessions = listOf(
                            SessionSummary(key = "websocket:pinned-chat", title = "Pinned chat"),
                            SessionSummary(key = "websocket:plain-chat", title = "Plain chat"),
                        ),
                        pinnedKeys = setOf("websocket:pinned-chat"),
                    ),
                    onSelect = {},
                    onToggleGroup = {},
                )
            }
        }

        composeRule.onAllNodesWithContentDescription("Pinned").assertCountEquals(1)
    }

    @Test
    fun `long pressing a conversation starts multi selection`() {
        val session = SessionSummary(key = "websocket:chat-a", title = "Chat A")

        composeRule.setContent {
            MaterialTheme {
                SessionList(
                    state = UiState(sessions = listOf(session)),
                    onSelect = {},
                    onToggleGroup = {},
                )
            }
        }

        composeRule.onNodeWithText("Chat A").performTouchInput { longClick() }

        composeRule.onNodeWithText("1 selected").assertExists()
        composeRule.onNodeWithContentDescription("Delete").assertExists()
        composeRule.onNodeWithContentDescription("Archive").assertExists()
    }
}
