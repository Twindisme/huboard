// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import androidx.core.graphics.toColorInt

object VisualThemeValidator {
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_MANIFEST_BYTES = 128 * 1024
    const val MAX_ANIMATION_FRAMES = 120

    private val safeId = Regex("^[a-z][a-z0-9_]{0,47}$")
    private val resourceReference = Regex("^res:[a-z][a-z0-9_]*$")
    private val fileReference = Regex("^file:assets/[a-zA-Z0-9_./-]+\\.(png|webp)$")

    fun validate(manifest: VisualThemeManifest) {
        require(manifest.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported visual theme schema ${manifest.schemaVersion}"
        }
        require(safeId.matches(manifest.id)) { "Invalid visual theme id '${manifest.id}'" }
        require(manifest.displayName.isNotBlank() && manifest.displayName.length <= 80) {
            "Visual theme displayName must contain 1-80 characters"
        }
        require((manifest.author?.length ?: 0) <= 80) { "Visual theme author is too long" }
        require((manifest.description?.length ?: 0) <= 500) { "Visual theme description is too long" }

        val unknownAssets = manifest.assets.keys.filterNot {
            it in ThemeAsset.known || it.matches(Regex("^animation\\.[a-z0-9_.-]+$"))
        }
        require(unknownAssets.isEmpty()) { "Unknown visual theme assets: $unknownAssets" }
        manifest.assets.forEach { (key, reference) ->
            require(resourceReference.matches(reference) ||
                    (fileReference.matches(reference) && isSafeRelativePath(reference.removePrefix("file:")))) {
                "Invalid asset reference for $key"
            }
        }

        if (manifest.capabilities.keys) {
            requireAssets(
                manifest,
                ThemeAsset.KEY_NORMAL,
                ThemeAsset.KEY_PRESSED,
                ThemeAsset.ICON_BACKSPACE,
                ThemeAsset.ICON_ACTION,
                ThemeAsset.ICON_SPACE_GLYPH,
                ThemeAsset.ICON_SPACE_LANGUAGE,
            )
            validateKeyRenderer(manifest.keyRenderer)
        }
        if (manifest.capabilities.keyPreview) {
            requireAssets(manifest, ThemeAsset.KEY_PREVIEW)
            validateKeyPreview(manifest.keyPreview)
        }
        if (manifest.capabilities.keyPressAnimation) {
            val animation = requireNotNull(manifest.keyPressAnimation) {
                "keyPressAnimation capability needs a keyPressAnimation block"
            }
            require(animation.frames.isNotEmpty()) { "Key animation has no frames" }
            require(animation.frames.size <= MAX_ANIMATION_FRAMES) { "Key animation has too many frames" }
            require(animation.frames.distinct().size == animation.frames.size) {
                "Key animation contains duplicate frames"
            }
            require(animation.frames.all(manifest.assets::containsKey)) {
                "Key animation references an asset missing from assets"
            }
            require(animation.frameDurationMs in 8L..1_000L) { "Invalid animation frame duration" }
            require(animation.maxSimultaneousEffects in 1..16) { "Invalid simultaneous effect count" }
            require(animation.heightToKeyHeight in 0.25f..4f) { "Invalid animation size" }
        }
        if (manifest.capabilities.toolbar) {
            requireAssets(
                manifest,
                ThemeAsset.TOOLBAR_BACKGROUND,
                ThemeAsset.TOOLBAR_START,
                ThemeAsset.TOOLBAR_END,
            )
        }
        if (manifest.capabilities.clipboard) {
            requireAssets(manifest, ThemeAsset.CLIPBOARD_SUGGESTION_BACKGROUND)
        }
        if (manifest.capabilities.keyboardBackground) {
            requireAssets(manifest, ThemeAsset.KEYBOARD_BACKGROUND)
        }
    }

    private fun validateKeyRenderer(config: KeyRendererConfig) {
        require(config.horizontalOverscan in 0f..0.5f) { "Invalid horizontal key overscan" }
        require(config.verticalOverscan in 0f..0.5f) { "Invalid vertical key overscan" }
        require(config.regularLeftCapPx >= 0 && config.regularRightCapPx >= 0) {
            "Invalid regular key caps"
        }
        require(config.spaceLeftCapPx >= 0 && config.spaceRightCapPx >= 0) {
            "Invalid space key caps"
        }
        listOf(
            config.backspaceIconSize,
            config.actionIconSize,
            config.spaceLanguageIconSize,
            config.spaceGlyphWidth,
        ).forEach { require(it in 0f..1f) { "Invalid proportional key metric" } }
        require(config.actionIconAspectRatio > 0f) { "Invalid action icon aspect ratio" }
        config.iconGradientStart?.let(::parseColor)
        config.iconGradientEnd?.let(::parseColor)
    }

    private fun validateKeyPreview(config: KeyPreviewConfig) {
        require(config.bitmapWidthPx > 0f && config.bitmapHeightPx > 0f) {
            "Invalid key preview bitmap size"
        }
        require(config.faceLeftPx >= 0f && config.faceTopPx >= 0f &&
                config.faceRightPx > config.faceLeftPx &&
                config.faceBottomPx > config.faceTopPx &&
                config.faceRightPx <= config.bitmapWidthPx &&
                config.faceBottomPx <= config.bitmapHeightPx) {
            "Invalid key preview face bounds"
        }
        require(config.verticalOverscan in 0f..0.5f) { "Invalid key preview overscan" }
        require(config.gapDp in -16f..64f) { "Invalid key preview gap" }
        config.textColor?.let(::parseColor)
    }

    private fun requireAssets(manifest: VisualThemeManifest, vararg keys: String) {
        val missing = keys.filterNot(manifest.assets::containsKey)
        require(missing.isEmpty()) { "Missing required visual theme assets: $missing" }
    }

    fun parseColor(value: String): Int = runCatching { value.toColorInt() }
        .getOrElse { throw IllegalArgumentException("Invalid theme color '$value'", it) }

    fun isSafeRelativePath(path: String): Boolean {
        if (path.startsWith('/') || path.startsWith('\\')) return false
        val segments = path.replace('\\', '/').split('/')
        return segments.isNotEmpty() && segments.none { it.isEmpty() || it == "." || it == ".." }
    }
}
