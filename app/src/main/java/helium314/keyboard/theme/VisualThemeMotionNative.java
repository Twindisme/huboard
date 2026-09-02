/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.theme;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;

import helium314.keyboard.latin.utils.JniUtils;

/** JNI boundary for the sandboxed Luau visual-theme runtime. */
@Keep
final class VisualThemeMotionNative {
    static {
        JniUtils.loadNativeLibrary();
    }

    private VisualThemeMotionNative() {}

    static native long nativeCreate(
            @NonNull byte[] source,
            @NonNull String[] assetNames,
            int maxMemoryBytes,
            int maxDrawCommands,
            int frameBudgetMicros,
            long maximumDurationMs,
            int maxEffects);

    static native boolean nativeStart(
            long handle,
            float centerX,
            float centerY,
            float keyWidth,
            float keyHeight,
            float viewportWidth,
            float viewportHeight,
            long nowMs,
            int seed);

    @NonNull
    static native float[] nativeFrame(
            long handle,
            long nowMs,
            float viewportWidth,
            float viewportHeight);

    static native boolean nativeHasEffects(long handle);

    static native void nativeClear(long handle);

    @NonNull
    static native String nativeLastError(long handle);

    static native void nativeClose(long handle);
}
