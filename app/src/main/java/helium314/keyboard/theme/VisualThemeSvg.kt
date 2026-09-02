// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.Xml
import com.caverock.androidsvg.SVG
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import kotlin.math.ceil
import org.xmlpull.v1.XmlPullParser

/** Parses a deliberately restricted, static subset of SVG for portable visual themes. */
internal object VisualThemeSvg {
    const val MAX_BYTES = 8L * 1024L * 1024L
    const val MAX_DIMENSION = 4_096
    const val MAX_PIXELS = 8_000_000L
    const val MAX_TOTAL_PIXELS = 40_000_000L
    private const val MAX_ELEMENTS = 4_096

    private val allowedElements = setOf(
        "svg",
        "g",
        "defs",
        "path",
        "rect",
        "circle",
        "ellipse",
        "line",
        "polyline",
        "polygon",
        "linearGradient",
        "radialGradient",
        "stop",
        "clipPath",
        "title",
        "desc",
    )
    private val forbiddenReference = Regex(
        "(?i)(?:https?|file|content|data|javascript):|@import",
    )
    private val parserLock = Any()

    data class Document(
        val svg: SVG,
        val width: Int,
        val height: Int,
    ) {
        val pixels: Long get() = width.toLong() * height

        fun bitmap(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
            synchronized(svg) {
                svg.renderToCanvas(Canvas(it), RectF(0f, 0f, width.toFloat(), height.toFloat()))
            }
        }

        fun drawable(density: Float): Drawable = SvgDrawable(this, density)
    }

    fun parse(input: InputStream): Document {
        val bytes = readLimited(input)
        val text = bytes.decodeToString(throwOnInvalidSequence = true)
        validateMarkup(text)

        val svg = synchronized(parserLock) {
            SVG.setInternalEntitiesEnabled(false)
            SVG.deregisterExternalFileResolver()
            SVG.getFromString(text)
        }
        val viewBox = svg.documentViewBox
        val width = svg.documentWidth.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.width()?.takeIf { it.isFinite() && it > 0f }
        val height = svg.documentHeight.takeIf { it.isFinite() && it > 0f }
            ?: viewBox?.height()?.takeIf { it.isFinite() && it > 0f }
        require(width != null && height != null) {
            "SVG requires positive width and height values or a valid viewBox"
        }

        val pixelWidth = ceil(width).toInt()
        val pixelHeight = ceil(height).toInt()
        val pixels = pixelWidth.toLong() * pixelHeight
        require(pixelWidth in 1..MAX_DIMENSION && pixelHeight in 1..MAX_DIMENSION &&
                pixels <= MAX_PIXELS) {
            "SVG dimensions are too large"
        }
        return Document(svg, pixelWidth, pixelHeight)
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "SVG file is too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun validateMarkup(text: String) {
        require(!text.contains("<!DOCTYPE", ignoreCase = true) &&
                !text.contains("<!ENTITY", ignoreCase = true)) {
            "SVG document types and entities are not supported"
        }

        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false) }
            setInput(StringReader(text))
        }
        var elements = 0
        var rootSeen = false
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.START_TAG -> {
                    val element = parser.name
                    if (!rootSeen) {
                        require(element == "svg") { "SVG root element must be <svg>" }
                        rootSeen = true
                    }
                    require(element in allowedElements) {
                        "Unsupported SVG element <$element>"
                    }
                    elements++
                    require(elements <= MAX_ELEMENTS) { "SVG contains too many elements" }

                    for (index in 0 until parser.attributeCount) {
                        val name = parser.getAttributeName(index)
                        val value = parser.getAttributeValue(index)
                        require(!name.startsWith("on", ignoreCase = true)) {
                            "SVG event attributes are not supported"
                        }
                        require(name != "href" && name != "xlink:href") {
                            "SVG references are not supported"
                        }
                        require(!forbiddenReference.containsMatchIn(value)) {
                            "SVG external references are not supported"
                        }
                        val urlStart = value.indexOf("url(", ignoreCase = true)
                        require(urlStart < 0 || value.indexOf("url(#", ignoreCase = true) == urlStart) {
                            "SVG may only use local paint and clip references"
                        }
                    }
                }
                XmlPullParser.DOCDECL -> error("SVG document types are not supported")
                XmlPullParser.END_DOCUMENT -> break
            }
        }
        require(rootSeen) { "SVG document is empty" }
    }

    private class SvgDrawable(
        private val document: Document,
        density: Float,
    ) : Drawable() {
        private val layerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val intrinsicWidth = ceil(document.width * density).toInt()
        private val intrinsicHeight = ceil(document.height * density).toInt()

        override fun draw(canvas: Canvas) {
            if (bounds.isEmpty) return
            val target = RectF(bounds)
            val save = if (layerPaint.alpha < 255 || layerPaint.colorFilter != null) {
                canvas.saveLayer(target, layerPaint)
            } else {
                canvas.save()
            }
            canvas.translate(target.left, target.top)
            canvas.scale(
                target.width() / document.width,
                target.height() / document.height,
            )
            synchronized(document.svg) {
                document.svg.renderToCanvas(
                    canvas,
                    RectF(0f, 0f, document.width.toFloat(), document.height.toFloat()),
                )
            }
            canvas.restoreToCount(save)
        }

        override fun setAlpha(alpha: Int) {
            if (layerPaint.alpha == alpha) return
            layerPaint.alpha = alpha
            invalidateSelf()
        }

        override fun getAlpha(): Int = layerPaint.alpha

        override fun setColorFilter(colorFilter: ColorFilter?) {
            if (layerPaint.colorFilter == colorFilter) return
            layerPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in the Android framework")
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

        override fun getIntrinsicWidth(): Int = intrinsicWidth

        override fun getIntrinsicHeight(): Int = intrinsicHeight
    }
}
