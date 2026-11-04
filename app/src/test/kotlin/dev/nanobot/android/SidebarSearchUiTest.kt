package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
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
class SidebarSearchUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    /** These assertions describe the Simplified Chinese UI. */
    private fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalStrings provides ChineseStrings) {
                MaterialTheme(content = content)
            }
        }
    }

    @Test
    fun `quick actions offer add workspace and search`() {
        var added = false
        var toggled = false
        setContent {
            SidebarQuickActions(
                searchOpen = false,
                onAddWorkspace = { added = true },
                onToggleSearch = { toggled = true },
            )
        }

        composeRule.onNodeWithText("添加工作区").performClick()
        composeRule.onNodeWithText("搜索").performClick()

        composeRule.runOnIdle {
            assertTrue(added)
            assertTrue(toggled)
        }
    }

    @Test
    fun `results show the role that matched and open the conversation`() {
        var selected: ConversationSearchHit? = null
        val hit = ConversationSearchHit(
            chatId = "chat-1",
            sessionKey = "websocket:chat-1",
            title = "Release notes",
            preview = "…the transformer cache now reuses keys…",
            role = MessageRole.Assistant,
            messageId = "a1",
            messageText = "The transformer cache now reuses keys.",
        )
        setContent {
            ConversationSearchResults(
                results = listOf(hit),
                searching = false,
                onSelect = { selected = it },
            )
        }

        composeRule.onNodeWithText("Release notes").assertExists()
        composeRule.onNodeWithText("nanobot：").assertExists()
        composeRule.onNodeWithText("…the transformer cache now reuses keys…").performClick()

        composeRule.runOnIdle { assertEquals(hit, selected) }
    }

    @Test
    fun `an empty result list explains that nothing matched`() {
        setContent {
            ConversationSearchResults(
                results = emptyList(),
                searching = false,
                onSelect = {},
            )
        }

        composeRule.onNodeWithText("没有匹配的对话").assertExists()
    }
}
