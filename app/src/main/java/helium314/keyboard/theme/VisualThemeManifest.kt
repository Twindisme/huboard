// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

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
    val keyRenderer: KeyRendererConfig = KeyRendererConfig(),
    val keyPreview: KeyPreviewConfig = KeyPreviewConfig(),
    val keyPressAnimation: KeyPressAnimationConfig? = null,
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
)

@Serializable
data class KeyPressAnimationConfig(
    val frames: List<String>,
    val frameDurationMs: Long = 24L,
    val maxSimultaneousEffects: Int = 8,
    val heightToKeyHeight: Float = 1.2f,
    val characterKeysOnly: Boolean = true,
)
