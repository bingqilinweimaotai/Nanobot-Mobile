package dev.nanobot.android

import java.util.UUID

/**
 * Picks the transcript bubble for a reasoning frame.
 *
 * The gateway sends `stream_id` on answer deltas but not on reasoning frames, so the app has to
 * invent one. A single literal id made every turn append its thoughts to the first turn's bubble;
 * this keeps one id per reasoning segment instead. A segment stays open until `reasoning_end` or
 * the turn ends, so all chunks of one block land in the same bubble and the next block starts a
 * fresh one.
 */
internal class ReasoningBubbleIds(
    private val newId: () -> String = { "reasoning-segment-${UUID.randomUUID()}" },
) {
    private var openId: String? = null

    fun idFor(streamId: String?): String {
        if (streamId != null) return "reasoning-$streamId"
        return openId ?: newId().also { openId = it }
    }

    fun close() {
        openId = null
    }
}
