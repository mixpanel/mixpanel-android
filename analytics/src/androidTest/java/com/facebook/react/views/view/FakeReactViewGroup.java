package com.facebook.react.views.view;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Test double for React Native's {@code ReactViewGroup}.
 *
 * <p>Deliberately declared in React Native's package so that
 * {@code AutocaptureDefaults.isReactNativeView(View)} — which matches on the
 * {@code com.facebook.react.} class-name prefix — treats instances as React Native views. That lets
 * the SDK's React-Native-specific accessibility guards be tested without adding a React Native
 * dependency to the SDK.
 *
 * <p>React Native applies accessibility props through {@code BaseViewManager}: an
 * {@code accessible={true}} + contentDescription pairing leaves the view focusable, while
 * {@code accessible={false}} clears focusability and leaves the content description in place. Tests
 * reproduce those two shapes by setting {@code focusable} and {@code contentDescription} directly.
 */
public class FakeReactViewGroup extends FrameLayout {

    public FakeReactViewGroup(Context context) {
        super(context);
        // React Native's touchables are clickable; matching that keeps the fixture realistic.
        setClickable(true);
    }
}
