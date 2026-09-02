// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualThemeSvgTest {
    @Test
    fun parsesAndRendersStaticSvg() {
        val document = parse(
            """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="16" viewBox="0 0 24 16"><path fill="#fff" d="M0 0h24v16H0z"/></svg>""",
        )

        assertEquals(24, document.width)
        assertEquals(16, document.height)
        assertNotNull(document.bitmap())
    }

    @Test
    fun usesViewBoxWhenDimensionsAreOmitted() {
        val document = parse(
            """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 32"><circle cx="24" cy="16" r="12"/></svg>""",
        )

        assertEquals(48, document.width)
        assertEquals(32, document.height)
    }

    @Test
    fun rejectsExternalContent() {
        assertFailsWith<IllegalArgumentException> {
            parse(
                """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><image href="https://example.com/image.png"/></svg>""",
            )
        }
    }

    @Test
    fun rejectsEntities() {
        assertFailsWith<IllegalArgumentException> {
            parse(
                """<!DOCTYPE svg [<!ENTITY x "boom">]><svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"><text>&x;</text></svg>""",
            )
        }
    }

    private fun parse(svg: String): VisualThemeSvg.Document =
        ByteArrayInputStream(svg.encodeToByteArray()).use(VisualThemeSvg::parse)
}
