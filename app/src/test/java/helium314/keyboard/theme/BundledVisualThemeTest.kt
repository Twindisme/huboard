// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BundledVisualThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeTest
    fun setUp() = VisualThemeManager.clearCache()

    @Test
    fun huTaoUsesPortableRasterAndSvgAssets() {
        val theme = VisualThemeManager.availableThemes(context).first { it.id == "hu_tao" }

        assertTrue(theme.hasCustomKeys)
        assertNotNull(theme.bitmap(context, ThemeAsset.KEY_NORMAL))
        assertNotNull(theme.drawable(context, ThemeAsset.ICON_BACKSPACE))
        assertNotNull(theme.drawable(context, ThemeAsset.TOOLBAR_SEARCH))
        assertNotNull(theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_NORMAL))
        assertNotNull(theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_PINNED))
        assertNotNull(theme.drawable(context, ThemeAsset.CLIPBOARD_PIN))
        assertFalse(theme.showMoreSuggestionsHint)
        assertEquals(-2, theme.clipboardSuggestionContentOffsetYDp)
        assertTrue(theme.manifest.keyRenderer.centerSpecialKeyArtworkHorizontally)
        assertEquals(32, theme.manifest.clipboardRenderer.entryInsets.leftPx)
        assertEquals(75, theme.manifest.clipboardRenderer.pinnedEntryInsets.rightPx)
        assertEquals(0.62f, theme.manifest.clipboardRenderer.entryCornerScale)
        assertTrue(
            VisualThemeManager.availableThemes(context)
                .first { it.id == "classic" }
                .showMoreSuggestionsHint,
        )
    }

    @Test
    fun nocturneWispLoadsCompleteVectorTheme() {
        val theme = VisualThemeManager.availableThemes(context)
            .first { it.id == "nocturne_wisp" }

        assertEquals(1, theme.manifest.schemaVersion)
        assertTrue(theme.hasCustomKeys)
        assertTrue(theme.hasCustomKeyPreview)
        assertTrue(theme.hasKeyPressAnimation)
        assertTrue(theme.hasCustomToolbar)
        assertTrue(theme.hasCustomClipboard)
        assertTrue(theme.hasKeyboardBackground)
        assertNotNull(theme.drawable(context, ThemeAsset.THEME_THUMBNAIL))
        assertNotNull(theme.drawable(context, ThemeAsset.KEYBOARD_BACKGROUND))
        assertNotNull(theme.drawable(context, ThemeAsset.KEY_PREVIEW))
        val script = assertNotNull(theme.manifest.keyPressAnimation?.script)
        assertEquals(1, script.apiVersion)
        assertEquals("animation.wisp_script", script.asset)
        val source = assertNotNull(
            theme.bytes(context, script.asset, VisualThemeValidator.MAX_SCRIPT_BYTES),
        ).decodeToString()
        assertTrue(source.contains("motion.glow"))
        assertTrue(source.contains("return elapsed < 3.5"))
    }

    @Test
    fun astralWeaveLoadsThemeOwnedConstellation() {
        val theme = VisualThemeManager.availableThemes(context)
            .first { it.id == "astral_weave" }

        assertFalse(theme.hasCustomKeys)
        assertTrue(theme.hasKeyPressAnimation)
        assertTrue(theme.hasKeyboardBackground)
        assertNotNull(theme.drawable(context, ThemeAsset.THEME_THUMBNAIL))
        assertNotNull(theme.drawable(context, ThemeAsset.KEYBOARD_BACKGROUND))
        val script = assertNotNull(theme.manifest.keyPressAnimation?.script)
        assertEquals("animation.constellation_script", script.asset)
        val source = assertNotNull(
            theme.bytes(context, script.asset, VisualThemeValidator.MAX_SCRIPT_BYTES),
        ).decodeToString()
        assertTrue(source.contains("table.sort(candidates"))
        assertTrue(source.contains("SESSION_GAP_SECONDS"))
        assertTrue(source.contains("segmentsCross"))
        assertTrue(source.contains("motion.line"))
        assertTrue(source.contains("motion.glow"))
    }
}
