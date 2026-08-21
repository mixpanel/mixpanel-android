package com.mixpanel.android.autocapture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.facebook.react.views.view.FakeReactViewGroup;

import com.mixpanel.android.test.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the {@code $el_id} resolution order implemented by {@link DefaultViewElementIdExtractor}:
 *
 * <ol>
 *   <li>React Native {@code nativeID} (view tag keyed by the {@code view_tag_native_id} resource)</li>
 *   <li>Android resource entry name</li>
 *   <li>contentDescription, only when the view is important for accessibility</li>
 *   <li>{@code <SimpleClassName>_<hashCode>}</li>
 * </ol>
 *
 * <p>Lives in the {@code autocapture} package to reach the package-private extractor. Views are
 * built directly (no activity) and always on the main thread; the resolved id is handed back to the
 * test thread for assertions.
 *
 * <p>The {@code view_tag_native_id} and {@code mp_test_*} id resources come from
 * {@code src/androidTest/res/values/mp_test_ids.xml} — the test APK stands in for React Native,
 * which declares the same resource name.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DefaultViewElementIdExtractorTest {

    /** Matches the anonymous fallback: SimpleClassName_hexHash. */
    private static final String HASH_ID_PATTERN = "[A-Za-z0-9$_]+_[0-9a-f]+";

    private Context mContext;
    private int mNativeIdTagRes;

    /** Builds the view under test. Always invoked on the main thread. */
    private interface ViewBuilder {
        View build(Context context);
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        // Resolve the React Native tag resource exactly the way the extractor does. A zero here
        // means the test-only resource is missing, which would silently skip the nativeID step.
        mNativeIdTagRes = mContext.getResources().getIdentifier(
                "view_tag_native_id", "id", mContext.getPackageName());
        assertNotEquals(
                "view_tag_native_id must resolve for the nativeID tests to mean anything",
                0, mNativeIdTagRes);
    }

    // ============ Priority 1: React Native nativeID ============

    @Test
    public void testNativeIdWinsOverResourceIdAndContentDescription() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(R.id.mp_test_checkout_button);
            view.setContentDescription("Checkout Label");
            view.setTag(mNativeIdTagRes, "rn_checkout_button");
            return view;
        });

        assertEquals("rn_checkout_button", elementId);
    }

    @Test
    public void testEmptyNativeIdFallsThroughToResourceId() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(R.id.mp_test_checkout_button);
            view.setTag(mNativeIdTagRes, "");
            return view;
        });

        assertEquals("mp_test_checkout_button", elementId);
    }

    @Test
    public void testNonStringNativeIdTagIsIgnored() {
        // React Native stores a String; anything else must not be reported and must not crash.
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setContentDescription("Checkout Label");
            view.setTag(mNativeIdTagRes, Integer.valueOf(42));
            return view;
        });

        assertEquals("Checkout Label", elementId);
    }

    // ============ Priority 2: Android resource entry name ============

    @Test
    public void testResourceEntryNameWinsOverContentDescription() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(R.id.mp_test_checkout_button);
            view.setContentDescription("Checkout Label");
            return view;
        });

        assertEquals("mp_test_checkout_button", elementId);
    }

    @Test
    public void testProgrammaticNumericIdFallsThroughToContentDescription() {
        // Ids assigned as bare ints (or via View.generateViewId()) have no resource entry name.
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(10001);
            view.setContentDescription("Checkout Label");
            return view;
        });

        assertEquals("Checkout Label", elementId);
    }

    // ============ Priority 3: contentDescription ============

    @Test
    public void testContentDescriptionUsedWhenNothingElseResolves() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setContentDescription("Checkout Label");
            return view;
        });

        assertEquals("Checkout Label", elementId);
    }

    @Test
    public void testContentDescriptionIgnoredWhenNotImportantForAccessibility() {
        // Frameworks auto-derive contentDescription from child text even for views the developer
        // excluded from accessibility; that text can carry user data, so it must not be reported.
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setContentDescription("Account ending 4321");
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            return view;
        });

        assertTrue("Expected the hash fallback, got: " + elementId,
                elementId.matches(HASH_ID_PATTERN));
        assertTrue("Derived contentDescription must not leak into $el_id: " + elementId,
                !elementId.contains("4321"));
    }

    @Test
    public void testEmptyContentDescriptionFallsThroughToHash() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setContentDescription("");
            return view;
        });

        assertTrue("Expected the hash fallback, got: " + elementId,
                elementId.matches(HASH_ID_PATTERN));
    }

    // ============ Priority 3: React Native accessible={false} ============

    @Test
    public void testReactNativeViewIgnoresContentDescriptionWhenNotFocusable() {
        // React Native expresses accessible={false} by clearing focusability while leaving the view
        // important for accessibility with its contentDescription intact, so the label must not be
        // reported — it is exactly the shape that leaks user data into $el_id.
        String elementId = resolve(context -> {
            FakeReactViewGroup view = new FakeReactViewGroup(context);
            view.setContentDescription("Account ending 4321");
            view.setFocusable(false);
            return view;
        });

        assertTrue("Expected the hash fallback, got: " + elementId,
                elementId.matches(HASH_ID_PATTERN));
        assertTrue("Label from an accessible={false} view must not leak: " + elementId,
                !elementId.contains("4321"));
    }

    @Test
    public void testReactNativeViewUsesContentDescriptionWhenFocusable() {
        // accessible={true}, and a label with no accessible prop at all, both leave the view
        // focusable — those labels are intentional and still resolve.
        String elementId = resolve(context -> {
            FakeReactViewGroup view = new FakeReactViewGroup(context);
            view.setContentDescription("Checkout Label");
            view.setFocusable(true);
            return view;
        });

        assertEquals("Checkout Label", elementId);
    }

    @Test
    public void testNativeViewUsesContentDescriptionEvenWhenNotFocusable() {
        // The focusability guard is scoped to React Native views: a native Android view can be
        // clickable without being focusable and still carry an intentional contentDescription.
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setContentDescription("Checkout Label");
            view.setClickable(true);
            view.setFocusable(false);
            return view;
        });

        assertEquals("Checkout Label", elementId);
    }

    // ============ Priority 4: hash fallback ============

    @Test
    public void testHashFallbackUsesSimpleClassName() {
        String elementId = resolve(TextView::new);

        assertTrue("Expected TextView_<hex>, got: " + elementId,
                elementId.matches("TextView_[0-9a-f]+"));
    }

    @Test
    public void testHashFallbackIsStablePerViewAndDistinctAcrossViews() {
        final String[] ids = new String[3];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View first = new TextView(mContext);
            View second = new TextView(mContext);
            ids[0] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(first);
            ids[1] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(first);
            ids[2] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(second);
        });

        assertEquals("Same view must resolve to the same id", ids[0], ids[1]);
        assertNotEquals("Different views must resolve to different ids", ids[0], ids[2]);
    }

    @Test
    public void testAnonymousIdMatchesHashFallback() {
        // resolveElementId() hands a null-returning custom extractor over to anonymousId(); the two
        // must agree so the fallback shape is identical on both paths.
        final String[] ids = new String[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View view = new TextView(mContext);
            ids[0] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(view);
            ids[1] = DefaultViewElementIdExtractor.anonymousId(view);
        });

        assertEquals(ids[0], ids[1]);
    }

    // ============ Helpers ============

    private String resolve(final ViewBuilder builder) {
        final String[] elementId = new String[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> elementId[0] =
                        DefaultViewElementIdExtractor.INSTANCE.extractElementId(builder.build(mContext)));
        assertNotNull("Extractor must never return null", elementId[0]);
        return elementId[0];
    }
}
