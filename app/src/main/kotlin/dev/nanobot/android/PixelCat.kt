package dev.nanobot.android

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.delay

internal const val CAT_CONTENT_DESCRIPTION = "Pixel cat"
internal const val CAT_HEART_TAG = "cat-heart"
internal const val CAT_TAP_STREAK_RESET_NANOS = 3_000_000_000L
internal const val CAT_SPINNER_TAG = "cat-spinner"
internal const val CAT_SPINNER_ACTIVE_TAG = "cat-spinner-active"
internal const val CAT_SPINNER_PERIOD_MILLIS = 1_500
private const val CAT_SPINNER_TAIL_DEGREES = 120f

internal enum class CatMood { Idle, Blink, Happy, Love }

internal data class CatReaction(
    val taps: Int,
    val mood: CatMood,
    val message: String,
    val hearts: Int,
    val hopDp: Float,
    val wiggleDegrees: Float,
)

/**
 * The cat gets more excited the faster you keep tapping it. A pause of a few
 * seconds starts the streak over, so a single tap always gets the calm purr.
 */
internal fun catReactionFor(taps: Int): CatReaction {
    val streak = taps.coerceAtLeast(1)
    return when {
        streak == 1 -> CatReaction(streak, CatMood.Happy, "purr…", 2, 6f, 3f)
        streak <= 3 -> CatReaction(streak, CatMood.Happy, "purr purr~", 5, 9f, 5f)
        streak <= 6 -> CatReaction(streak, CatMood.Love, "meow!", 9, 14f, 8f)
        else -> CatReaction(streak, CatMood.Love, "♥ ♥ ♥", 16, 18f, 12f)
    }
}

internal fun nextCatTapStreak(previousTaps: Int, elapsedNanos: Long): Int =
    if (elapsedNanos >= CAT_TAP_STREAK_RESET_NANOS) 1 else previousTaps + 1

private val CAT_WIDTH = 128.dp
private val CAT_HEIGHT = 120.dp
private val STAGE_WIDTH = 180.dp
private val STAGE_HEIGHT = 176.dp

private val INK = Color(0xFF2B3440)
private val FUR = Color(0xFFA9C1DE)
private val CREAM = Color(0xFFFFF6E6)
private val PINK = Color(0xFFF2A7B8)
private val EYE = Color(0xFF25313F)
private val BLUSH = Color(0xFFF6C0CC)
private val TONGUE = Color(0xFFE8778F)
private val HEART_COLORS = listOf(
    Color(0xFFE8778F),
    Color(0xFFF2A7B8),
    Color(0xFFF7C1CE),
)

private val CAT_COLORS = mapOf(
    'K' to INK,
    'F' to FUR,
    'C' to CREAM,
    'P' to PINK,
)

/**
 * Sitting cat sprite. The face and blush are drawn separately so the
 * expression can change while the body stays pixel-crisp.
 */
private val CAT_SPRITE = listOf(
    "......KFFFK..........KFFFK......",
    ".....KFFFFFK........KFFFFFK.....",
    ".....KFFFFFK........KFFFFFK.....",
    "....KFFPPPFFK......KFFPPPFFK....",
    "...KFFPPPPPFFK....KFFPPPPPFFK...",
    "...KFFPPPPPFFK....KFFPPPPPFFK...",
    "..KFFFPPPPPFFFKKKKFFFPPPPPFFFK..",
    "..KFFPPPPPPPFFFFFFFFPPPPPPPFFK..",
    ".KFFFFFFFFFFDFFDFFDFFFFFFFFFFFK.",
    "..KKKKFFFFFFDFFDFFDFFFFFFFKKKK..",
    "....KFFFFFFFFFFFFFFFFFFFFFFK....",
    "....KFFFFFFFFFFFFFFFFFFFFFFK....",
    "....KFFFFFFFFFFFFFFFFFFFFFFK....",
    "...KFFFFFFFFFFFCCFFFFFFFFFFFK...",
    "...KFFFFFFFFFCCCCCCFFFFFFFFFK...",
    "....KFFFFFFFFCCCCCCFFFFFFFFK....",
    "....KKKKFFFFCCCCCCCCFFFFKKKK....",
    "....KFFFFFFFFCCCCCCFFFFFFFFK....",
    "....KKKFFFFFFCCCCCCFFFFFFKKK....",
    ".....KKFFFFFFFFCCFFFFFFFFKK.....",
    ".......KKDDDDDDDDDDDDDDKK.......",
    ".......KFFFFFFFFFFFFFFFFK...KKK.",
    "......KDFFFFFFFCCFFFFFFFDKKKFCCK",
    "......KDFFFFFCCCCCCFFFFFDKFFFCCK",
    "......KDFFFFFCCCCCCFFFFFDFFFFFFK",
    "......KDFFFFFCCCCCCFFFFFDFFFFFK.",
    "......KDFFFFFFFCCFFFFFFFDFFFFK..",
    ".......KFFCCFFFFFFFFCCFFFFFFK...",
    "........KFCCFFFFFFFFCCFDDDFK....",
    ".........KKKKKKKKKKKKKKKKKK.....",
)

/**
 * A dot-matrix cat head used as the compact thinking marker. It is deliberately
 * smaller than [CAT_SPRITE]: at 16dp only the ears, face, and eye gaps survive.
 */
private val CAT_SPINNER_DOTS = listOf(
    "X.......X",
    "XX.....XX",
    "XXXXXXXXX",
    "XXXXXXXXX",
    "X.XXXXX.X",
    "XXXXXXXXX",
    ".XXXXXXX.",
    "..XXXXX..",
)

private val SMALL_HEART = listOf(
    ".X.X.",
    "XXXXX",
    "XXXXX",
    ".XXX.",
    "..X..",
)

private val FLOATING_HEART = listOf(
    ".XX.XX.",
    "XXXXXXX",
    "XXXXXXX",
    ".XXXXX.",
    "..XXX..",
    "...X...",
)

@Composable
internal fun PixelCat(modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    var taps by remember { mutableIntStateOf(0) }
    var lastTapNanos by remember { mutableLongStateOf(0L) }
    var reaction by remember { mutableStateOf<CatReaction?>(null) }
    var hearts by remember { mutableStateOf(emptyList<Heart>()) }
    var nextHeartId by remember { mutableLongStateOf(0L) }
    var touched by remember { mutableStateOf(false) }
    var hopAmplitudeDp by remember { mutableFloatStateOf(0f) }
    var wiggleDegrees by remember { mutableFloatStateOf(0f) }
    var blinking by remember { mutableStateOf(false) }

    val hop = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    val idleTransition = rememberInfiniteTransition(label = "cat")
    val breathe by idleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathe",
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            blinking = true
            delay(110)
            blinking = false
        }
    }

    LaunchedEffect(reaction) {
        if (reaction == null) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(520, easing = LinearEasing))
    }

    LaunchedEffect(reaction) {
        val active = reaction ?: return@LaunchedEffect
        delay(1500)
        if (reaction == active) reaction = null
    }

    val mood = reaction?.mood ?: if (blinking) CatMood.Blink else CatMood.Idle
    val density = LocalDensity.current
    val jumpProgress = hop.value
    val jumpPx = with(density) {
        (sin(jumpProgress * PI).toFloat() * hopAmplitudeDp).dp.toPx()
    }
    val bobPx = with(density) { (breathe * 1.5f).dp.toPx() }
    val stretch = sin(jumpProgress * PI).toFloat()
    val catContentDescription = LocalStrings.current.pixelCat

    Box(modifier = modifier.size(STAGE_WIDTH, STAGE_HEIGHT)) {
        val catModifier = Modifier
            .align(Alignment.BottomCenter)
            .size(CAT_WIDTH, CAT_HEIGHT)
            .graphicsLayer {
                translationY = bobPx - jumpPx
                rotationZ = sin(jumpProgress * PI * 2).toFloat() * wiggleDegrees
                scaleX = 1f - stretch * 0.03f
                scaleY = 1f + stretch * 0.05f
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = {
                    val now = System.nanoTime()
                    val streak = nextCatTapStreak(taps, now - lastTapNanos)
                    taps = streak
                    lastTapNanos = now
                    val next = catReactionFor(streak)
                    reaction = next
                    hopAmplitudeDp = next.hopDp
                    wiggleDegrees = next.wiggleDegrees
                    touched = true
                    hearts = hearts + spawnHearts(next.hearts, nextHeartId)
                    nextHeartId += next.hearts
                },
            )
            .semantics { contentDescription = catContentDescription }

        CatArt(mood = mood, modifier = catModifier)

        hearts.forEach { heart ->
            key(heart.id) {
                FloatingHeart(
                    heart = heart,
                    modifier = Modifier.align(Alignment.TopStart),
                    onFinished = { id -> hearts = hearts.filterNot { it.id == id } },
                )
            }
        }

        val active = reaction
        if (active != null) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    active.message,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (!touched) {
            Text(
                strings.tapMe,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
    }
}

/**
 * The compact activity marker: the same dotted-cat language as [PixelCat], but
 * made of dots. While [active] a bright sweep travels clockwise through the
 * dot matrix; the head itself stays upright instead of spinning as a sticker.
 */
@Composable
internal fun PixelCatSpinner(
    active: Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 16.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val sweep = if (active) {
        val transition = rememberInfiniteTransition(label = "cat-spinner")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(CAT_SPINNER_PERIOD_MILLIS, easing = LinearEasing),
            ),
            label = "cat-spinner-sweep",
        )
        value
    } else {
        0f
    }

    Canvas(
        modifier = modifier
            .size(diameter)
            // Decorative: the "Thinking…" label carries the state for screen readers.
            .testTag(if (active) CAT_SPINNER_ACTIVE_TAG else CAT_SPINNER_TAG),
    ) {
        val columns = CAT_SPINNER_DOTS.first().length
        val rows = CAT_SPINNER_DOTS.size
        // Center the non-square sprite so the sweep turns around its middle.
        val cell = minOf(size.width / columns, size.height / rows)
        val left = (size.width - columns * cell) / 2f
        val top = (size.height - rows * cell) / 2f
        val dotRadius = cell * 0.36f
        val centerX = left + columns * cell / 2f
        val centerY = top + rows * cell / 2f
        CAT_SPINNER_DOTS.forEachIndexed { y, row ->
            row.forEachIndexed { x, symbol ->
                if (symbol != 'X') return@forEachIndexed
                val dotX = left + (x + 0.5f) * cell
                val dotY = top + (y + 0.5f) * cell
                if (!active) {
                    drawCircle(
                        color = color,
                        radius = dotRadius,
                        center = Offset(dotX, dotY),
                    )
                    return@forEachIndexed
                }
                // A comet head sweeps clockwise; the tail trails behind it.
                // The dots keep their positions, so the cat head stays upright.
                val angle = atan2(dotY - centerY, dotX - centerX) * 180f / PI.toFloat()
                val behind = ((sweep - angle) % 360f + 360f) % 360f
                val wave = if (behind < CAT_SPINNER_TAIL_DEGREES) {
                    1f - behind / CAT_SPINNER_TAIL_DEGREES
                } else {
                    0f
                }
                val distance = hypot(dotX - centerX, dotY - centerY)
                val radial = (distance / (cell * 3.5f)).coerceIn(0f, 1f)
                val intensity = 0.35f + 0.65f * (wave * radial + (1f - radial) * 0.5f)
                drawCircle(
                    color = color.copy(alpha = color.alpha * intensity),
                    radius = dotRadius * (0.85f + 0.3f * wave * radial),
                    center = Offset(dotX, dotY),
                )
            }
        }
    }
}

@Composable
private fun CatArt(mood: CatMood, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cell = size.width / CAT_SPRITE.first().length
        CAT_SPRITE.forEachIndexed { y, row ->
            row.forEachIndexed { x, symbol ->
                val color = CAT_COLORS[symbol] ?: return@forEachIndexed
                pixel(x, y, 1, 1, cell, color)
            }
        }

        pixel(5, 14, 2, 2, cell, BLUSH)
        pixel(25, 14, 2, 2, cell, BLUSH)
        pixel(14, 14, 3, 2, cell, PINK)

        when (mood) {
            CatMood.Idle, CatMood.Blink -> {
                if (mood == CatMood.Blink) {
                    pixel(8, 11, 4, 1, cell, EYE)
                    pixel(20, 11, 4, 1, cell, EYE)
                } else {
                    pixel(8, 10, 4, 4, cell, EYE)
                    pixel(20, 10, 4, 4, cell, EYE)
                    pixel(8, 10, 1, 1, cell, Color.White)
                    pixel(20, 10, 1, 1, cell, Color.White)
                }
                smile(cell)
            }

            CatMood.Happy -> {
                happyEyes(cell)
                openMouth(cell)
            }

            CatMood.Love -> {
                drawMask(SMALL_HEART, 8, 10, cell, TONGUE)
                drawMask(SMALL_HEART, 19, 10, cell, TONGUE)
                openMouth(cell)
            }
        }
    }
}

private fun DrawScope.smile(cell: Float) {
    for ((x, y) in listOf(13 to 16, 17 to 16, 14 to 17, 16 to 17, 15 to 18)) {
        pixel(x, y, 1, 1, cell, EYE)
    }
}

private fun DrawScope.happyEyes(cell: Float) {
    for ((x, y) in listOf(8 to 11, 9 to 10, 10 to 10, 11 to 11)) {
        pixel(x, y, 1, 1, cell, EYE)
    }
    for ((x, y) in listOf(20 to 11, 21 to 10, 22 to 10, 23 to 11)) {
        pixel(x, y, 1, 1, cell, EYE)
    }
}

private fun DrawScope.openMouth(cell: Float) {
    pixel(14, 16, 4, 3, cell, EYE)
    pixel(15, 18, 2, 1, cell, TONGUE)
}

private fun DrawScope.drawMask(
    mask: List<String>,
    x0: Int,
    y0: Int,
    cell: Float,
    color: Color,
) {
    mask.forEachIndexed { dy, row ->
        row.forEachIndexed { dx, symbol ->
            if (symbol == 'X') pixel(x0 + dx, y0 + dy, 1, 1, cell, color)
        }
    }
}

private fun DrawScope.pixel(x: Int, y: Int, w: Int, h: Int, cell: Float, color: Color) {
    drawRect(
        color = color,
        topLeft = Offset(x * cell, y * cell),
        size = Size(w * cell + 0.5f, h * cell + 0.5f),
    )
}

private data class Heart(
    val id: Long,
    val xDp: Float,
    val yDp: Float,
    val driftDp: Float,
    val riseDp: Float,
    val scale: Float,
    val durationMs: Int,
    val color: Color,
)

private fun spawnHearts(count: Int, firstId: Long): List<Heart> =
    List(count) { index ->
        Heart(
            id = firstId + index,
            xDp = 56f + Random.nextFloat() * 68f,
            yDp = 62f + Random.nextFloat() * 16f,
            driftDp = Random.nextFloat() * 46f - 23f,
            riseDp = 55f + Random.nextFloat() * 45f,
            scale = 0.8f + Random.nextFloat() * 0.5f,
            durationMs = 900 + Random.nextInt(450),
            color = HEART_COLORS[index % HEART_COLORS.size],
        )
    }

@Composable
private fun FloatingHeart(
    heart: Heart,
    modifier: Modifier = Modifier,
    onFinished: (Long) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(heart.id) {
        progress.animateTo(1f, tween(heart.durationMs, easing = LinearEasing))
        onFinished(heart.id)
    }

    val fraction = progress.value
    val alpha = if (fraction < 0.65f) 1f else ((1f - fraction) / 0.35f).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .offset {
                IntOffset(
                    (heart.xDp + heart.driftDp * fraction).dp.roundToPx(),
                    (heart.yDp - heart.riseDp * fraction).dp.roundToPx(),
                )
            }
            .size(
                width = (14 * heart.scale).dp,
                height = (12 * heart.scale).dp,
            )
            .graphicsLayer { this.alpha = alpha }
            .testTag(CAT_HEART_TAG),
    ) {
        val cell = size.width / FLOATING_HEART.first().length
        FLOATING_HEART.forEachIndexed { y, row ->
            row.forEachIndexed { x, symbol ->
                if (symbol == 'X') {
                    drawRect(
                        color = heart.color,
                        topLeft = Offset(x * cell, y * cell),
                        size = Size(cell + 0.4f, cell + 0.4f),
                    )
                }
            }
        }
    }
}
