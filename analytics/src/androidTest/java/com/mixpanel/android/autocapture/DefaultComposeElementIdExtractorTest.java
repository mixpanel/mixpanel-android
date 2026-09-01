package com.mixpanel.android.autocapture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the Compose {@code $el_id} resolution order implemented by
 * {@link DefaultComposeElementIdExtractor}: {@code testTag} > contentDescription >
 * {@code <TagName>_<hash>}.
 *
 * <p>Lives in the {@code autocapture} package to reach the package-private extractor and the
 * package-private {@link ComposeElementInfo} constructor. No Compose runtime is needed —
 * {@code ComposeElementInfo} carries plain values, which is exactly why the public API is shaped that
 * way.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DefaultComposeElementIdExtractorTest {

    private static final String ANONYMOUS_ID = "Button_1a2b3c";

    private static ComposeElementInfo element(String testTag) {
        return new ComposeElementInfo(testTag, "Button", "Button", ANONYMOUS_ID);
    }

    @Test
    public void testTestTagIsUsedWhenPresent() {
        assertEquals(
                "compose_checkout_btn",
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(
                        element("compose_checkout_btn")));
    }

    @Test
    public void testAnonymousIdUsedWhenNoTestTag() {
        assertEquals(
                ANONYMOUS_ID,
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(element(null)));
    }

    @Test
    public void testEmptyTestTagIsTreatedAsAbsent() {
        assertEquals(
                ANONYMOUS_ID,
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(element("")));
    }

    @Test
    public void testElementInfoExposesSemanticsToCustomExtractors() {
        ComposeElementInfo info = element("compose_checkout_btn");

        assertEquals("compose_checkout_btn", info.getTestTag());
        assertEquals("Button", info.getRole());
        assertEquals("Button", info.getTagName());
        assertEquals(ANONYMOUS_ID, info.getAnonymousId());
    }

    @Test
    public void testContentDescriptionIsNotExposedToExtractors() {
        // Accessibility text is localized and can carry user data, so it is neither an identifier
        // source nor visible to a custom extractor. This test fails to compile if a getter returns.
        for (java.lang.reflect.Method method : ComposeElementInfo.class.getMethods()) {
            assertTrue(
                    "ComposeElementInfo must not expose accessibility text: " + method.getName(),
                    !method.getName().toLowerCase().contains("contentdescription")
                            && !method.getName().toLowerCase().contains("label"));
        }
    }
}
