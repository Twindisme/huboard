// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Rect

object VisualThemeSpriteAtlas {
    @JvmStatic
    fun frameRects(
        bitmapWidth: Int,
        bitmapHeight: Int,
        config: KeyPressSpriteAtlasConfig,
    ): Array<Rect> {
        require(bitmapWidth > 0 && bitmapHeight > 0) { "Sprite atlas has no dimensions" }
        require(config.columns > 0 && config.rows > 0) { "Sprite atlas grid must be positive" }
        val availableFrames = config.columns * config.rows
        val frameCount = config.frameCount ?: availableFrames
        require(frameCount in 1..availableFrames) { "Invalid sprite atlas frame count" }
        require(bitmapWidth % config.columns == 0 && bitmapHeight % config.rows == 0) {
            "Sprite atlas dimensions must divide evenly into its grid"
        }
        val frameWidth = bitmapWidth / config.columns
        val frameHeight = bitmapHeight / config.rows
        return Array(frameCount) { index ->
            val column = index % config.columns
            val row = index / config.columns
            Rect(
                column * frameWidth,
                row * frameHeight,
                (column + 1) * frameWidth,
                (row + 1) * frameHeight,
            )
        }
    }
}
