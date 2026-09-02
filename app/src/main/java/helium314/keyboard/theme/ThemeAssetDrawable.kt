// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import androidx.core.graphics.withSave
import kotlin.math.roundToInt

internal fun Drawable.withRenderSpec(
    spec: ThemeAssetRenderSpec,
    bitmap: () -> Bitmap?,
): Drawable = when (spec.mode) {
    ThemeAssetScaleMode.STRETCH -> this
    ThemeAssetScaleMode.FIT,
    ThemeAssetScaleMode.CROP -> ThemeScaledDrawable(this, spec.mode)
    ThemeAssetScaleMode.NINE_SLICE -> bitmap()?.let {
        ThemeNineSliceDrawable(it, spec.insets, spec.cornerScale)
    } ?: this
}

/** Creates the same renderer for bitmaps that are drawn directly by the keyboard canvas. */
object ThemeAssetRendering {
    @JvmStatic
    fun bitmapDrawable(bitmap: Bitmap, spec: ThemeAssetRenderSpec): Drawable = when (spec.mode) {
        ThemeAssetScaleMode.STRETCH,
        ThemeAssetScaleMode.FIT,
        ThemeAssetScaleMode.CROP -> ThemeBitmapDrawable(bitmap, spec.mode)
        ThemeAssetScaleMode.NINE_SLICE -> ThemeNineSliceDrawable(
            bitmap,
            spec.insets,
            spec.cornerScale,
        )
    }
}

private class ThemeBitmapDrawable(
    private val bitmap: Bitmap,
    private val mode: ThemeAssetScaleMode,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (mode == ThemeAssetScaleMode.STRETCH) {
            destination.set(bounds)
            canvas.drawBitmap(bitmap, null, destination, paint)
            return
        }
        val scale = when (mode) {
            ThemeAssetScaleMode.FIT -> minOf(
                bounds.width() / bitmap.width.toFloat(),
                bounds.height() / bitmap.height.toFloat(),
            )
            ThemeAssetScaleMode.CROP -> maxOf(
                bounds.width() / bitmap.width.toFloat(),
                bounds.height() / bitmap.height.toFloat(),
            )
            else -> 1f
        }
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        destination.set(
            bounds.exactCenterX() - width / 2f,
            bounds.exactCenterY() - height / 2f,
            bounds.exactCenterX() + width / 2f,
            bounds.exactCenterY() + height / 2f,
        )
        canvas.withSave {
            if (mode == ThemeAssetScaleMode.CROP) clipRect(bounds)
            drawBitmap(bitmap, null, destination, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in the Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = bitmap.width

    override fun getIntrinsicHeight(): Int = bitmap.height
}

/** Scales imported artwork without requiring Android resource compilation. */
private class ThemeScaledDrawable(
    private val source: Drawable,
    private val mode: ThemeAssetScaleMode,
) : Drawable() {
    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val sourceWidth = source.intrinsicWidth.takeIf { it > 0 } ?: bounds.width()
        val sourceHeight = source.intrinsicHeight.takeIf { it > 0 } ?: bounds.height()
        val scale = when (mode) {
            ThemeAssetScaleMode.FIT -> minOf(
                bounds.width() / sourceWidth.toFloat(),
                bounds.height() / sourceHeight.toFloat(),
            )
            ThemeAssetScaleMode.CROP -> maxOf(
                bounds.width() / sourceWidth.toFloat(),
                bounds.height() / sourceHeight.toFloat(),
            )
            else -> 1f
        }
        val width = sourceWidth * scale
        val height = sourceHeight * scale
        val left = bounds.exactCenterX() - width / 2f
        val top = bounds.exactCenterY() - height / 2f
        canvas.withSave {
            if (mode == ThemeAssetScaleMode.CROP) clipRect(bounds)
            source.setBounds(
                left.roundToInt(),
                top.roundToInt(),
                (left + width).roundToInt(),
                (top + height).roundToInt(),
            )
            source.draw(this)
        }
    }

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = source.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in the Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int = source.intrinsicWidth

    override fun getIntrinsicHeight(): Int = source.intrinsicHeight
}

/** Keeps corners and edge artwork intact while stretching the center of an imported bitmap. */
internal class ThemeNineSliceDrawable(
    private val bitmap: Bitmap,
    insets: ThemeAssetInsets,
    private val cornerScale: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val source = Rect()
    private val destination = RectF()
    private val left = insets.leftPx.coerceIn(0, bitmap.width)
    private val top = insets.topPx.coerceIn(0, bitmap.height)
    private val right = insets.rightPx.coerceIn(0, bitmap.width - left)
    private val bottom = insets.bottomPx.coerceIn(0, bitmap.height - top)

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val horizontalCapWidth = left + right
        val verticalCapHeight = top + bottom
        val scale = minOf(
            cornerScale,
            if (horizontalCapWidth == 0) Float.POSITIVE_INFINITY
            else width / horizontalCapWidth,
            if (verticalCapHeight == 0) Float.POSITIVE_INFINITY
            else height / verticalCapHeight,
        ).coerceAtLeast(0f)
        val destinationLeft = left * scale
        val destinationRight = right * scale
        val destinationTop = top * scale
        val destinationBottom = bottom * scale
        val sourceX = intArrayOf(0, left, bitmap.width - right, bitmap.width)
        val sourceY = intArrayOf(0, top, bitmap.height - bottom, bitmap.height)
        val destinationX = floatArrayOf(
            bounds.left.toFloat(),
            bounds.left + destinationLeft,
            bounds.right - destinationRight,
            bounds.right.toFloat(),
        )
        val destinationY = floatArrayOf(
            bounds.top.toFloat(),
            bounds.top + destinationTop,
            bounds.bottom - destinationBottom,
            bounds.bottom.toFloat(),
        )
        for (row in 0 until 3) {
            for (column in 0 until 3) {
                if (sourceX[column] == sourceX[column + 1] ||
                    sourceY[row] == sourceY[row + 1] ||
                    destinationX[column] == destinationX[column + 1] ||
                    destinationY[row] == destinationY[row + 1]
                ) {
                    continue
                }
                source.set(
                    sourceX[column],
                    sourceY[row],
                    sourceX[column + 1],
                    sourceY[row + 1],
                )
                destination.set(
                    destinationX[column],
                    destinationY[row],
                    destinationX[column + 1],
                    destinationY[row + 1],
                )
                canvas.drawBitmap(bitmap, source, destination, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun getAlpha(): Int = paint.alpha

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in the Android framework")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
