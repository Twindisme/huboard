// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VisualThemeValidatorTest {
    @Test
    fun acceptsMinimalClassicTheme() {
        VisualThemeValidator.validate(
            VisualThemeManifest(1, "classic_test", "Classic test"),
        )
    }

    @Test
    fun rejectsUnsafeAssetPath() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "unsafe",
                    displayName = "Unsafe",
                    assets = mapOf(
                        ThemeAsset.KEY_NORMAL to "file:assets/../outside.png",
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsSvgAsset() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "svg_theme",
                displayName = "SVG theme",
                assets = mapOf(
                    ThemeAsset.ICON_BACKSPACE to "file:assets/backspace.svg",
                ),
            ),
        )
    }

    @Test
    fun rejectsIncompleteClipboardEntryAssetPair() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "bad_clipboard_cards",
                    displayName = "Bad clipboard cards",
                    capabilities = ThemeCapabilities(clipboard = true),
                    assets = mapOf(
                        ThemeAsset.CLIPBOARD_SUGGESTION_BACKGROUND to
                            "file:assets/clipboard.svg",
                        ThemeAsset.CLIPBOARD_ENTRY_NORMAL to "file:assets/entry.png",
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsUnknownAssetKey() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "unknown_asset",
                    displayName = "Unknown asset",
                    assets = mapOf("keys.typo" to "file:assets/key.png"),
                ),
            )
        }
    }

    @Test
    fun rejectsAnimationFrameThatIsNotDeclared() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "bad_animation",
                    displayName = "Bad animation",
                    capabilities = ThemeCapabilities(keyPressAnimation = true),
                    keyPressAnimation = KeyPressAnimationConfig(
                        frames = listOf("animation.missing"),
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsAssetRendering() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "rendered",
                displayName = "Rendered",
                versionCode = 2,
                versionName = "1.1",
                minimumEngineVersion = 2,
                assets = mapOf(
                    ThemeAsset.CLIPBOARD_SUGGESTION_BACKGROUND to "file:assets/card.png",
                ),
                rendering = mapOf(
                    ThemeAsset.CLIPBOARD_SUGGESTION_BACKGROUND to ThemeAssetRenderSpec(
                        mode = ThemeAssetScaleMode.NINE_SLICE,
                        insets = ThemeAssetInsets(8, 8, 8, 8),
                        cornerScale = 0.75f,
                    ),
                ),
            ),
        )
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 2,
                    id = "future_schema",
                    displayName = "Future schema",
                ),
            )
        }
    }

    @Test
    fun acceptsLottieAnimation() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "lottie_animation",
                displayName = "Lottie animation",
                capabilities = ThemeCapabilities(keyPressAnimation = true),
                assets = mapOf("animation.spark" to "file:assets/spark.json"),
                keyPressAnimation = KeyPressAnimationConfig(
                    lottieAsset = "animation.spark",
                    durationMs = 280,
                ),
                minimumEngineVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsMixedFrameAndLottieAnimation() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "mixed_animation",
                    displayName = "Mixed animation",
                    capabilities = ThemeCapabilities(keyPressAnimation = true),
                    assets = mapOf(
                        "animation.frame" to "file:assets/frame.png",
                        "animation.spark" to "file:assets/spark.json",
                    ),
                    keyPressAnimation = KeyPressAnimationConfig(
                        frames = listOf("animation.frame"),
                        lottieAsset = "animation.spark",
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsSandboxedMotionScript() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "motion_script",
                displayName = "Motion script",
                capabilities = ThemeCapabilities(keyPressAnimation = true),
                assets = mapOf(
                    "animation.motion" to "file:assets/key_press.luau",
                ),
                keyPressAnimation = KeyPressAnimationConfig(
                    script = ThemeMotionScriptConfig(asset = "animation.motion"),
                    durationMs = 2_000,
                ),
                minimumEngineVersion = 3,
            ),
        )
    }

    @Test
    fun rejectsMotionScriptAlongsideAuthoredAnimation() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "mixed_motion",
                    displayName = "Mixed motion",
                    capabilities = ThemeCapabilities(keyPressAnimation = true),
                    assets = mapOf(
                        "animation.frame" to "file:assets/frame.png",
                        "animation.motion" to "file:assets/key_press.luau",
                    ),
                    keyPressAnimation = KeyPressAnimationConfig(
                        frames = listOf("animation.frame"),
                        script = ThemeMotionScriptConfig(asset = "animation.motion"),
                        durationMs = 2_000,
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsUnboundedMotionScript() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "unbounded_motion",
                    displayName = "Unbounded motion",
                    capabilities = ThemeCapabilities(keyPressAnimation = true),
                    assets = mapOf(
                        "animation.motion" to "file:assets/key_press.luau",
                    ),
                    keyPressAnimation = KeyPressAnimationConfig(
                        script = ThemeMotionScriptConfig(asset = "animation.motion"),
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsThemeColorPalette() {
        val manifest = VisualThemeManifest(
            schemaVersion = 1,
            id = "colored",
            displayName = "Colored",
            appearance = ThemeAppearance(
                keyboardStyle = "Rounded",
                keyBorders = true,
            ),
            colors = ThemeColors(
                accent = "#CD563C",
                background = "#48231F",
                keyBackground = "#4B302C",
                functionalKey = "#331E22",
                spaceBar = "#692D2B",
                keyText = "#FDECD2",
                keyHintText = "#D3B9A0",
            ),
        )

        VisualThemeValidator.validate(manifest)

        assertEquals("#CD563C", manifest.colors?.accent)
        assertEquals("Rounded", manifest.appearance.keyboardStyle)
    }

    @Test
    fun rejectsInvalidThemeColor() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "bad_color",
                    displayName = "Bad color",
                    colors = ThemeColors(
                        accent = "red",
                        background = "#000000",
                        keyBackground = "#000000",
                        functionalKey = "#000000",
                        spaceBar = "#000000",
                        keyText = "#FFFFFF",
                        keyHintText = "#FFFFFF",
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsUnknownKeyboardStyle() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "bad_style",
                    displayName = "Bad style",
                    appearance = ThemeAppearance(keyboardStyle = "Fancy"),
                ),
            )
        }
    }

    @Test
    fun acceptsShiftStatesAndPerClassContent() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "stateful_shift",
                displayName = "Stateful shift",
                capabilities = ThemeCapabilities(keys = true),
                assets = requiredKeyAssets() + mapOf(
                    ThemeAsset.ICON_SHIFT_OFF to "file:assets/shift_off.svg",
                    ThemeAsset.ICON_SHIFT_ON to "file:assets/shift_on.svg",
                    ThemeAsset.ICON_SHIFT_LOCKED to "file:assets/shift_locked.svg",
                ),
                keyRenderer = KeyRendererConfig(
                    content = mapOf(
                        ThemeKeyClass.REGULAR to ThemeKeyContentSpec(centerY = 0.48f),
                        ThemeKeyClass.SHIFT to ThemeKeyContentSpec(
                            iconMode = ThemeKeyIconMode.OVERLAY,
                            iconWidth = 0.5f,
                            iconHeight = 0.45f,
                        ),
                    ),
                ),
                minimumEngineVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsShiftStateIconsWithoutOffFallback() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "shift_without_fallback",
                    displayName = "Shift without fallback",
                    capabilities = ThemeCapabilities(keys = true),
                    assets = requiredKeyAssets() + mapOf(
                        ThemeAsset.ICON_SHIFT_ON to "file:assets/shift_on.svg",
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsEmbeddedIconWithoutDedicatedBackgrounds() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "missing_embedded_art",
                    displayName = "Missing embedded art",
                    capabilities = ThemeCapabilities(keys = true),
                    assets = requiredKeyAssets(),
                    keyRenderer = KeyRendererConfig(
                        content = mapOf(
                            ThemeKeyClass.SHIFT to ThemeKeyContentSpec(
                                iconMode = ThemeKeyIconMode.EMBEDDED,
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsPerClassKeyContent() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "key_content",
                displayName = "Key content",
                capabilities = ThemeCapabilities(keys = true),
                assets = requiredKeyAssets(),
                keyRenderer = KeyRendererConfig(
                    content = mapOf(
                        ThemeKeyClass.REGULAR to ThemeKeyContentSpec(centerY = 0.45f),
                    ),
                ),
            ),
        )
    }

    @Test
    fun acceptsDynamicToolbarIconsAndGeometry() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "dynamic_toolbar",
                displayName = "Dynamic toolbar",
                capabilities = ThemeCapabilities(toolbar = true),
                assets = mapOf(
                    ThemeAsset.TOOLBAR_BACKGROUND to "file:assets/toolbar.svg",
                    ThemeAsset.TOOLBAR_START to "file:assets/start.svg",
                    ThemeAsset.TOOLBAR_END to "file:assets/end.svg",
                    ThemeAsset.toolbarIcon("undo") to "file:assets/undo.svg",
                    ThemeAsset.THEME_THUMBNAIL to "file:assets/thumbnail.png",
                ),
                toolbarRenderer = ToolbarRendererConfig(
                    heightDp = 60f,
                    startWidthDp = 64f,
                    endWidthDp = 68f,
                    keyWidthDp = 52f,
                    contentPaddingStartDp = 4f,
                    contentPaddingEndDp = 6f,
                    iconSizeDp = 24f,
                    iconOffsetYDp = -1f,
                ),
                minimumEngineVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsToolbarGeometryWithoutCapability() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "orphan_toolbar_geometry",
                    displayName = "Orphan toolbar geometry",
                    toolbarRenderer = ToolbarRendererConfig(heightDp = 60f),
                ),
            )
        }
    }

    @Test
    fun acceptsNormalizedPreviewBounds() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "normalized_preview",
                displayName = "Normalized preview",
                capabilities = ThemeCapabilities(keyPreview = true),
                assets = mapOf(
                    ThemeAsset.KEY_PREVIEW to "file:assets/preview.svg",
                ),
                keyPreview = KeyPreviewConfig(
                    faceBounds = ThemeNormalizedRect(0.2f, 0.3f, 0.8f, 0.95f),
                ),
                minimumEngineVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsInvalidNormalizedPreviewBounds() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "bad_normalized_preview",
                    displayName = "Bad normalized preview",
                    capabilities = ThemeCapabilities(keyPreview = true),
                    assets = mapOf(
                        ThemeAsset.KEY_PREVIEW to "file:assets/preview.svg",
                    ),
                    keyPreview = KeyPreviewConfig(
                        faceBounds = ThemeNormalizedRect(0.2f, 0.3f, 1.1f, 0.95f),
                    ),
                ),
            )
        }
    }

    @Test
    fun acceptsSpriteAtlasAnimation() {
        VisualThemeValidator.validate(
            VisualThemeManifest(
                schemaVersion = 1,
                id = "atlas_animation",
                displayName = "Atlas animation",
                capabilities = ThemeCapabilities(keyPressAnimation = true),
                assets = mapOf(
                    "animation.spark_atlas" to "file:assets/spark_atlas.webp",
                ),
                keyPressAnimation = KeyPressAnimationConfig(
                    spriteAtlas = KeyPressSpriteAtlasConfig(
                        asset = "animation.spark_atlas",
                        columns = 4,
                        rows = 3,
                        frameCount = 10,
                    ),
                ),
                minimumEngineVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsSpriteAtlasAlongsideFrames() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeValidator.validate(
                VisualThemeManifest(
                    schemaVersion = 1,
                    id = "mixed_atlas_animation",
                    displayName = "Mixed atlas animation",
                    capabilities = ThemeCapabilities(keyPressAnimation = true),
                    assets = mapOf(
                        "animation.frame" to "file:assets/frame.png",
                        "animation.atlas" to "file:assets/atlas.png",
                    ),
                    keyPressAnimation = KeyPressAnimationConfig(
                        frames = listOf("animation.frame"),
                        spriteAtlas = KeyPressSpriteAtlasConfig(
                            asset = "animation.atlas",
                            columns = 2,
                            rows = 2,
                        ),
                    ),
                ),
            )
        }
    }

    private fun requiredKeyAssets(): Map<String, String> = mapOf(
        ThemeAsset.KEY_NORMAL to "file:assets/key_normal.png",
        ThemeAsset.KEY_PRESSED to "file:assets/key_pressed.png",
        ThemeAsset.ICON_BACKSPACE to "file:assets/backspace.svg",
        ThemeAsset.ICON_ACTION to "file:assets/action.svg",
        ThemeAsset.ICON_SPACE_GLYPH to "file:assets/space.svg",
        ThemeAsset.ICON_SPACE_LANGUAGE to "file:assets/language.svg",
    )
}
