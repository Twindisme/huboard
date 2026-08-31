// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import kotlin.test.Test
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
}
