// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VisualThemeManifest(
    val schemaVersion: Int,
    val id: String,
    val displayName: String,
    val author: String? = null,
    val description: String? = null,
    val capabilities: ThemeCapabilities = ThemeCapabilities(),
    val assets: Map<String, String> = emptyMap(),
    val colors: ThemeColors? = null,
    val appearance: ThemeAppearance = ThemeAppearance(),
    val keyRenderer: KeyRendererConfig = KeyRendererConfig(),
    val toolbarRenderer: ToolbarRendererConfig = ToolbarRendererConfig(),
    val clipboardRenderer: ClipboardRendererConfig = ClipboardRendererConfig(),
    val keyPreview: KeyPreviewConfig = KeyPreviewConfig(),
    val keyPressAnimation: KeyPressAnimationConfig? = null,
    val versionCode: Int = 1,
    val versionName: String? = null,
    val minimumEngineVersion: Int = 1,
    val rendering: Map<String, ThemeAssetRenderSpec> = emptyMap(),
)

@Serializable
data class ThemeAppearance(
    val keyboardStyle: String? = null,
    val keyBorders: Boolean? = null,
    val showMoreSuggestionsHint: Boolean = true,
    val clipboardSuggestionContentOffsetYDp: Int = 0,
)

@Serializable
data class ThemeColors(
    val accent: String,
    val background: String,
    val keyBackground: String,
    val functionalKey: String,
    val spaceBar: String,
    val keyText: String,
    val keyHintText: String,
    val suggestionText: String? = null,
    val spaceBarText: String? = null,
    val gesture: String? = null,
)

@Serializable
data class ThemeCapabilities(
    val keys: Boolean = false,
    val keyPreview: Boolean = false,
    val keyPressAnimation: Boolean = false,
    val toolbar: Boolean = false,
    val clipboard: Boolean = false,
    val keyboardBackground: Boolean = false,
)

@Serializable
data class KeyRendererConfig(
    val horizontalOverscan: Float = 0f,
    val verticalOverscan: Float = 0f,
    val centerSpecialKeyArtworkHorizontally: Boolean = false,
    val regularLeftCapPx: Int = 0,
    val regularRightCapPx: Int = 0,
    val spaceLeftCapPx: Int = 0,
    val spaceRightCapPx: Int = 0,
    val backspaceIconSize: Float = 0.52f,
    val actionIconSize: Float = 0.62f,
    val actionIconAspectRatio: Float = 80f / 66f,
    val actionIconVisibleOffsetX: Float = 0f,
    val actionIconVisibleOffsetY: Float = 0f,
    val spaceLanguageIconSize: Float = 0.25f,
    val spaceLanguageIconTop: Float = 0.20f,
    val spaceGlyphWidth: Float = 0.416f,
    val spaceGlyphTop: Float = 0.58f,
    val iconGradientStart: String? = null,
    val iconGradientEnd: String? = null,
    val content: Map<String, ThemeKeyContentSpec> = emptyMap(),
)

object ThemeKeyClass {
    const val REGULAR = "regular"
    const val SPACE = "space"
    const val SHIFT = "shift"
    const val DELETE = "delete"
    const val ACTION = "action"
    const val ROUND_FUNCTION = "roundFunction"
    const val DIAMOND_FUNCTION = "diamondFunction"

    val known: Set<String> = setOf(
        REGULAR,
        SPACE,
        SHIFT,
        DELETE,
        ACTION,
        ROUND_FUNCTION,
        DIAMOND_FUNCTION,
    )
}

@Serializable
enum class ThemeKeyIconMode {
    @SerialName("overlay")
    OVERLAY,

    @SerialName("embedded")
    EMBEDDED,

    @SerialName("hidden")
    HIDDEN,
}

@Serializable
data class ThemeKeyContentSpec(
    val iconMode: ThemeKeyIconMode? = null,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val iconWidth: Float? = null,
    val iconHeight: Float? = null,
)

@Serializable
data class ToolbarRendererConfig(
    val heightDp: Float? = null,
    val startWidthDp: Float? = null,
    val endWidthDp: Float? = null,
    val keyWidthDp: Float? = null,
    val contentPaddingStartDp: Float? = null,
    val contentPaddingEndDp: Float? = null,
    val iconSizeDp: Float? = null,
    val iconOffsetXDp: Float = 0f,
    val iconOffsetYDp: Float = 0f,
    val startArtworkOffsetXDp: Float? = null,
    val startArtworkOffsetYDp: Float? = null,
    val endArtworkOffsetXDp: Float? = null,
    val endArtworkOffsetYDp: Float? = null,
)

@Serializable
data class ThemeAssetInsets(
    val leftPx: Int = 0,
    val topPx: Int = 0,
    val rightPx: Int = 0,
    val bottomPx: Int = 0,
)

@Serializable
enum class ThemeAssetScaleMode {
    @SerialName("stretch")
    STRETCH,

    @SerialName("fit")
    FIT,

    @SerialName("crop")
    CROP,

    @SerialName("nineSlice")
    NINE_SLICE,
}

@Serializable
data class ThemeAssetRenderSpec(
    val mode: ThemeAssetScaleMode = ThemeAssetScaleMode.STRETCH,
    val insets: ThemeAssetInsets = ThemeAssetInsets(),
    val cornerScale: Float = 1f,
)

@Serializable
data class ClipboardRendererConfig(
    val entryInsets: ThemeAssetInsets = ThemeAssetInsets(),
    val pinnedEntryInsets: ThemeAssetInsets = ThemeAssetInsets(),
    val entryCornerScale: Float = 1f,
    val pinnedEntryCornerScale: Float = 1f,
    val pinnedContentEndPaddingDp: Int = 0,
)

@Serializable
data class KeyPreviewConfig(
    val bitmapWidthPx: Float = 0f,
    val bitmapHeightPx: Float = 0f,
    val faceLeftPx: Float = 0f,
    val faceTopPx: Float = 0f,
    val faceRightPx: Float = 0f,
    val faceBottomPx: Float = 0f,
    val verticalOverscan: Float = 0f,
    val gapDp: Float = 0f,
    val textColor: String? = null,
    val faceBounds: ThemeNormalizedRect? = null,
)

@Serializable
data class ThemeNormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

@Serializable
data class KeyPressAnimationConfig(
    val frames: List<String> = emptyList(),
    val lottieAsset: String? = null,
    val spriteAtlas: KeyPressSpriteAtlasConfig? = null,
    val script: ThemeMotionScriptConfig? = null,
    val durationMs: Long = 0L,
    val frameDurationMs: Long = 24L,
    val maxSimultaneousEffects: Int = 8,
    val heightToKeyHeight: Float = 1.2f,
    val characterKeysOnly: Boolean = true,
)

@Serializable
data class ThemeMotionScriptConfig(
    val asset: String,
    val apiVersion: Int = 1,
    val memoryLimitKb: Int = 2_048,
    val frameTimeLimitMs: Float = 2f,
    val maxDrawCommandsPerFrame: Int = 128,
)

@Serializable
data class KeyPressSpriteAtlasConfig(
    val asset: String,
    val columns: Int,
    val rows: Int,
    val frameCount: Int? = null,
)
