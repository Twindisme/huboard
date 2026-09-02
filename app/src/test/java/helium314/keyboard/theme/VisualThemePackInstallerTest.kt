// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
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
              "displayName": "Installed test",
              "assets": {
                "icon.backspace": "file:assets/backspace.svg"
              }
            }
        """.trimIndent()
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/backspace.svg"))
                zip.write(VALID_SVG.encodeToByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        val installed = ByteArrayInputStream(archive).use {
            VisualThemePackInstaller.install(context, it)
        }

        assertEquals(THEME_ID, installed.id)
        val theme = VisualThemeManager.availableThemes(context).first { it.id == THEME_ID }
        assertNotNull(theme.drawable(context, ThemeAsset.ICON_BACKSPACE))
        assertTrue(VisualThemePackInstaller.uninstall(context, THEME_ID))
        assertTrue(VisualThemeManager.availableThemes(context).none { it.id == THEME_ID })
    }

    @Test
    fun installsAndParsesLottieAnimation() {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "$THEME_ID",
              "displayName": "Lottie test",
              "minimumEngineVersion": 2,
              "capabilities": { "keyPressAnimation": true },
              "assets": {
                "animation.spark": "file:assets/spark.json"
              },
              "keyPressAnimation": {
                "lottieAsset": "animation.spark",
                "durationMs": 280
              }
            }
        """.trimIndent()
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/spark.json"))
                zip.write(VALID_LOTTIE.encodeToByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        ByteArrayInputStream(archive).use {
            VisualThemePackInstaller.install(context, it)
        }

        val theme = VisualThemeManager.availableThemes(context).first { it.id == THEME_ID }
        val composition = assertNotNull(theme.lottieComposition(context, "animation.spark"))
        assertEquals(64, composition.bounds.width())
        assertTrue(composition.duration > 0f)
    }

    @Test
    fun installshuBoardMotionScript() {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "$THEME_ID",
              "displayName": "Motion test",
              "minimumEngineVersion": 3,
              "capabilities": { "keyPressAnimation": true },
              "assets": {
                "animation.motion": "file:assets/key_press.luau"
              },
              "keyPressAnimation": {
                "script": { "asset": "animation.motion" },
                "durationMs": 1000
              }
            }
        """.trimIndent()
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/key_press.luau"))
                zip.write(VALID_MOTION_SCRIPT.encodeToByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        ByteArrayInputStream(archive).use {
            VisualThemePackInstaller.install(context, it)
        }

        val theme = VisualThemeManager.availableThemes(context).first { it.id == THEME_ID }
        val script = assertNotNull(theme.manifest.keyPressAnimation?.script)
        assertEquals("animation.motion", script.asset)
        assertEquals(
            VALID_MOTION_SCRIPT,
            theme.bytes(context, script.asset, VisualThemeValidator.MAX_SCRIPT_BYTES)
                ?.decodeToString(),
        )
    }

    @Test
    fun installsKeyRenderingAndShiftStates() {
        val manifest = """
            {
              "schemaVersion": 1,
              "id": "$THEME_ID",
              "displayName": "Stateful key test",
              "minimumEngineVersion": 2,
              "capabilities": { "keys": true },
              "assets": {
                "key.normal": "file:assets/art.svg",
                "key.pressed": "file:assets/art.svg",
                "icon.backspace": "file:assets/art.svg",
                "icon.action": "file:assets/art.svg",
                "icon.space.glyph": "file:assets/art.svg",
                "icon.space.language": "file:assets/art.svg",
                "icon.shift.off": "file:assets/art.svg",
                "icon.shift.on": "file:assets/art.svg",
                "icon.shift.locked": "file:assets/art.svg"
              },
              "rendering": {
                "key.normal": {
                  "mode": "nineSlice",
                  "insets": { "leftPx": 2, "topPx": 2, "rightPx": 2, "bottomPx": 2 }
                }
              },
              "keyRenderer": {
                "content": {
                  "regular": { "centerY": 0.48 },
                  "shift": {
                    "iconMode": "overlay",
                    "iconWidth": 0.5,
                    "iconHeight": 0.45
                  }
                }
              }
            }
        """.trimIndent()
        val archive = ByteArrayOutputStream().use { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("assets/art.svg"))
                zip.write(VALID_SVG.encodeToByteArray())
                zip.closeEntry()
            }
            bytes.toByteArray()
        }

        val installed = ByteArrayInputStream(archive).use {
            VisualThemePackInstaller.install(context, it)
        }

        assertEquals(
            ThemeAssetScaleMode.NINE_SLICE,
            installed.rendering[ThemeAsset.KEY_NORMAL]?.mode,
        )
        assertEquals(
            ThemeKeyIconMode.OVERLAY,
            installed.keyRenderer.content[ThemeKeyClass.SHIFT]?.iconMode,
        )
        assertEquals(0.48f, installed.keyRenderer.content[ThemeKeyClass.REGULAR]?.centerY)
    }

    @Test
    fun updatesOnlyToANewerVersion() {
        fun manifest(versionCode: Int, versionName: String) = """
            {
              "schemaVersion": 1,
              "id": "$THEME_ID",
              "displayName": "Versioned test",
              "versionCode": $versionCode,
              "versionName": "$versionName",
              "minimumEngineVersion": 2
            }
        """.trimIndent()

        ByteArrayInputStream(archive(manifest(1, "1.0"))).use {
            VisualThemePackInstaller.install(context, it)
        }
        val update = ByteArrayInputStream(archive(manifest(2, "2.0"))).use {
            VisualThemePackInstaller.installOrUpdate(context, it, THEME_ID)
        }

        assertEquals(VisualThemeInstallAction.UPDATED, update.action)
        assertEquals(
            2,
            VisualThemeManager.availableThemes(context)
                .first { it.id == THEME_ID }
                .manifest.versionCode,
        )
        assertFailsWith<IllegalArgumentException> {
            ByteArrayInputStream(archive(manifest(1, "1.0"))).use {
                VisualThemePackInstaller.installOrUpdate(context, it, THEME_ID)
            }
        }
        assertEquals(
            2,
            VisualThemeManager.availableThemes(context)
                .first { it.id == THEME_ID }
                .manifest.versionCode,
        )
    }

    @Test
    fun restoresBackupLeftByInterruptedUpdate() {
        val root = VisualThemeManager.installedThemesDirectory(context).apply { mkdirs() }
        val backup = File(root, ".backup-$THEME_ID").apply { mkdirs() }
        File(backup, "manifest.json").writeText(
            """
                {
                  "schemaVersion": 1,
                  "id": "$THEME_ID",
                  "displayName": "Recovered test",
                  "versionCode": 3,
                  "minimumEngineVersion": 2
                }
            """.trimIndent(),
        )

        VisualThemePackInstaller.recoverInterruptedUpdates(context)
        VisualThemeManager.clearCache()

        assertTrue(!backup.exists())
        assertEquals(
            3,
            VisualThemeManager.availableThemes(context)
                .first { it.id == THEME_ID }
                .manifest.versionCode,
        )
    }

    private fun archive(manifest: String): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.encodeToByteArray())
            zip.closeEntry()
        }
        bytes.toByteArray()
    }

    private companion object {
        const val THEME_ID = "installed_test"
        const val VALID_SVG = """<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24"><path fill="#fff" d="M2 2h20v20H2z"/></svg>"""
        const val VALID_LOTTIE = """{"v":"5.12.2","fr":60,"ip":0,"op":18,"w":64,"h":64,"nm":"spark","ddd":0,"assets":[],"layers":[]}"""
        const val VALID_MOTION_SCRIPT = """return { onPress = function() return {} end, frame = function() return false end }"""
    }
}
