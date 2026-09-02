// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

object VisualThemePreviewGeometry {
    @JvmStatic
    fun resolveFaceBounds(
        config: KeyPreviewConfig,
        sourceWidthPx: Float,
        sourceHeightPx: Float,
    ): ThemeNormalizedRect {
        config.faceBounds?.let { return it }
        val width = config.bitmapWidthPx.takeIf { it > 0f } ?: sourceWidthPx
        val height = config.bitmapHeightPx.takeIf { it > 0f } ?: sourceHeightPx
        require(width > 0f && height > 0f) { "Key preview source has no dimensions" }
        return ThemeNormalizedRect(
            left = config.faceLeftPx / width,
            top = config.faceTopPx / height,
            right = config.faceRightPx / width,
            bottom = config.faceBottomPx / height,
        )
    }
}
