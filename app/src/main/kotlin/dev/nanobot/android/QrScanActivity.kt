package dev.nanobot.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.journeyapps.barcodescanner.Size
import com.journeyapps.barcodescanner.camera.CameraSettings
import com.journeyapps.barcodescanner.camera.PreviewScalingStrategy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Result contract for [QrScanActivity]. Returns the decoded text, or null if cancelled. */
class QrScanContract : ActivityResultContract<Unit, String?>() {
    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, QrScanActivity::class.java)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(QrScanActivity.EXTRA_CONTENTS)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
}

/**
 * Portrait, full-screen QR scanner modeled on the usual messaging-app scanners: live camera
 * preview, a dimmed surround with a corner-bracketed frame, a sweeping scan line and a torch
 * toggle. Uses zxing's [BarcodeView] for the camera and decoding.
 */
class QrScanActivity : ComponentActivity() {
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val hasTorch = packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        val preferences = SettingsStore(this)
        val strings = stringsFor(preferences.language)
        val fontSize = preferences.fontSize
        setContent {
            val density = LocalDensity.current
            val scaledDensity = remember(density, fontSize) {
                Density(density.density, density.fontScale * fontSize.scale)
            }
            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalDensity provides scaledDensity,
            ) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    ScanScreen(
                        hasTorch = hasTorch,
                        onClose = ::close,
                        onScanned = ::deliver,
                    )
                }
            }
        }
    }

    private fun close() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun deliver(contents: String) {
        if (delivered) return
        delivered = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CONTENTS, contents))
        finish()
    }

    companion object {
        const val EXTRA_CONTENTS = "SCAN_RESULT"
    }
}

@Composable
private fun ScanScreen(
    hasTorch: Boolean,
    onClose: () -> Unit,
    onScanned: (String) -> Unit,
) {
    var barcodeView by remember { mutableStateOf<BarcodeView?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    val strings = LocalStrings.current
    val progress = rememberInfiniteTransition(label = "scan-line").animateFloat(
        initialValue = 0.04f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan-line-progress",
    )
    val accent = Color(0xFF5B9CFF)

    LifecycleResumeEffect(barcodeView) {
        barcodeView?.resume()
        onPauseOrDispose { barcodeView?.pause() }
    }
    LaunchedEffect(barcodeView, torchOn) { barcodeView?.setTorch(torchOn) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val sidePx = min(widthPx * 0.72f, with(density) { 300.dp.toPx() })
        val leftPx = (widthPx - sidePx) / 2f
        val topPx = ((heightPx - sidePx) / 2f - heightPx * 0.05f)
            .coerceAtLeast(with(density) { 110.dp.toPx() })
        val hintTopPx = topPx + sidePx + with(density) { 26.dp.toPx() }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                BarcodeView(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    previewScalingStrategy = FillPreviewScalingStrategy()
                    cameraSettings = CameraSettings().apply {
                        isBarcodeSceneModeEnabled = true
                        isContinuousFocusEnabled = true
                    }
                    setDecoderFactory(DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE)))
                    decodeContinuous(
                        BarcodeCallback { result ->
                            result.text?.takeIf { it.isNotBlank() }?.let(onScanned)
                        },
                    )
                    barcodeView = this
                }
            },
        )

        Canvas(Modifier.fillMaxSize()) {
            drawScanOverlay(leftPx, topPx, sidePx, progress, accent)
        }

        Box(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            FilledIconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).align(Alignment.CenterStart),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.35f),
                    contentColor = Color.White,
                ),
            ) {
                Icon(Icons.Default.Close, contentDescription = strings.closeScanner)
            }
            Text(
                strings.scanQrCode,
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Text(
            strings.alignQrInFrame,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, hintTopPx.roundToInt()) }
                .padding(horizontal = 32.dp),
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (hasTorch) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledIconButton(
                    onClick = { torchOn = !torchOn },
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (torchOn) Color.White else Color.White.copy(alpha = 0.18f),
                        contentColor = if (torchOn) Color.Black else Color.White,
                    ),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_flash),
                        contentDescription = if (torchOn) strings.turnOffTorch else strings.turnOnTorch,
                    )
                }
                Text(
                    if (torchOn) strings.torchOn else strings.torch,
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

private fun DrawScope.drawScanOverlay(
    left: Float,
    top: Float,
    side: Float,
    progress: State<Float>,
    accent: Color,
) {
    val corner = 18.dp.toPx()
    val hole = Path().apply {
        addRoundRect(
            RoundRect(left, top, left + side, top + side, CornerRadius(corner)),
        )
    }

    // Dim everything outside the scan window.
    val scrim = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(ComposeRect(0f, 0f, size.width, size.height))
        addRoundRect(
            RoundRect(left, top, left + side, top + side, CornerRadius(corner)),
        )
    }
    drawPath(scrim, Color.Black.copy(alpha = 0.58f))

    val inset = corner + 8.dp.toPx()
    val lineStartX = left + inset
    val lineEndX = left + side - inset
    val lineY = top + corner + (side - corner * 2f) * progress.value
    clipPath(hole) {
        drawLine(
            color = accent.copy(alpha = 0.22f),
            start = Offset(lineStartX, lineY),
            end = Offset(lineEndX, lineY),
            strokeWidth = 9.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.4f),
                    accent,
                    Color.White,
                    accent,
                    accent.copy(alpha = 0.4f),
                    Color.Transparent,
                ),
                startX = lineStartX,
                endX = lineEndX,
            ),
            start = Offset(lineStartX, lineY),
            end = Offset(lineEndX, lineY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    drawPath(
        path = bracketPath(left, top, side, corner, 26.dp.toPx()),
        color = Color.White.copy(alpha = 0.95f),
        style = Stroke(
            width = 3.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        ),
    )
}

/** Corner brackets that follow the rounded corners of the scan window. */
private fun bracketPath(left: Float, top: Float, side: Float, radius: Float, arm: Float): Path {
    val right = left + side
    val bottom = top + side
    return Path().apply {
        // top-left
        moveTo(left, top + radius + arm)
        lineTo(left, top + radius)
        arcTo(
            ComposeRect(left, top, left + radius * 2f, top + radius * 2f),
            180f,
            90f,
            false,
        )
        lineTo(left + radius + arm, top)

        // top-right
        moveTo(right - radius - arm, top)
        lineTo(right - radius, top)
        arcTo(
            ComposeRect(right - radius * 2f, top, right, top + radius * 2f),
            270f,
            90f,
            false,
        )
        lineTo(right, top + radius + arm)

        // bottom-right
        moveTo(right, bottom - radius - arm)
        lineTo(right, bottom - radius)
        arcTo(
            ComposeRect(right - radius * 2f, bottom - radius * 2f, right, bottom),
            0f,
            90f,
            false,
        )
        lineTo(right - radius - arm, bottom)

        // bottom-left
        moveTo(left + radius + arm, bottom)
        lineTo(left + radius, bottom)
        arcTo(
            ComposeRect(left, bottom - radius * 2f, left + radius * 2f, bottom),
            90f,
            90f,
            false,
        )
        lineTo(left, bottom - radius - arm)
    }
}

/** Scales the camera preview to fill the view, cropping the surplus instead of letterboxing. */
private class FillPreviewScalingStrategy : PreviewScalingStrategy() {
    override fun scalePreview(previewSize: Size, viewSize: Size): Rect {
        val scale = max(
            viewSize.width.toFloat() / previewSize.width,
            viewSize.height.toFloat() / previewSize.height,
        )
        val width = (previewSize.width * scale).roundToInt()
        val height = (previewSize.height * scale).roundToInt()
        val left = (viewSize.width - width) / 2
        val top = (viewSize.height - height) / 2
        return Rect(left, top, left + width, top + height)
    }
}
