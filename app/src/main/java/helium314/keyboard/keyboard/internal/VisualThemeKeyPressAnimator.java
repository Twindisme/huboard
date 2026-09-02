/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieComposition;
import com.airbnb.lottie.LottieDrawable;

import java.util.ArrayList;
import java.util.Iterator;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.theme.KeyPressAnimationConfig;
import helium314.keyboard.theme.KeyPressSpriteAtlasConfig;
import helium314.keyboard.theme.ResolvedVisualTheme;
import helium314.keyboard.theme.VisualThemeMotionAnimator;
import helium314.keyboard.theme.VisualThemeSpriteAtlas;

/** Draws a theme's authored or scripted animation over pressed keys. */
public final class VisualThemeKeyPressAnimator {
    private static final String TAG = VisualThemeKeyPressAnimator.class.getSimpleName();

    @NonNull
    private final Bitmap[] mFrames;
    @Nullable
    private final Bitmap mSpriteAtlas;
    @NonNull
    private final Rect[] mSpriteFrames;
    @Nullable
    private final LottieDrawable mLottieDrawable;
    @Nullable
    private final VisualThemeMotionAnimator mMotionAnimator;
    @NonNull
    private final ArrayList<Effect> mEffects = new ArrayList<>();
    @NonNull
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    @NonNull
    private final RectF mDestination = new RectF();
    @NonNull
    private final KeyPressAnimationConfig mConfig;
    private final long mDurationMs;
    private final float mAspectRatio;
    private final boolean mDisabled;

    public VisualThemeKeyPressAnimator(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme) {
        if (!theme.getHasKeyPressAnimation()) {
            throw new IllegalArgumentException("Theme does not provide a key press animation");
        }
        mConfig = theme.getManifest().getKeyPressAnimation();
        if (mConfig == null) {
            throw new IllegalArgumentException("Theme has no key press animation config");
        }
        final String lottieAsset = mConfig.getLottieAsset();
        if (mConfig.getScript() != null) {
            VisualThemeMotionAnimator motionAnimator = null;
            boolean disabled = false;
            try {
                motionAnimator = new VisualThemeMotionAnimator(
                        context, theme, mConfig, mConfig.getScript());
            } catch (RuntimeException | LinkageError error) {
                Log.e(TAG, "Could not initialize huBoard Motion script", error);
                disabled = true;
            }
            mMotionAnimator = motionAnimator;
            mDisabled = disabled;
            mFrames = new Bitmap[0];
            mSpriteAtlas = null;
            mSpriteFrames = new Rect[0];
            mLottieDrawable = null;
            mDurationMs = mConfig.getDurationMs();
            mAspectRatio = 1.0f;
        } else if (lottieAsset != null) {
            mMotionAnimator = null;
            mDisabled = false;
            final LottieComposition composition = theme.lottieComposition(context, lottieAsset);
            if (composition == null) {
                throw new IllegalArgumentException("Theme Lottie animation could not be decoded");
            }
            mFrames = new Bitmap[0];
            mSpriteAtlas = null;
            mSpriteFrames = new Rect[0];
            mLottieDrawable = new LottieDrawable();
            mLottieDrawable.setComposition(composition);
            mDurationMs = mConfig.getDurationMs() > 0L
                    ? mConfig.getDurationMs() : Math.max(1L, Math.round(composition.getDuration()));
            mAspectRatio = composition.getBounds().width()
                    / (float) composition.getBounds().height();
        } else if (mConfig.getSpriteAtlas() != null) {
            mMotionAnimator = null;
            mDisabled = false;
            final KeyPressSpriteAtlasConfig atlasConfig = mConfig.getSpriteAtlas();
            mSpriteAtlas = theme.bitmap(context, atlasConfig.getAsset());
            if (mSpriteAtlas == null) {
                throw new IllegalArgumentException("Theme sprite atlas could not be decoded");
            }
            mFrames = new Bitmap[0];
            mSpriteFrames = VisualThemeSpriteAtlas.frameRects(
                    mSpriteAtlas.getWidth(), mSpriteAtlas.getHeight(), atlasConfig);
            mLottieDrawable = null;
            mDurationMs = mConfig.getDurationMs() > 0L
                    ? mConfig.getDurationMs()
                    : mConfig.getFrameDurationMs() * mSpriteFrames.length;
            mAspectRatio = mSpriteFrames[0].width() / (float) mSpriteFrames[0].height();
        } else {
            mMotionAnimator = null;
            mDisabled = false;
            mFrames = new Bitmap[mConfig.getFrames().size()];
            for (int index = 0; index < mFrames.length; index++) {
                mFrames[index] = theme.bitmap(context, mConfig.getFrames().get(index));
                if (mFrames[index] == null) {
                    throw new IllegalArgumentException("Theme animation frame could not be decoded");
                }
            }
            mSpriteAtlas = null;
            mSpriteFrames = new Rect[0];
            mLottieDrawable = null;
            mDurationMs = mConfig.getDurationMs() > 0L
                    ? mConfig.getDurationMs() : mConfig.getFrameDurationMs() * mFrames.length;
            final Bitmap firstFrame = mFrames[0];
            mAspectRatio = firstFrame.getWidth() / (float) firstFrame.getHeight();
        }
    }

    public void start(@NonNull final Key key, final int paddingLeft, final int paddingTop,
            final int viewportWidth, final int viewportHeight) {
        if (mDisabled || mConfig.getCharacterKeysOnly() && !isVisibleCharacterKey(key)) {
            return;
        }
        if (mMotionAnimator != null) {
            mMotionAnimator.start(
                    key, paddingLeft, paddingTop, viewportWidth, viewportHeight);
            return;
        }
        if (mEffects.size() == mConfig.getMaxSimultaneousEffects()) {
            mEffects.remove(0);
        }
        final float centerX = paddingLeft + key.getDrawX() + key.getDrawWidth() * 0.5f;
        final float centerY = paddingTop + key.getY() + key.getHeight() * 0.5f;
        final float height = key.getHeight() * mConfig.getHeightToKeyHeight();
        final float width = height * mAspectRatio;
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
        if (mDisabled) {
            return false;
        }
        if (mMotionAnimator != null) {
            return mMotionAnimator.draw(canvas, now);
        }
        final Iterator<Effect> iterator = mEffects.iterator();
        while (iterator.hasNext()) {
            final Effect effect = iterator.next();
            final long elapsed = now - effect.startTime;
            if (elapsed >= mDurationMs) {
                iterator.remove();
                continue;
            }
            if (elapsed < 0L) {
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
            final float progress = elapsed / (float) mDurationMs;
            if (mLottieDrawable != null) {
                mLottieDrawable.setProgress(progress);
                mLottieDrawable.setBounds(
                        Math.round(mDestination.left),
                        Math.round(mDestination.top),
                        Math.round(mDestination.right),
                        Math.round(mDestination.bottom)
                );
                mLottieDrawable.draw(canvas);
            } else if (mSpriteAtlas != null) {
                final int frameIndex = Math.min(
                        mSpriteFrames.length - 1,
                        (int) (progress * mSpriteFrames.length)
                );
                canvas.drawBitmap(
                        mSpriteAtlas,
                        mSpriteFrames[frameIndex],
                        mDestination,
                        mPaint
                );
            } else {
                final int frameIndex = Math.min(
                        mFrames.length - 1,
                        (int) (progress * mFrames.length)
                );
                canvas.drawBitmap(mFrames[frameIndex], null, mDestination, mPaint);
            }
        }
        return !mEffects.isEmpty();
    }

    public void clear() {
        mEffects.clear();
        if (mMotionAnimator != null) {
            mMotionAnimator.clear();
        }
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
