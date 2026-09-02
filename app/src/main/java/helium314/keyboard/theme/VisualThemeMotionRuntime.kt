// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.theme

import java.util.concurrent.atomic.AtomicLong

/** Owns one isolated Luau VM for the active theme's key-press script. */
internal class VisualThemeMotionRuntime(
    source: ByteArray,
    assetNames: Array<String>,
    config: ThemeMotionScriptConfig,
    maximumDurationMs: Long,
    maxEffects: Int,
) : AutoCloseable {
    private val handle = AtomicLong(
        VisualThemeMotionNative.nativeCreate(
            source,
            assetNames,
            config.memoryLimitKb * 1024,
            config.maxDrawCommandsPerFrame,
            (config.frameTimeLimitMs * 1_000f).toInt(),
            maximumDurationMs,
            maxEffects,
        ).also { require(it != 0L) { "Could not create huBoard Motion runtime" } },
    )

    fun start(
        centerX: Float,
        centerY: Float,
        keyWidth: Float,
        keyHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        nowMs: Long,
        seed: Int,
    ): Boolean = withHandle { handle ->
        VisualThemeMotionNative.nativeStart(
            handle,
            centerX,
            centerY,
            keyWidth,
            keyHeight,
            viewportWidth,
            viewportHeight,
            nowMs,
            seed,
        )
    } ?: false

    fun frame(nowMs: Long, viewportWidth: Float, viewportHeight: Float): FloatArray =
        withHandle { handle ->
            VisualThemeMotionNative.nativeFrame(handle, nowMs, viewportWidth, viewportHeight)
        } ?: FloatArray(0)

    fun hasEffects(): Boolean = withHandle(VisualThemeMotionNative::nativeHasEffects) ?: false

    fun clear() {
        withHandle { VisualThemeMotionNative.nativeClear(it) }
    }

    fun lastError(): String = withHandle(VisualThemeMotionNative::nativeLastError).orEmpty()

    override fun close() {
        handle.getAndSet(0L).takeIf { it != 0L }?.let(VisualThemeMotionNative::nativeClose)
    }

    private inline fun <T> withHandle(block: (Long) -> T): T? =
        handle.get().takeIf { it != 0L }?.let(block)

    @Suppress("deprecation")
    protected fun finalize() {
        close()
    }
}
