// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

/** Stable asset keys used by visual theme manifests. */
object ThemeAsset {
    const val KEY_NORMAL = "key.normal"
    const val KEY_PRESSED = "key.pressed"
    const val SPACE_NORMAL = "key.space.normal"
    const val SPACE_PRESSED = "key.space.pressed"
    const val SHIFT_NORMAL = "key.shift.normal"
    const val SHIFT_PRESSED = "key.shift.pressed"
    const val DELETE_NORMAL = "key.delete.normal"
    const val DELETE_PRESSED = "key.delete.pressed"
    const val ACTION_NORMAL = "key.action.normal"
    const val ACTION_PRESSED = "key.action.pressed"
    const val ROUND_FUNCTION_NORMAL = "key.function.round.normal"
    const val ROUND_FUNCTION_PRESSED = "key.function.round.pressed"
    const val DIAMOND_FUNCTION_NORMAL = "key.function.diamond.normal"
    const val DIAMOND_FUNCTION_PRESSED = "key.function.diamond.pressed"

    const val ICON_BACKSPACE = "icon.backspace"
    const val ICON_ACTION = "icon.action"
    const val ICON_SPACE_GLYPH = "icon.space.glyph"
    const val ICON_SPACE_LANGUAGE = "icon.space.language"

    const val KEY_PREVIEW = "preview.background"
    const val KEYBOARD_BACKGROUND = "keyboard.background"

    const val TOOLBAR_BACKGROUND = "toolbar.background"
    const val TOOLBAR_START = "toolbar.start"
    const val TOOLBAR_START_PRESSED = "toolbar.start.pressed"
    const val TOOLBAR_END = "toolbar.end"
    const val TOOLBAR_KEYBOARD = "toolbar.icon.keyboard"
    const val TOOLBAR_CURSOR = "toolbar.icon.cursor"
    const val TOOLBAR_SEARCH = "toolbar.icon.search"
    const val TOOLBAR_EMOJI = "toolbar.icon.emoji"
    const val TOOLBAR_EXPAND = "toolbar.icon.expand"

    const val CLIPBOARD_SUGGESTION_BACKGROUND = "clipboard.suggestion.background"
    const val CLIPBOARD_PASTE = "clipboard.icon.paste"
    const val CLIPBOARD_CLOSE = "clipboard.icon.close"
    const val POPUP_PANEL_BACKGROUND = "popup.panel.background"

    val known: Set<String> = setOf(
        KEY_NORMAL,
        KEY_PRESSED,
        SPACE_NORMAL,
        SPACE_PRESSED,
        SHIFT_NORMAL,
        SHIFT_PRESSED,
        DELETE_NORMAL,
        DELETE_PRESSED,
        ACTION_NORMAL,
        ACTION_PRESSED,
        ROUND_FUNCTION_NORMAL,
        ROUND_FUNCTION_PRESSED,
        DIAMOND_FUNCTION_NORMAL,
        DIAMOND_FUNCTION_PRESSED,
        ICON_BACKSPACE,
        ICON_ACTION,
        ICON_SPACE_GLYPH,
        ICON_SPACE_LANGUAGE,
        KEY_PREVIEW,
        KEYBOARD_BACKGROUND,
        TOOLBAR_BACKGROUND,
        TOOLBAR_START,
        TOOLBAR_START_PRESSED,
        TOOLBAR_END,
        TOOLBAR_KEYBOARD,
        TOOLBAR_CURSOR,
        TOOLBAR_SEARCH,
        TOOLBAR_EMOJI,
        TOOLBAR_EXPAND,
        CLIPBOARD_SUGGESTION_BACKGROUND,
        CLIPBOARD_PASTE,
        CLIPBOARD_CLOSE,
        POPUP_PANEL_BACKGROUND,
    )
}
