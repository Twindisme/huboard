// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.ComponentName
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.database.ClipboardDao
import helium314.keyboard.latin.utils.prefs
import java.io.File
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualThemeGoldenTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val record = InstrumentationRegistry.getArguments()
        .getString("recordVisualThemeGoldens") == "true"
    private var previousIme: String? = null
    private var previousAnimationScales = emptyMap<String, String>()

    @BeforeTest
    fun setUp() {
        previousIme = shell("settings get secure default_input_method").trim()
        context.prefs().edit {
            putString(Settings.PREF_VISUAL_THEME_PACK, "hu_tao")
        }
        VisualThemeManager.clearCache()
        ClipboardDao.getInstance(context)?.clear()
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            @Suppress("DEPRECATION")
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
        val ime = "${context.packageName}/helium314.keyboard.latin.LatinIME"
        shell("ime enable $ime")
        shell("ime set $ime")
        previousAnimationScales = ANIMATION_SCALE_KEYS.associateWith { key ->
            shell("settings get global $key").trim()
        }
        ANIMATION_SCALE_KEYS.forEach { key -> shell("settings put global $key 0") }
    }

    @AfterTest
    fun tearDown() {
        previousIme?.takeIf { it.isNotBlank() && it != "null" }?.let { shell("ime set $it") }
        previousAnimationScales.forEach { (key, value) ->
            if (value.isBlank() || value == "null") {
                shell("settings delete global $key")
            } else {
                shell("settings put global $key $value")
            }
        }
    }

    @Test
    fun alphabetKeyboard() {
        withKeyboard { assertGolden("hu_tao_alphabet") }
    }

    @Test
    fun shiftedKeyboard() {
        withKeyboard {
            val shift = keyboardPoint(xFraction = 0.07f, yFraction = 0.625f)
            assertTrue(device.click(shift.x, shift.y))
            device.waitForIdle()
            assertGolden("hu_tao_shifted")
        }
    }

    @Test
    fun symbolsKeyboard() {
        withKeyboard {
            val symbols = keyboardPoint(xFraction = 0.07f, yFraction = 0.875f)
            assertTrue(device.click(symbols.x, symbols.y))
            device.waitForIdle()
            assertGolden("hu_tao_symbols")
        }
    }

    @Test
    fun clipboardPanel() {
        withKeyboard {
            val clipboard = requireObject(
                By.res(context.packageName, "visual_theme_toolbar_start_key"),
            )
            clipboard.click()
            device.waitForIdle()
            assertGolden("hu_tao_clipboard")
        }
    }

    @Test
    fun pressedKeyPreview() {
        withKeyboard {
            val center = keyboardPoint(xFraction = 0.05f, yFraction = 0.125f)
            val downTime = SystemClock.uptimeMillis()
            val down = MotionEvent.obtain(
                downTime,
                downTime,
                MotionEvent.ACTION_DOWN,
                center.x.toFloat(),
                center.y.toFloat(),
                0,
            ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
            assertTrue(instrumentation.uiAutomation.injectInputEvent(down, true))
            down.recycle()
            SystemClock.sleep(120L)
            try {
                assertGolden("hu_tao_key_preview")
            } finally {
                val upTime = SystemClock.uptimeMillis()
                val up = MotionEvent.obtain(
                    downTime,
                    upTime,
                    MotionEvent.ACTION_UP,
                    center.x.toFloat(),
                    center.y.toFloat(),
                    0,
                ).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                instrumentation.uiAutomation.injectInputEvent(up, true)
                up.recycle()
            }
        }
    }

    private fun withKeyboard(block: () -> Unit) {
        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                "helium314.keyboard.theme.VisualThemeGoldenHostActivity",
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        ActivityScenario.launch<Activity>(intent).use {
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5_000L))
            SystemClock.sleep(1_000L)
            device.waitForIdle()
            block()
        }
    }

    private fun requireObject(vararg selectors: androidx.test.uiautomator.BySelector) =
        selectors.firstNotNullOfOrNull(device::findObject)
            ?: error("Could not find keyboard control matching $selectors")

    private fun keyboardPoint(xFraction: Float, yFraction: Float): android.graphics.Point {
        val bounds = requireObject(By.res(context.packageName, "keyboard_view")).visibleBounds
        return android.graphics.Point(
            (bounds.left + bounds.width() * xFraction).toInt(),
            (bounds.top + bounds.height() * yFraction).toInt(),
        )
    }

    private fun assertGolden(name: String) {
        val screenshot = assertNotNull(instrumentation.uiAutomation.takeScreenshot())
        val outputDirectory = File(
            assertNotNull(context.getExternalFilesDir(null)),
            GOLDEN_DIRECTORY,
        ).apply { mkdirs() }
        val actualFile = File(outputDirectory, "$name.png")
        actualFile.outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (record) return

        val expected = instrumentation.context.assets.open("$GOLDEN_DIRECTORY/$name.png").use {
            assertNotNull(BitmapFactory.decodeStream(it))
        }
        assertTrue(
            expected.width == screenshot.width && expected.height == screenshot.height,
            "Golden dimensions changed for $name; actual saved to ${actualFile.path}",
        )
        val diff = Bitmap.createBitmap(screenshot.width, screenshot.height, Bitmap.Config.ARGB_8888)
        val expectedPixels = IntArray(expected.width * expected.height)
        val actualPixels = IntArray(screenshot.width * screenshot.height)
        expected.getPixels(expectedPixels, 0, expected.width, 0, 0, expected.width, expected.height)
        screenshot.getPixels(actualPixels, 0, screenshot.width, 0, 0, screenshot.width, screenshot.height)
        var changed = 0
        for (index in expectedPixels.indices) {
            val expectedPixel = expectedPixels[index]
            val actualPixel = actualPixels[index]
            val different = maxOf(
                abs(Color.red(expectedPixel) - Color.red(actualPixel)),
                abs(Color.green(expectedPixel) - Color.green(actualPixel)),
                abs(Color.blue(expectedPixel) - Color.blue(actualPixel)),
                abs(Color.alpha(expectedPixel) - Color.alpha(actualPixel)),
            ) > CHANNEL_TOLERANCE
            if (different) {
                changed++
                actualPixels[index] = Color.MAGENTA
            } else {
                actualPixels[index] = Color.TRANSPARENT
            }
        }
        val changedFraction = changed / expectedPixels.size.toFloat()
        if (changedFraction > MAX_CHANGED_FRACTION) {
            diff.setPixels(actualPixels, 0, diff.width, 0, 0, diff.width, diff.height)
            File(outputDirectory, "${name}_diff.png").outputStream().use {
                diff.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertTrue(
            changedFraction <= MAX_CHANGED_FRACTION,
            "$name changed by ${changedFraction * 100f}%; actual and diff are in ${outputDirectory.path}",
        )
    }

    private fun shell(command: String): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {
            it.readText()
        }
    }

    private companion object {
        const val GOLDEN_DIRECTORY = "visual-theme-goldens"
        const val CHANNEL_TOLERANCE = 8
        const val MAX_CHANGED_FRACTION = 0.001f
        val ANIMATION_SCALE_KEYS = listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale",
        )
    }
}
