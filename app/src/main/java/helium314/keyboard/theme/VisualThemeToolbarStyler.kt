// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

object VisualThemeToolbarStyler {
    @JvmStatic
    fun applyToAuxiliaryToolbars(root: View) {
        val context = root.context
        val theme = VisualThemeManager.activeTheme(context)
        val colors = Settings.getValues().mColors
        applyGeometry(root, theme)
        val containers = listOfNotNull(
            root.findViewById<View>(R.id.emoji_tab_strip),
            root.findViewById<View>(R.id.clipboard_strip_container),
        )
        containers.forEach { container ->
            if (theme.hasCustomToolbar) {
                container.background = theme.drawable(context, ThemeAsset.TOOLBAR_BACKGROUND)
            } else {
                colors.setBackground(container, ColorType.STRIP_BACKGROUND)
            }
        }

        val startKeys = listOfNotNull(
            root.findViewById<ImageButton>(R.id.emoji_toolbar_backpack_key),
            root.findViewById<ImageButton>(R.id.clipboard_toolbar_backpack_key),
        )
        val endArt = listOfNotNull(
            root.findViewById<ImageView>(R.id.emoji_toolbar_end_art),
            root.findViewById<ImageView>(R.id.clipboard_toolbar_end_art),
        )
        val returnKeys = listOfNotNull(
            root.findViewById<ImageButton>(R.id.emoji_toolbar_return_key),
            root.findViewById<ImageButton>(R.id.clipboard_toolbar_return_key),
        )

        if (theme.hasCustomToolbar) {
            startKeys.forEach { key ->
                key.setImageDrawable(stateDrawable(
                    theme.drawable(context, ThemeAsset.TOOLBAR_START),
                    theme.drawable(context, ThemeAsset.TOOLBAR_START_PRESSED),
                ))
                key.clearColorFilter()
            }
            endArt.forEach { art ->
                art.setImageDrawable(theme.drawable(context, ThemeAsset.TOOLBAR_END))
                art.visibility = View.VISIBLE
            }
            returnKeys.forEach {
                it.setImageDrawable(
                    theme.drawable(context, ThemeAsset.TOOLBAR_EXPAND)
                        ?: KeyboardIconsSet.instance.getNewDrawable(
                            KeyboardIconsSet.NAME_TOOLBAR_KEY,
                            context,
                        ),
                )
                styleToolbarButton(it, theme)
            }
        } else {
            val icons = KeyboardIconsSet.instance
            startKeys.forEach { key ->
                key.setImageDrawable(icons.getNewDrawable(ToolbarKey.CLIPBOARD.name, context))
                colors.setColor(key, ColorType.TOOL_BAR_KEY)
            }
            endArt.forEach { it.visibility = View.GONE }
            returnKeys.forEach { key ->
                key.setImageDrawable(
                    icons.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context),
                )
                colors.setColor(key, ColorType.TOOL_BAR_KEY)
            }
        }
        (startKeys + returnKeys).forEach { it.setBackgroundColor(Color.TRANSPARENT) }
        startKeys.forEach { styleStartArtworkButton(it, theme) }
    }

    fun themedToolbarIcon(
        context: android.content.Context,
        theme: ResolvedVisualTheme,
        key: ToolbarKey,
    ): Drawable? {
        val dynamicAsset = ThemeAsset.toolbarIcon(key.name.lowercase(Locale.US))
        val legacyAsset = when (key) {
            ToolbarKey.SETTINGS -> ThemeAsset.TOOLBAR_KEYBOARD
            ToolbarKey.SELECT_WORD -> ThemeAsset.TOOLBAR_CURSOR
            ToolbarKey.EMOJI -> ThemeAsset.TOOLBAR_EMOJI
            else -> null
        }
        return theme.drawable(context, dynamicAsset)
            ?: legacyAsset?.let { theme.drawable(context, it) }
            ?: KeyboardIconsSet.instance.getNewDrawable(key.name, context)
    }

    fun toolbarKeyWidthPx(
        context: android.content.Context,
        theme: ResolvedVisualTheme,
        defaultWidthPx: Int,
    ): Int = theme.manifest.toolbarRenderer.keyWidthDp
        ?.dpToPx(context.resources)
        ?.coerceAtLeast(MIN_TOUCH_TARGET_DP.dpToPx(context.resources))
        ?: defaultWidthPx

    fun styleToolbarButton(button: ImageButton, theme: ResolvedVisualTheme) {
        if (!theme.hasCustomToolbar) return
        val config = theme.manifest.toolbarRenderer
        styleButton(
            button,
            config.iconSizeDp,
            config.iconOffsetXDp,
            config.iconOffsetYDp,
        )
    }

    fun styleStartArtworkButton(button: ImageButton, theme: ResolvedVisualTheme) {
        if (!theme.hasCustomToolbar) return
        val config = theme.manifest.toolbarRenderer
        config.startArtworkOffsetXDp?.let {
            button.translationX = it.dpToPx(button.resources).toFloat()
        }
        config.startArtworkOffsetYDp?.let {
            button.translationY = it.dpToPx(button.resources).toFloat()
        }
    }

    private fun styleButton(
        button: ImageButton,
        iconSizeDp: Float?,
        offsetXDp: Float,
        offsetYDp: Float,
    ) {
        if (iconSizeDp == null && offsetXDp == 0f && offsetYDp == 0f) return
        button.scaleType = ImageView.ScaleType.FIT_CENTER
        button.doOnLayout { view ->
            val availableSize = min(view.width, view.height)
            if (availableSize <= 0) return@doOnLayout
            val intrinsicSize = min(
                button.drawable?.intrinsicWidth?.takeIf { it > 0 } ?: availableSize,
                button.drawable?.intrinsicHeight?.takeIf { it > 0 } ?: availableSize,
            )
            val iconSize = (iconSizeDp?.dpToPx(view.resources) ?: intrinsicSize)
                .coerceIn(1, availableSize)
            val offsetX = offsetXDp.dpToPx(view.resources)
            val offsetY = offsetYDp.dpToPx(view.resources)
            val horizontalSpace = (view.width - iconSize).coerceAtLeast(0)
            val verticalSpace = (view.height - iconSize).coerceAtLeast(0)
            val left = (horizontalSpace / 2 + offsetX).coerceIn(0, horizontalSpace)
            val top = (verticalSpace / 2 + offsetY).coerceIn(0, verticalSpace)
            button.setPadding(left, top, horizontalSpace - left, verticalSpace - top)
        }
    }

    private fun applyGeometry(root: View, theme: ResolvedVisualTheme) {
        if (!theme.hasCustomToolbar) return
        val config = theme.manifest.toolbarRenderer
        config.heightDp?.let { heightDp ->
            val height = maxOf(heightDp, MIN_TOUCH_TARGET_DP).dpToPx(root.resources)
            root.findViewById<View>(R.id.strip_container)?.updateLayoutParams {
                this.height = height
            }
        }
        updateWidths(
            root,
            listOf(
                R.id.visual_theme_toolbar_start_container,
                R.id.visual_theme_toolbar_start_key,
                R.id.emoji_toolbar_start_container,
                R.id.emoji_toolbar_backpack_key,
                R.id.clipboard_toolbar_start_container,
                R.id.clipboard_toolbar_backpack_key,
            ),
            config.startWidthDp,
        )
        updateWidths(
            root,
            listOf(
                R.id.visual_theme_toolbar_end_container,
                R.id.emoji_toolbar_end,
                R.id.clipboard_toolbar_end,
            ),
            config.endWidthDp,
        )
        updateClipboardToolbarMargins(root, config.startWidthDp, config.endWidthDp)
        config.contentPaddingStartDp?.let { startDp ->
            val toolbar = root.findViewById<View>(R.id.toolbar)
            toolbar?.setPaddingRelative(
                startDp.dpToPx(root.resources),
                toolbar.paddingTop,
                config.contentPaddingEndDp?.dpToPx(root.resources) ?: toolbar.paddingEnd,
                toolbar.paddingBottom,
            )
        }
        if (config.contentPaddingStartDp == null) {
            config.contentPaddingEndDp?.let { endDp ->
                val toolbar = root.findViewById<View>(R.id.toolbar)
                toolbar?.setPaddingRelative(
                    toolbar.paddingStart,
                    toolbar.paddingTop,
                    endDp.dpToPx(root.resources),
                    toolbar.paddingBottom,
                )
            }
        }
        applyOffsets(
            root,
            listOf(
                R.id.visual_theme_toolbar_end_art,
                R.id.emoji_toolbar_end_art,
                R.id.clipboard_toolbar_end_art,
            ),
            config.endArtworkOffsetXDp,
            config.endArtworkOffsetYDp,
        )
    }

    private fun updateWidths(root: View, ids: List<Int>, widthDp: Float?) {
        if (widthDp == null) return
        val width = toolbarWidthPx(root, widthDp)
        ids.mapNotNull { id -> root.findViewById<View>(id) }.forEach { view ->
            view.updateLayoutParams { this.width = width }
        }
    }

    private fun updateClipboardToolbarMargins(
        root: View,
        startWidthDp: Float?,
        endWidthDp: Float?,
    ) {
        if (startWidthDp == null && endWidthDp == null) return
        root.findViewById<View>(R.id.clipboard_strip_scroll_view)
            ?.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                startWidthDp?.let { marginStart = toolbarWidthPx(root, it) }
                endWidthDp?.let { marginEnd = toolbarWidthPx(root, it) }
            }
    }

    private fun toolbarWidthPx(root: View, widthDp: Float): Int =
        maxOf(widthDp, MIN_TOUCH_TARGET_DP).dpToPx(root.resources)

    private fun applyOffsets(
        root: View,
        ids: List<Int>,
        offsetXDp: Float?,
        offsetYDp: Float?,
    ) {
        ids.mapNotNull { id -> root.findViewById<View>(id) }.forEach { view ->
            offsetXDp?.let { view.translationX = it.dpToPx(root.resources).toFloat() }
            offsetYDp?.let { view.translationY = it.dpToPx(root.resources).toFloat() }
        }
    }

    fun stateDrawable(
        normal: android.graphics.drawable.Drawable?,
        pressed: android.graphics.drawable.Drawable?,
    ): android.graphics.drawable.Drawable? {
        if (normal == null) return null
        if (pressed == null) return normal
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(intArrayOf(), normal)
        }
    }

    private fun Float.dpToPx(resources: android.content.res.Resources): Int =
        (this * resources.displayMetrics.density).roundToInt()

    private const val MIN_TOUCH_TARGET_DP = 48f
}
