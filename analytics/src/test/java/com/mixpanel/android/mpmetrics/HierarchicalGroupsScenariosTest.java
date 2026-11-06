package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.mixpanel.android.identity.MixpanelIdentity;
import com.mixpanel.android.util.MPConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * The hierarchical groups scenarios from the "Hierarchical Group Android Testing" Notion doc, one
 * test per section and in the same order, so each SDK can port this file as its contract.
 *
 * <p>Each test asserts the endpoint a call hits and the request it sends, or that nothing is sent.
 * Edge cases beyond the doc live in {@link HierarchicalGroupsTest}.
 */
@RunWith(RobolectricTestRunner.class)
public class HierarchicalGroupsScenariosTest {

    private static final String TRACK = MPConstants.URL.EVENT;
    private static final String ENGAGE = MPConstants.URL.PEOPLE;
    private static final String GROUPS = MPConstants.URL.GROUPS;

    private static final String USER_ID = "my_user_id";
    private static final String COMPANY_ID = "my_company_id";
    private static final String TEAM_ID = "my_team_id";

    // Every /groups write has these top-level keys beside its one operation
    private static final Set<String> GROUP_MESSAGE_KEYS = new HashSet<>(Arrays.asList(
            "$token", "$time", "$group_key", "$group_id", "$company_id", "$mp_metadata"));

    /** One request the SDK queued, with the endpoint its queue is sent to. */
    private static class Sent {
        final String endpoint;
        final String eventName;
        final JSONObject body;

        Sent(String endpoint, String eventName, JSONObject body) {
            this.endpoint = endpoint;
            this.eventName = eventName;
            this.body = body;
        }
    }

    private Context mContext;
    private List<Sent> mSent;
    private AnalyticsMessages mCapturing;
    private MixpanelAPI mMixpanel;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mSent = Collections.synchronizedList(new ArrayList<Sent>());
        mCapturing = new AnalyticsMessages(mContext, MPConfig.getInstance(mContext, null)) {
            @Override
            public void eventsMessage(EventDescription description) {
                if (!description.isAutomatic()) {
                    mSent.add(new Sent(TRACK, description.getEventName(), description.getProperties()));
                }
            }

            @Override
            public void peopleMessage(PeopleDescription description) {
                mSent.add(new Sent(ENGAGE, null, description.getMessage()));
            }

            @Override
            public void groupMessage(GroupDescription description) {
                mSent.add(new Sent(GROUPS, null, description.getMessage()));
            }
        };
        MixpanelOptions options = new MixpanelOptions.Builder().hierarchicalGroups(true).build();
        mMixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }
        };
    }

    private void identify() {
        mMixpanel.identify(new MixpanelIdentity(USER_ID, COMPANY_ID));
    }

    // Starts a scenario from what the steps before it set up, so it asserts only its own requests
    private void clearSent() {
        mSent.clear();
    }

    private void assertNothingSent() {
        assertEquals("Expected no requests", 0, mSent.size());
    }

    private Sent onlySent(String endpoint) {
        assertEquals("Expected exactly one request", 1, mSent.size());
        Sent sent = mSent.get(0);
        assertEquals(endpoint, sent.endpoint);
        return sent;
    }

    private JSONObject onlyEvent(String eventName) {
        Sent sent = onlySent(TRACK);
        assertEquals(eventName, sent.eventName);
        return sent.body;
    }

    private String deviceDistinctId() {
        return "$device:" + mMixpanel.getAnonymousId();
    }

    private void assertAnonymous(JSONObject props) throws JSONException {
        assertEquals(deviceDistinctId(), props.getString("distinct_id"));
        assertEquals(mMixpanel.getAnonymousId(), props.getString("$device_id"));
        assertFalse(props.has("$user_id"));
        assertFalse(props.has("$company_id"));
    }

    private void assertIdentified(JSONObject props) throws JSONException {
        assertFalse(props.has("distinct_id"));
        assertEquals(USER_ID, props.getString("$user_id"));
        assertEquals(COMPANY_ID, props.getString("$company_id"));
        assertEquals(mMixpanel.getAnonymousId(), props.getString("$device_id"));
    }

    // Asserts the single /groups request and returns the value of its operation
    private Object onlyGroupsUpdate(String groupKey, String groupId, String operation) throws JSONException {
        JSONObject message = onlySent(GROUPS).body;
        Set<String> expectedKeys = new HashSet<>(GROUP_MESSAGE_KEYS);
        expectedKeys.add(operation);
        assertEquals(expectedKeys, keys(message));
        assertEquals(groupKey, message.getString("$group_key"));
        assertEquals(groupId, message.getString("$group_id"));
        assertEquals(COMPANY_ID, message.getString("$company_id"));
        return message.get(operation);
    }

    private Object onlyUserUpdate(String operation) throws JSONException {
        return onlyGroupsUpdate("$user_id", USER_ID, operation);
    }

    private static Set<String> keys(JSONObject object) {
        Set<String> keys = new HashSet<>();
        for (Iterator<String> iter = object.keys(); iter.hasNext(); ) {
            keys.add(iter.next());
        }
        return keys;
    }

    // $set on a user profile also carries the $android_* device properties
    private static void assertHasDeviceProperties(JSONObject set) {
        assertTrue(set.has("$android_lib_version"));
        assertTrue(set.has("$android_os"));
        assertTrue(set.has("$android_model"));
    }

    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // Track (Anonymous)

    @Test
    public void testTrackAnonymous() throws JSONException {
        mMixpanel.track("HG Event!", new JSONObject().put("source", "hierarchical_groups_page"));

        JSONObject props = onlyEvent("HG Event!");
        assertAnonymous(props);
        assertEquals("hierarchical_groups_page", props.getString("source"));
    }

    // Identify

    @Test
    public void testIdentify() throws JSONException {
        String anonymousDistinctId = deviceDistinctId();
        identify();

        JSONObject props = onlyEvent("$identify");
        assertIdentified(props);
        assertEquals(anonymousDistinctId, props.getString("$anon_distinct_id"));
    }

    // Track (Identified)

    @Test
    public void testTrackIdentified() throws JSONException {
        identify();
        clearSent();
        mMixpanel.track("HG Event!", new JSONObject().put("source", "hierarchical_groups_page"));

        JSONObject props = onlyEvent("HG Event!");
        assertIdentified(props);
        assertEquals("hierarchical_groups_page", props.getString("source"));
    }

    // Set User Property

    @Test
    public void testSetUserProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().set("plan", "enterprise");

        JSONObject set = (JSONObject) onlyUserUpdate("$set");
        assertEquals("enterprise", set.getString("plan"));
        assertHasDeviceProperties(set);
    }

    // Set User Properties

    @Test
    public void testSetUserProperties() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().setMap(mapOf("role", "admin", "seats", 5));

        JSONObject set = (JSONObject) onlyUserUpdate("$set");
        assertEquals("admin", set.getString("role"));
        assertEquals(5, set.getInt("seats"));
        assertHasDeviceProperties(set);
    }

    // Set Once User Property

    @Test
    public void testSetOnceUserProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().setOnce("first_seen", "2026-10-27");

        JSONObject setOnce = (JSONObject) onlyUserUpdate("$set_once");
        assertEquals(Collections.singleton("first_seen"), keys(setOnce));
        assertEquals("2026-10-27", setOnce.getString("first_seen"));
    }

    // Union User Property

    @Test
    public void testUnionUserProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().union("devices", new JSONArray().put("android"));

        JSONObject union = (JSONObject) onlyUserUpdate("$union");
        assertEquals("[\"android\"]", union.getJSONArray("devices").toString());
    }

    // Remove User Property

    @Test
    public void testRemoveUserProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().remove("devices", "android");

        JSONObject remove = (JSONObject) onlyUserUpdate("$remove");
        assertEquals("android", remove.getString("devices"));
    }

    // Unset User Property

    @Test
    public void testUnsetUserProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().unset("plan");

        JSONArray unset = (JSONArray) onlyUserUpdate("$unset");
        assertEquals("[\"plan\"]", unset.toString());
    }

    // Delete User

    @Test
    public void testDeleteUser() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().deleteUser();

        assertEquals(JSONObject.NULL, onlyUserUpdate("$delete"));
    }

    // Clear Charges (Unsupported)

    @Test
    public void testClearChargesIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.getPeople().clearCharges();

        assertNothingSent();
    }

    // Increment User Property (Unsupported)

    @Test
    public void testIncrementUserPropertyIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.getPeople().increment("logins", 1.0);
        Map<String, Integer> counters = new HashMap<>();
        counters.put("logins", 1);
        counters.put("sessions", 2);
        mMixpanel.getPeople().increment(counters);

        assertNothingSent();
    }

    // Append User Property (Unsupported)

    @Test
    public void testAppendUserPropertyIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.getPeople().append("history", "opened_hg_page");

        assertNothingSent();
    }

    // Merge User Property (Unsupported)

    @Test
    public void testMergeUserPropertyIsUnsupported() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getPeople().merge("settings", new JSONObject().put("theme", "dark"));

        assertNothingSent();
    }

    // Track Charge (Unsupported)

    @Test
    public void testTrackChargeIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.getPeople().trackCharge(9.99, null);

        assertNothingSent();
    }

    // Set Company Property

    @Test
    public void testSetCompanyProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().set("tier", "enterprise");

        JSONObject set = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$set");
        assertEquals(Collections.singleton("tier"), keys(set));
        assertEquals("enterprise", set.getString("tier"));
    }

    @Test
    public void testGetCompanyIsTheCompanyGroup() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("$company_id", COMPANY_ID).set("tier", "enterprise");

        JSONObject set = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$set");
        assertEquals("enterprise", set.getString("tier"));
    }

    // Set Company Properties

    @Test
    public void testSetCompanyProperties() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().setMap(mapOf("size", 12, "region", "us"));

        JSONObject set = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$set");
        assertEquals(new HashSet<>(Arrays.asList("size", "region")), keys(set));
        assertEquals(12, set.getInt("size"));
        assertEquals("us", set.getString("region"));
    }

    // Set Once Company Property

    @Test
    public void testSetOnceCompanyProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().setOnce("created", "2026-10-27");

        JSONObject setOnce = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$set_once");
        assertEquals("2026-10-27", setOnce.getString("created"));
    }

    // Union Company Property

    @Test
    public void testUnionCompanyProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().union("tags", new JSONArray().put("android"));

        JSONObject union = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$union");
        assertEquals("[\"android\"]", union.getJSONArray("tags").toString());
    }

    // Remove Company Property

    @Test
    public void testRemoveCompanyProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().remove("tags", "android");

        JSONObject remove = (JSONObject) onlyGroupsUpdate("$company_id", COMPANY_ID, "$remove");
        assertEquals("android", remove.getString("tags"));
    }

    // Unset Company Property

    @Test
    public void testUnsetCompanyProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().unset("tier");

        JSONArray unset = (JSONArray) onlyGroupsUpdate("$company_id", COMPANY_ID, "$unset");
        assertEquals("[\"tier\"]", unset.toString());
    }

    // Set User's Custom Group (setGroup)

    @Test
    public void testSetUsersCustomGroup() throws JSONException {
        identify();
        clearSent();
        mMixpanel.setGroup("team_id", TEAM_ID);

        JSONObject set = (JSONObject) onlyUserUpdate("$set");
        assertEquals(TEAM_ID, set.getString("team_id"));
        assertHasDeviceProperties(set);
    }

    // Track with Custom Group (trackWithGroups)

    @Test
    public void testTrackWithCustomGroup() throws JSONException {
        identify();
        clearSent();
        mMixpanel.trackWithGroups("HG Team Event!", null, mapOf("team_id", TEAM_ID));

        JSONObject props = onlyEvent("HG Team Event!");
        assertIdentified(props);
        assertEquals(TEAM_ID, props.get("team_id"));
    }

    // Track (After setGroup)

    @Test
    public void testTrackAfterSetGroup() throws JSONException {
        identify();
        mMixpanel.setGroup("team_id", TEAM_ID);
        clearSent();
        mMixpanel.track("HG Event!", new JSONObject().put("source", "hierarchical_groups_page"));

        JSONObject props = onlyEvent("HG Event!");
        assertIdentified(props);
        // A single value, not a list: hierarchical groups would read ["product_team"] as one group
        assertEquals(TEAM_ID, props.get("team_id"));
        assertEquals("hierarchical_groups_page", props.getString("source"));
    }

    // Remove User's Custom Group (removeGroup)

    @Test
    public void testRemoveUsersCustomGroup() throws JSONException {
        identify();
        mMixpanel.setGroup("team_id", TEAM_ID);
        clearSent();
        mMixpanel.removeGroup("team_id", TEAM_ID);

        JSONArray unset = (JSONArray) onlyUserUpdate("$unset");
        assertEquals("[\"team_id\"]", unset.toString());
        assertFalse(mMixpanel.getSuperProperties().has("team_id"));
    }

    // Set Custom Group Property

    @Test
    public void testSetCustomGroupProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).set("tier", "enterprise");

        JSONObject set = (JSONObject) onlyGroupsUpdate("team_id", TEAM_ID, "$set");
        assertEquals(Collections.singleton("tier"), keys(set));
        assertEquals("enterprise", set.getString("tier"));
    }

    // Set Custom Group Properties

    @Test
    public void testSetCustomGroupProperties() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).setMap(mapOf("size", 12, "region", "us"));

        JSONObject set = (JSONObject) onlyGroupsUpdate("team_id", TEAM_ID, "$set");
        assertEquals(12, set.getInt("size"));
        assertEquals("us", set.getString("region"));
    }

    // Set Once Custom Group Property

    @Test
    public void testSetOnceCustomGroupProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).setOnce("created", "2026-10-27");

        JSONObject setOnce = (JSONObject) onlyGroupsUpdate("team_id", TEAM_ID, "$set_once");
        assertEquals("2026-10-27", setOnce.getString("created"));
    }

    // Union Custom Group Property

    @Test
    public void testUnionCustomGroupProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).union("tags", new JSONArray().put("android"));

        JSONObject union = (JSONObject) onlyGroupsUpdate("team_id", TEAM_ID, "$union");
        assertEquals("[\"android\"]", union.getJSONArray("tags").toString());
    }

    // Remove Custom Group Property

    @Test
    public void testRemoveCustomGroupProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).remove("tags", "android");

        JSONObject remove = (JSONObject) onlyGroupsUpdate("team_id", TEAM_ID, "$remove");
        assertEquals("android", remove.getString("tags"));
    }

    // Unset Custom Group Property

    @Test
    public void testUnsetCustomGroupProperty() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).unset("tier");

        JSONArray unset = (JSONArray) onlyGroupsUpdate("team_id", TEAM_ID, "$unset");
        assertEquals("[\"tier\"]", unset.toString());
    }

    // Delete Company

    @Test
    public void testDeleteCompany() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getCompany().deleteGroup();

        assertEquals(JSONObject.NULL, onlyGroupsUpdate("$company_id", COMPANY_ID, "$delete"));
    }

    // Delete Custom Group

    @Test
    public void testDeleteCustomGroup() throws JSONException {
        identify();
        clearSent();
        mMixpanel.getGroup("team_id", TEAM_ID).deleteGroup();

        assertEquals(JSONObject.NULL, onlyGroupsUpdate("team_id", TEAM_ID, "$delete"));
    }

    // Reset

    @Test
    public void testReset() {
        identify();
        mMixpanel.setGroup("team_id", TEAM_ID);
        String deviceId = mMixpanel.getAnonymousId();
        clearSent();
        mMixpanel.reset();

        assertNothingSent();
        MixpanelIdentity identity = mMixpanel.getIdentity();
        assertNull(identity.getUserId());
        assertNull(identity.getCompanyId());
        assertNotEquals(deviceId, identity.getDeviceId());
        assertEquals(0, mMixpanel.getSuperProperties().length());
        assertFalse(mMixpanel.hasOptedOutTracking());
    }

    // Track (After Reset)

    @Test
    public void testTrackAfterReset() throws JSONException {
        identify();
        mMixpanel.setGroup("team_id", TEAM_ID);
        mMixpanel.reset();
        clearSent();
        mMixpanel.track("HG Event!", new JSONObject().put("source", "hierarchical_groups_page"));

        JSONObject props = onlyEvent("HG Event!");
        assertAnonymous(props);
        assertFalse(props.has("team_id"));
    }

    // Opt Out (Identified)

    @Test
    public void testOptOutIdentified() throws JSONException {
        identify();
        String deviceId = mMixpanel.getAnonymousId();
        clearSent();
        mMixpanel.optOutTracking();

        // The user's profile is deleted; there is no clearCharges $unset of $transactions
        assertEquals(JSONObject.NULL, onlyUserUpdate("$delete"));
        assertTrue(mMixpanel.hasOptedOutTracking());
        MixpanelIdentity identity = mMixpanel.getIdentity();
        assertNull(identity.getUserId());
        assertNull(identity.getCompanyId());
        assertNotEquals(deviceId, identity.getDeviceId());
    }

    // Opt Out (Anonymous)

    @Test
    public void testOptOutAnonymous() {
        String deviceId = mMixpanel.getAnonymousId();
        mMixpanel.optOutTracking();

        assertNothingSent();
        assertTrue(mMixpanel.hasOptedOutTracking());
        assertNotEquals(deviceId, mMixpanel.getAnonymousId());
    }

    // Track (Opted Out)

    @Test
    public void testTrackOptedOut() throws JSONException {
        mMixpanel.optOutTracking();
        clearSent();
        mMixpanel.track("HG Event!", new JSONObject().put("source", "hierarchical_groups_page"));

        assertNothingSent();
    }

    // Opt In (Anonymous)

    @Test
    public void testOptInAnonymous() throws JSONException {
        mMixpanel.optOutTracking();
        clearSent();
        mMixpanel.optInTracking();

        assertAnonymous(onlyEvent("$opt_in"));
        assertFalse(mMixpanel.hasOptedOutTracking());
    }

    // Opt In With Identity

    @Test
    public void testOptInWithIdentity() throws JSONException {
        mMixpanel.optOutTracking();
        clearSent();
        mMixpanel.optInTrackingWithIdentity(new MixpanelIdentity(USER_ID, COMPANY_ID));

        // $identify and $opt_in, in that order, both carrying the user and company
        assertEquals(2, mSent.size());
        assertEquals(TRACK, mSent.get(0).endpoint);
        assertEquals("$identify", mSent.get(0).eventName);
        assertIdentified(mSent.get(0).body);
        assertEquals(TRACK, mSent.get(1).endpoint);
        assertEquals("$opt_in", mSent.get(1).eventName);
        assertIdentified(mSent.get(1).body);
        assertFalse(mMixpanel.hasOptedOutTracking());
    }

    // Identify Without Company (Unsupported)

    @Test
    public void testIdentifyWithoutCompanyIsUnsupported() {
        MixpanelIdentity before = mMixpanel.getIdentity();
        mMixpanel.identify(new MixpanelIdentity(USER_ID));

        assertNothingSent();
        assertEquals(before, mMixpanel.getIdentity());
    }

    // Identify With Distinct ID (Unsupported)

    @Test
    public void testIdentifyWithDistinctIdIsUnsupported() {
        MixpanelIdentity before = mMixpanel.getIdentity();
        mMixpanel.identify(USER_ID);

        assertNothingSent();
        assertEquals(before, mMixpanel.getIdentity());
    }

    // Alias (Unsupported)

    @Test
    public void testAliasIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.alias("new_alias", null);

        assertNothingSent();
        assertEquals(USER_ID, mMixpanel.getIdentity().getUserId());
    }

    // Opt In With Distinct ID (Unsupported)

    @Test
    public void testOptInWithDistinctIdIsUnsupported() {
        mMixpanel.optOutTracking();
        clearSent();
        mMixpanel.optInTracking(USER_ID);

        assertNothingSent();
        assertTrue(mMixpanel.hasOptedOutTracking());
    }

    // Set User's Custom Groups With a List (Unsupported)

    @Test
    public void testSetUsersCustomGroupsWithAListIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.setGroup("team_id", Collections.<Object>singletonList(TEAM_ID));

        assertNothingSent();
        assertFalse(mMixpanel.getSuperProperties().has("team_id"));
    }

    // Add User's Custom Group (Unsupported)

    @Test
    public void testAddUsersCustomGroupIsUnsupported() {
        identify();
        clearSent();
        mMixpanel.addGroup("team_id", TEAM_ID);

        assertNothingSent();
        assertFalse(mMixpanel.getSuperProperties().has("team_id"));
    }

    // Set User Property Before Identify (Unsupported)

    @Test
    public void testSetUserPropertyBeforeIdentifyIsUnsupported() {
        mMixpanel.getPeople().set("plan", "enterprise");

        assertNothingSent();
    }

    // Set Company Property Before Identify (Unsupported)

    @Test
    public void testSetCompanyPropertyBeforeIdentifyIsUnsupported() {
        assertNull(mMixpanel.getCompany());

        assertNothingSent();
    }

    // Set Custom Group Property Before Identify (Unsupported)

    @Test
    public void testSetCustomGroupPropertyBeforeIdentifyIsUnsupported() {
        mMixpanel.getGroup("team_id", TEAM_ID).set("tier", "enterprise");

        assertNothingSent();
    }
}
