/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Iterator;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.theme.KeyPressAnimationConfig;
import helium314.keyboard.theme.ResolvedVisualTheme;

/** Draws a theme's frame animation over pressed keys. */
public final class VisualThemeKeyPressAnimator {

    @NonNull
    private final Bitmap[] mFrames;
    @NonNull
    private final ArrayList<Effect> mEffects = new ArrayList<>();
    @NonNull
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    @NonNull
    private final RectF mDestination = new RectF();
    @NonNull
    private final KeyPressAnimationConfig mConfig;

    public VisualThemeKeyPressAnimator(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme) {
        if (!theme.getHasKeyPressAnimation()) {
            throw new IllegalArgumentException("Theme does not provide a key press animation");
        }
        mConfig = theme.getManifest().getKeyPressAnimation();
        if (mConfig == null) {
            throw new IllegalArgumentException("Theme has no key press animation config");
        }
        mFrames = new Bitmap[mConfig.getFrames().size()];
        for (int index = 0; index < mFrames.length; index++) {
            mFrames[index] = theme.bitmap(context, mConfig.getFrames().get(index));
            if (mFrames[index] == null) {
                throw new IllegalArgumentException("Theme animation frame could not be decoded");
            }
        }
    }

    public void start(@NonNull final Key key, final int paddingLeft, final int paddingTop) {
        if (mConfig.getCharacterKeysOnly() && !isVisibleCharacterKey(key)) {
            return;
        }
        if (mEffects.size() == mConfig.getMaxSimultaneousEffects()) {
            mEffects.remove(0);
        }
        final float centerX = paddingLeft + key.getDrawX() + key.getDrawWidth() * 0.5f;
        final float centerY = paddingTop + key.getY() + key.getHeight() * 0.5f;
        final float height = key.getHeight() * mConfig.getHeightToKeyHeight();
        final Bitmap firstFrame = mFrames[0];
        final float width = height * firstFrame.getWidth() / firstFrame.getHeight();
        mEffects.add(new Effect(centerX, centerY, width, height, SystemClock.uptimeMillis()));
    }

    private static boolean isVisibleCharacterKey(@NonNull final Key key) {
        if (key.getIconName() != null) {
            return false;
        }
        final int code = key.getCode();
        if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            return !TextUtils.isEmpty(key.getOutputText());
        }
        if (code <= Constants.CODE_SPACE || !Character.isValidCodePoint(code)
                || Character.isWhitespace(code)) {
            return false;
        }
        final int type = Character.getType(code);
        return type != Character.CONTROL && type != Character.FORMAT;
    }

    /**
     * @return {@code true} while another animation frame needs to be drawn.
     */
    public boolean draw(@NonNull final Canvas canvas, final long now) {
        final Iterator<Effect> iterator = mEffects.iterator();
        while (iterator.hasNext()) {
            final Effect effect = iterator.next();
            final int frameIndex = (int) ((now - effect.startTime)
                    / mConfig.getFrameDurationMs());
            if (frameIndex >= mFrames.length) {
                iterator.remove();
                continue;
            }
            if (frameIndex < 0) {
                continue;
            }

            final float halfWidth = effect.width * 0.5f;
            final float halfHeight = effect.height * 0.5f;
            mDestination.set(
                    effect.centerX - halfWidth,
                    effect.centerY - halfHeight,
                    effect.centerX + halfWidth,
                    effect.centerY + halfHeight
            );
            canvas.drawBitmap(mFrames[frameIndex], null, mDestination, mPaint);
        }
        return !mEffects.isEmpty();
    }

    public void clear() {
        mEffects.clear();
    }

    private static final class Effect {
        private final float centerX;
        private final float centerY;
        private final float width;
        private final float height;
        private final long startTime;

        private Effect(final float centerX, final float centerY, final float width,
                final float height, final long startTime) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.width = width;
            this.height = height;
            this.startTime = startTime;
        }
    }
}
