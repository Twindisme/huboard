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
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.theme.KeyRendererConfig;
import helium314.keyboard.theme.ResolvedVisualTheme;
import helium314.keyboard.theme.ThemeAsset;
import helium314.keyboard.theme.ThemeAssetRenderSpec;
import helium314.keyboard.theme.ThemeAssetRendering;
import helium314.keyboard.theme.ThemeKeyClass;
import helium314.keyboard.theme.ThemeKeyContentSpec;
import helium314.keyboard.theme.ThemeKeyIconMode;
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
    @NonNull
    private final Map<String, ThemeAssetRenderSpec> mRenderSpecs;
    @NonNull
    private final Map<String, Drawable> mRenderedBackgrounds = new HashMap<>();
    @NonNull
    private final Map<String, ThemeKeyContentSpec> mContentSpecs;
    @NonNull
    private final IdentityHashMap<Bitmap, Float> mHorizontalOpticalOffsets =
            new IdentityHashMap<>();
    @Nullable
    private final Drawable mShiftOffIcon;
    @Nullable
    private final Drawable mShiftOnIcon;
    @Nullable
    private final Drawable mShiftLockedIcon;
    @NonNull
    private final ThemeKeyIconMode mDefaultShiftIconMode;
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
        mRenderSpecs = theme.getManifest().getRendering();
        mContentSpecs = mConfig.getContent();
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
        mShiftOffIcon = optionalDrawable(context, theme, ThemeAsset.ICON_SHIFT_OFF);
        mShiftOnIcon = optionalDrawable(context, theme, ThemeAsset.ICON_SHIFT_ON);
        mShiftLockedIcon = optionalDrawable(context, theme, ThemeAsset.ICON_SHIFT_LOCKED);
        mDefaultShiftIconMode = ThemeKeyIconMode.OVERLAY;
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

    @Nullable
    private static Drawable optionalDrawable(@NonNull final Context context,
            @NonNull final ResolvedVisualTheme theme, @NonNull final String asset) {
        return theme.hasAsset(asset) ? theme.drawable(context, asset) : null;
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
            final String asset = pressed ? ThemeAsset.SPACE_PRESSED : ThemeAsset.SPACE_NORMAL;
            final Bitmap bitmap = pressed ? mPressedSpace : mNormalSpace;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawHorizontallyStretchable(bitmap, canvas, width, height,
                        mConfig.getSpaceLeftCapPx(), mConfig.getSpaceRightCapPx(),
                        mConfig.getCenterSpecialKeyArtworkHorizontally());
            }
        } else if (key.isShift() && mHasShiftFrames) {
            final String asset = pressed ? ThemeAsset.SHIFT_PRESSED : ThemeAsset.SHIFT_NORMAL;
            final Bitmap bitmap = pressed ? mPressedShift : mNormalShift;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawAspectFit(bitmap, canvas, width, height);
            }
        } else if (code == KeyCode.DELETE && mHasDeleteFrames) {
            final String asset = pressed ? ThemeAsset.DELETE_PRESSED : ThemeAsset.DELETE_NORMAL;
            final Bitmap bitmap = pressed ? mPressedDelete : mNormalDelete;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawAspectFit(bitmap, canvas, width, height);
            }
        } else if (key.hasActionKeyBackground() && mHasActionFrames) {
            final String asset = pressed ? ThemeAsset.ACTION_PRESSED : ThemeAsset.ACTION_NORMAL;
            final Bitmap bitmap = pressed ? mPressedEnter : mNormalEnter;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawAspectFit(bitmap, canvas, width, height);
            }
        } else if (isRoundFunction(code) && mHasRoundFunctionFrames) {
            final String asset = pressed ? ThemeAsset.ROUND_FUNCTION_PRESSED
                    : ThemeAsset.ROUND_FUNCTION_NORMAL;
            final Bitmap bitmap = pressed ? mPressedRoundFunction : mNormalRoundFunction;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawAspectFit(bitmap, canvas, width, height);
            }
        } else if (isDiamondFunction(code) && mHasDiamondFunctionFrames) {
            final String asset = pressed ? ThemeAsset.DIAMOND_FUNCTION_PRESSED
                    : ThemeAsset.DIAMOND_FUNCTION_NORMAL;
            final Bitmap bitmap = pressed ? mPressedDiamondFunction : mNormalDiamondFunction;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawAspectFit(bitmap, canvas, width, height);
            }
        } else {
            final String asset = pressed ? ThemeAsset.KEY_PRESSED : ThemeAsset.KEY_NORMAL;
            final Bitmap bitmap = pressed ? mPressedKey : mNormalKey;
            if (!drawWithRenderSpec(asset, bitmap, canvas, width, height)) {
                drawHorizontallyStretchable(bitmap, canvas, width, height,
                        mConfig.getRegularLeftCapPx(), mConfig.getRegularRightCapPx());
            }
        }
        canvas.restore();
    }

    private boolean drawWithRenderSpec(@NonNull final String asset,
            @NonNull final Bitmap bitmap, @NonNull final Canvas canvas,
            final int width, final int height) {
        final ThemeAssetRenderSpec spec = mRenderSpecs.get(asset);
        if (spec == null) return false;
        Drawable drawable = mRenderedBackgrounds.get(asset);
        if (drawable == null) {
            drawable = ThemeAssetRendering.bitmapDrawable(bitmap, spec);
            mRenderedBackgrounds.put(asset, drawable);
        }
        drawable.setBounds(0, 0, width, height);
        drawable.draw(canvas);
        return true;
    }

    /** Draws our custom visual, or reports that the source art already contains one. */
    public boolean drawTopVisual(@NonNull final Key key, @NonNull final Canvas canvas) {
        final ThemeKeyIconMode iconMode = iconMode(key);
        if (key.getCode() == Constants.CODE_SPACE) {
            if (iconMode == ThemeKeyIconMode.OVERLAY) drawSpaceTopVisual(key, canvas);
            return true;
        }
        if (key.getCode() == KeyCode.DELETE) {
            if (iconMode == ThemeKeyIconMode.OVERLAY) {
                drawConfiguredIcon(key, canvas, mBackspaceIcon,
                        mConfig.getBackspaceIconSize(), mConfig.getBackspaceIconSize(),
                        true, 0f, 0f);
            }
            return true;
        }
        if (key.hasActionKeyBackground()) {
            if (iconMode == ThemeKeyIconMode.OVERLAY) {
                drawConfiguredIcon(key, canvas, mReturnArrowIcon,
                        mConfig.getActionIconSize(),
                        mConfig.getActionIconSize() / mConfig.getActionIconAspectRatio(),
                        false, mConfig.getActionIconVisibleOffsetX(),
                        mConfig.getActionIconVisibleOffsetY());
            }
            return true;
        }
        if (key.isShift()) return drawShiftTopVisual(key, canvas, iconMode);
        return iconMode != ThemeKeyIconMode.OVERLAY
                && key.getLabel() == null && key.getIconName() != null;
    }

    private boolean drawShiftTopVisual(@NonNull final Key key, @NonNull final Canvas canvas,
            @NonNull final ThemeKeyIconMode iconMode) {
        if (iconMode != ThemeKeyIconMode.OVERLAY) return true;
        final Drawable icon;
        final String iconName = key.getIconName();
        if (KeyboardIconsSet.NAME_SHIFT_KEY_LOCKED.equals(iconName)) {
            icon = mShiftLockedIcon != null ? mShiftLockedIcon : mShiftOffIcon;
        } else if (KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED.equals(iconName)) {
            icon = mShiftOnIcon != null ? mShiftOnIcon : mShiftOffIcon;
        } else {
            icon = mShiftOffIcon;
        }
        if (icon == null) return false;
        drawConfiguredIcon(key, canvas, icon, 0.52f, 0.52f, true, 0f, 0f);
        return true;
    }

    public void translateTopContent(@NonNull final Key key, @NonNull final Canvas canvas) {
        final ThemeKeyContentSpec spec = contentSpec(key);
        if (spec == null) return;
        canvas.translate(
                (spec.getCenterX() - 0.5f) * key.getDrawWidth(),
                (spec.getCenterY() - 0.5f) * key.getHeight());
    }

    @NonNull
    private ThemeKeyIconMode iconMode(@NonNull final Key key) {
        final ThemeKeyContentSpec spec = contentSpec(key);
        if (spec != null && spec.getIconMode() != null) return spec.getIconMode();
        return key.isShift() ? mDefaultShiftIconMode : ThemeKeyIconMode.OVERLAY;
    }

    @Nullable
    private ThemeKeyContentSpec contentSpec(@NonNull final Key key) {
        return mContentSpecs.get(keyClass(key));
    }

    @NonNull
    private static String keyClass(@NonNull final Key key) {
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE) return ThemeKeyClass.SPACE;
        if (key.isShift()) return ThemeKeyClass.SHIFT;
        if (code == KeyCode.DELETE) return ThemeKeyClass.DELETE;
        if (key.hasActionKeyBackground()) return ThemeKeyClass.ACTION;
        if (isRoundFunction(code)) return ThemeKeyClass.ROUND_FUNCTION;
        if (isDiamondFunction(code)) return ThemeKeyClass.DIAMOND_FUNCTION;
        return ThemeKeyClass.REGULAR;
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

    private void drawConfiguredIcon(@NonNull final Key key, @NonNull final Canvas canvas,
            @NonNull final Drawable icon, final float defaultWidthRatio,
            final float defaultHeightRatio, final boolean gradient,
            final float visibleOffsetX, final float visibleOffsetY) {
        final ThemeKeyContentSpec spec = contentSpec(key);
        final int baseSize = Math.min(key.getDrawWidth(), key.getHeight());
        final Float configuredWidth = spec == null ? null : spec.getIconWidth();
        final Float configuredHeight = spec == null ? null : spec.getIconHeight();
        final float defaultAspectRatio = defaultHeightRatio > 0f
                ? defaultWidthRatio / defaultHeightRatio : 1f;
        final float widthRatio;
        final float heightRatio;
        if (configuredWidth != null && configuredHeight != null) {
            widthRatio = configuredWidth;
            heightRatio = configuredHeight;
        } else if (configuredWidth != null) {
            widthRatio = configuredWidth;
            heightRatio = configuredWidth / defaultAspectRatio;
        } else if (configuredHeight != null) {
            heightRatio = configuredHeight;
            widthRatio = configuredHeight * defaultAspectRatio;
        } else {
            widthRatio = defaultWidthRatio;
            heightRatio = defaultHeightRatio;
        }
        final int width = Math.round(baseSize * widthRatio);
        final int height = Math.round(baseSize * heightRatio);
        final int left = Math.round((key.getDrawWidth() - width) * 0.5f
                + width * visibleOffsetX);
        final int top = Math.round((key.getHeight() - height) * 0.5f
                + height * visibleOffsetY);
        if (gradient) {
            drawGradientIcon(canvas, icon, left, top, width, height);
        } else {
            drawOriginalIcon(canvas, icon, left, top, width, height);
        }
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

    /** Applies optional per-key geometry before drawing a native huBoard icon. */
    public void drawGradientIcon(@NonNull final Canvas canvas, @NonNull final Drawable icon,
            @Nullable final Key key, final int defaultLeft, final int defaultTop,
            final int defaultWidth, final int defaultHeight) {
        if (key == null) {
            drawGradientIcon(canvas, icon, defaultLeft, defaultTop, defaultWidth, defaultHeight);
            return;
        }
        final ThemeKeyContentSpec spec = contentSpec(key);
        final Float configuredWidth = spec == null ? null : spec.getIconWidth();
        final Float configuredHeight = spec == null ? null : spec.getIconHeight();
        if (configuredWidth == null && configuredHeight == null) {
            drawGradientIcon(canvas, icon, defaultLeft, defaultTop, defaultWidth, defaultHeight);
            return;
        }
        final int baseSize = Math.min(key.getDrawWidth(), key.getHeight());
        final float aspectRatio = defaultHeight == 0 ? 1f : defaultWidth / (float) defaultHeight;
        final int width;
        final int height;
        if (configuredWidth != null && configuredHeight != null) {
            width = Math.round(baseSize * configuredWidth);
            height = Math.round(baseSize * configuredHeight);
        } else if (configuredWidth != null) {
            width = Math.round(baseSize * configuredWidth);
            height = Math.round(width / aspectRatio);
        } else {
            height = Math.round(baseSize * Objects.requireNonNull(configuredHeight));
            width = Math.round(height * aspectRatio);
        }
        drawGradientIcon(canvas, icon,
                (key.getDrawWidth() - width) / 2,
                (key.getHeight() - height) / 2,
                width, height);
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
        final float opticalOffset = mConfig.getCenterSpecialKeyArtworkHorizontally()
                ? getHorizontalOpticalOffset(bitmap) * scale : 0f;
        final float left = (width - drawWidth) * 0.5f + opticalOffset;
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
        drawHorizontallyStretchable(
                bitmap, canvas, width, height, sourceLeftCap, sourceRightCap, false);
    }

    private void drawHorizontallyStretchable(@NonNull final Bitmap bitmap,
            @NonNull final Canvas canvas, final int width, final int height,
            final int sourceLeftCap, final int sourceRightCap,
            final boolean opticallyCenter) {
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

        final float opticalOffset = opticallyCenter
                ? getHorizontalOpticalOffset(bitmap) * verticalScale : 0f;
        canvas.save();
        canvas.translate(opticalOffset, 0f);
        drawSlice(bitmap, canvas, 0, sourceLeftCap,
                0f, destinationLeftCap, sourceHeight, height);
        drawSlice(bitmap, canvas, sourceLeftCap, sourceWidth - sourceRightCap,
                destinationLeftCap, width - destinationRightCap, sourceHeight, height);
        drawSlice(bitmap, canvas, sourceWidth - sourceRightCap, sourceWidth,
                width - destinationRightCap, width, sourceHeight, height);
        canvas.restore();
    }

    /** Returns the source-pixel shift that balances transparent padding on the left and right. */
    private float getHorizontalOpticalOffset(@NonNull final Bitmap bitmap) {
        final Float cached = mHorizontalOpticalOffsets.get(bitmap);
        if (cached != null) return cached;

        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();
        final int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        int visibleLeft = width;
        int visibleRight = -1;
        for (int y = 0; y < height; y++) {
            final int row = y * width;
            for (int x = 0; x < width; x++) {
                if ((pixels[row + x] >>> 24) < 32) continue;
                visibleLeft = Math.min(visibleLeft, x);
                visibleRight = Math.max(visibleRight, x);
            }
        }
        final float offset = visibleRight < visibleLeft ? 0f
                : width * 0.5f - (visibleLeft + visibleRight + 1) * 0.5f;
        mHorizontalOpticalOffsets.put(bitmap, offset);
        return offset;
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
