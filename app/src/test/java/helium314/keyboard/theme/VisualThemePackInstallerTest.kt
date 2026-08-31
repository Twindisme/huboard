// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VisualThemePackInstallerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @BeforeTest
    fun setUp() {
        VisualThemeManager.clearCache()
        VisualThemePackInstaller.uninstall(context, THEME_ID)
        VisualThemeManager.clearCache()
    }

    @AfterTest
    fun cleanUp() {
        VisualThemePackInstaller.uninstall(context, THEME_ID)
        VisualThemeManager.clearCache()
    }

    @Test
    fun installsAndRemovesPack() {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "$THEME_ID",
              "displayName": "Installed test"
            }
        """.trimIndent()
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.encodeToByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        val installed = ByteArrayInputStream(archive).use {
            VisualThemePackInstaller.install(context, it)
        }

        assertEquals(THEME_ID, installed.id)
        assertTrue(VisualThemeManager.availableThemes(context).any { it.id == THEME_ID })
        assertTrue(VisualThemePackInstaller.uninstall(context, THEME_ID))
        assertTrue(VisualThemeManager.availableThemes(context).none { it.id == THEME_ID })
    }

    private companion object {
        const val THEME_ID = "installed_test"
    }
}
