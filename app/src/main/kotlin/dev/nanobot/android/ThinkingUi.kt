package dev.nanobot.android

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal const val REASONING_HEADER_TAG = "reasoning-header"
internal const val REASONING_BODY_TAG = "reasoning-body"
internal const val REASONING_PREVIEW_LIMIT = 512

private val REASONING_LINK = Regex("""\[([^\]]+)]\([^)]+\)""")
private val REASONING_MARKERS = Regex("""[*_#`~]+""")
private val REASONING_WHITESPACE = Regex("""\s+""")

/**
 * Flattens reasoning Markdown into the single muted line shown next to the
 * "Thinking" label. Mirrors the WebUI's compact reasoning preview so the
 * collapsed block never carries the weight of the full transcript.
 */
internal fun compactReasoningPreview(value: String): String =
    value
        .replace(REASONING_LINK, "$1")
        .replace(REASONING_MARKERS, "")
        .replace(REASONING_WHITESPACE, " ")
        .trim()

/** Caps the preview without splitting a surrogate pair at the cut. */
internal fun reasoningPreview(
    value: String,
    limit: Int = REASONING_PREVIEW_LIMIT,
): String {
    val compact = compactReasoningPreview(value)
    if (compact.length <= limit) return compact
    val cut = if (compact[limit - 1].isHighSurrogate()) limit - 1 else limit
    return compact.take(cut) + "…"
}

/**
 * Collapsed chain-of-thought. The header stays one line: a cat spinner while
 * the model is thinking, then a check once it settles, with the live preview
 * truncated beside it. Tapping opens the full reasoning in place.
 *
 * Consecutive reasoning messages arrive as one run. They share a single header
 * ("Thought · 8 steps") instead of stacking a row per step, which keeps a
 * pause-and-continue turn from filling the transcript with "Thought" headers.
 * Expanding the run lists each step as its own one-line disclosure.
 */
@Composable
internal fun ReasoningBlock(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) return
    val single = messages.size == 1
    var expanded by rememberSaveable(messages.first().id) { mutableStateOf(false) }
    val active = messages.any { it.streaming }
    val stopped = !active && messages.last().stopped
    val latest = messages.lastOrNull { it.text.isNotBlank() } ?: messages.last()
    val preview = remember(latest.text) { reasoningPreview(latest.text) }
    val strings = LocalStrings.current
    val label = when {
        stopped -> strings.thinkingStopped
        active -> strings.thinking
        else -> strings.thought
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180),
        label = "reasoning-chevron",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .padding(vertical = 2.dp)
                    .testTag(REASONING_HEADER_TAG)
                    .semantics {
                        contentDescription = strings.reasoningDescription(
                            label = label,
                            count = messages.size,
                            expanded = expanded,
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReasoningMarker(active = active, stopped = stopped)
                Spacer(Modifier.width(8.dp))
                StreamingLabel(text = label, active = active)
                if (!single) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        strings.stepsLabel(messages.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = muted.copy(alpha = 0.55f),
                        maxLines = 1,
                    )
                }
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        preview,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = muted.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(chevronAngle),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(180)) + fadeIn(tween(120)),
                exit = shrinkVertically(tween(150)) + fadeOut(tween(90)),
            ) {
                if (single) {
                    ReasoningBody(messages.first())
                } else {
                    ReasoningStepList(messages)
                }
            }
        }
    }
}

@Composable
private fun ReasoningBody(message: ChatMessage) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(IntrinsicSize.Min)
            .testTag(REASONING_BODY_TAG),
    ) {
        ReasoningRail()
        Spacer(Modifier.width(8.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                message.text.ifBlank { " " },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
    }
}

/** The vertical thread that ties the expanded steps to the header marker. */
@Composable
private fun ReasoningRail() {
    Box(
        Modifier
            .width(16.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun ReasoningStepList(messages: List<ChatMessage>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(IntrinsicSize.Min)
            .testTag(REASONING_BODY_TAG),
    ) {
        ReasoningRail()
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            messages.forEach { message ->
                ReasoningStepRow(message)
            }
        }
    }
}

/** One folded step inside an expanded reasoning run. */
@Composable
private fun ReasoningStepRow(message: ChatMessage) {
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    val preview = remember(message.text) { reasoningPreview(message.text) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val chevronAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(160),
        label = "reasoning-step-chevron",
    )

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                preview.ifEmpty { "…" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                color = muted.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = muted.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(14.dp)
                    .rotate(chevronAngle),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(160)) + fadeIn(tween(100)),
            exit = shrinkVertically(tween(140)) + fadeOut(tween(80)),
        ) {
            SelectionContainer {
                Text(
                    message.text.ifBlank { " " },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ReasoningMarker(active: Boolean, stopped: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when {
        active -> PixelCatSpinner(active = true, color = muted)
        stopped -> PixelCatSpinner(
            active = false,
            color = muted.copy(alpha = 0.55f),
        )

        else -> Box(
            modifier = Modifier
                .size(16.dp)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/** The "Working…" row that keeps the tail of a live turn company. */
@Composable
internal fun WorkingRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PixelCatSpinner(active = true)
        StreamingLabel(text = LocalStrings.current.working, active = true)
    }
}

/** Keeps the per-frame sheen state inside its own restart scope. */
@Composable
private fun StreamingLabel(text: String, active: Boolean) {
    Text(
        text,
        style = streamingLabelStyle(active),
        maxLines = 1,
    )
}

/**
 * A muted label with a highlight sweeping across it while [active]. The sweep
 * is a text brush, so the label string never changes while the light moves.
 */
@Composable
private fun streamingLabelStyle(active: Boolean): TextStyle {
    val base = MaterialTheme.typography.labelSmall
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    if (!active) return base.copy(color = muted)

    val width = with(LocalDensity.current) { 96.dp.toPx() }
    val transition = rememberInfiniteTransition(label = "streaming-sheen")
    val shift by transition.animateFloat(
        initialValue = -width,
        targetValue = width * 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
        ),
        label = "streaming-sheen-shift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            muted.copy(alpha = 0.55f),
            muted,
            MaterialTheme.colorScheme.onSurface,
            muted,
            muted.copy(alpha = 0.55f),
        ),
        start = Offset(shift, 0f),
        end = Offset(shift + width, 0f),
    )
    return base.copy(brush = brush)
}
