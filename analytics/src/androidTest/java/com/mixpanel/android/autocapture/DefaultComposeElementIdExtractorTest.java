package com.mixpanel.android.autocapture;

import static org.junit.Assert.assertEquals;

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

    private static ComposeElementInfo element(String testTag, String contentDescription) {
        return new ComposeElementInfo(
                testTag, contentDescription, "Button", "Button", ANONYMOUS_ID);
    }

    @Test
    public void testTestTagWinsOverContentDescription() {
        assertEquals(
                "compose_checkout_btn",
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(
                        element("compose_checkout_btn", "Checkout Label")));
    }

    @Test
    public void testContentDescriptionUsedWhenNoTestTag() {
        assertEquals(
                "Checkout Label",
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(
                        element(null, "Checkout Label")));
    }

    @Test
    public void testAnonymousIdUsedWhenNoSemantics() {
        assertEquals(
                ANONYMOUS_ID,
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(element(null, null)));
    }

    @Test
    public void testEmptyValuesAreTreatedAsAbsent() {
        assertEquals(
                "Checkout Label",
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(
                        element("", "Checkout Label")));
        assertEquals(
                ANONYMOUS_ID,
                DefaultComposeElementIdExtractor.INSTANCE.extractElementId(element("", "")));
    }

    @Test
    public void testElementInfoExposesSemanticsToCustomExtractors() {
        ComposeElementInfo info = element("compose_checkout_btn", "Checkout Label");

        assertEquals("compose_checkout_btn", info.getTestTag());
        assertEquals("Checkout Label", info.getContentDescription());
        assertEquals("Button", info.getRole());
        assertEquals("Button", info.getTagName());
        assertEquals(ANONYMOUS_ID, info.getAnonymousId());
    }
}
