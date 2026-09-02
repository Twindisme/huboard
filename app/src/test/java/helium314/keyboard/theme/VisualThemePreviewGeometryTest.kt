// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import kotlin.test.Test
import kotlin.test.assertEquals

class VisualThemePreviewGeometryTest {
    @Test
    fun keepsNormalizedBoundsIndependentOfSourceSize() {
        val expected = ThemeNormalizedRect(0.2f, 0.3f, 0.8f, 0.9f)
        val resolved = VisualThemePreviewGeometry.resolveFaceBounds(
            KeyPreviewConfig(faceBounds = expected),
            sourceWidthPx = 2_000f,
            sourceHeightPx = 1_000f,
        )

        assertEquals(expected, resolved)
    }

    @Test
    fun convertsLegacyPixelBounds() {
        val resolved = VisualThemePreviewGeometry.resolveFaceBounds(
            KeyPreviewConfig(
                bitmapWidthPx = 200f,
                bitmapHeightPx = 400f,
                faceLeftPx = 40f,
                faceTopPx = 100f,
                faceRightPx = 160f,
                faceBottomPx = 360f,
            ),
            sourceWidthPx = 800f,
            sourceHeightPx = 800f,
        )

        assertEquals(0.2f, resolved.left)
        assertEquals(0.25f, resolved.top)
        assertEquals(0.8f, resolved.right)
        assertEquals(0.9f, resolved.bottom)
    }
}
