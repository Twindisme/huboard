// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.graphics.Color
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey

object VisualThemeToolbarStyler {
    @JvmStatic
    fun applyToAuxiliaryToolbars(root: View) {
        val context = root.context
        val theme = VisualThemeManager.activeTheme(context)
        val colors = Settings.getValues().mColors
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
                it.setImageDrawable(theme.drawable(context, ThemeAsset.TOOLBAR_EXPAND))
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
}
