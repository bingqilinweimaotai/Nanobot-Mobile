package dev.nanobot.android

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import kotlin.math.max
import kotlin.math.min

/** 配色。预设取自 dsh-go 的六套低饱和配色，另加自定义色相与 Android 12 的壁纸取色。 */
enum class AccentTheme(
    val storageValue: String,
    val label: String,
    internal val lightPrimary: Color = Color.Unspecified,
    internal val lightOnPrimary: Color = Color.Unspecified,
    internal val lightPrimaryContainer: Color = Color.Unspecified,
    internal val lightOnPrimaryContainer: Color = Color.Unspecified,
    internal val darkPrimary: Color = Color.Unspecified,
    internal val darkOnPrimary: Color = Color.Unspecified,
    internal val darkPrimaryContainer: Color = Color.Unspecified,
    internal val darkOnPrimaryContainer: Color = Color.Unspecified,
    internal val tintHue: Double = 266.0,
    internal val tintChroma: Double = 1.0,
) {
    Mist(
        storageValue = "mist",
        label = "雾蓝",
        lightPrimary = Color(0xFF4B67B4),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFDEE8FF),
        lightOnPrimaryContainer = Color(0xFF213262),
        darkPrimary = Color(0xFF97B1F0),
        darkOnPrimary = Color(0xFF121C34),
        darkPrimaryContainer = Color(0xFF2E3C5F),
        darkOnPrimaryContainer = Color(0xFFD8E1F6),
        tintHue = 266.0,
        tintChroma = 1.0,
    ),
    Daiqing(
        storageValue = "daiqing",
        label = "黛青",
        lightPrimary = Color(0xFF236185),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFDBEAF5),
        lightOnPrimaryContainer = Color(0xFF123950),
        darkPrimary = Color(0xFF8CB9D7),
        darkOnPrimary = Color(0xFF0D1F2B),
        darkPrimaryContainer = Color(0xFF274152),
        darkOnPrimaryContainer = Color(0xFFD5E3ED),
        tintHue = 238.0,
        tintChroma = 0.9,
    ),
    Dusk(
        storageValue = "dusk",
        label = "暮紫",
        lightPrimary = Color(0xFF726291),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFEAE5F4),
        lightOnPrimaryContainer = Color(0xFF392F4C),
        darkPrimary = Color(0xFFB8ABD2),
        darkOnPrimary = Color(0xFF1F1A29),
        darkPrimaryContainer = Color(0xFF40394F),
        darkOnPrimaryContainer = Color(0xFFE2DEEC),
        tintHue = 295.0,
        tintChroma = 0.85,
    ),
    Turquoise(
        storageValue = "turquoise",
        label = "松石",
        lightPrimary = Color(0xFF34766F),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFDBECE9),
        lightOnPrimaryContainer = Color(0xFF123D39),
        darkPrimary = Color(0xFF8DBEB7),
        darkOnPrimary = Color(0xFF0D211F),
        darkPrimaryContainer = Color(0xFF274340),
        darkOnPrimaryContainer = Color(0xFFD6E5E2),
        tintHue = 190.0,
        tintChroma = 0.7,
    ),
    Amber(
        storageValue = "amber",
        label = "琥珀",
        lightPrimary = Color(0xFF8D6636),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFF2E6D9),
        lightOnPrimaryContainer = Color(0xFF483011),
        darkPrimary = Color(0xFFCCAC87),
        darkOnPrimary = Color(0xFF271A0C),
        darkPrimaryContainer = Color(0xFF4C3A25),
        darkOnPrimaryContainer = Color(0xFFEADFD3),
        tintHue = 75.0,
        tintChroma = 0.75,
    ),
    Graphite(
        storageValue = "graphite",
        label = "石墨",
        lightPrimary = Color(0xFF2C2E32),
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = Color(0xFFE3E6EC),
        lightOnPrimaryContainer = Color(0xFF2C2E32),
        darkPrimary = Color(0xFFE5E8ED),
        darkOnPrimary = Color(0xFF191B1E),
        darkPrimaryContainer = Color(0xFF3B3D41),
        darkOnPrimaryContainer = Color(0xFFDEE1E7),
        tintHue = 262.0,
        tintChroma = 0.25,
    ),

    /** 由色相现推整套配色。 */
    Custom("custom", "自定义"),

    /** Android 12 起使用系统壁纸的动态配色。 */
    Wallpaper("wallpaper", "跟随壁纸"),
    ;

    companion object {
        val presets: List<AccentTheme> = listOf(Mist, Daiqing, Dusk, Turquoise, Amber, Graphite)

        fun fromStorage(value: String?): AccentTheme =
            entries.firstOrNull { it.storageValue == value } ?: Mist
    }
}

/** 自定义色相的初始值：雾蓝所在的色相。 */
const val DEFAULT_CUSTOM_HUE: Float = 266f

/** 明暗模式最终落到哪一边。 */
fun ThemeMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.System -> systemDark
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}

/** 一套配色在浅色 / 深色下的强调色。 */
internal data class AccentColors(
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightPrimaryContainer: Color,
    val lightOnPrimaryContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkPrimaryContainer: Color,
    val darkOnPrimaryContainer: Color,
    val tintHue: Double,
    val tintChroma: Double,
) {
    fun primaryFor(dark: Boolean): Color = if (dark) darkPrimary else lightPrimary
}

internal fun accentColors(accent: AccentTheme, customHue: Float): AccentColors = when (accent) {
    AccentTheme.Custom -> customAccentColors(customHue)
    // 壁纸配色由动态方案接管；这里回退到雾蓝，供预览与旧系统使用。
    AccentTheme.Wallpaper -> accentColors(AccentTheme.Mist, customHue)
    else -> AccentColors(
        lightPrimary = accent.lightPrimary,
        lightOnPrimary = accent.lightOnPrimary,
        lightPrimaryContainer = accent.lightPrimaryContainer,
        lightOnPrimaryContainer = accent.lightOnPrimaryContainer,
        darkPrimary = accent.darkPrimary,
        darkOnPrimary = accent.darkOnPrimary,
        darkPrimaryContainer = accent.darkPrimaryContainer,
        darkOnPrimaryContainer = accent.darkOnPrimaryContainer,
        tintHue = accent.tintHue,
        tintChroma = accent.tintChroma,
    )
}

/**
 * 把雾蓝那套中性色转到 [tintHue]，彩度乘 [tintChroma]，明度保持不变。
 * 这样每套配色的文字对比度、层次都一样，只有冷暖不同。
 */
private class Neutrals(
    val lightBackground: Color,
    val lightSurface: Color,
    val lightSurfaceVariant: Color,
    val lightOnSurface: Color,
    val lightOnSurfaceVariant: Color,
    val lightOutline: Color,
    val lightOutlineVariant: Color,
    val lightSecondary: Color,
    val lightSecondaryContainer: Color,
    val lightSurfaceContainer: Color,
    val lightSurfaceContainerHigh: Color,
    val lightSurfaceContainerHighest: Color,
    val lightSurfaceContainerLow: Color,
    val lightSurfaceContainerLowest: Color,
    val lightSurfaceBright: Color,
    val lightSurfaceDim: Color,
    val darkBackground: Color,
    val darkSurface: Color,
    val darkSurfaceVariant: Color,
    val darkOnSurface: Color,
    val darkOnSurfaceVariant: Color,
    val darkOutline: Color,
    val darkOutlineVariant: Color,
    val darkSecondary: Color,
    val darkSecondaryContainer: Color,
    val darkOnSecondary: Color,
    val darkSurfaceContainer: Color,
    val darkSurfaceContainerHigh: Color,
    val darkSurfaceContainerHighest: Color,
    val darkSurfaceContainerLow: Color,
    val darkSurfaceContainerLowest: Color,
    val darkSurfaceBright: Color,
    val darkSurfaceDim: Color,
) {
    companion object {
        private const val BASE_HUE = 266.0

        fun of(tintHue: Double, tintChroma: Double): Neutrals {
            if (tintHue == BASE_HUE && tintChroma == 1.0) return base
            val shift = tintHue - BASE_HUE
            fun t(color: Color): Color = color.retint(shift, tintChroma)
            return Neutrals(
                lightBackground = t(base.lightBackground),
                lightSurface = t(base.lightSurface),
                lightSurfaceVariant = t(base.lightSurfaceVariant),
                lightOnSurface = t(base.lightOnSurface),
                lightOnSurfaceVariant = t(base.lightOnSurfaceVariant),
                lightOutline = t(base.lightOutline),
                lightOutlineVariant = t(base.lightOutlineVariant),
                lightSecondary = t(base.lightSecondary),
                lightSecondaryContainer = t(base.lightSecondaryContainer),
                lightSurfaceContainer = t(base.lightSurfaceContainer),
                lightSurfaceContainerHigh = t(base.lightSurfaceContainerHigh),
                lightSurfaceContainerHighest = t(base.lightSurfaceContainerHighest),
                lightSurfaceContainerLow = t(base.lightSurfaceContainerLow),
                lightSurfaceContainerLowest = t(base.lightSurfaceContainerLowest),
                lightSurfaceBright = t(base.lightSurfaceBright),
                lightSurfaceDim = t(base.lightSurfaceDim),
                darkBackground = t(base.darkBackground),
                darkSurface = t(base.darkSurface),
                darkSurfaceVariant = t(base.darkSurfaceVariant),
                darkOnSurface = t(base.darkOnSurface),
                darkOnSurfaceVariant = t(base.darkOnSurfaceVariant),
                darkOutline = t(base.darkOutline),
                darkOutlineVariant = t(base.darkOutlineVariant),
                darkSecondary = t(base.darkSecondary),
                darkSecondaryContainer = t(base.darkSecondaryContainer),
                darkOnSecondary = t(base.darkOnSecondary),
                darkSurfaceContainer = t(base.darkSurfaceContainer),
                darkSurfaceContainerHigh = t(base.darkSurfaceContainerHigh),
                darkSurfaceContainerHighest = t(base.darkSurfaceContainerHighest),
                darkSurfaceContainerLow = t(base.darkSurfaceContainerLow),
                darkSurfaceContainerLowest = t(base.darkSurfaceContainerLowest),
                darkSurfaceBright = t(base.darkSurfaceBright),
                darkSurfaceDim = t(base.darkSurfaceDim),
            )
        }

        /** 雾蓝的中性色（与 dsh-go 的晨雾 / 深海石板一致）。 */
        private val base = Neutrals(
            lightBackground = Color(0xFFF0F4FD),
            lightSurface = Color(0xFFFBFCFF),
            lightSurfaceVariant = Color(0xFFEEF1F9),
            lightOnSurface = Color(0xFF1E232E),
            lightOnSurfaceVariant = Color(0xFF4F5562),
            lightOutline = Color(0xFF7E838D),
            lightOutlineVariant = Color(0xFFDCE0E8),
            lightSecondary = Color(0xFF545E6F),
            lightSecondaryContainer = Color(0xFFE2E6F0),
            // M3's surface container roles: menus, sheets and elevated cards read these instead
            // of surface/surfaceVariant. Leaving them unset keeps Material's baseline lavender.
            lightSurfaceContainer = Color(0xFFF3F5FC),
            lightSurfaceContainerHigh = Color(0xFFECF0F8),
            lightSurfaceContainerHighest = Color(0xFFE6EBF4),
            lightSurfaceContainerLow = Color(0xFFF8FAFE),
            lightSurfaceContainerLowest = Color(0xFFFFFFFF),
            lightSurfaceBright = Color(0xFFFBFCFF),
            lightSurfaceDim = Color(0xFFDFE4EE),
            darkBackground = Color(0xFF1C1F28),
            darkSurface = Color(0xFF232730),
            darkSurfaceVariant = Color(0xFF272C36),
            darkOnSurface = Color(0xFFE6E9F0),
            darkOnSurfaceVariant = Color(0xFFB6BBC5),
            darkOutline = Color(0xFF737781),
            darkOutlineVariant = Color(0xFF343841),
            darkSecondary = Color(0xFFB3BED2),
            darkSecondaryContainer = Color(0xFF2D323C),
            darkOnSecondary = Color(0xFF1A2230),
            darkSurfaceContainer = Color(0xFF252A33),
            darkSurfaceContainerHigh = Color(0xFF2A303A),
            darkSurfaceContainerHighest = Color(0xFF303641),
            darkSurfaceContainerLow = Color(0xFF20242C),
            darkSurfaceContainerLowest = Color(0xFF191C23),
            darkSurfaceBright = Color(0xFF2C313C),
            darkSurfaceDim = Color(0xFF1B1E26),
        )
    }
}

private val LightError = Color(0xFFA54743)
private val LightOnError = Color(0xFFFDF9F8)
private val LightErrorContainer = Color(0xFFFFE2E0)
private val LightOnErrorContainer = Color(0xFF642724)
private val DarkError = Color(0xFFE69089)
private val DarkOnError = Color(0xFF331513)
private val DarkErrorContainer = Color(0xFF542C29)
private val DarkOnErrorContainer = Color(0xFFF8D9D7)

/** 由配色与明暗生成 Material 3 的 ColorScheme。 */
internal fun nanobotColorScheme(accent: AccentTheme, customHue: Float, dark: Boolean): ColorScheme {
    val a = accentColors(accent, customHue)
    val n = Neutrals.of(a.tintHue, a.tintChroma)
    return if (dark) {
        darkColorScheme(
            primary = a.darkPrimary,
            onPrimary = a.darkOnPrimary,
            primaryContainer = a.darkPrimaryContainer,
            onPrimaryContainer = a.darkOnPrimaryContainer,
            secondary = n.darkSecondary,
            onSecondary = n.darkOnSecondary,
            secondaryContainer = n.darkSecondaryContainer,
            onSecondaryContainer = n.darkOnSurface,
            background = n.darkBackground,
            onBackground = n.darkOnSurface,
            surface = n.darkSurface,
            onSurface = n.darkOnSurface,
            surfaceVariant = n.darkSurfaceVariant,
            onSurfaceVariant = n.darkOnSurfaceVariant,
            surfaceContainer = n.darkSurfaceContainer,
            surfaceContainerHigh = n.darkSurfaceContainerHigh,
            surfaceContainerHighest = n.darkSurfaceContainerHighest,
            surfaceContainerLow = n.darkSurfaceContainerLow,
            surfaceContainerLowest = n.darkSurfaceContainerLowest,
            surfaceBright = n.darkSurfaceBright,
            surfaceDim = n.darkSurfaceDim,
            outline = n.darkOutline,
            outlineVariant = n.darkOutlineVariant,
            error = DarkError,
            onError = DarkOnError,
            errorContainer = DarkErrorContainer,
            onErrorContainer = DarkOnErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = a.lightPrimary,
            onPrimary = a.lightOnPrimary,
            primaryContainer = a.lightPrimaryContainer,
            onPrimaryContainer = a.lightOnPrimaryContainer,
            secondary = n.lightSecondary,
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = n.lightSecondaryContainer,
            onSecondaryContainer = n.lightOnSurface,
            background = n.lightBackground,
            onBackground = n.lightOnSurface,
            surface = n.lightSurface,
            onSurface = n.lightOnSurface,
            surfaceVariant = n.lightSurfaceVariant,
            onSurfaceVariant = n.lightOnSurfaceVariant,
            surfaceContainer = n.lightSurfaceContainer,
            surfaceContainerHigh = n.lightSurfaceContainerHigh,
            surfaceContainerHighest = n.lightSurfaceContainerHighest,
            surfaceContainerLow = n.lightSurfaceContainerLow,
            surfaceContainerLowest = n.lightSurfaceContainerLowest,
            surfaceBright = n.lightSurfaceBright,
            surfaceDim = n.lightSurfaceDim,
            outline = n.lightOutline,
            outlineVariant = n.lightOutlineVariant,
            error = LightError,
            onError = LightOnError,
            errorContainer = LightErrorContainer,
            onErrorContainer = LightOnErrorContainer,
        )
    }
}

/** 当前配色与明暗对应的方案；壁纸取色在 Android 12 以下回退到雾蓝。 */
internal fun nanobotScheme(
    context: Context,
    accent: AccentTheme,
    customHue: Float,
    dark: Boolean,
): ColorScheme =
    if (accent == AccentTheme.Wallpaper && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }.getOrElse { nanobotColorScheme(AccentTheme.Mist, customHue, dark) }
    } else {
        nanobotColorScheme(accent, customHue, dark)
    }

@Composable
fun NanobotTheme(
    themeMode: ThemeMode = ThemeMode.System,
    accent: AccentTheme = AccentTheme.Mist,
    customHue: Float = DEFAULT_CUSTOM_HUE,
    language: AppLanguage = AppLanguage.English,
    fontSize: AppFontSize = AppFontSize.Standard,
    content: @Composable () -> Unit,
) {
    val dark = themeMode.resolveDark(isSystemInDarkTheme())
    val context = LocalContext.current
    val scheme = remember(accent, customHue, dark) {
        nanobotScheme(context, accent, customHue, dark)
    }
    val strings = remember(language) { stringsFor(language) }
    val density = LocalDensity.current
    // Scale sp only; dp stays put so layout and icons keep their proportions.
    val scaledDensity = remember(density, fontSize) {
        Density(density.density, density.fontScale * fontSize.scale)
    }
    CompositionLocalProvider(
        LocalStrings provides strings,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** 自定义色相：固定明度与彩度，按 dsh-go 的比例推出各槽位，再对主色做对比度守卫。 */
private fun customAccentColors(hue: Float): AccentColors {
    val h = (((hue % 360f) + 360f) % 360f).toDouble()
    val chroma = 0.11
    val tintChroma = 0.8 * min(1.0, chroma / 0.1255)
    val n = Neutrals.of(h, tintChroma)
    fun tone(lightness: Double, ratio: Double): Color = Oklch(lightness, chroma * ratio, h).toColor()

    val onSeedDark = tone(0.231, 0.39)
    val lightSeed = guard(
        Oklch(0.53, chroma, h),
        dark = false,
        surface = n.lightSurface,
        onAccent = Color(0xFFFFFFFF),
    )
    val darkSeed = guard(
        Oklch(0.764, chroma * 0.76, h),
        dark = true,
        surface = n.darkBackground,
        onAccent = onSeedDark,
    )
    return AccentColors(
        lightPrimary = lightSeed,
        lightOnPrimary = Color(0xFFFFFFFF),
        lightPrimaryContainer = tone(0.93, 0.26),
        lightOnPrimaryContainer = tone(0.33, 0.70),
        darkPrimary = darkSeed,
        darkOnPrimary = onSeedDark,
        darkPrimaryContainer = tone(0.361, 0.51),
        darkOnPrimaryContainer = tone(0.909, 0.24),
        tintHue = h,
        tintChroma = tintChroma,
    )
}

/** 沿明度移动 [start]，直到主色对页面 ≥ 3:1 且主色上的文字 ≥ 4.5:1。 */
private fun guard(start: Oklch, dark: Boolean, surface: Color, onAccent: Color): Color {
    fun ok(color: Color): Boolean =
        contrastRatio(color, surface) >= 3.0 && contrastRatio(onAccent, color) >= 4.5

    val first = start.toColor()
    if (ok(first)) return first
    var pass = if (dark) 1.0 else 0.0
    var fail = start.l
    repeat(24) {
        val mid = (pass + fail) / 2
        if (ok(start.copy(l = mid).toColor())) pass = mid else fail = mid
    }
    return start.copy(l = pass).toColor()
}

/** WCAG 2.x 相对亮度对比度。 */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = a.luminance().toDouble()
    val lb = b.luminance().toDouble()
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

private data class Oklch(val l: Double, val c: Double, val h: Double)

private fun Color.retint(hueShift: Double, chromaScale: Double): Color {
    val o = toOklch()
    val hue = ((o.h + hueShift) % 360.0 + 360.0) % 360.0
    return Oklch(o.l, o.c * chromaScale, hue).toColor()
}

private fun Color.toOklch(): Oklch {
    fun linear(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    val r = linear(red)
    val g = linear(green)
    val b = linear(blue)
    val l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    val m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    val s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    val lRoot = Math.cbrt(l)
    val mRoot = Math.cbrt(m)
    val sRoot = Math.cbrt(s)
    val lightness = 0.2104542553 * lRoot + 0.7936177850 * mRoot - 0.0040720468 * sRoot
    val a = 1.9779984951 * lRoot - 2.4285922050 * mRoot + 0.4505937099 * sRoot
    val bb = 0.0259040371 * lRoot + 0.7827717662 * mRoot - 0.8086757660 * sRoot
    return Oklch(
        l = lightness,
        c = Math.hypot(a, bb),
        h = Math.toDegrees(Math.atan2(bb, a)),
    )
}

private fun Oklch.toColor(): Color {
    val radians = Math.toRadians(h)
    val a = c * Math.cos(radians)
    val b = c * Math.sin(radians)
    val lRoot = l + 0.3963377774 * a + 0.2158037573 * b
    val mRoot = l - 0.1055613458 * a - 0.0638541728 * b
    val sRoot = l - 0.0894841775 * a - 1.2914855480 * b
    val l3 = lRoot * lRoot * lRoot
    val m3 = mRoot * mRoot * mRoot
    val s3 = sRoot * sRoot * sRoot
    val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
    val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
    val bb = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

    fun encode(value: Double): Float {
        val v = value.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) {
            (v * 12.92).toFloat()
        } else {
            (1.055 * Math.pow(v, 1.0 / 2.4) - 0.055).toFloat()
        }
    }

    return Color(encode(r), encode(g), encode(bb), 1f)
}
