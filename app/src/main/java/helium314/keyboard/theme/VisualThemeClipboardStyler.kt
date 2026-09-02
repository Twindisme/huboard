// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import helium314.keyboard.latin.utils.dpToPx

/** Builds scalable clipboard cards from assets supplied by a visual theme. */
object VisualThemeClipboardStyler {
    class EntryStyle internal constructor(
        private val context: Context,
        private val theme: ResolvedVisualTheme,
        private val normal: Bitmap,
        private val pressed: Bitmap,
        private val pinned: Bitmap,
        private val pinnedPressed: Bitmap,
        private val normalInsets: ThemeAssetInsets,
        private val pinnedInsets: ThemeAssetInsets,
        private val normalCornerScale: Float,
        private val pinnedCornerScale: Float,
        val pinnedContentEndPadding: Int,
    ) {
        fun background(isPinned: Boolean): Drawable {
            val normalBitmap = if (isPinned) pinned else normal
            val pressedBitmap = if (isPinned) pinnedPressed else pressed
            val insets = if (isPinned) pinnedInsets else normalInsets
            val cornerScale = if (isPinned) pinnedCornerScale else normalCornerScale
            return StateListDrawable().apply {
                addState(
                    intArrayOf(android.R.attr.state_pressed),
                    ThemeNineSliceDrawable(pressedBitmap, insets, cornerScale),
                )
                addState(intArrayOf(), ThemeNineSliceDrawable(normalBitmap, insets, cornerScale))
            }
        }

        fun pinIcon(): Drawable? = theme.drawable(context, ThemeAsset.CLIPBOARD_PIN)
    }

    fun entryStyle(
        context: Context,
        theme: ResolvedVisualTheme,
        referenceHeight: Int,
    ): EntryStyle? {
        if (!theme.hasCustomClipboard ||
                !theme.hasAsset(ThemeAsset.CLIPBOARD_ENTRY_NORMAL) ||
                !theme.hasAsset(ThemeAsset.CLIPBOARD_ENTRY_PRESSED)) {
            return null
        }
        val normal = theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_NORMAL) ?: return null
        val pressed = theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_PRESSED) ?: return null
        val pinned = theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_PINNED) ?: normal
        val pinnedPressed = theme.bitmap(context, ThemeAsset.CLIPBOARD_ENTRY_PINNED_PRESSED)
            ?: pressed
        val config = theme.manifest.clipboardRenderer
        val normalRenderSpec = theme.renderSpec(ThemeAsset.CLIPBOARD_ENTRY_NORMAL)
            ?.takeIf { it.mode == ThemeAssetScaleMode.NINE_SLICE }
        val pinnedRenderSpec = theme.renderSpec(ThemeAsset.CLIPBOARD_ENTRY_PINNED)
            ?.takeIf { it.mode == ThemeAssetScaleMode.NINE_SLICE }
        return EntryStyle(
            context = context,
            theme = theme,
            normal = normal,
            pressed = pressed,
            pinned = pinned,
            pinnedPressed = pinnedPressed,
            normalInsets = normalRenderSpec?.insets ?: config.entryInsets,
            pinnedInsets = pinnedRenderSpec?.insets ?: config.pinnedEntryInsets,
            normalCornerScale = normalRenderSpec?.cornerScale
                ?: referenceHeight / normal.height.toFloat() * config.entryCornerScale,
            pinnedCornerScale = pinnedRenderSpec?.cornerScale
                ?: referenceHeight / pinned.height.toFloat() * config.pinnedEntryCornerScale,
            pinnedContentEndPadding = config.pinnedContentEndPaddingDp.dpToPx(context.resources),
        )
    }
}
