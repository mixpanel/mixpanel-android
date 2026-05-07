package com.mixpanel.android.mpmetrics;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the property blacklist applied to outgoing events.
 *
 * <p>The blacklist is configured at SDK init via {@link MixpanelOptions.Builder#blacklistedProperties(Set)}.
 * It is applied at two chokepoints:
 * <ol>
 *   <li>{@code MixpanelAPI.buildEventDescription} for user / super / referrer properties</li>
 *   <li>{@link AnalyticsMessages#applyPropertyBlacklist(JSONObject, Set)} for SDK auto-properties
 *       (the default-event-properties added in the worker just before send).</li>
 * </ol>
 *
 * <p>These tests target the second chokepoint directly, since it's the one with the most
 * customer value (those auto-properties are usually what bloats {@code $mp_event_size}).
 * Tests for the {@link MixpanelOptions} builder live in {@link MixpanelOptionsTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class PropertyBlacklistTest {

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
    public void testNullBlacklistIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyPropertyBlacklist(props, null);
        assertEquals(before, props.length());
    }

    @Test
    public void testEmptyBlacklistIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyPropertyBlacklist(props, Collections.<String>emptySet());
        assertEquals(before, props.length());
    }

    @Test
    public void testStripsAutoProperty() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        AnalyticsMessages.applyPropertyBlacklist(props, Collections.singleton("$screen_height"));
        assertFalse(props.has("$screen_height"));
        // Adjacent auto-props remain untouched.
        assertTrue(props.has("$screen_width"));
        assertTrue(props.has("$lib_version"));
    }

    @Test
    public void testStripsCustomUserProperty() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        AnalyticsMessages.applyPropertyBlacklist(props, Collections.singleton("custom_user_prop"));
        assertFalse(props.has("custom_user_prop"));
    }

    @Test
    public void testReservedKeysAreNeverStripped() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        Set<String> blacklist = new HashSet<>();
        // Try to blacklist every reserved identity/routing key.
        blacklist.add("token");
        blacklist.add("time");
        blacklist.add("distinct_id");
        blacklist.add("$device_id");
        blacklist.add("$user_id");
        blacklist.add("$insert_id");
        blacklist.add("$had_persisted_distinct_id");

        AnalyticsMessages.applyPropertyBlacklist(props, blacklist);

        assertTrue(props.has("token"));
        assertTrue(props.has("time"));
        assertTrue(props.has("distinct_id"));
        assertTrue(props.has("$device_id"));
        assertTrue(props.has("$user_id"));
        assertTrue(props.has("$insert_id"));
        assertTrue(props.has("$had_persisted_distinct_id"));
    }

    @Test
    public void testMultiplePropertiesStripped() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        Set<String> blacklist = new HashSet<>();
        blacklist.add("$screen_height");
        blacklist.add("$screen_width");
        blacklist.add("$carrier");
        blacklist.add("custom_user_prop");

        AnalyticsMessages.applyPropertyBlacklist(props, blacklist);

        assertFalse(props.has("$screen_height"));
        assertFalse(props.has("$screen_width"));
        assertFalse(props.has("$carrier"));
        assertFalse(props.has("custom_user_prop"));
        // Untouched samples
        assertTrue(props.has("$os"));
        assertTrue(props.has("$lib_version"));
    }

    @Test
    public void testBlacklistedKeyNotPresentIsNoOp() throws JSONException {
        JSONObject props = buildSampleAutoProps();
        int before = props.length();
        AnalyticsMessages.applyPropertyBlacklist(props, Collections.singleton("never_added"));
        assertEquals(before, props.length());
    }

    @Test
    public void testNullTargetIsNoOp() {
        // No exception should be thrown.
        AnalyticsMessages.applyPropertyBlacklist(null, Collections.singleton("$lib_version"));
    }

    /**
     * Plumbing check: an EventDescription built without a blacklist defaults to an empty set,
     * and one built with a blacklist exposes the same set to the worker.
     */
    @Test
    public void testEventDescriptionPlumbsBlacklist() throws JSONException {
        JSONObject props = new JSONObject();
        props.put("k", "v");

        AnalyticsMessages.EventDescription noBlacklist = new AnalyticsMessages.EventDescription(
                "evt", props, "tok", false, new JSONObject());
        assertTrue(noBlacklist.getBlacklistedProperties().isEmpty());

        Set<String> blacklist = Collections.singleton("$screen_height");
        AnalyticsMessages.EventDescription withBlacklist = new AnalyticsMessages.EventDescription(
                "evt", props, "tok", false, new JSONObject(), blacklist);
        assertEquals(blacklist, withBlacklist.getBlacklistedProperties());
    }
}
