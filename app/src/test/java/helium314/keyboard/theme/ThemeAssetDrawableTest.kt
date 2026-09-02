// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeAssetDrawableTest {
    @Test
    fun nineSlicePreservesCornersAtDifferentAspectRatio() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            setPixel(0, 0, Color.RED)
            setPixel(2, 0, Color.BLUE)
            setPixel(0, 2, Color.YELLOW)
            setPixel(2, 2, Color.MAGENTA)
        }
        val drawable = ThemeNineSliceDrawable(
            source,
            ThemeAssetInsets(leftPx = 1, topPx = 1, rightPx = 1, bottomPx = 1),
            cornerScale = 1f,
        )
        val target = Bitmap.createBitmap(9, 7, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, target.width, target.height)

        drawable.draw(Canvas(target))

        assertEquals(Color.RED, target.getPixel(0, 0))
        assertEquals(Color.BLUE, target.getPixel(8, 0))
        assertEquals(Color.YELLOW, target.getPixel(0, 6))
        assertEquals(Color.MAGENTA, target.getPixel(8, 6))
        assertEquals(Color.GREEN, target.getPixel(4, 3))
    }

    @Test
    fun bitmapRenderingUsesTheSharedNineSliceContract() {
        val source = Bitmap.createBitmap(3, 3, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.GREEN)
            setPixel(0, 0, Color.RED)
            setPixel(2, 2, Color.MAGENTA)
        }
        val drawable = ThemeAssetRendering.bitmapDrawable(
            source,
            ThemeAssetRenderSpec(
                mode = ThemeAssetScaleMode.NINE_SLICE,
                insets = ThemeAssetInsets(1, 1, 1, 1),
            ),
        )
        val target = Bitmap.createBitmap(11, 5, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, target.width, target.height)

        drawable.draw(Canvas(target))

        assertEquals(Color.RED, target.getPixel(0, 0))
        assertEquals(Color.MAGENTA, target.getPixel(10, 4))
        assertEquals(Color.GREEN, target.getPixel(5, 2))
    }
}
