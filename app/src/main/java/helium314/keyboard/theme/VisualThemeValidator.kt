// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

object VisualThemeValidator {
    const val CURRENT_SCHEMA_VERSION = 1
    const val CURRENT_ENGINE_VERSION = 4
    const val MAX_MANIFEST_BYTES = 128 * 1024
    const val MAX_SCRIPT_BYTES = 128 * 1024
    const val MAX_ANIMATION_FRAMES = 120

    private val safeId = Regex("^[a-z][a-z0-9_]{0,47}$")
    private val resourceReference = Regex("^res:[a-z][a-z0-9_]*$")
    private val fileReference = Regex("^file:assets/[a-zA-Z0-9_./-]+\\.(png|webp|svg|json|luau)$")
    private val color = Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$")
    private val keyboardStyles = setOf("Material", "Holo", "Rounded")

    fun validate(manifest: VisualThemeManifest) {
        require(manifest.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported visual theme schema ${manifest.schemaVersion}"
        }
        require(manifest.versionCode > 0) { "Visual theme versionCode must be positive" }
        require((manifest.versionName?.length ?: 0) <= 40) {
            "Visual theme versionName is too long"
        }
        require(manifest.minimumEngineVersion in 1..CURRENT_ENGINE_VERSION) {
            "Visual theme requires unsupported engine ${manifest.minimumEngineVersion}"
        }
        require(safeId.matches(manifest.id)) { "Invalid visual theme id '${manifest.id}'" }
        require(manifest.displayName.isNotBlank() && manifest.displayName.length <= 80) {
            "Visual theme displayName must contain 1-80 characters"
        }
        require((manifest.author?.length ?: 0) <= 80) { "Visual theme author is too long" }
        require((manifest.description?.length ?: 0) <= 500) { "Visual theme description is too long" }
        manifest.appearance.keyboardStyle?.let { style ->
            require(style in keyboardStyles) { "Unknown keyboard style '$style'" }
        }
        require(manifest.appearance.clipboardSuggestionContentOffsetYDp in -16..16) {
            "Invalid clipboard suggestion content offset"
        }

        manifest.colors?.let { colors ->
            listOf(
                colors.accent,
                colors.background,
                colors.keyBackground,
                colors.functionalKey,
                colors.spaceBar,
                colors.keyText,
                colors.keyHintText,
                colors.suggestionText,
                colors.spaceBarText,
                colors.gesture,
            ).filterNotNull().forEach(::parseColor)
        }

        val unknownAssets = manifest.assets.keys.filterNot {
            ThemeAsset.isKnown(it) || it.matches(Regex("^animation\\.[a-z0-9_.-]+$"))
        }
        require(unknownAssets.isEmpty()) { "Unknown visual theme assets: $unknownAssets" }
        manifest.assets.forEach { (key, reference) ->
            require(resourceReference.matches(reference) ||
                    (fileReference.matches(reference) && isSafeRelativePath(reference.removePrefix("file:")))) {
                "Invalid asset reference for $key"
            }
        }
        val lottieAsset = manifest.keyPressAnimation?.lottieAsset
        manifest.assets.filterValues { it.endsWith(".json") }.keys.forEach { asset ->
            require(asset == lottieAsset) {
                "JSON asset '$asset' is not the configured Lottie animation"
            }
        }
        val scriptAsset = manifest.keyPressAnimation?.script?.asset
        manifest.assets.filterValues { it.endsWith(".luau") }.keys.forEach { asset ->
            require(asset == scriptAsset) {
                "Luau asset '$asset' is not the configured huBoard Motion script"
            }
        }
        require(manifest.capabilities.keyPressAnimation || manifest.keyPressAnimation == null) {
            "keyPressAnimation block requires the matching capability"
        }
        validateAssetRendering(manifest)
        validateKeyContent(manifest)
        require(manifest.capabilities.keys ||
                (manifest.keyRenderer.content.isEmpty() && listOf(
                    ThemeAsset.ICON_SHIFT_OFF,
                    ThemeAsset.ICON_SHIFT_ON,
                    ThemeAsset.ICON_SHIFT_LOCKED,
                ).none(manifest.assets::containsKey))) {
            "Key content configuration requires the keys capability"
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
            validateKeyPreview(manifest)
        }
        if (manifest.capabilities.keyPressAnimation) {
            val animation = requireNotNull(manifest.keyPressAnimation) {
                "keyPressAnimation capability needs a keyPressAnimation block"
            }
            val hasFrames = animation.frames.isNotEmpty()
            val hasLottie = !animation.lottieAsset.isNullOrBlank()
            val hasSpriteAtlas = animation.spriteAtlas != null
            val hasScript = animation.script != null
            require(listOf(hasFrames, hasLottie, hasSpriteAtlas, hasScript).count { it } == 1) {
                "Key animation needs exactly one of frames, lottieAsset, spriteAtlas, or script"
            }
            if (hasFrames) {
                require(animation.frames.size <= MAX_ANIMATION_FRAMES) {
                    "Key animation has too many frames"
                }
                require(animation.frames.distinct().size == animation.frames.size) {
                    "Key animation contains duplicate frames"
                }
                require(animation.frames.all(manifest.assets::containsKey)) {
                    "Key animation references an asset missing from assets"
                }
            } else if (hasLottie) {
                val asset = requireNotNull(animation.lottieAsset)
                val reference = manifest.assets[asset]
                require(reference != null && reference.endsWith(".json")) {
                    "Lottie animation must reference a declared JSON asset"
                }
            } else if (hasSpriteAtlas) {
                val atlas = requireNotNull(animation.spriteAtlas)
                val reference = manifest.assets[atlas.asset]
                require(reference != null && (reference.endsWith(".png") ||
                        reference.endsWith(".webp"))) {
                    "Sprite atlas must reference a declared PNG or WebP asset"
                }
                require(atlas.columns in 1..32 && atlas.rows in 1..32) {
                    "Invalid sprite atlas grid"
                }
                val availableFrames = atlas.columns * atlas.rows
                require(availableFrames <= MAX_ANIMATION_FRAMES) {
                    "Sprite atlas has too many cells"
                }
                require(atlas.frameCount == null || atlas.frameCount in 1..availableFrames) {
                    "Invalid sprite atlas frame count"
                }
            } else {
                val script = requireNotNull(animation.script)
                val reference = manifest.assets[script.asset]
                require(reference != null && reference.endsWith(".luau")) {
                    "huBoard Motion must reference a declared Luau asset"
                }
                require(script.apiVersion == 1) { "Unsupported huBoard Motion API version" }
                require(script.memoryLimitKb in 512..4_096) {
                    "Invalid huBoard Motion memory limit"
                }
                require(script.frameTimeLimitMs in 0.25f..4f) {
                    "Invalid huBoard Motion frame-time limit"
                }
                require(script.maxDrawCommandsPerFrame in 1..256) {
                    "Invalid huBoard Motion draw-command limit"
                }
                require(animation.durationMs in 16L..5_000L) {
                    "huBoard Motion requires a bounded animation duration"
                }
            }
            require(animation.frameDurationMs in 8L..1_000L) { "Invalid animation frame duration" }
            require(animation.durationMs == 0L || animation.durationMs in 16L..5_000L) {
                "Invalid animation duration"
            }
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
            validateToolbarRenderer(manifest.toolbarRenderer)
        } else {
            require(manifest.toolbarRenderer == ToolbarRendererConfig()) {
                "toolbarRenderer requires the matching capability"
            }
        }
        if (manifest.capabilities.clipboard) {
            requireAssets(manifest, ThemeAsset.CLIPBOARD_SUGGESTION_BACKGROUND)
            validateClipboardRenderer(manifest)
        }
        if (manifest.capabilities.keyboardBackground) {
            requireAssets(manifest, ThemeAsset.KEYBOARD_BACKGROUND)
        }
    }

    private fun validateAssetRendering(manifest: VisualThemeManifest) {
        require(manifest.rendering.keys.all(manifest.assets::containsKey)) {
            "Asset rendering references an asset missing from assets"
        }
        manifest.rendering.forEach { (_, spec) ->
            validateInsets(spec.insets)
            require(spec.cornerScale in 0.1f..4f) { "Invalid asset corner scale" }
            if (spec.mode != ThemeAssetScaleMode.NINE_SLICE) {
                require(spec.insets == ThemeAssetInsets()) {
                    "Slice insets require nineSlice rendering"
                }
                require(spec.cornerScale == 1f) {
                    "Corner scale requires nineSlice rendering"
                }
            }
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

    private fun validateKeyContent(manifest: VisualThemeManifest) {
        val content = manifest.keyRenderer.content
        require(content.keys.all(ThemeKeyClass.known::contains)) {
            "Unknown key content classes: ${content.keys - ThemeKeyClass.known}"
        }
        content.values.forEach { spec ->
            require(spec.centerX in 0f..1f && spec.centerY in 0f..1f) {
                "Invalid key content alignment"
            }
            listOfNotNull(spec.iconWidth, spec.iconHeight).forEach {
                require(it in 0.05f..1f) { "Invalid key icon geometry" }
            }
        }

        val shiftIcons = listOf(
            ThemeAsset.ICON_SHIFT_OFF,
            ThemeAsset.ICON_SHIFT_ON,
            ThemeAsset.ICON_SHIFT_LOCKED,
        )
        if (shiftIcons.any(manifest.assets::containsKey)) {
            require(manifest.assets.containsKey(ThemeAsset.ICON_SHIFT_OFF)) {
                "Shift state icons require icon.shift.off as the fallback"
            }
        }

        val embeddedBackgrounds = mapOf(
            ThemeKeyClass.REGULAR to listOf(ThemeAsset.KEY_NORMAL, ThemeAsset.KEY_PRESSED),
            ThemeKeyClass.SPACE to listOf(ThemeAsset.SPACE_NORMAL, ThemeAsset.SPACE_PRESSED),
            ThemeKeyClass.SHIFT to listOf(ThemeAsset.SHIFT_NORMAL, ThemeAsset.SHIFT_PRESSED),
            ThemeKeyClass.DELETE to listOf(ThemeAsset.DELETE_NORMAL, ThemeAsset.DELETE_PRESSED),
            ThemeKeyClass.ACTION to listOf(ThemeAsset.ACTION_NORMAL, ThemeAsset.ACTION_PRESSED),
            ThemeKeyClass.ROUND_FUNCTION to listOf(
                ThemeAsset.ROUND_FUNCTION_NORMAL,
                ThemeAsset.ROUND_FUNCTION_PRESSED,
            ),
            ThemeKeyClass.DIAMOND_FUNCTION to listOf(
                ThemeAsset.DIAMOND_FUNCTION_NORMAL,
                ThemeAsset.DIAMOND_FUNCTION_PRESSED,
            ),
        )
        content.forEach { (keyClass, spec) ->
            if (spec.iconMode != ThemeKeyIconMode.EMBEDDED) return@forEach
            val backgrounds = embeddedBackgrounds[keyClass]
            require(backgrounds != null && backgrounds.all(manifest.assets::containsKey)) {
                "Embedded $keyClass icons require dedicated normal and pressed backgrounds"
            }
        }
    }

    private fun validateClipboardRenderer(manifest: VisualThemeManifest) {
        val entryAssets = listOf(
            ThemeAsset.CLIPBOARD_ENTRY_NORMAL,
            ThemeAsset.CLIPBOARD_ENTRY_PRESSED,
        )
        require(entryAssets.all(manifest.assets::containsKey) ||
                entryAssets.none(manifest.assets::containsKey)) {
            "Clipboard entry normal and pressed assets must be provided together"
        }
        val pinnedEntryAssets = listOf(
            ThemeAsset.CLIPBOARD_ENTRY_PINNED,
            ThemeAsset.CLIPBOARD_ENTRY_PINNED_PRESSED,
        )
        require(pinnedEntryAssets.all(manifest.assets::containsKey) ||
                pinnedEntryAssets.none(manifest.assets::containsKey)) {
            "Clipboard pinned entry normal and pressed assets must be provided together"
        }
        listOf(
            manifest.clipboardRenderer.entryInsets,
            manifest.clipboardRenderer.pinnedEntryInsets,
        ).forEach(::validateInsets)
        require(manifest.clipboardRenderer.entryCornerScale in 0.1f..2f &&
                manifest.clipboardRenderer.pinnedEntryCornerScale in 0.1f..2f) {
            "Invalid clipboard entry corner scale"
        }
        require(manifest.clipboardRenderer.pinnedContentEndPaddingDp in 0..64) {
            "Invalid pinned clipboard entry content padding"
        }
    }

    private fun validateToolbarRenderer(config: ToolbarRendererConfig) {
        config.heightDp?.let { require(it in 24f..96f) { "Invalid toolbar height" } }
        listOf(config.startWidthDp, config.endWidthDp, config.keyWidthDp).filterNotNull()
            .forEach { require(it in 24f..160f) { "Invalid toolbar width" } }
        listOf(config.contentPaddingStartDp, config.contentPaddingEndDp).filterNotNull()
            .forEach { require(it in 0f..64f) { "Invalid toolbar content padding" } }
        config.iconSizeDp?.let { require(it in 8f..64f) { "Invalid toolbar icon size" } }
        listOf(
            config.iconOffsetXDp,
            config.iconOffsetYDp,
            config.startArtworkOffsetXDp,
            config.startArtworkOffsetYDp,
            config.endArtworkOffsetXDp,
            config.endArtworkOffsetYDp,
        ).filterNotNull().forEach {
            require(it in -48f..48f) { "Invalid toolbar artwork offset" }
        }
    }

    private fun validateInsets(insets: ThemeAssetInsets) {
        require(listOf(insets.leftPx, insets.topPx, insets.rightPx, insets.bottomPx)
            .all { it in 0..4_096 }) {
            "Invalid asset slice insets"
        }
    }

    private fun validateKeyPreview(manifest: VisualThemeManifest) {
        val config = manifest.keyPreview
        config.faceBounds?.let { bounds ->
            require(bounds.left >= 0f && bounds.right > bounds.left && bounds.right <= 1f &&
                    bounds.top >= 0f && bounds.bottom > bounds.top && bounds.bottom <= 1f) {
                "Invalid normalized key preview face bounds"
            }
            require(config.verticalOverscan in 0f..0.5f) { "Invalid key preview overscan" }
            require(config.gapDp in -16f..64f) { "Invalid key preview gap" }
            config.textColor?.let(::parseColor)
            return
        }
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

    internal fun validateSpriteAtlasDimensions(
        manifest: VisualThemeManifest,
        asset: String,
        width: Int,
        height: Int,
    ) {
        val atlas = manifest.keyPressAnimation?.spriteAtlas ?: return
        if (atlas.asset != asset) return
        VisualThemeSpriteAtlas.frameRects(width, height, atlas)
    }

    private fun requireAssets(manifest: VisualThemeManifest, vararg keys: String) {
        val missing = keys.filterNot(manifest.assets::containsKey)
        require(missing.isEmpty()) { "Missing required visual theme assets: $missing" }
    }

    fun parseColor(value: String): Int {
        require(color.matches(value)) { "Invalid theme color '$value'" }
        val hex = value.substring(1).toLong(16)
        return if (value.length == 7) (hex or 0xFF000000).toInt() else hex.toInt()
    }

    fun isSafeRelativePath(path: String): Boolean {
        if (path.startsWith('/') || path.startsWith('\\')) return false
        val segments = path.replace('\\', '/').split('/')
        return segments.isNotEmpty() && segments.none { it.isEmpty() || it == "." || it == ".." }
    }
}
