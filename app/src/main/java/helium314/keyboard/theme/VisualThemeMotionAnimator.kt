// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import helium314.keyboard.keyboard.Key
import kotlin.math.roundToInt

/** Converts bounded huBoard Motion commands into Android Canvas operations. */
class VisualThemeMotionAnimator(
    context: Context,
    private val theme: ResolvedVisualTheme,
    private val animation: KeyPressAnimationConfig,
    script: ThemeMotionScriptConfig,
) : AutoCloseable {
    private val applicationContext = context.applicationContext
    private val assetNames = theme.manifest.assets.keys.toTypedArray()
    private val bitmaps = arrayOfNulls<Bitmap>(assetNames.size)
    private val attemptedBitmaps = BooleanArray(assetNames.size)
    private val runtime = VisualThemeMotionRuntime(
        requireNotNull(theme.bytes(context, script.asset, VisualThemeValidator.MAX_SCRIPT_BYTES)) {
            "huBoard Motion script '${script.asset}' could not be read"
        },
        assetNames,
        script,
        animation.durationMs,
        animation.maxSimultaneousEffects,
    )
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private var seed = SystemClock.uptimeMillis().toInt()

    fun start(
        key: Key,
        paddingLeft: Int,
        paddingTop: Int,
        viewportWidth: Int,
        viewportHeight: Int,
    ) {
        val centerX = paddingLeft + key.drawX + key.drawWidth * 0.5f
        val centerY = paddingTop + key.y + key.height * 0.5f
        seed = seed * 1664525 + 1013904223
        runtime.start(
            centerX,
            centerY,
            key.drawWidth.toFloat(),
            key.height.toFloat(),
            viewportWidth.toFloat(),
            viewportHeight.toFloat(),
            SystemClock.uptimeMillis(),
            seed,
        )
    }

    fun draw(canvas: Canvas, nowMs: Long): Boolean {
        val commands = runtime.frame(nowMs, canvas.width.toFloat(), canvas.height.toFloat())
        var offset = 0
        while (offset + COMMAND_STRIDE <= commands.size) {
            when (commands[offset].toInt()) {
                COMMAND_CIRCLE -> drawCircle(canvas, commands, offset)
                COMMAND_GLOW -> drawGlow(canvas, commands, offset)
                COMMAND_IMAGE -> drawImage(canvas, commands, offset)
                COMMAND_LINE -> drawLine(canvas, commands, offset)
                COMMAND_ROUNDED_RECT -> drawRoundedRect(canvas, commands, offset)
            }
            offset += COMMAND_STRIDE
        }
        paint.shader = null
        paint.alpha = 255
        return runtime.hasEffects()
    }

    fun clear() = runtime.clear()

    fun lastError(): String = runtime.lastError()

    override fun close() = runtime.close()

    private fun drawCircle(canvas: Canvas, command: FloatArray, offset: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        setSolidColor(command[offset + COLOR_A].rawBits, command[offset + ALPHA])
        canvas.drawCircle(
            command[offset + X],
            command[offset + Y],
            command[offset + WIDTH].coerceAtLeast(0f),
            paint,
        )
    }

    private fun drawGlow(canvas: Canvas, command: FloatArray, offset: Int) {
        val radius = command[offset + WIDTH].coerceAtLeast(0.5f)
        paint.style = Paint.Style.FILL
        paint.alpha = alpha(command[offset + ALPHA])
        paint.shader = RadialGradient(
            command[offset + X],
            command[offset + Y],
            radius,
            command[offset + COLOR_A].rawBits,
            command[offset + COLOR_B].rawBits,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(command[offset + X], command[offset + Y], radius, paint)
    }

    private fun drawImage(canvas: Canvas, command: FloatArray, offset: Int) {
        val assetIndex = command[offset + ASSET_INDEX].toInt()
        val bitmap = bitmap(assetIndex) ?: return
        val width = command[offset + WIDTH].coerceAtLeast(0f)
        val height = command[offset + HEIGHT].coerceAtLeast(0f)
        destination.set(-width * 0.5f, -height * 0.5f, width * 0.5f, height * 0.5f)
        paint.shader = null
        paint.alpha = alpha(command[offset + ALPHA])
        canvas.save()
        canvas.translate(command[offset + X], command[offset + Y])
        canvas.rotate(command[offset + ROTATION])
        canvas.drawBitmap(bitmap, null, destination, paint)
        canvas.restore()
    }

    private fun drawLine(canvas: Canvas, command: FloatArray, offset: Int) {
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = command[offset + LINE_WIDTH].coerceAtLeast(0f)
        setSolidColor(command[offset + COLOR_A].rawBits, command[offset + ALPHA])
        canvas.drawLine(
            command[offset + X],
            command[offset + Y],
            command[offset + WIDTH],
            command[offset + HEIGHT],
            paint,
        )
    }

    private fun drawRoundedRect(canvas: Canvas, command: FloatArray, offset: Int) {
        paint.shader = null
        paint.style = Paint.Style.FILL
        setSolidColor(command[offset + COLOR_A].rawBits, command[offset + ALPHA])
        val radius = command[offset + ROTATION].coerceAtLeast(0f)
        destination.set(
            command[offset + X],
            command[offset + Y],
            command[offset + WIDTH],
            command[offset + HEIGHT],
        )
        canvas.drawRoundRect(destination, radius, radius, paint)
    }

    private fun bitmap(index: Int): Bitmap? {
        if (index !in assetNames.indices) return null
        if (!attemptedBitmaps[index]) {
            attemptedBitmaps[index] = true
            bitmaps[index] = theme.bitmap(applicationContext, assetNames[index])
        }
        return bitmaps[index]
    }

    private fun alpha(value: Float): Int =
        (value.coerceIn(0f, 1f) * Color.alpha(Color.WHITE)).roundToInt()

    private fun setSolidColor(color: Int, opacity: Float) {
        val combinedAlpha = (Color.alpha(color) * opacity.coerceIn(0f, 1f)).roundToInt()
        paint.color = color and 0x00FFFFFF or (combinedAlpha shl 24)
        paint.alpha = 255
    }

    private val Float.rawBits: Int get() = java.lang.Float.floatToRawIntBits(this)

    private companion object {
        const val COMMAND_STRIDE = 10
        const val COMMAND_CIRCLE = 1
        const val COMMAND_GLOW = 2
        const val COMMAND_IMAGE = 3
        const val COMMAND_LINE = 4
        const val COMMAND_ROUNDED_RECT = 5

        const val X = 1
        const val Y = 2
        const val WIDTH = 3
        const val HEIGHT = 4
        const val ROTATION = 5
        const val LINE_WIDTH = 5
        const val ALPHA = 6
        const val COLOR_A = 7
        const val COLOR_B = 8
        const val ASSET_INDEX = 7
    }
}
