// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualThemeMotionRuntimeTest {
    @Test
    fun executesSandboxedStatefulDrawScript() {
        val source = """
            assert(os == nil)
            assert(require == nil)
            return {
                onPress = function(x, y, _, _, _, _, _, nowSeconds)
                    assert(nowSeconds == 1)
                    return { x = x, y = y }
                end,
                frame = function(state, dt, elapsed)
                    state.x += 100 * dt
                    motion.circle(state.x, state.y, 8, 0xFFFFFFFF, 1)
                    return elapsed < 0.1
                end,
            }
        """.trimIndent().encodeToByteArray()

        VisualThemeMotionRuntime(
            source = source,
            assetNames = emptyArray(),
            config = ThemeMotionScriptConfig(
                asset = "animation.test",
                memoryLimitKb = 512,
                maxDrawCommandsPerFrame = 4,
            ),
            maximumDurationMs = 500,
            maxEffects = 2,
        ).use { runtime ->
            assertTrue(runtime.start(20f, 30f, 40f, 50f, 300f, 200f, 1_000L, 7))

            val firstFrame = runtime.frame(1_016L, 300f, 200f)
            assertEquals(10, firstFrame.size, runtime.lastError())
            assertEquals(1f, firstFrame[0])
            assertEquals(21.6f, firstFrame[1], 0.001f)
            assertTrue(runtime.hasEffects())

            runtime.frame(1_200L, 300f, 200f)
            assertFalse(runtime.hasEffects())
        }
    }

    @Test
    fun executesAstralWeaveConstellationScript() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = context.assets
            .open("visual-themes/astral_weave/assets/astral_constellation.luau")
            .use { it.readBytes() }

        VisualThemeMotionRuntime(
            source = source,
            assetNames = emptyArray(),
            config = ThemeMotionScriptConfig(
                asset = "animation.constellation_script",
                memoryLimitKb = 2_048,
                frameTimeLimitMs = 4f,
                maxDrawCommandsPerFrame = 256,
            ),
            maximumDurationMs = 1_700,
            maxEffects = 16,
        ).use { runtime ->
            assertTrue(runtime.start(80f, 120f, 64f, 84f, 600f, 300f, 1_000L, 11))
            assertTrue(runtime.start(210f, 80f, 64f, 84f, 600f, 300f, 1_004L, 22))
            assertTrue(runtime.start(330f, 150f, 64f, 84f, 600f, 300f, 1_008L, 33))

            val commands = runtime.frame(1_080L, 600f, 300f)
            assertTrue(runtime.lastError().isEmpty(), runtime.lastError())
            assertTrue(commands.isNotEmpty())
            val commandTypes = commands.toList().chunked(10).map { it.first() }
            assertTrue(4f in commandTypes, "Constellation did not emit connecting lines")
            assertTrue(2f in commandTypes, "Constellation did not emit star glows")

            repeat(13) { index ->
                assertTrue(
                    runtime.start(
                        40f + index * 38f,
                        70f + index % 3 * 55f,
                        64f,
                        84f,
                        600f,
                        300f,
                        1_084L + index * 4L,
                        100 + index,
                    ),
                )
            }
            val burstCommands = runtime.frame(1_150L, 600f, 300f)
            assertTrue(runtime.lastError().isEmpty(), runtime.lastError())
            assertTrue(burstCommands.size / 10 <= 256)
            assertTrue(runtime.hasEffects())
        }
    }

    @Test
    fun astralWeaveKeepsLinesWhileTheirOpacityFalls() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = context.assets
            .open("visual-themes/astral_weave/assets/astral_constellation.luau")
            .use { it.readBytes() }

        VisualThemeMotionRuntime(
            source = source,
            assetNames = emptyArray(),
            config = ThemeMotionScriptConfig(
                asset = "animation.constellation_script",
                memoryLimitKb = 2_048,
                frameTimeLimitMs = 4f,
                maxDrawCommandsPerFrame = 256,
            ),
            maximumDurationMs = 1_700,
            maxEffects = 16,
        ).use { runtime ->
            assertTrue(runtime.start(80f, 120f, 64f, 84f, 600f, 300f, 1_000L, 11))
            assertTrue(runtime.start(210f, 80f, 64f, 84f, 600f, 300f, 1_004L, 22))

            val steady = connectionLineAlphas(runtime.frame(1_500L, 600f, 300f))
            val fading = connectionLineAlphas(runtime.frame(2_300L, 600f, 300f))
            val nearlyGone = connectionLineAlphas(runtime.frame(2_640L, 600f, 300f))

            assertEquals(2, steady.size)
            assertEquals(2, fading.size)
            assertEquals(2, nearlyGone.size)
            assertTrue(fading.max() < steady.max() * 0.8f)
            assertTrue(nearlyGone.max() < steady.max() * 0.1f)
            assertTrue(runtime.lastError().isEmpty(), runtime.lastError())
        }
    }

    private fun connectionLineAlphas(commands: FloatArray): List<Float> = commands
        .toList()
        .chunked(10)
        .filter { command -> command.first() == 4f }
        .map { command -> command[6] }
}
