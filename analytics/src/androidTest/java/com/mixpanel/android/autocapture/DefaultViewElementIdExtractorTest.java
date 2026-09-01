package com.mixpanel.android.autocapture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

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

        assertTrue("Expected the hash fallback, got: " + elementId,
                elementId.matches(HASH_ID_PATTERN));
        assertTrue("The label must not be used: " + elementId,
                !elementId.contains("Checkout"));
    }

    // ============ Priority 2: Android resource entry name ============

    @Test
    public void testResourceEntryNameIsUsedAndLabelIsIgnored() {
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(R.id.mp_test_checkout_button);
            view.setContentDescription("Checkout Label");
            return view;
        });

        assertEquals("mp_test_checkout_button", elementId);
    }

    @Test
    public void testContentDescriptionIsNeverUsedAsIdentity() {
        // Accessibility text is localized — the same element would report a different id per
        // language — and it can carry user data, so it is not an identity source at any priority.
        String elementId = resolve(context -> {
            TextView view = new TextView(context);
            view.setId(10001);  // generated id: no resource entry name
            view.setContentDescription("Account ending 4321");
            return view;
        });

        assertTrue("Expected the hash fallback, got: " + elementId,
                elementId.matches(HASH_ID_PATTERN));
        assertTrue("Label must not appear in the id: " + elementId,
                !elementId.contains("4321"));
    }

    // ============ Priority 4: hash fallback ============

    @Test
    public void testHashFallbackUsesSimpleClassName() {
        String elementId = resolve(TextView::new);

        assertTrue("Expected TextView_<hex>, got: " + elementId,
                elementId.matches("TextView_[0-9a-f]+"));
    }

    @Test
    public void testHashFallbackIsStableForTheSameStructure() {
        // The hash describes where the view sits, not which instance it is, so an identical layout
        // built twice — as happens on every launch, and every time a list row is recycled — resolves
        // to the same id. An identity hash could not do this.
        final String[] ids = new String[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < 2; i++) {
                LinearLayout root = new LinearLayout(mContext);
                LinearLayout row = new LinearLayout(mContext);
                TextView leaf = new TextView(mContext);
                row.addView(leaf);
                root.addView(row);
                ids[i] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(leaf);
            }
        });

        assertEquals("The same structure must resolve to the same id", ids[0], ids[1]);
        assertTrue("Expected TextView_<hex>, got: " + ids[0],
                ids[0].matches("TextView_[0-9a-f]+"));
    }

    @Test
    public void testHashFallbackDistinguishesSiblingPositions() {
        final String[] ids = new String[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LinearLayout root = new LinearLayout(mContext);
            TextView first = new TextView(mContext);
            TextView second = new TextView(mContext);
            root.addView(first);
            root.addView(second);
            ids[0] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(first);
            ids[1] = DefaultViewElementIdExtractor.INSTANCE.extractElementId(second);
        });

        assertNotEquals("Siblings at different positions must resolve to different ids",
                ids[0], ids[1]);
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
