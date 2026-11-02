package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.mixpanel.android.identity.MixpanelIdentity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Hierarchical groups is fixed by {@link MixpanelOptions.Builder#hierarchicalGroups(boolean)}.
 * These tests cover what changes when it is on, and that nothing changes when it is off.
 */
@RunWith(RobolectricTestRunner.class)
public class HierarchicalGroupsTest {

    private Context mContext;
    private List<AnalyticsMessages.EventDescription> mEvents;
    private List<AnalyticsMessages.PeopleDescription> mPeople;
    private List<AnalyticsMessages.GroupDescription> mGroups;
    private AnalyticsMessages mCapturing;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mEvents = Collections.synchronizedList(new ArrayList<AnalyticsMessages.EventDescription>());
        mPeople = Collections.synchronizedList(new ArrayList<AnalyticsMessages.PeopleDescription>());
        mGroups = Collections.synchronizedList(new ArrayList<AnalyticsMessages.GroupDescription>());
        mCapturing = new AnalyticsMessages(mContext, MPConfig.getInstance(mContext, null)) {
            @Override
            public void eventsMessage(EventDescription description) {
                if (!description.isAutomatic()) {
                    mEvents.add(description);
                }
            }

            @Override
            public void peopleMessage(PeopleDescription description) {
                mPeople.add(description);
            }

            @Override
            public void groupMessage(GroupDescription description) {
                mGroups.add(description);
            }
        };
    }

    private MixpanelAPI newApi(boolean hierarchicalGroups) {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .hierarchicalGroups(hierarchicalGroups)
                .build();
        return new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }
        };
    }

    private JSONObject lastEventProperties(String eventName) {
        for (int i = mEvents.size() - 1; i >= 0; i--) {
            if (mEvents.get(i).getEventName().equals(eventName)) {
                return mEvents.get(i).getProperties();
            }
        }
        throw new AssertionError("No " + eventName + " event was tracked");
    }

    private int countEvents(String eventName) {
        int count = 0;
        for (AnalyticsMessages.EventDescription event : mEvents) {
            if (event.getEventName().equals(eventName)) {
                count++;
            }
        }
        return count;
    }

    // Events

    @Test
    public void testEventsCarryUserCompanyAndDevice() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.track("Viewed Report");

        JSONObject props = lastEventProperties("Viewed Report");
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("acme", props.getString("$company_id"));
        assertEquals(mixpanel.getAnonymousId(), props.getString("$device_id"));
        assertEquals("acme", mixpanel.getIdentity().getCompanyId());
        assertEquals("$company:acme|$user:user_123", mixpanel.getDistinctId());
    }

    @Test
    public void testIdentifyEventCarriesTheNewCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));

        JSONObject props = lastEventProperties("$identify");
        assertEquals("acme", props.getString("$company_id"));
        assertEquals("user_123", props.getString("$user_id"));
    }

    @Test
    public void testAnonymousEventsCarryNoCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.track("Opened");

        JSONObject props = lastEventProperties("Opened");
        assertFalse(props.has("$company_id"));
        assertFalse(props.has("$user_id"));
        assertTrue(props.has("$device_id"));
    }

    @Test
    public void testCompanyIdOverridesSuperProperty() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.registerSuperProperties(new JSONObject().put("$company_id", "stale"));
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.track("Viewed Report");

        assertEquals("acme", lastEventProperties("Viewed Report").getString("$company_id"));
    }

    @Test
    public void testCompanyChangeIsAnIdentityChange() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        assertEquals(1, countEvents("$identify"));

        mixpanel.identify(new MixpanelIdentity("user_123", "globex"));
        assertEquals(2, countEvents("$identify"));

        mixpanel.track("Viewed Report");
        assertEquals("globex", lastEventProperties("Viewed Report").getString("$company_id"));
    }

    @Test
    public void testIdentifyWithDistinctIdDoesNothing() {
        MixpanelAPI mixpanel = newApi(true);
        String anonymous = mixpanel.getDistinctId();
        mixpanel.identify("user_123");

        assertEquals(anonymous, mixpanel.getDistinctId());
        assertNull(mixpanel.getIdentity().getUserId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testIdentifyWithDistinctIdKeepsCurrentIdentity() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.identify("user_456");

        assertEquals("user_123", mixpanel.getIdentity().getUserId());
        assertEquals("acme", mixpanel.getIdentity().getCompanyId());
        mixpanel.track("Viewed Report");
        assertEquals("acme", lastEventProperties("Viewed Report").getString("$company_id"));
        assertEquals(1, countEvents("$identify"));
    }

    @Test
    public void testIdentifyWithEmptyCompanyDoesNothing() {
        MixpanelAPI mixpanel = newApi(true);
        String anonymous = mixpanel.getDistinctId();
        mixpanel.identify(new MixpanelIdentity("user_123", ""));

        assertEquals(anonymous, mixpanel.getDistinctId());
        assertNull(mixpanel.getIdentity().getCompanyId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testIdentityWithoutCompanyDoesNothing() {
        MixpanelAPI mixpanel = newApi(true);
        String anonymous = mixpanel.getDistinctId();
        mixpanel.identify(new MixpanelIdentity("user_123"));

        assertEquals(anonymous, mixpanel.getDistinctId());
        assertNull(mixpanel.getIdentity().getUserId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testIdentityWithoutCompanyKeepsCurrentIdentity() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.identify(new MixpanelIdentity("user_456"));

        assertEquals("user_123", mixpanel.getIdentity().getUserId());
        assertEquals("acme", mixpanel.getIdentity().getCompanyId());
        assertEquals(1, countEvents("$identify"));
    }

    @Test
    public void testInvalidIdentitiesDoNothing() {
        MixpanelAPI mixpanel = newApi(true);
        String anonymous = mixpanel.getDistinctId();
        mixpanel.identify((MixpanelIdentity) null);
        mixpanel.identify(new MixpanelIdentity(null, "acme"));
        mixpanel.identify(new MixpanelIdentity("", "acme"));

        assertEquals(anonymous, mixpanel.getDistinctId());
        assertNull(mixpanel.getIdentity().getCompanyId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testIdentityDeviceIdIsIgnored() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        String deviceId = mixpanel.getAnonymousId();
        mixpanel.identify(new MixpanelIdentity("user_123", "acme", "some_other_device"));

        assertEquals(deviceId, mixpanel.getAnonymousId());
        assertEquals("user_123", mixpanel.getIdentity().getUserId());
        mixpanel.track("Viewed Report");
        assertEquals(deviceId, lastEventProperties("Viewed Report").getString("$device_id"));
    }

    @Test
    public void testGetIdentityReportsUserCompanyAndDevice() {
        MixpanelAPI mixpanel = newApi(true);
        assertEquals(new MixpanelIdentity(null, null, mixpanel.getAnonymousId()), mixpanel.getIdentity());

        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        assertEquals(new MixpanelIdentity("user_123", "acme", mixpanel.getAnonymousId()), mixpanel.getIdentity());

        // What analytics reports, it accepts back unchanged
        mixpanel.identify(mixpanel.getIdentity());
        assertEquals(1, countEvents("$identify"));
    }

    @Test
    public void testGetIdentityWhenDisabledHasNoCompany() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.identify("user_123");
        assertEquals(new MixpanelIdentity("user_123", null, mixpanel.getAnonymousId()), mixpanel.getIdentity());
    }

    // getDistinctId() reports the id Mixpanel resolves the user to

    @Test
    public void testDistinctIdIsDeviceBeforeIdentify() {
        MixpanelAPI mixpanel = newApi(true);
        assertEquals("$device:" + mixpanel.getAnonymousId(), mixpanel.getDistinctId());
    }

    @Test
    public void testDistinctIdCombinesCompanyAndUser() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        assertEquals("$company:acme|$user:user_123", mixpanel.getDistinctId());

        // Events still send the stored id; ingestion resolves the user from the identity keys
        mixpanel.track("Viewed Report");
        assertEquals("user_123", lastEventProperties("Viewed Report").getString("distinct_id"));

        mixpanel.identify(new MixpanelIdentity("user_123", "globex"));
        assertEquals("$company:globex|$user:user_123", mixpanel.getDistinctId());
    }

    @Test
    public void testDistinctIdStaysOnDeviceUntilCompanyIsKnown() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify("user_123");
        assertEquals("$device:" + mixpanel.getAnonymousId(), mixpanel.getDistinctId());
    }

    @Test
    public void testDistinctIdUnchangedWhenDisabled() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.identify("user_123");
        assertEquals("user_123", mixpanel.getDistinctId());
    }

    @Test
    public void testResetReturnsToDeviceDistinctId() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.reset();
        assertEquals("$device:" + mixpanel.getAnonymousId(), mixpanel.getDistinctId());
    }

    @Test
    public void testResetClearsCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.reset();

        assertNull(mixpanel.getIdentity().getCompanyId());
        mixpanel.track("Opened");
        assertFalse(lastEventProperties("Opened").has("$company_id"));
    }

    @Test
    public void testAliasIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.alias("new_id", null);

        assertEquals(0, countEvents("$create_alias"));
    }

    @Test
    public void testOptInWithIdentityCarriesCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.optOutTracking();
        mixpanel.optInTrackingWithIdentity(new MixpanelIdentity("user_123", "acme"));

        JSONObject props = lastEventProperties("$opt_in");
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("acme", props.getString("$company_id"));
        assertEquals("acme", mixpanel.getIdentity().getCompanyId());
    }

    @Test
    public void testOptInWithDistinctIdIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.optOutTracking();
        mixpanel.optInTracking("user_123");
        mixpanel.optInTracking("user_123", null);

        assertTrue(mixpanel.hasOptedOutTracking());
        assertEquals(0, countEvents("$opt_in"));
    }

    @Test
    public void testOptInWithoutArgumentsStillWorks() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.optOutTracking();
        mixpanel.optInTracking();

        assertFalse(mixpanel.hasOptedOutTracking());
        assertEquals(1, countEvents("$opt_in"));
    }

    @Test
    public void testOptInWithoutIdentityStaysAnonymous() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.optOutTracking();
        mixpanel.optInTrackingWithIdentity(null, new JSONObject().put("source", "settings"));

        JSONObject props = lastEventProperties("$opt_in");
        assertEquals("settings", props.getString("source"));
        assertFalse(props.has("$company_id"));
        assertFalse(props.has("$user_id"));
    }

    // Disabled: behaves exactly as before

    @Test
    public void testIdentifyWithCompanyIsIgnoredWhenDisabled() {
        MixpanelAPI mixpanel = newApi(false);
        String anonymous = mixpanel.getDistinctId();
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));

        assertEquals(anonymous, mixpanel.getDistinctId());
        assertNull(mixpanel.getIdentity().getCompanyId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testDisabledSendsPeopleToEngage() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.identify("user_123");
        mixpanel.getPeople().set("plan", "pro");
        mixpanel.getPeople().increment("logins", 1);
        mixpanel.track("Viewed Report");

        assertEquals(2, mPeople.size());
        assertEquals(0, mGroups.size());
        assertEquals("user_123", mPeople.get(0).getMessage().getString("$distinct_id"));
        assertFalse(lastEventProperties("Viewed Report").has("$company_id"));
    }

    // User profiles

    @Test
    public void testPeopleUpdatesBecomeUserIdGroupProfiles() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        MixpanelAPI.People people = mixpanel.getPeople();
        people.set("plan", "pro");
        people.setOnce("first_seen", "today");
        people.union("roles", new JSONArray().put("admin"));
        people.remove("roles", "viewer");
        people.unset("legacy");
        people.deleteUser();

        assertEquals(0, mPeople.size());
        assertEquals(6, mGroups.size());
        String[] operations = {"$set", "$set_once", "$union", "$remove", "$unset", "$delete"};
        for (int i = 0; i < operations.length; i++) {
            JSONObject message = mGroups.get(i).getMessage();
            assertTrue(operations[i], message.has(operations[i]));
            assertEquals("$user_id", message.getString("$group_key"));
            assertEquals("user_123", message.getString("$group_id"));
            assertEquals("acme", message.getString("$company_id"));
            assertFalse(message.has("$distinct_id"));
        }
        assertEquals("pro", mGroups.get(0).getMessage().getJSONObject("$set").getString("plan"));
    }

    @Test
    public void testUnsupportedPeopleOperationsAreDropped() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        MixpanelAPI.People people = mixpanel.getPeople();
        people.increment("logins", 1);
        people.append("history", "a");
        people.merge("settings", new JSONObject().put("theme", "dark"));
        people.trackCharge(9.99, null);
        people.clearCharges();

        assertEquals(0, mPeople.size());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testOptOutDeletesUserWithoutClearingCharges() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.optOutTracking();

        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertTrue(message.has("$delete"));
        assertEquals("$user_id", message.getString("$group_key"));
        assertEquals("acme", message.getString("$company_id"));
    }

    @Test
    public void testPeopleUpdatesBeforeIdentifyAreDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.getPeople().set("plan", "pro");

        assertEquals(0, mPeople.size());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testPeopleUpdatesWithoutCompanyAreDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify("user_123");
        mixpanel.getPeople().set("plan", "pro");

        assertEquals(0, mPeople.size());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testSetGroupWritesUserProfileThroughGroups() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");

        assertEquals(0, mPeople.size());
        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertEquals("$user_id", message.getString("$group_key"));
        assertEquals("finance", message.getJSONObject("$set").get("team"));
    }

    // Custom group membership: one group per key, stored as a single value

    @Test
    public void testSetGroupSendsSingleValueOnEvents() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");
        mixpanel.track("Viewed Report");

        assertEquals("finance", lastEventProperties("Viewed Report").get("team"));
    }

    @Test
    public void testSetGroupWithListIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", Collections.<Object>singletonList("finance"));
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testSetGroupWithArrayValueIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", (Object) new JSONArray().put("finance"));
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testSetGroupWithSeveralGroupsIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        List<Object> teams = new ArrayList<>();
        teams.add("finance");
        teams.add("sales");
        mixpanel.setGroup("team", teams);
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testAddGroupIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.addGroup("team", "finance");
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testAddGroupToSecondGroupIsDropped() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");
        mixpanel.addGroup("team", "sales");
        mixpanel.track("Viewed Report");

        assertEquals("finance", lastEventProperties("Viewed Report").get("team"));
        assertEquals(1, mGroups.size());
    }

    @Test
    public void testRemoveGroupClearsTheGroup() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");
        mixpanel.removeGroup("team", "finance");
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
        assertEquals(2, mGroups.size());
        assertEquals("team", mGroups.get(1).getMessage().getJSONArray("$unset").getString(0));
    }

    @Test
    public void testRemoveOtherGroupDoesNothing() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");
        mixpanel.removeGroup("team", "sales");
        mixpanel.track("Viewed Report");

        assertEquals("finance", lastEventProperties("Viewed Report").get("team"));
        assertEquals(1, mGroups.size());
    }

    @Test
    public void testRemoveGroupClearsListStoredBeforeHierarchicalGroups() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.registerSuperProperties(new JSONObject().put("team", new JSONArray().put("finance")));
        mixpanel.removeGroup("team", "finance");
        mixpanel.track("Viewed Report");

        assertFalse(lastEventProperties("Viewed Report").has("team"));
    }

    @Test
    public void testDisabledGroupMembershipStaysAList() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.setGroup("team", "finance");
        mixpanel.addGroup("team", "sales");
        mixpanel.track("Viewed Report");

        JSONArray teams = lastEventProperties("Viewed Report").getJSONArray("team");
        assertEquals(2, teams.length());
        assertEquals("finance", teams.getString(0));
        assertEquals("sales", teams.getString(1));
    }

    // Group profiles

    @Test
    public void testGroupUpdatesCarryCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.getGroup("$company_id", "acme").set("tier", "enterprise");
        mixpanel.getGroup("team", "finance").set("size", 12);

        assertEquals(2, mGroups.size());
        JSONObject company = mGroups.get(0).getMessage();
        assertEquals("$company_id", company.getString("$group_key"));
        assertEquals("acme", company.getString("$group_id"));
        assertEquals("acme", company.getString("$company_id"));
        assertFalse(company.getJSONObject("$set").has("$company_id"));

        JSONObject team = mGroups.get(1).getMessage();
        assertEquals("team", team.getString("$group_key"));
        assertEquals("acme", team.getString("$company_id"));
    }

    @Test
    public void testGetCompanyUpdatesIdentifiedCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.getCompany().set("tier", "enterprise");

        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertEquals("$company_id", message.getString("$group_key"));
        assertEquals("acme", message.getString("$group_id"));
        assertEquals("acme", message.getString("$company_id"));
        assertEquals("enterprise", message.getJSONObject("$set").getString("tier"));
        assertSame(mixpanel.getGroup("$company_id", "acme"), mixpanel.getCompany());
    }

    @Test
    public void testGetCompanyBeforeIdentifyIsNull() {
        MixpanelAPI mixpanel = newApi(true);

        assertNull(mixpanel.getCompany());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testGetCompanyAfterResetIsNull() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.reset();

        assertNull(mixpanel.getCompany());
    }

    @Test
    public void testGetCompanyWhenDisabledIsNull() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));

        assertNull(mixpanel.getCompany());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testGroupUpdatesWithoutCompanyAreDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.getGroup("team", "finance").set("size", 12);

        assertEquals(0, mGroups.size());
    }

    @Test
    public void testDisabledGroupUpdatesCarryNoCompany() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.getGroup("team", "finance").set("size", 12);

        assertEquals(1, mGroups.size());
        assertFalse(mGroups.get(0).getMessage().has("$company_id"));
    }

    @Test
    public void testIdentityWithoutCompanyWorksWhenDisabled() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.identify(new MixpanelIdentity("user_123"));

        assertEquals("user_123", mixpanel.getDistinctId());
        assertEquals(1, countEvents("$identify"));
    }

    @Test
    public void testIdentityJavaConstructors() {
        // @JvmOverloads gives Java callers each trailing value as optional
        assertNull(new MixpanelIdentity("user_123").getCompanyId());
        assertNull(new MixpanelIdentity("user_123", "acme").getDeviceId());
        assertEquals("device_abc", new MixpanelIdentity("user_123", "acme", "device_abc").getDeviceId());
    }

    @Test
    public void testCompanyIdIsReserved() {
        assertTrue(MixpanelOptions.RESERVED_PROPERTY_KEYS.contains("$company_id"));
    }

    @Test
    public void testOptionDefaultsToDisabled() {
        assertFalse(new MixpanelOptions.Builder().build().isHierarchicalGroups());
        assertTrue(new MixpanelOptions.Builder().hierarchicalGroups(true).build().isHierarchicalGroups());
    }
}
