/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import helium314.keyboard.keyboard.Key;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.common.CoordinateUtils;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.ViewLayoutUtils;
import helium314.keyboard.theme.KeyPreviewConfig;
import helium314.keyboard.theme.ResolvedVisualTheme;
import helium314.keyboard.theme.ThemeAsset;
import helium314.keyboard.theme.VisualThemeValidator;

import java.util.ArrayDeque;
import java.util.HashMap;

/**
 * This class controls pop up key previews. This class decides:
 * - what kind of key previews should be shown.
 * - where key previews should be placed.
 * - how key previews should be shown and dismissed.
 */
public final class KeyPreviewChoreographer {
    // Free {@link KeyPreviewView} pool that can be used for key preview.
    private final ArrayDeque<KeyPreviewView> mFreeKeyPreviewViews = new ArrayDeque<>();
    // Map from {@link Key} to {@link KeyPreviewView} that is currently being displayed as key
    // preview.
    private final HashMap<Key,KeyPreviewView> mShowingKeyPreviewViews = new HashMap<>();

    private final KeyPreviewDrawParams mParams;
    private final KeyPreviewConfig mThemePreview;
    private final android.graphics.drawable.Drawable mThemePreviewBackground;

    public KeyPreviewChoreographer(final Context context, final KeyPreviewDrawParams params,
            final ResolvedVisualTheme theme) {
        mParams = params;
        mThemePreview = theme.getHasCustomKeyPreview()
                ? theme.getManifest().getKeyPreview() : null;
        mThemePreviewBackground = theme.getHasCustomKeyPreview()
                ? theme.drawable(context, ThemeAsset.KEY_PREVIEW) : null;
    }

    public KeyPreviewView getKeyPreviewView(final Key key, final ViewGroup placerView) {
        KeyPreviewView keyPreviewView = mShowingKeyPreviewViews.remove(key);
        if (keyPreviewView != null) {
            return keyPreviewView;
        }
        keyPreviewView = mFreeKeyPreviewViews.poll();
        if (keyPreviewView != null) {
            return keyPreviewView;
        }
        final Context context = placerView.getContext();
        keyPreviewView = new KeyPreviewView(context, null /* attrs */);
        if (mThemePreview == null || mThemePreviewBackground == null) {
            keyPreviewView.setBackgroundResource(mParams.mPreviewBackgroundResId);
        } else {
            keyPreviewView.setBackground(mThemePreviewBackground.getConstantState() == null
                    ? mThemePreviewBackground
                    : mThemePreviewBackground.getConstantState().newDrawable().mutate());
        }
        placerView.addView(keyPreviewView, ViewLayoutUtils.newLayoutParam(placerView, 0, 0));
        return keyPreviewView;
    }

    public boolean isShowingKeyPreview(final Key key) {
        return mShowingKeyPreviewViews.containsKey(key);
    }

    public void dismissKeyPreview(final Key key) {
        if (key == null) {
            return;
        }
        final KeyPreviewView keyPreviewView = mShowingKeyPreviewViews.get(key);
        if (keyPreviewView == null) {
            return;
        }
        // Dismiss preview
        mShowingKeyPreviewViews.remove(key);
        keyPreviewView.setTag(null);
        keyPreviewView.setVisibility(View.INVISIBLE);
        mFreeKeyPreviewViews.add(keyPreviewView);
    }

    public void placeAndShowKeyPreview(final Key key, final KeyboardIconsSet iconsSet,
            final KeyDrawParams drawParams, final int fullKeyboardViewWidth, final int[] keyboardOrigin,
            final ViewGroup placerView) {
        final KeyPreviewView keyPreviewView = getKeyPreviewView(key, placerView);
        placeKeyPreview(key, keyPreviewView, iconsSet, drawParams, fullKeyboardViewWidth, keyboardOrigin);
        showKeyPreview(key, keyPreviewView);
    }

    public void placeAndShowSinglePopupKeyPreview(final Key key, final PopupKeySpec popupKey,
            final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams,
            final int fullKeyboardViewWidth, final int[] keyboardOrigin,
            final ViewGroup placerView) {
        final KeyPreviewView keyPreviewView = getKeyPreviewView(key, placerView);
        placeKeyPreview(key, keyPreviewView, iconsSet, drawParams, fullKeyboardViewWidth, keyboardOrigin);
        keyPreviewView.setPreviewVisual(popupKey, key, iconsSet, drawParams);
        showKeyPreview(key, keyPreviewView);
    }

    private void placeKeyPreview(Key key, KeyPreviewView keyPreviewView, KeyboardIconsSet iconsSet,
            KeyDrawParams drawParams, int fullKeyboardViewWidth, int[] originCoords) {
        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams);
        if (mThemePreview == null) {
            placeDefaultKeyPreview(
                    key, keyPreviewView, fullKeyboardViewWidth, originCoords);
            return;
        }
        if (mThemePreview.getTextColor() != null) {
            keyPreviewView.setTextColor(
                    VisualThemeValidator.INSTANCE.parseColor(mThemePreview.getTextColor()));
        }
        int keyDrawWidth = key.getDrawWidth();
        float previewScale = key.getHeight()
                * (1.0f + mThemePreview.getVerticalOverscan() * 2.0f)
                / (mThemePreview.getFaceBottomPx() - mThemePreview.getFaceTopPx());
        int previewWidth = Math.round(mThemePreview.getBitmapWidthPx() * previewScale);
        int previewHeight = Math.round(mThemePreview.getBitmapHeightPx() * previewScale);
        int faceLeft = Math.round(mThemePreview.getFaceLeftPx() * previewScale);
        int faceTop = Math.round(mThemePreview.getFaceTopPx() * previewScale);
        int faceRight = Math.round(mThemePreview.getFaceRightPx() * previewScale);
        int faceBottom = Math.round(mThemePreview.getFaceBottomPx() * previewScale);
        // Treat the transparent halo margins like 9-patch content padding. This centers labels and
        // icons inside the framed key face and keeps popup-key geometry based on the face itself.
        keyPreviewView.setPadding(
                faceLeft, faceTop, previewWidth - faceRight, previewHeight - faceBottom);
        keyPreviewView.measure(
                View.MeasureSpec.makeMeasureSpec(previewWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(previewHeight, View.MeasureSpec.EXACTLY));
        mParams.setGeometry(keyPreviewView);
        int originX = CoordinateUtils.x(originCoords);
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        int keyPreviewPosition;
        int previewX = key.getDrawX() + (keyDrawWidth - previewWidth) / 2 + originX;
        if (previewX + faceLeft < originX) {
            previewX = originX - faceLeft;
            keyPreviewPosition = KeyPreviewView.POSITION_LEFT;
        } else if (previewX + faceRight > fullKeyboardViewWidth + originX) {
            previewX = fullKeyboardViewWidth + originX - faceRight;
            keyPreviewPosition = KeyPreviewView.POSITION_RIGHT;
        } else {
            keyPreviewPosition = KeyPreviewView.POSITION_MIDDLE;
        }
        boolean hasPopupKeys = (key.getPopupKeys() != null);
        keyPreviewView.setPreviewBackground(hasPopupKeys, keyPreviewPosition);

        // Keep the pressed key visible: decorated previews sit fully above the pressed key.
        int previewGap = Math.round(mThemePreview.getGapDp()
                * keyPreviewView.getResources().getDisplayMetrics().density);
        int previewY = key.getY() - faceBottom - previewGap
                + CoordinateUtils.y(originCoords);

        ViewLayoutUtils.placeViewAt(keyPreviewView, previewX, previewY, previewWidth, previewHeight);
        keyPreviewView.setPivotX(previewWidth / 2.0f);
        keyPreviewView.setPivotY(previewHeight);
    }

    private void placeDefaultKeyPreview(final Key key, final KeyPreviewView keyPreviewView,
            final int fullKeyboardViewWidth, final int[] originCoords) {
        keyPreviewView.measure(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        mParams.setGeometry(keyPreviewView);
        final int previewWidth = keyPreviewView.getMeasuredWidth();
        final int previewHeight = keyPreviewView.getMeasuredHeight();
        final int originX = CoordinateUtils.x(originCoords);
        int previewX = key.getDrawX() - (previewWidth - key.getDrawWidth()) / 2 + originX;
        final int previewPosition;
        if (previewX < originX) {
            previewX = originX;
            previewPosition = KeyPreviewView.POSITION_LEFT;
        } else if (previewX > fullKeyboardViewWidth - previewWidth + originX) {
            previewX = fullKeyboardViewWidth - previewWidth + originX;
            previewPosition = KeyPreviewView.POSITION_RIGHT;
        } else {
            previewPosition = KeyPreviewView.POSITION_MIDDLE;
        }
        keyPreviewView.setPreviewBackground(key.getPopupKeys() != null, previewPosition);
        final Colors colors = Settings.getValues().mColors;
        colors.setBackground(keyPreviewView, ColorType.KEY_PREVIEW_BACKGROUND);

        final int previewY = key.getY() - previewHeight + key.getHeight()
                - mParams.mPreviewOffset + CoordinateUtils.y(originCoords);
        ViewLayoutUtils.placeViewAt(
                keyPreviewView, previewX, previewY, previewWidth, previewHeight);
        keyPreviewView.setPivotX(previewWidth / 2.0f);
        keyPreviewView.setPivotY(previewHeight);
    }

    void showKeyPreview(final Key key, final KeyPreviewView keyPreviewView) {
        keyPreviewView.setVisibility(View.VISIBLE);
        mShowingKeyPreviewViews.put(key, keyPreviewView);
    }

}
