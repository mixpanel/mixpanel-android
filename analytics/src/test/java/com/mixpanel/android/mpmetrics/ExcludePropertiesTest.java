package com.mixpanel.android.mpmetrics;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the property exclude set applied to outgoing payloads.
 *
 * <p>The exclude set is configured at SDK init via {@link MixpanelOptions.Builder#excludeProperties(Set)}.
 * It is applied at two chokepoints:
 * <ol>
 *   <li>{@code AnalyticsMessages.Worker.prepareEventObject} for every event, covering super /
 *       caller / referrer properties and the SDK auto-properties merged in just before send.</li>
 *   <li>{@code MixpanelAPI.PeopleImpl.set} for the {@code $set} bag, which auto-merges the
 *       SDK's device-info properties. Other people operators and group updates do not merge
 *       SDK fat and are not filtered.</li>
 * </ol>
 *
 * <p>Most of these tests target {@link AnalyticsMessages#applyExcludeProperties(JSONObject, Set)}
 * directly because it carries the substantive behavior; one end-to-end test
 * ({@link #testPeopleSetStripsAutoMergedDeviceInfo}) verifies the people-side wiring through a
 * real {@link MixpanelAPI}. Tests for the {@link MixpanelOptions} builder live in
 * {@link MixpanelOptionsTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class ExcludePropertiesTest {

    private JSONObject buildSampleAutoProps() throws JSONException {
        // Mirrors a representative subset of what
        // AnalyticsMessages.Worker.getDefaultEventProperties() produces.
        JSONObject props = new JSONObject();
        props.put("token", "test-token");
        props.put("time", 1700000000000L);
        props.put("distinct_id", "abc-123");
        props.put("$device_id", "device-xyz");
        props.put("$user_id", "user-42");
        props.put("$insert_id", "insert-1");
        props.put("$had_persisted_distinct_id", true);
        props.put("mp_lib", "android");
        props.put("$lib_version", "7.0.0");
        props.put("$os", "Android");
        props.put("$screen_height", 1920);
        props.put("$screen_width", 1080);
        props.put("$carrier", "Verizon");
        props.put("custom_user_prop", "value");
        return props;
    }

    @Test
    public void testNullExcludeSetIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyExcludeProperties(props, null);
        assertEquals(before, props.length());
    }

    @Test
    public void testEmptyExcludeSetIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyExcludeProperties(props, Collections.<String>emptySet());
        assertEquals(before, props.length());
    }

    @Test
    public void testStripsAutoProperty() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        AnalyticsMessages.applyExcludeProperties(props, Collections.singleton("$screen_height"));
        assertFalse(props.has("$screen_height"));
        // Adjacent auto-props remain untouched.
        assertTrue(props.has("$screen_width"));
        assertTrue(props.has("$lib_version"));
    }

    @Test
    public void testStripsCustomUserProperty() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        AnalyticsMessages.applyExcludeProperties(props, Collections.singleton("custom_user_prop"));
        assertFalse(props.has("custom_user_prop"));
    }

    @Test
    public void testReservedKeysAreNeverStripped() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        // Drive the test from the canonical reserved set so adding a new reserved key
        // here automatically tightens this test rather than leaving it stale.
        Set<String> exclude = new HashSet<>(MixpanelOptions.RESERVED_PROPERTY_KEYS);

        // Sanity check: every reserved key we're about to try to exclude is actually
        // present in the sample, otherwise the test would silently pass for missing keys.
        for (String key : MixpanelOptions.RESERVED_PROPERTY_KEYS) {
            assertTrue("Sample auto-props is missing reserved key " + key, props.has(key));
        }

        AnalyticsMessages.applyExcludeProperties(props, exclude);

        for (String key : MixpanelOptions.RESERVED_PROPERTY_KEYS) {
            assertTrue("Reserved key was stripped: " + key, props.has(key));
        }
    }

    /**
     * {@code $insert_id} is in the Mixpanel ingestion vocabulary but this SDK never writes it
     * (we use {@code $mp_event_id} inside {@code $mp_metadata} for dedup, which lives outside
     * the properties bag). So if a customer lists {@code $insert_id} in their exclude set we
     * should honor it — there is nothing to protect.
     */
    @Test
    public void testInsertIdIsNotReserved() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        AnalyticsMessages.applyExcludeProperties(props, Collections.singleton("$insert_id"));
        assertFalse(props.has("$insert_id"));
    }

    @Test
    public void testMultiplePropertiesStripped() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        Set<String> exclude = new HashSet<>();
        exclude.add("$screen_height");
        exclude.add("$screen_width");
        exclude.add("$carrier");
        exclude.add("custom_user_prop");

        AnalyticsMessages.applyExcludeProperties(props, exclude);

        assertFalse(props.has("$screen_height"));
        assertFalse(props.has("$screen_width"));
        assertFalse(props.has("$carrier"));
        assertFalse(props.has("custom_user_prop"));
        // Untouched samples
        assertTrue(props.has("$os"));
        assertTrue(props.has("$lib_version"));
    }

    @Test
    public void testExcludedKeyNotPresentIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyExcludeProperties(props, Collections.singleton("never_added"));
        assertEquals(before, props.length());
    }

    @Test
    public void testNullTargetIsNoOp() {
        // No exception should be thrown.
        AnalyticsMessages.applyExcludeProperties(null, Collections.singleton("$lib_version"));
    }

    /**
     * End-to-end check that {@code People.set(...)} honors the exclude set by stripping an
     * auto-merged device-info key while preserving caller-supplied properties. This is the
     * one wiring path that {@link AnalyticsMessages#applyExcludeProperties(JSONObject, Set)}
     * alone can't cover, since the merge happens in {@link MixpanelAPI} before the worker
     * sees it.
     */
    @Test
    public void testPeopleSetStripsAutoMergedDeviceInfo() throws Exception {
        final Context context = ApplicationProvider.getApplicationContext();
        final String token = UUID.randomUUID().toString();
        final BlockingQueue<AnalyticsMessages.PeopleDescription> seen =
                new LinkedBlockingQueue<>();

        final AnalyticsMessages capturing = new AnalyticsMessages(
                context, MPConfig.getInstance(context, null)) {
            @Override
            public void peopleMessage(PeopleDescription description) {
                seen.add(description);
                // Don't forward; we just want to inspect the bag the API constructed.
            }
        };

        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(Collections.singleton("$android_lib_version"))
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                context, new TestUtils.EmptyPreferences(context), token, options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return capturing;
            }
        };
        mixpanel.identify("user-under-test");

        JSONObject callerProps = new JSONObject();
        callerProps.put("custom_prop", "kept");
        mixpanel.getPeople().set(callerProps);

        AnalyticsMessages.PeopleDescription description =
                seen.poll(5, TimeUnit.SECONDS);
        assertNotNull("PeopleDescription should have been recorded", description);

        JSONObject setBag = description.getMessage().getJSONObject("$set");
        assertFalse("Excluded auto-merged property should be stripped",
                setBag.has("$android_lib_version"));
        assertEquals("Caller property should pass through",
                "kept", setBag.getString("custom_prop"));
        // Adjacent auto-properties should still be present.
        assertTrue("Adjacent auto-property should remain",
                setBag.has("$android_os"));
    }

    /**
     * Plumbing check: an EventDescription built without an exclude set defaults to empty,
     * and one built with an exclude set exposes the same set to the worker.
     */
    @Test
    public void testEventDescriptionPlumbsExcludeProperties() throws JSONException {
        JSONObject props = new JSONObject();
        props.put("k", "v");

        AnalyticsMessages.EventDescription noExclude = new AnalyticsMessages.EventDescription(
                "evt", props, "tok", false, new JSONObject());
        assertTrue(noExclude.getExcludeProperties().isEmpty());

        Set<String> exclude = Collections.singleton("$screen_height");
        AnalyticsMessages.EventDescription withExclude = new AnalyticsMessages.EventDescription(
                "evt", props, "tok", false, new JSONObject(), exclude);
        assertEquals(exclude, withExclude.getExcludeProperties());
    }
}
