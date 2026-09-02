// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import com.airbnb.lottie.LottieComposition
import java.io.File
import java.io.InputStream

internal data class ResolvedThemeAsset(
    @param:DrawableRes val resourceId: Int = 0,
    val file: File? = null,
    val bundledPath: String? = null,
) {
    val isSvg: Boolean get() = (file?.name ?: bundledPath)
        ?.endsWith(".svg", ignoreCase = true) == true

    fun open(context: Context): InputStream? = when {
        file != null -> file.inputStream()
        bundledPath != null -> context.assets.open(bundledPath)
        else -> null
    }
}

class ResolvedVisualTheme internal constructor(
    val manifest: VisualThemeManifest,
    private val assets: Map<String, ResolvedThemeAsset>,
    val isBundled: Boolean,
) {
    private val svgDocuments = mutableMapOf<String, VisualThemeSvg.Document>()
    private val lottieCompositions = mutableMapOf<String, LottieComposition>()

    val id: String get() = manifest.id
    val displayName: String get() = manifest.displayName
    val hasCustomKeys: Boolean get() = manifest.capabilities.keys
    val hasCustomKeyPreview: Boolean get() = manifest.capabilities.keyPreview
    val hasKeyPressAnimation: Boolean get() = manifest.capabilities.keyPressAnimation
    val hasCustomToolbar: Boolean get() = manifest.capabilities.toolbar
    val hasCustomClipboard: Boolean get() = manifest.capabilities.clipboard
    val hasKeyboardBackground: Boolean get() = manifest.capabilities.keyboardBackground
    val showMoreSuggestionsHint: Boolean get() = manifest.appearance.showMoreSuggestionsHint
    val clipboardSuggestionContentOffsetYDp: Int
        get() = manifest.appearance.clipboardSuggestionContentOffsetYDp

    @DrawableRes
    fun drawableResource(asset: String): Int = assets[asset]?.resourceId ?: 0

    fun hasAsset(asset: String): Boolean = assets.containsKey(asset)

    fun renderSpec(asset: String): ThemeAssetRenderSpec? = manifest.rendering[asset]

    @DrawableRes
    fun drawableResource(asset: String, fallbackAsset: String): Int =
        drawableResource(asset).takeIf { it != 0 } ?: drawableResource(fallbackAsset)

    fun bitmap(context: Context, asset: String): Bitmap? = assets[asset]?.let { source ->
        when {
            source.resourceId != 0 -> BitmapFactory.decodeResource(context.resources, source.resourceId)
            source.isSvg -> svgDocument(context, asset, source)?.bitmap()
            else -> source.open(context)?.use(BitmapFactory::decodeStream)
        }
    }

    fun bitmap(context: Context, asset: String, fallbackAsset: String): Bitmap? =
        bitmap(context, asset) ?: bitmap(context, fallbackAsset)

    fun bytes(context: Context, asset: String, maximumBytes: Int): ByteArray? =
        assets[asset]?.open(context)?.use { input ->
            val buffer = ByteArray(maximumBytes + 1)
            var size = 0
            while (size < buffer.size) {
                val count = input.read(buffer, size, buffer.size - size)
                if (count <= 0) break
                size += count
            }
            buffer.copyOf(size).takeIf { size <= maximumBytes }
        }

    fun drawable(context: Context, asset: String): Drawable? {
        val drawable = rawDrawable(context, asset) ?: return null
        val spec = renderSpec(asset) ?: return drawable
        return drawable.withRenderSpec(spec) { bitmap(context, asset) }
    }

    private fun rawDrawable(context: Context, asset: String): Drawable? = assets[asset]?.let { source ->
        when {
            source.resourceId != 0 -> ContextCompat.getDrawable(context, source.resourceId)?.mutate()
            source.isSvg -> svgDocument(context, asset, source)?.drawable(
                context.resources.displayMetrics.density,
            )
            else -> source.open(context)?.use(BitmapFactory::decodeStream)
                ?.toDrawable(context.resources)
        }
    }

    private fun svgDocument(
        context: Context,
        asset: String,
        source: ResolvedThemeAsset,
    ): VisualThemeSvg.Document? = synchronized(svgDocuments) {
        svgDocuments[asset] ?: source.open(context)?.use(VisualThemeSvg::parse)?.also {
            svgDocuments[asset] = it
        }
    }

    fun lottieComposition(context: Context, asset: String): LottieComposition? =
        assets[asset]?.let { source ->
            synchronized(lottieCompositions) {
                lottieCompositions[asset] ?: source.open(context)?.use(VisualThemeLottie::parse)
                    ?.also { lottieCompositions[asset] = it }
            }
        }
}
