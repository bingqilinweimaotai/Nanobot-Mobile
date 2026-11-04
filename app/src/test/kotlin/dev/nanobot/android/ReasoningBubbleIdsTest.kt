package dev.nanobot.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReasoningBubbleIdsTest {
    @Test
    fun `a gateway stream id keeps its own bubble`() {
        val ids = ReasoningBubbleIds()

        assertEquals("reasoning-a", ids.idFor("a"))
        assertEquals("reasoning-a", ids.idFor("a"))
        assertEquals("reasoning-b", ids.idFor("b"))
    }

    @Test
    fun `streamless chunks share one bubble until the segment closes`() {
        var next = 0
        val ids = ReasoningBubbleIds { "reasoning-segment-${next++}" }

        val first = ids.idFor(null)
        assertEquals(first, ids.idFor(null))
        assertEquals(first, ids.idFor(null))

        ids.close()
        val second = ids.idFor(null)
        assertNotEquals(first, second)
        assertEquals(second, ids.idFor(null))
    }

    @Test
    fun `a new turn never reuses the previous turn's bubble`() {
        var next = 0
        val ids = ReasoningBubbleIds { "reasoning-segment-${next++}" }

        val turnOne = ids.idFor(null)
        ids.close() // turn_end / send
        val turnTwo = ids.idFor(null)

        assertNotEquals(turnOne, turnTwo)
    }
}
