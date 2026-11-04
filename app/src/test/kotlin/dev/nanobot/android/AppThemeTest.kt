package dev.nanobot.android

import kotlin.test.Test
import kotlin.test.assertTrue

class AppThemeTest {
    @Test
    fun `preset accents keep primary text readable`() {
        for (accent in AccentTheme.presets) {
            for (dark in listOf(false, true)) {
                val scheme = nanobotColorScheme(accent, DEFAULT_CUSTOM_HUE, dark)
                val ratio = contrastRatio(scheme.primary, scheme.onPrimary)
                assertTrue(ratio >= 4.5, "$accent dark=$dark primary text contrast $ratio")
            }
        }
    }

    @Test
    fun `custom hues keep primary text readable`() {
        for (hue in 0..330 step 30) {
            for (dark in listOf(false, true)) {
                val scheme = nanobotColorScheme(AccentTheme.Custom, hue.toFloat(), dark)
                val ratio = contrastRatio(scheme.primary, scheme.onPrimary)
                assertTrue(ratio >= 4.5, "hue=$hue dark=$dark primary text contrast $ratio")
            }
        }
    }

    @Test
    fun `preset accents keep body text readable`() {
        for (accent in AccentTheme.presets) {
            for (dark in listOf(false, true)) {
                val scheme = nanobotColorScheme(accent, DEFAULT_CUSTOM_HUE, dark)
                val body = contrastRatio(scheme.onSurface, scheme.background)
                val muted = contrastRatio(scheme.onSurfaceVariant, scheme.surfaceVariant)
                assertTrue(body >= 4.5, "$accent dark=$dark body contrast $body")
                assertTrue(muted >= 4.5, "$accent dark=$dark muted contrast $muted")
            }
        }
    }

    @Test
    fun `presets are visually distinct`() {
        val primaries = AccentTheme.presets.map { accentColors(it, DEFAULT_CUSTOM_HUE).lightPrimary }
        assertTrue(primaries.toSet().size == primaries.size, "preset primaries should differ")
    }

    @Test
    fun `unknown stored accents fall back to mist`() {
        assertTrue(AccentTheme.fromStorage(null) == AccentTheme.Mist)
        assertTrue(AccentTheme.fromStorage("nope") == AccentTheme.Mist)
        assertTrue(AccentTheme.fromStorage("dusk") == AccentTheme.Dusk)
    }
}
