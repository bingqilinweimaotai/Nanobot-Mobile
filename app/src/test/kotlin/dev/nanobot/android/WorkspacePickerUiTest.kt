package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.nanobot.protocol.WorkspaceDirectoriesResponse
import dev.nanobot.protocol.WorkspaceProject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WorkspacePickerUiTest {
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

    private val directories = mapOf(
        "/home/me" to WorkspaceDirectoriesResponse(
            path = "/home/me",
            parent = "/home",
            entries = listOf(
                WorkspaceProject(name = "projects", path = "/home/me/projects"),
                WorkspaceProject(name = "notes", path = "/home/me/notes"),
            ),
        ),
        "/home/me/projects" to WorkspaceDirectoriesResponse(
            path = "/home/me/projects",
            parent = "/home/me",
            entries = emptyList(),
        ),
    )

    @Test
    fun `saved favorites and recent folders are listed`() {
        var selected: WorkspaceProject? = null
        setContent {
            WorkspacePickerContent(
                hostName = "DESKTOP-1",
                defaultPath = "/home/me",
                canBrowse = true,
                favoriteProjects = listOf(WorkspaceProject("projects", "/home/me/projects")),
                recentProjects = listOf(
                    WorkspaceProject("notes", "/home/me/notes"),
                    WorkspaceProject("projects", "/home/me/projects"),
                ),
                browse = { path -> directories.getValue(path) },
                onSelect = { selected = it },
                onClose = {},
            )
        }

        composeRule.onNodeWithText("添加工作区").assertExists()
        composeRule.onNodeWithText("DESKTOP-1").assertExists()
        composeRule.onNodeWithText("收藏").assertExists()
        composeRule.onNodeWithText("最近").assertExists()
        composeRule.onNodeWithText("projects").performClick()

        composeRule.runOnIdle {
            assertEquals("/home/me/projects", selected?.path)
        }
    }

    @Test
    fun `browsing lists directories and adds the chosen folder`() {
        var selected: WorkspaceProject? = null
        setContent {
            WorkspacePickerContent(
                hostName = "DESKTOP-1",
                defaultPath = "/home/me",
                canBrowse = true,
                favoriteProjects = emptyList(),
                recentProjects = emptyList(),
                browse = { path -> directories.getValue(path) },
                onSelect = { selected = it },
                onClose = {},
            )
        }

        composeRule.onNodeWithText("浏览文件夹…").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("/home/me").assertExists()
        composeRule.onNodeWithText("notes").assertExists()

        composeRule.onNodeWithText("projects").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("/home/me/projects").assertExists()

        composeRule.onNodeWithText("添加").performClick()
        composeRule.runOnIdle {
            assertEquals("/home/me/projects", selected?.path)
            assertEquals("projects", selected?.name)
        }
    }

    @Test
    fun `a folder listing failure is shown instead of an empty list`() {
        setContent {
            WorkspacePickerContent(
                hostName = "",
                defaultPath = "/missing",
                canBrowse = true,
                favoriteProjects = emptyList(),
                recentProjects = emptyList(),
                browse = { throw IllegalStateException("目录不存在") },
                onSelect = {},
                onClose = {},
            )
        }

        composeRule.onNodeWithText("浏览文件夹…").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("目录不存在").assertExists()
    }
}
