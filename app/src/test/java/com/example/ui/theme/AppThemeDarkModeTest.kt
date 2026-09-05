package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The app has no emulator in CI, so "does the system toggle actually reach the UI"
 * is proved here instead: Robolectric's `night` qualifier is what `isSystemInDarkTheme()`
 * reads, and `AppTheme` is entered exactly as MainActivity enters it — trailing lambda
 * only, no explicit darkTheme argument. If the default ever stops following the system,
 * or the dark scheme stops differing from the light one, these fail.
 */
@RunWith(AndroidJUnit4::class)
class AppThemeDarkModeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun schemeInAmbientTheme(): ColorScheme {
        lateinit var captured: ColorScheme
        composeTestRule.setContent {
            AppTheme {
                captured = MaterialTheme.colorScheme
            }
        }
        return captured
    }

    @Test
    @Config(qualifiers = "night")
    fun `system dark mode selects the dark scheme`() {
        val scheme = schemeInAmbientTheme()
        assertEquals(ZadIosBackgroundDark, scheme.background)
        assertEquals(ZadIosSurfaceDark, scheme.surface)
        assertEquals(ZadNeutralOnDark, scheme.onSurface)
        assertEquals(ZadEmeraldOnDark, scheme.primary)
    }

    @Test
    @Config(qualifiers = "notnight")
    fun `system light mode still selects the light scheme`() {
        val scheme = schemeInAmbientTheme()
        assertEquals(ZadIosBackground, scheme.background)
        assertEquals(ZadIosSurface, scheme.surface)
        assertEquals(ZadNeutralDark, scheme.onSurface)
        assertEquals(ZadForestEmerald, scheme.primary)
    }

    @Test
    fun `the two schemes actually differ on the base slots`() {
        // Guards against a dark scheme that is a copy of the light one — the failure
        // mode where dark mode "works" but nothing visibly changes.
        assertNotEquals(ZadColorScheme.background, ZadDarkColorScheme.background)
        assertNotEquals(ZadColorScheme.surface, ZadDarkColorScheme.surface)
        assertNotEquals(ZadColorScheme.onSurface, ZadDarkColorScheme.onSurface)
        assertNotEquals(ZadColorScheme.primary, ZadDarkColorScheme.primary)
        assertNotEquals(ZadColorScheme.outline, ZadDarkColorScheme.outline)
    }

    @Test
    fun `dark text is light and dark surfaces are dark`() {
        // Cheap contrast sanity check: on-colours must be lighter than the surfaces they
        // sit on, otherwise the palette is internally inconsistent regardless of taste.
        fun luminance(c: androidx.compose.ui.graphics.Color) = c.red + c.green + c.blue
        assert(luminance(ZadDarkColorScheme.onSurface) > luminance(ZadDarkColorScheme.surface))
        assert(luminance(ZadDarkColorScheme.onBackground) > luminance(ZadDarkColorScheme.background))
        assert(luminance(ZadDarkColorScheme.primary) > luminance(ZadDarkColorScheme.onPrimary))
    }
}
