package dev.nanobot.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PixelCatTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `tapping the cat shows hearts and a purr`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent { MaterialTheme { PixelCat() } }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithContentDescription(CAT_CONTENT_DESCRIPTION).performClick()
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithText(catReactionFor(1).message).assertExists()
        composeRule.onAllNodesWithTag(CAT_HEART_TAG)
            .assertCountEquals(catReactionFor(1).hearts)
    }

    @Test
    fun `every tap tier looks and sounds different`() {
        val tiers = listOf(1, 2, 4, 7).map(::catReactionFor)

        assertEquals(1, tiers[0].taps)
        assertEquals(tiers.size, tiers.map { it.message }.distinct().size)
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.hearts < b.hearts })
        assertTrue(tiers.zipWithNext().all { (a, b) -> a.hopDp < b.hopDp })
        assertNotEquals(tiers[1].mood, tiers[3].mood)
    }

    @Test
    fun `a pause resets the tap streak`() {
        assertEquals(4, nextCatTapStreak(3, CAT_TAP_STREAK_RESET_NANOS - 1))
        assertEquals(1, nextCatTapStreak(9, CAT_TAP_STREAK_RESET_NANOS))
    }
}
