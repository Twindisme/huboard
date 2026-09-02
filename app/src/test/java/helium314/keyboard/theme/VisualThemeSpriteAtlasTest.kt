// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualThemeSpriteAtlasTest {
    @Test
    fun createsRowMajorFrameRects() {
        val frames = VisualThemeSpriteAtlas.frameRects(
            bitmapWidth = 400,
            bitmapHeight = 200,
            config = KeyPressSpriteAtlasConfig(
                asset = "animation.atlas",
                columns = 4,
                rows = 2,
                frameCount = 6,
            ),
        )

        assertEquals(
            listOf(
                listOf(0, 0, 100, 100),
                listOf(100, 0, 200, 100),
                listOf(200, 0, 300, 100),
                listOf(300, 0, 400, 100),
                listOf(0, 100, 100, 200),
                listOf(100, 100, 200, 200),
            ),
            frames.map { listOf(it.left, it.top, it.right, it.bottom) },
        )
    }

    @Test
    fun rejectsUnevenGridDimensions() {
        assertFailsWith<IllegalArgumentException> {
            VisualThemeSpriteAtlas.frameRects(
                bitmapWidth = 401,
                bitmapHeight = 200,
                config = KeyPressSpriteAtlasConfig(
                    asset = "animation.atlas",
                    columns = 4,
                    rows = 2,
                ),
            )
        }
    }
}
