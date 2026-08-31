/*
 * SPDX-License-Identifier: GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import java.util.Objects;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.theme.KeyRendererConfig;
import helium314.keyboard.theme.ResolvedVisualTheme;
import helium314.keyboard.theme.ThemeAsset;
import helium314.keyboard.theme.VisualThemeValidator;

/** Draws keys from a validated visual theme without tinting or distorting its ornamentation. */
public final class VisualThemeKeyRenderer {

    @NonNull
    private final Bitmap mNormalKey;
    @NonNull
    private final Bitmap mPressedKey;
    @NonNull
    private final Bitmap mNormalSpace;
    @NonNull
    private final Bitmap mPressedSpace;
    @NonNull
    private final Bitmap mNormalShift;
    @NonNull
    private final Bitmap mPressedShift;
    @NonNull
    private final Bitmap mNormalDelete;
    @NonNull
    private final Bitmap mPressedDelete;
    @NonNull
    private final Bitmap mNormalEnter;
    @NonNull
    private final Bitmap mPressedEnter;
    @NonNull
    private final Bitmap mNormalRoundFunction;
    @NonNull
    private final Bitmap mPressedRoundFunction;
    @NonNull
    private final Bitmap mNormalDiamondFunction;
    @NonNull
    private final Bitmap mPressedDiamondFunction;
    @NonNull
    private final Drawable mBackspaceIcon;
    @NonNull
    private final Drawable mReturnArrowIcon;
    @NonNull
    private final Drawable mSpaceGlyph;
    @NonNull
    private final Drawable mSpaceGlobeIcon;
    @NonNull
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    @NonNull
    private final Paint mIconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull
    private final Rect mSource = new Rect();
    @NonNull
    private final RectF mDestination = new RectF();
    @NonNull
    private final KeyRendererConfig mConfig;
    private final int mGradientStart;
    private final int mGradientEnd;
    private final boolean mHasSpaceFrames;
    private final boolean mHasShiftFrames;
    private final boolean mHasDeleteFrames;
    private final boolean mHasActionFrames;
    private final boolean mHasRoundFunctionFrames;
    private final boolean mHasDiamondFunctionFrames;

    public VisualThemeKeyRenderer(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme) {
        this(context, theme, false);
    }

    public VisualThemeKeyRenderer(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme,
            final boolean opaqueRegularKeys) {
        if (!theme.getHasCustomKeys()) {
            throw new IllegalArgumentException("Theme does not provide custom keys");
        }
        mConfig = theme.getManifest().getKeyRenderer();
        mHasSpaceFrames = hasPair(theme, ThemeAsset.SPACE_NORMAL, ThemeAsset.SPACE_PRESSED);
        mHasShiftFrames = hasPair(theme, ThemeAsset.SHIFT_NORMAL, ThemeAsset.SHIFT_PRESSED);
        mHasDeleteFrames = hasPair(theme, ThemeAsset.DELETE_NORMAL, ThemeAsset.DELETE_PRESSED);
        mHasActionFrames = hasPair(theme, ThemeAsset.ACTION_NORMAL, ThemeAsset.ACTION_PRESSED);
        mHasRoundFunctionFrames = hasPair(
                theme, ThemeAsset.ROUND_FUNCTION_NORMAL, ThemeAsset.ROUND_FUNCTION_PRESSED);
        mHasDiamondFunctionFrames = hasPair(
                theme, ThemeAsset.DIAMOND_FUNCTION_NORMAL, ThemeAsset.DIAMOND_FUNCTION_PRESSED);
        final Bitmap normalKey = Objects.requireNonNull(
                theme.bitmap(context, ThemeAsset.KEY_NORMAL));
        final Bitmap pressedKey = Objects.requireNonNull(
                theme.bitmap(context, ThemeAsset.KEY_PRESSED));
        mNormalKey = opaqueRegularKeys ? makeSolidPixelsOpaque(normalKey) : normalKey;
        mPressedKey = opaqueRegularKeys ? makeSolidPixelsOpaque(pressedKey) : pressedKey;
        mNormalSpace = decodeBitmap(context, theme, ThemeAsset.SPACE_NORMAL, ThemeAsset.KEY_NORMAL);
        mPressedSpace = decodeBitmap(context, theme, ThemeAsset.SPACE_PRESSED, ThemeAsset.KEY_PRESSED);
        mNormalShift = decodeBitmap(context, theme, ThemeAsset.SHIFT_NORMAL, ThemeAsset.KEY_NORMAL);
        mPressedShift = decodeBitmap(context, theme, ThemeAsset.SHIFT_PRESSED, ThemeAsset.KEY_PRESSED);
        mNormalDelete = decodeBitmap(context, theme, ThemeAsset.DELETE_NORMAL, ThemeAsset.KEY_NORMAL);
        mPressedDelete = decodeBitmap(context, theme, ThemeAsset.DELETE_PRESSED, ThemeAsset.KEY_PRESSED);
        mNormalEnter = decodeBitmap(context, theme, ThemeAsset.ACTION_NORMAL, ThemeAsset.KEY_NORMAL);
        mPressedEnter = decodeBitmap(context, theme, ThemeAsset.ACTION_PRESSED, ThemeAsset.KEY_PRESSED);
        mNormalRoundFunction = Objects.requireNonNull(theme.bitmap(
                context, ThemeAsset.ROUND_FUNCTION_NORMAL, ThemeAsset.KEY_NORMAL));
        mPressedRoundFunction = Objects.requireNonNull(theme.bitmap(
                context, ThemeAsset.ROUND_FUNCTION_PRESSED, ThemeAsset.KEY_PRESSED));
        mNormalDiamondFunction = Objects.requireNonNull(theme.bitmap(
                context, ThemeAsset.DIAMOND_FUNCTION_NORMAL, ThemeAsset.KEY_NORMAL));
        mPressedDiamondFunction = Objects.requireNonNull(theme.bitmap(
                context, ThemeAsset.DIAMOND_FUNCTION_PRESSED, ThemeAsset.KEY_PRESSED));
        mBackspaceIcon = requireDrawable(context, theme, ThemeAsset.ICON_BACKSPACE);
        mReturnArrowIcon = requireDrawable(context, theme, ThemeAsset.ICON_ACTION);
        mSpaceGlyph = requireDrawable(context, theme, ThemeAsset.ICON_SPACE_GLYPH);
        mSpaceGlobeIcon = requireDrawable(context, theme, ThemeAsset.ICON_SPACE_LANGUAGE);
        mGradientStart = parseColor(mConfig.getIconGradientStart(), Color.WHITE);
        mGradientEnd = parseColor(mConfig.getIconGradientEnd(), Color.WHITE);
    }

    @NonNull
    private static Bitmap decodeBitmap(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme, @NonNull final String asset,
            @NonNull final String fallbackAsset) {
        return Objects.requireNonNull(theme.bitmap(context, asset, fallbackAsset));
    }

    @NonNull
    private static Drawable requireDrawable(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme, @NonNull final String asset) {
        final Drawable drawable = theme.drawable(context, asset);
        if (drawable == null) throw new IllegalArgumentException("Theme is missing " + asset);
        return drawable;
    }

    private static int parseColor(final String value, final int fallback) {
        return value == null ? fallback : VisualThemeValidator.INSTANCE.parseColor(value);
    }

    private static boolean hasPair(@NonNull final ResolvedVisualTheme theme,
            @NonNull final String normal, @NonNull final String pressed) {
        return theme.hasAsset(normal) && theme.hasAsset(pressed);
    }

    /** Makes the key face fully opaque without flattening its translucent outer glow. */
    @NonNull
    private static Bitmap makeSolidPixelsOpaque(@NonNull final Bitmap source) {
        final Bitmap result = source.copy(Bitmap.Config.ARGB_8888, true);
        final int width = result.getWidth();
        final int height = result.getHeight();
        final int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);
        for (int index = 0; index < pixels.length; index++) {
            if ((pixels[index] >>> 24) >= 0x80) {
                pixels[index] |= 0xFF000000;
            }
        }
        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return result;
    }

    public void draw(@NonNull final Key key, @NonNull final Canvas canvas) {
        final int code = key.getCode();
        final boolean pressed = key.isPressedOrLocked();
        final boolean isSpace = code == Constants.CODE_SPACE;
        final float horizontalOverscan = isSpace
                ? 0f : key.getDrawWidth() * mConfig.getHorizontalOverscan();
        final float verticalOverscan = key.getHeight() * mConfig.getVerticalOverscan();
        final int width = Math.round(key.getDrawWidth() + horizontalOverscan * 2f);
        final int height = Math.round(key.getHeight() + verticalOverscan * 2f);

        canvas.save();
        canvas.translate(-horizontalOverscan, -verticalOverscan);

        if (isSpace && mHasSpaceFrames) {
            drawHorizontallyStretchable(pressed ? mPressedSpace : mNormalSpace,
                    canvas, width, height, mConfig.getSpaceLeftCapPx(),
                    mConfig.getSpaceRightCapPx());
        } else if (key.isShift() && mHasShiftFrames) {
            drawAspectFit(pressed ? mPressedShift : mNormalShift, canvas, width, height);
        } else if (code == KeyCode.DELETE && mHasDeleteFrames) {
            drawAspectFit(pressed ? mPressedDelete : mNormalDelete, canvas, width, height);
        } else if (key.hasActionKeyBackground() && mHasActionFrames) {
            drawAspectFit(pressed ? mPressedEnter : mNormalEnter, canvas, width, height);
        } else if (isRoundFunction(code) && mHasRoundFunctionFrames) {
            drawAspectFit(pressed ? mPressedRoundFunction : mNormalRoundFunction,
                    canvas, width, height);
        } else if (isDiamondFunction(code) && mHasDiamondFunctionFrames) {
            drawAspectFit(pressed ? mPressedDiamondFunction : mNormalDiamondFunction,
                    canvas, width, height);
        } else {
            drawHorizontallyStretchable(pressed ? mPressedKey : mNormalKey,
                    canvas, width, height, mConfig.getRegularLeftCapPx(),
                    mConfig.getRegularRightCapPx());
        }
        canvas.restore();
    }

    /** Draws our custom visual, or reports that the source art already contains one. */
    public boolean drawTopVisual(@NonNull final Key key, @NonNull final Canvas canvas) {
        if (key.getCode() == Constants.CODE_SPACE) {
            drawSpaceTopVisual(key, canvas);
            return true;
        }
        if (key.getCode() == KeyCode.DELETE) {
            drawSquareGradientIcon(key, canvas, mBackspaceIcon,
                    mConfig.getBackspaceIconSize());
            return true;
        }
        if (key.hasActionKeyBackground()) {
            final int width = Math.round(Math.min(key.getDrawWidth(), key.getHeight())
                    * mConfig.getActionIconSize());
            final int height = Math.round(width / mConfig.getActionIconAspectRatio());
            final int left = Math.round((key.getDrawWidth() - width) * 0.5f
                    + width * mConfig.getActionIconVisibleOffsetX());
            final int top = Math.round((key.getHeight() - height) * 0.5f
                    + height * mConfig.getActionIconVisibleOffsetY());
            drawOriginalIcon(canvas, mReturnArrowIcon, left, top, width, height);
            return true;
        }
        return key.isShift() && mHasShiftFrames;
    }

    private void drawSpaceTopVisual(@NonNull final Key key, @NonNull final Canvas canvas) {
        final int keyWidth = key.getDrawWidth();
        final int keyHeight = key.getHeight();

        final int globeSize = Math.round(keyHeight * mConfig.getSpaceLanguageIconSize());
        final int globeLeft = (keyWidth - globeSize) / 2;
        final int globeTop = Math.round(keyHeight * mConfig.getSpaceLanguageIconTop());
        drawGradientIcon(canvas, mSpaceGlobeIcon,
                globeLeft, globeTop, globeSize, globeSize);

        final int glyphWidth = Math.round(keyWidth * mConfig.getSpaceGlyphWidth());
        final int glyphHeight = Math.round(
                glyphWidth * mSpaceGlyph.getIntrinsicHeight()
                        / (float) mSpaceGlyph.getIntrinsicWidth());
        final int glyphLeft = (keyWidth - glyphWidth) / 2;
        final int glyphTop = Math.round(keyHeight * mConfig.getSpaceGlyphTop());
        drawOriginalIcon(canvas, mSpaceGlyph,
                glyphLeft, glyphTop, glyphWidth, glyphHeight);
    }

    private void drawSquareGradientIcon(@NonNull final Key key, @NonNull final Canvas canvas,
            @NonNull final Drawable icon, final float sizeRatio) {
        final int size = Math.round(Math.min(key.getDrawWidth(), key.getHeight()) * sizeRatio);
        final int left = (key.getDrawWidth() - size) / 2;
        final int top = (key.getHeight() - size) / 2;
        drawGradientIcon(canvas, icon, left, top, size, size);
    }

    /** Applies the official ivory-to-coral foreground treatment to any keyboard icon. */
    public void drawGradientIcon(@NonNull final Canvas canvas, @NonNull final Drawable icon,
            final int left, final int top, final int width, final int height) {
        final int right = left + width;
        final int bottom = top + height;
        final int layer = canvas.saveLayer(left, top, right, bottom, null);

        icon.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        icon.setBounds(left, top, right, bottom);
        icon.draw(canvas);

        mIconPaint.setShader(new LinearGradient(left, top, right, top,
                mGradientStart, mGradientEnd, Shader.TileMode.CLAMP));
        mIconPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawRect(left, top, right, bottom, mIconPaint);
        mIconPaint.setXfermode(null);
        mIconPaint.setShader(null);
        canvas.restoreToCount(layer);
    }

    private static void drawOriginalIcon(@NonNull final Canvas canvas,
            @NonNull final Drawable icon, final int left, final int top,
            final int width, final int height) {
        icon.clearColorFilter();
        icon.setBounds(left, top, left + width, top + height);
        icon.draw(canvas);
    }

    private static boolean isRoundFunction(final int code) {
        return code == KeyCode.SYMBOL_ALPHA || code == KeyCode.SYMBOL
                || code == KeyCode.ALPHA || code == KeyCode.NUMPAD;
    }

    private static boolean isDiamondFunction(final int code) {
        return code == KeyCode.LANGUAGE_SWITCH || code == KeyCode.EMOJI
                || code < Constants.CODE_SPACE;
    }

    private void drawAspectFit(@NonNull final Bitmap bitmap, @NonNull final Canvas canvas,
            final int width, final int height) {
        final float scale = Math.min(
                width / (float) bitmap.getWidth(),
                height / (float) bitmap.getHeight());
        final float drawWidth = bitmap.getWidth() * scale;
        final float drawHeight = bitmap.getHeight() * scale;
        final float left = (width - drawWidth) * 0.5f;
        final float top = (height - drawHeight) * 0.5f;
        mDestination.set(left, top, left + drawWidth, top + drawHeight);
        canvas.drawBitmap(bitmap, null, mDestination, mPaint);
    }

    /**
     * Scales the frame vertically but stretches only its undecorated horizontal center. This is
     * effectively a three-patch and keeps the spacebar corners and gold trim in proportion.
     */
    private void drawHorizontallyStretchable(@NonNull final Bitmap bitmap,
            @NonNull final Canvas canvas, final int width, final int height,
            final int sourceLeftCap, final int sourceRightCap) {
        final int sourceWidth = bitmap.getWidth();
        final int sourceHeight = bitmap.getHeight();
        final float verticalScale = height / (float) sourceHeight;
        float destinationLeftCap = sourceLeftCap * verticalScale;
        float destinationRightCap = sourceRightCap * verticalScale;
        final float capsWidth = destinationLeftCap + destinationRightCap;
        if (capsWidth > width) {
            final float capScale = width / capsWidth;
            destinationLeftCap *= capScale;
            destinationRightCap *= capScale;
        }

        drawSlice(bitmap, canvas, 0, sourceLeftCap,
                0f, destinationLeftCap, sourceHeight, height);
        drawSlice(bitmap, canvas, sourceLeftCap, sourceWidth - sourceRightCap,
                destinationLeftCap, width - destinationRightCap, sourceHeight, height);
        drawSlice(bitmap, canvas, sourceWidth - sourceRightCap, sourceWidth,
                width - destinationRightCap, width, sourceHeight, height);
    }

    private void drawSlice(@NonNull final Bitmap bitmap, @NonNull final Canvas canvas,
            final int sourceLeft, final int sourceRight,
            final float destinationLeft, final float destinationRight,
            final int sourceHeight, final int destinationHeight) {
        mSource.set(sourceLeft, 0, sourceRight, sourceHeight);
        mDestination.set(destinationLeft, 0, destinationRight, destinationHeight);
        canvas.drawBitmap(bitmap, mSource, mDestination, mPaint);
    }
}
