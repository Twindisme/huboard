// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import java.io.File

internal data class ResolvedThemeAsset(
    @param:DrawableRes val resourceId: Int = 0,
    val file: File? = null,
)

class ResolvedVisualTheme internal constructor(
    val manifest: VisualThemeManifest,
    private val assets: Map<String, ResolvedThemeAsset>,
    val isBundled: Boolean,
) {
    val id: String get() = manifest.id
    val displayName: String get() = manifest.displayName
    val hasCustomKeys: Boolean get() = manifest.capabilities.keys
    val hasCustomKeyPreview: Boolean get() = manifest.capabilities.keyPreview
    val hasKeyPressAnimation: Boolean get() = manifest.capabilities.keyPressAnimation
    val hasCustomToolbar: Boolean get() = manifest.capabilities.toolbar
    val hasCustomClipboard: Boolean get() = manifest.capabilities.clipboard
    val hasKeyboardBackground: Boolean get() = manifest.capabilities.keyboardBackground

    @DrawableRes
    fun drawableResource(asset: String): Int = assets[asset]?.resourceId ?: 0

    fun hasAsset(asset: String): Boolean = assets.containsKey(asset)

    @DrawableRes
    fun drawableResource(asset: String, fallbackAsset: String): Int =
        drawableResource(asset).takeIf { it != 0 } ?: drawableResource(fallbackAsset)

    fun bitmap(context: Context, asset: String): Bitmap? = assets[asset]?.let {
        when {
            it.resourceId != 0 -> BitmapFactory.decodeResource(context.resources, it.resourceId)
            it.file != null -> BitmapFactory.decodeFile(it.file.path)
            else -> null
        }
    }

    fun bitmap(context: Context, asset: String, fallbackAsset: String): Bitmap? =
        bitmap(context, asset) ?: bitmap(context, fallbackAsset)

    fun drawable(context: Context, asset: String): Drawable? = assets[asset]?.let {
        when {
            it.resourceId != 0 -> ContextCompat.getDrawable(context, it.resourceId)?.mutate()
            it.file != null -> BitmapFactory.decodeFile(it.file.path)
                ?.toDrawable(context.resources)
            else -> null
        }
    }
}
