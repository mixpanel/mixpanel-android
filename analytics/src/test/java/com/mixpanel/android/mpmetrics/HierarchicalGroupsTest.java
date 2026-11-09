package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import com.mixpanel.android.identity.MixpanelIdentity;
import com.mixpanel.android.util.Base64Coder;
import com.mixpanel.android.util.OfflineMode;
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.shadows.ShadowLooper;

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
    private List<AnalyticsMessages.PushAnonymousPeopleDescription> mPushes;
    private List<String> mClears;
    private AnalyticsMessages mCapturing;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mEvents = Collections.synchronizedList(new ArrayList<AnalyticsMessages.EventDescription>());
        mPeople = Collections.synchronizedList(new ArrayList<AnalyticsMessages.PeopleDescription>());
        mGroups = Collections.synchronizedList(new ArrayList<AnalyticsMessages.GroupDescription>());
        mPushes = Collections.synchronizedList(new ArrayList<AnalyticsMessages.PushAnonymousPeopleDescription>());
        mClears = Collections.synchronizedList(new ArrayList<String>());
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

            @Override
            public void pushAnonymousPeopleMessage(PushAnonymousPeopleDescription description) {
                mPushes.add(description);
            }

            @Override
            public void clearAnonymousUpdatesMessage(MixpanelDescription description) {
                mClears.add(description.getToken());
            }
        };
    }

    private MixpanelAPI newApi(boolean hierarchicalGroups) {
        return newApi(hierarchicalGroups, null);
    }

    private MixpanelAPI newApi(boolean hierarchicalGroups, Set<String> excludeProperties) {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .hierarchicalGroups(hierarchicalGroups)
                .excludeProperties(excludeProperties)
                .build();
        return new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }
        };
    }

    private AnalyticsMessages.EventDescription lastEvent(String eventName) {
        for (int i = mEvents.size() - 1; i >= 0; i--) {
            if (mEvents.get(i).getEventName().equals(eventName)) {
                return mEvents.get(i);
            }
        }
        throw new AssertionError("No " + eventName + " event was tracked");
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
    public void testIdentifyTracksNoIdentifyEvent() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        assertEquals(0, countEvents("$identify"));

        // The next event carries the identity keys that tie the device to the user
        mixpanel.track("Viewed Report");
        JSONObject props = lastEventProperties("Viewed Report");
        assertEquals("acme", props.getString("$company_id"));
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals(mixpanel.getAnonymousId(), props.getString("$device_id"));
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
        assertEquals("$company:acme|$user:user_123", mixpanel.getDistinctId());

        mixpanel.identify(new MixpanelIdentity("user_123", "globex"));
        assertEquals("$company:globex|$user:user_123", mixpanel.getDistinctId());

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
        assertEquals(new MixpanelIdentity("user_123", "acme", mixpanel.getAnonymousId()), mixpanel.getIdentity());
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

        // Events send no distinct_id; ingestion resolves the user from the identity keys
        mixpanel.track("Viewed Report");
        assertFalse(lastEventProperties("Viewed Report").has("distinct_id"));

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
    public void testOptInWithIdentityWithoutCompanyIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.optOutTracking();
        mixpanel.optInTrackingWithIdentity(new MixpanelIdentity("user_123"));

        assertTrue(mixpanel.hasOptedOutTracking());
        assertEquals(0, countEvents("$opt_in"));
    }

    @Test
    public void testOptInWithCompanyWhenDisabledIsDropped() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.optOutTracking();
        mixpanel.optInTrackingWithIdentity(new MixpanelIdentity("user_123", "acme"));

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

    // Records calls to the public overridable members that master's reset(), alias() and
    // optInTracking() went through, so an app subclass overriding them still sees those calls.
    private MixpanelAPI newRecordingApi(boolean hierarchicalGroups, final List<String> calls) {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .hierarchicalGroups(hierarchicalGroups)
                .build();
        return new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }

            @Override
            public void identify(String distinctId, boolean usePeople) {
                calls.add("identify(" + distinctId + "," + usePeople + ")");
                super.identify(distinctId, usePeople);
            }

            @Override
            public String getDistinctId() {
                calls.add("getDistinctId");
                return super.getDistinctId();
            }

            @Override
            public void optInTracking(String distinctId, JSONObject properties) {
                calls.add("optInTracking(" + distinctId + "," + properties + ")");
                super.optInTracking(distinctId, properties);
            }
        };
    }

    @Test
    public void testDisabledResetCallsPublicIdentify() {
        List<String> calls = Collections.synchronizedList(new ArrayList<String>());
        MixpanelAPI mixpanel = newRecordingApi(false, calls);
        mixpanel.identify("user_123");
        calls.clear();

        mixpanel.reset();

        String newId = mixpanel.getDistinctId();
        assertTrue(calls.toString(), calls.contains("identify(" + newId + ",false)"));
    }

    @Test
    public void testDisabledAliasWithNullOriginalUsesGetDistinctId() throws JSONException {
        List<String> calls = Collections.synchronizedList(new ArrayList<String>());
        MixpanelAPI mixpanel = newRecordingApi(false, calls);
        mixpanel.identify("user_123");
        calls.clear();

        mixpanel.alias("alias_123", null);

        assertTrue(calls.toString(), calls.contains("getDistinctId"));
        JSONObject props = lastEventProperties("$create_alias");
        assertEquals("alias_123", props.getString("alias"));
        assertEquals("user_123", props.getString("distinct_id"));
    }

    @Test
    public void testDisabledOptInTrackingCallsOptInTrackingWithNulls() {
        List<String> calls = Collections.synchronizedList(new ArrayList<String>());
        MixpanelAPI mixpanel = newRecordingApi(false, calls);
        mixpanel.optOutTracking();
        calls.clear();

        mixpanel.optInTracking();

        assertEquals(Collections.singletonList("optInTracking(null,null)"), calls);
        assertFalse(mixpanel.hasOptedOutTracking());
        assertEquals(1, countEvents("$opt_in"));
    }

    @Test
    public void testEnabledOptInTrackingStillOptsIn() {
        List<String> calls = Collections.synchronizedList(new ArrayList<String>());
        MixpanelAPI mixpanel = newRecordingApi(true, calls);
        mixpanel.optOutTracking();
        calls.clear();

        mixpanel.optInTracking();

        assertFalse(calls.toString(), calls.contains("optInTracking(null,null)"));
        assertFalse(mixpanel.hasOptedOutTracking());
        assertEquals(1, countEvents("$opt_in"));
    }

    // User profiles

    @Test
    public void testWithIdentityDropsUpdates() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        MixpanelAPI.People other = mixpanel.getPeople().withIdentity("user_456");
        other.set("plan", "pro");
        other.union("tags", new JSONArray().put("a"));
        other.deleteUser();

        assertEquals(0, mPeople.size());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testDisabledWithIdentitySendsToEngage() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.getPeople().withIdentity("user_456").set("plan", "pro");

        assertEquals(1, mPeople.size());
        assertEquals("user_456", mPeople.get(0).getMessage().getString("$distinct_id"));
    }

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
    public void testPeopleUpdatesBeforeIdentifyAreQueued() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.getPeople().set("plan", "pro");

        // Queued with the anonymous people updates until identify names the profile
        assertEquals(1, mPeople.size());
        assertTrue(mPeople.get(0).isAnonymous());
        assertEquals("pro", mPeople.get(0).getMessage().getJSONObject("$set").getString("plan"));
        assertEquals(0, mGroups.size());
        assertEquals(0, mPushes.size());
    }

    @Test
    public void testPeopleUpdatesWithoutCompanyAreQueued() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify("user_123");
        mixpanel.getPeople().set("plan", "pro");

        assertEquals(1, mPeople.size());
        assertTrue(mPeople.get(0).isAnonymous());
        assertEquals(0, mGroups.size());
        assertEquals(0, mPushes.size());
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
    public void testTrackWithGroupsSendsSingleValue() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        Map<String, Object> groups = new HashMap<>();
        groups.put("team", "finance");
        mixpanel.trackWithGroups("Viewed Report", new HashMap<String, Object>(), groups);

        assertEquals("finance", lastEventProperties("Viewed Report").get("team"));
    }

    @Test
    public void testTrackWithGroupsWithListIsDropped() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        Map<String, Object> groups = new HashMap<>();
        groups.put("team", Collections.singletonList("finance"));
        mixpanel.trackWithGroups("Viewed Report", new HashMap<String, Object>(), groups);
        groups.put("team", new JSONArray().put("finance"));
        mixpanel.trackWithGroups("Viewed Report", null, groups);
        groups.put("team", new String[] {"finance"});
        mixpanel.trackWithGroups("Viewed Report", new HashMap<String, Object>(), groups);

        assertEquals(0, countEvents("Viewed Report"));
    }

    @Test
    public void testTrackWithGroupsWithListWhenOff() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        Map<String, Object> groups = new HashMap<>();
        List<Object> teams = new ArrayList<>();
        teams.add("finance");
        teams.add("sales");
        groups.put("team", teams);
        mixpanel.trackWithGroups("Viewed Report", new HashMap<String, Object>(), groups);

        JSONArray sent = lastEventProperties("Viewed Report").getJSONArray("team");
        assertEquals(2, sent.length());
        assertEquals("finance", sent.getString(0));
        assertEquals("sales", sent.getString(1));
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
    public void testSavedCompanyKeepsItsCompanyAfterIdentify() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        MixpanelAPI.Group acme = mixpanel.getCompany();
        mixpanel.identify(new MixpanelIdentity("user_123", "globex"));
        acme.set("tier", "enterprise");

        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertEquals("acme", message.getString("$group_id"));
        assertEquals("acme", message.getString("$company_id"));
    }

    @Test
    public void testSavedCompanyStillUpdatesAfterReset() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        MixpanelAPI.Group acme = mixpanel.getCompany();
        mixpanel.reset();
        acme.set("tier", "enterprise");

        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertEquals("acme", message.getString("$group_id"));
        assertEquals("acme", message.getString("$company_id"));
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
    public void testCompanyIdIsNeverExcluded() {
        newApi(true, Collections.singleton("$company_id")).track("Hierarchical Report");
        newApi(false, Collections.singleton("$company_id")).track("Legacy Report");

        assertFalse(lastEvent("Hierarchical Report").getExcludeProperties().contains("$company_id"));
        // without hierarchical groups $company_id is an ordinary property a customer can exclude
        assertTrue(lastEvent("Legacy Report").getExcludeProperties().contains("$company_id"));
        assertFalse(MixpanelOptions.RESERVED_PROPERTY_KEYS.contains("$company_id"));
    }

    @Test
    public void testOptionDefaultsToDisabled() {
        assertFalse(new MixpanelOptions.Builder().build().isHierarchicalGroups());
        assertTrue(new MixpanelOptions.Builder().hierarchicalGroups(true).build().isHierarchicalGroups());
    }

    // Opt-in without a distinct id

    @Test
    public void testOptInTrackingWithNullDistinctIdOptsIn() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.optOutTracking();
        mixpanel.optInTracking(null, new JSONObject().put("source", "settings"));

        assertFalse(mixpanel.hasOptedOutTracking());
        JSONObject props = lastEventProperties("$opt_in");
        assertEquals("settings", props.getString("source"));
        assertFalse(props.has("$user_id"));

        mixpanel.optOutTracking();
        mixpanel.optInTracking((String) null);
        assertFalse(mixpanel.hasOptedOutTracking());
        assertEquals(2, countEvents("$opt_in"));
    }

    // Identity validation

    @Test
    public void testIdentityWithDeviceUserIdIsRefused() {
        MixpanelAPI hierarchical = newApi(true);
        hierarchical.identify(new MixpanelIdentity("$device:abc", "acme"));
        assertNull(hierarchical.getIdentity().getUserId());
        assertNull(hierarchical.getIdentity().getCompanyId());

        hierarchical.optOutTracking();
        hierarchical.optInTrackingWithIdentity(new MixpanelIdentity("$device:abc", "acme"));
        assertTrue(hierarchical.hasOptedOutTracking());

        MixpanelAPI legacy = newApi(false);
        String anonymous = legacy.getDistinctId();
        legacy.identify(new MixpanelIdentity("$device:abc"));
        assertEquals(anonymous, legacy.getDistinctId());
        assertEquals(0, countEvents("$identify"));

        legacy.optOutTracking();
        legacy.optInTrackingWithIdentity(new MixpanelIdentity("$device:abc"));
        assertTrue(legacy.hasOptedOutTracking());
        assertEquals(0, countEvents("$opt_in"));
    }

    @Test
    public void testWhitespaceIdsAreRefused() {
        MixpanelAPI hierarchical = newApi(true);
        hierarchical.identify(new MixpanelIdentity("  ", "acme"));
        hierarchical.identify(new MixpanelIdentity("user_123", " \t"));
        assertNull(hierarchical.getIdentity().getUserId());
        assertNull(hierarchical.getIdentity().getCompanyId());

        hierarchical.optOutTracking();
        hierarchical.optInTrackingWithIdentity(new MixpanelIdentity("user_123", " "));
        assertTrue(hierarchical.hasOptedOutTracking());

        MixpanelAPI legacy = newApi(false);
        String anonymous = legacy.getDistinctId();
        legacy.identify(new MixpanelIdentity(" "));
        assertEquals(anonymous, legacy.getDistinctId());
        assertEquals(0, countEvents("$identify"));
    }

    @Test
    public void testIdsAreNotTrimmed() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity(" user_123 ", " acme "));

        assertEquals(" user_123 ", mixpanel.getIdentity().getUserId());
        assertEquals(" acme ", mixpanel.getIdentity().getCompanyId());
    }

    // $company_id is reserved for identify on a hierarchical groups project

    // An instance whose stored preferences survive, so a second one on the token reloads them
    private MixpanelAPI newPersistentApi(boolean hierarchicalGroups, String token) {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .hierarchicalGroups(hierarchicalGroups)
                .build();
        return new MixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), token,
                MPConfig.getInstance(mContext, null), options, false) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }

            @Override
            boolean sendAppOpen() {
                return false;
            }
        };
    }

    @Test
    public void testCompanyIdSuperPropertiesAreRefused() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.registerSuperProperties(new JSONObject().put("$company_id", "globex").put("plan", "pro"));
        mixpanel.registerSuperPropertiesOnce(new JSONObject().put("$company_id", "globex").put("plan", "pro"));
        Map<String, Object> props = new HashMap<>();
        props.put("$company_id", "globex");
        props.put("plan", "pro");
        mixpanel.registerSuperPropertiesMap(props);
        mixpanel.registerSuperPropertiesOnceMap(props);

        // The whole call is refused, not only $company_id
        assertEquals(0, mixpanel.getSuperProperties().length());
        mixpanel.track("Viewed Report");
        JSONObject event = lastEventProperties("Viewed Report");
        assertEquals("acme", event.getString("$company_id"));
        assertFalse(event.has("plan"));

        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));
        assertEquals("pro", mixpanel.getSuperProperties().getString("plan"));
    }

    @Test
    public void testUnregisterAndUpdateCannotChangeCompanyId() throws JSONException {
        String token = UUID.randomUUID().toString();
        // Stored before hierarchical groups was enabled
        newPersistentApi(false, token)
                .registerSuperProperties(new JSONObject().put("$company_id", "legacy").put("plan", "pro"));
        MixpanelAPI mixpanel = newPersistentApi(true, token);
        assertEquals("legacy", mixpanel.getSuperProperties().getString("$company_id"));

        mixpanel.unregisterSuperProperty("$company_id");
        assertEquals("legacy", mixpanel.getSuperProperties().getString("$company_id"));

        mixpanel.updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                in.remove("$company_id");
                in.remove("plan");
                return in;
            }
        });
        mixpanel.updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    in.put("$company_id", "globex");
                    in.put("plan", "free");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return in;
            }
        });
        JSONObject stored = mixpanel.getSuperProperties();
        assertEquals("legacy", stored.getString("$company_id"));
        assertEquals("pro", stored.getString("plan"));

        // An update that leaves $company_id alone applies
        mixpanel.updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    in.put("plan", "free");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                return in;
            }
        });
        assertEquals("free", mixpanel.getSuperProperties().getString("plan"));
        mixpanel.unregisterSuperProperty("plan");
        assertFalse(mixpanel.getSuperProperties().has("plan"));
    }

    @Test
    public void testUpdateCannotAddCompanyId() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));
        mixpanel.updateSuperProperties(new SuperPropertyUpdate() {
            public JSONObject update(JSONObject in) {
                try {
                    return new JSONObject().put("$company_id", "globex");
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        JSONObject stored = mixpanel.getSuperProperties();
        assertFalse(stored.has("$company_id"));
        assertEquals("pro", stored.getString("plan"));
    }

    @Test
    public void testGroupCallsRefuseCompanyId() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("$company_id", "globex");
        mixpanel.setGroup("$company_id", (Object) null);
        mixpanel.setGroup("$company_id", Collections.<Object>singletonList("globex"));
        mixpanel.addGroup("$company_id", "globex");
        mixpanel.removeGroup("$company_id", "acme");

        assertEquals(0, mGroups.size());
        assertEquals(0, mPeople.size());
        assertFalse(mixpanel.getSuperProperties().has("$company_id"));
        mixpanel.track("Viewed Report");
        assertEquals("acme", lastEventProperties("Viewed Report").getString("$company_id"));
    }

    @Test
    public void testCompanyIdIsOrdinaryWhenDisabled() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.registerSuperProperties(new JSONObject().put("$company_id", "globex"));
        assertEquals("globex", mixpanel.getSuperProperties().getString("$company_id"));
        mixpanel.unregisterSuperProperty("$company_id");
        assertFalse(mixpanel.getSuperProperties().has("$company_id"));

        mixpanel.setGroup("$company_id", "globex");
        mixpanel.track("Viewed Report");
        JSONArray sent = lastEventProperties("Viewed Report").getJSONArray("$company_id");
        assertEquals("globex", sent.getString(0));
    }

    // Events carry only the identified $company_id

    @Test
    public void testCallerCompanyIdIsReplacedByIdentifiedCompany() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.track("Viewed Report", new JSONObject().put("$company_id", "globex"));
        assertEquals("acme", lastEventProperties("Viewed Report").getString("$company_id"));

        Map<String, Object> groups = new HashMap<>();
        groups.put("$company_id", "globex");
        mixpanel.trackWithGroups("Grouped", new HashMap<String, Object>(), groups);
        assertEquals("acme", lastEventProperties("Grouped").getString("$company_id"));
        mixpanel.trackWithGroups("Grouped Only", null, groups);
        assertEquals("acme", lastEventProperties("Grouped Only").getString("$company_id"));
    }

    @Test
    public void testCallerCompanyIdIsRemovedBeforeIdentify() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.track("Viewed Report", new JSONObject().put("$company_id", "globex").put("plan", "pro"));

        JSONObject props = lastEventProperties("Viewed Report");
        assertFalse(props.has("$company_id"));
        assertEquals("pro", props.getString("plan"));

        Map<String, Object> groups = new HashMap<>();
        groups.put("$company_id", "globex");
        mixpanel.trackWithGroups("Grouped", new HashMap<String, Object>(), groups);
        assertFalse(lastEventProperties("Grouped").has("$company_id"));
    }

    @Test
    public void testCallerCompanyIdIsSentWhenDisabled() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.track("Viewed Report", new JSONObject().put("$company_id", "globex"));

        assertEquals("globex", lastEventProperties("Viewed Report").getString("$company_id"));
    }

    @Test
    public void testExcludingCompanyIdWarns() {
        ShadowLog.reset();
        newApi(true, Collections.singleton("$company_id"));
        newApi(false, Collections.singleton("$company_id"));
        newApi(true, Collections.singleton("plan"));

        int warnings = 0;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            if (item.msg != null && item.msg.contains("excludeProperties contains $company_id")) {
                warnings++;
            }
        }
        assertEquals(1, warnings);
    }

    // A null group

    @Test
    public void testSetGroupNullSetsNull() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", (Object) null);

        assertTrue(mixpanel.getSuperProperties().has("team"));
        assertTrue(mixpanel.getSuperProperties().isNull("team"));
        assertEquals(1, mGroups.size());
        JSONObject message = mGroups.get(0).getMessage();
        assertEquals("$user_id", message.getString("$group_key"));
        assertEquals("user_123", message.getString("$group_id"));
        assertSame(JSONObject.NULL, message.getJSONObject("$set").get("team"));

        mixpanel.track("Viewed Report");
        JSONObject props = lastEventProperties("Viewed Report");
        assertTrue(props.has("team"));
        assertTrue(props.isNull("team"));

        mixpanel.removeGroup("team", null);
        assertFalse(mixpanel.getSuperProperties().has("team"));
        assertEquals(2, mGroups.size());
        assertEquals("team", mGroups.get(1).getMessage().getJSONArray("$unset").getString(0));
    }

    @Test
    public void testRemoveNullGroupKeepsASetGroup() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", "finance");
        mixpanel.removeGroup("team", null);
        mixpanel.removeGroup("missing", null);

        assertEquals("finance", mixpanel.getSuperProperties().getString("team"));
        assertEquals(1, mGroups.size());
    }

    @Test
    public void testDisabledSetGroupNullIsAnEmptyList() throws JSONException {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.setGroup("team", (Object) null);

        assertEquals(0, mixpanel.getSuperProperties().getJSONArray("team").length());
    }

    // Removing a numeric group

    @Test
    public void testRemoveGroupMatchesDoubleAfterReload() throws JSONException {
        String token = UUID.randomUUID().toString();
        MixpanelAPI first = newPersistentApi(true, token);
        first.identify(new MixpanelIdentity("user_123", "acme"));
        first.setGroup("team", 5.0);

        // The stored 5.0 reloads from JSON as an Integer
        MixpanelAPI mixpanel = newPersistentApi(true, token);
        Object reloaded = mixpanel.getSuperProperties().get("team");
        assertTrue(reloaded instanceof Number);
        mGroups.clear();
        mixpanel.removeGroup("team", 5.0);

        assertFalse(mixpanel.getSuperProperties().has("team"));
        assertEquals(1, mGroups.size());
        assertEquals("team", mGroups.get(0).getMessage().getJSONArray("$unset").getString(0));
    }

    @Test
    public void testRemoveGroupMatchesNumbersByValue() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        mixpanel.setGroup("team", 5L);
        mixpanel.removeGroup("team", 6);
        mixpanel.removeGroup("team", 5.5);
        assertTrue(mixpanel.getSuperProperties().has("team"));

        mixpanel.removeGroup("team", 5);
        assertFalse(mixpanel.getSuperProperties().has("team"));

        mixpanel.setGroup("team", 7);
        mixpanel.removeGroup("team", "7");
        assertTrue(mixpanel.getSuperProperties().has("team"));
        mixpanel.removeGroup("team", 7.0);
        assertFalse(mixpanel.getSuperProperties().has("team"));
    }

    // Profile updates before identify are queued and sent to the $user_id profile at identify

    @Test
    public void testQueuedUpdatesArePushedToUserProfileAtIdentify() throws JSONException {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.getPeople().set("plan", "pro");
        mixpanel.setGroup("team", "finance");
        mixpanel.getPeople().increment("logins", 1);

        // The unsupported increment is still dropped
        assertEquals(2, mPeople.size());
        assertTrue(mPeople.get(0).isAnonymous());
        assertTrue(mPeople.get(1).isAnonymous());
        assertEquals("finance", mPeople.get(1).getMessage().getJSONObject("$set").getString("team"));
        assertEquals("finance", mixpanel.getSuperProperties().getString("team"));
        assertEquals(0, mPushes.size());

        mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
        assertEquals(1, mPushes.size());
        assertEquals("user_123", mPushes.get(0).getDistinctId());
        assertEquals("acme", mPushes.get(0).getCompanyId());
        assertEquals(0, mGroups.size());
    }

    @Test
    public void testResetClearsQueuedUpdates() {
        MixpanelAPI mixpanel = newApi(true);
        mixpanel.getPeople().set("plan", "pro");
        mixpanel.reset();

        assertEquals(1, mClears.size());
        assertEquals(mixpanel.getToken(), mClears.get(0));
    }

    @Test
    public void testDisabledIdentifyPushesToPeople() {
        MixpanelAPI mixpanel = newApi(false);
        mixpanel.getPeople().set("plan", "pro");
        mixpanel.identify("user_123");

        assertTrue(mPeople.get(0).isAnonymous());
        assertEquals(1, mPushes.size());
        assertEquals("user_123", mPushes.get(0).getDistinctId());
        assertNull(mPushes.get(0).getCompanyId());
    }

    @Test
    public void testQueuedUpdatesMoveToUserProfileGroup() throws JSONException {
        MPDbAdapter adapter = new MPDbAdapter(mContext, "HierarchicalGroupsQueue", MPConfig.getInstance(mContext, null));
        try {
            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("plan", "pro")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            adapter.addJSON(new JSONObject().put("$unset", new JSONArray().put("team")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("plan", "other")), "OTHER",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);

            adapter.pushAnonymousUpdatesToUserProfileDb("TOKEN", "user_123", "acme");

            assertNull(adapter.generateDataString(MPDbAdapter.Table.PEOPLE, "TOKEN"));
            JSONArray sent = new JSONArray(adapter.generateDataString(MPDbAdapter.Table.GROUPS, "TOKEN")[1]);
            assertEquals(2, sent.length());
            assertEquals("pro", sent.getJSONObject(0).getJSONObject("$set").getString("plan"));
            assertTrue(sent.getJSONObject(1).has("$unset"));
            for (int i = 0; i < sent.length(); i++) {
                JSONObject message = sent.getJSONObject(i);
                assertEquals("$user_id", message.getString("$group_key"));
                assertEquals("user_123", message.getString("$group_id"));
                assertEquals("acme", message.getString("$company_id"));
                assertFalse(message.has("$distinct_id"));
            }
            assertNull(adapter.generateDataString(MPDbAdapter.Table.GROUPS, "OTHER"));
        } finally {
            adapter.deleteDB();
        }
    }

    // A real queue and database, so the updates are checked where /groups sends them from
    private static class QueueDatabase extends MPDbAdapter {
        final java.util.concurrent.Semaphore mWrites = new java.util.concurrent.Semaphore(0);

        QueueDatabase(Context context) {
            super(context, "HierarchicalGroups-" + UUID.randomUUID(), MPConfig.getInstance(context, null));
        }

        @Override
        public int addJSON(JSONObject j, String token, Table table) {
            int result = super.addJSON(j, token, table);
            mWrites.release();
            return result;
        }

        @Override
        int pushAnonymousUpdatesToUserProfileDb(String token, String userId, String companyId) {
            int result = super.pushAnonymousUpdatesToUserProfileDb(token, userId, companyId);
            mWrites.release();
            return result;
        }

        @Override
        public void cleanupAllEvents(Table table, String token) {
            super.cleanupAllEvents(table, token);
            mWrites.release();
        }

        void awaitWrites(int count) throws InterruptedException {
            assertTrue(mWrites.tryAcquire(count, 10, TimeUnit.SECONDS));
        }
    }

    private MixpanelAPI newQueueApi(final QueueDatabase database) {
        return newQueueApi(database, MPConfig.getInstance(mContext, null));
    }

    private MixpanelAPI newQueueApi(final QueueDatabase database, MPConfig config) {
        final AnalyticsMessages messages = new AnalyticsMessages(mContext, config) {
            @Override
            protected RemoteService getPoster() {
                return mFlagsService;
            }

            @Override
            protected MPDbAdapter makeDbAdapter(Context context) {
                return database;
            }
        };
        MixpanelOptions options = new MixpanelOptions.Builder().hierarchicalGroups(true).build();
        return new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return messages;
            }
        };
    }

    @Test
    public void testUpdatesBeforeIdentifyAreSentToUserProfileInOrder() throws Exception {
        QueueDatabase database = new QueueDatabase(mContext);
        try {
            MixpanelAPI mixpanel = newQueueApi(database);
            mixpanel.getPeople().set("plan", "pro");
            database.awaitWrites(1);
            Thread.sleep(5);
            mixpanel.setGroup("team", "finance");
            database.awaitWrites(1);
            Thread.sleep(5); // distinct created_at, which orders the queue
            mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
            database.awaitWrites(1);
            Thread.sleep(5);
            mixpanel.getPeople().set("seen", true);
            database.awaitWrites(1);

            String token = mixpanel.getToken();
            assertNull(database.generateDataString(MPDbAdapter.Table.ANONYMOUS_PEOPLE, token));
            assertNull(database.generateDataString(MPDbAdapter.Table.PEOPLE, token));
            JSONArray sent = new JSONArray(database.generateDataString(MPDbAdapter.Table.GROUPS, token)[1]);
            assertEquals(3, sent.length());
            assertEquals("pro", sent.getJSONObject(0).getJSONObject("$set").getString("plan"));
            assertEquals("finance", sent.getJSONObject(1).getJSONObject("$set").getString("team"));
            assertTrue(sent.getJSONObject(2).getJSONObject("$set").getBoolean("seen"));
            for (int i = 0; i < sent.length(); i++) {
                JSONObject message = sent.getJSONObject(i);
                assertEquals("$user_id", message.getString("$group_key"));
                assertEquals("user_123", message.getString("$group_id"));
                assertEquals("acme", message.getString("$company_id"));
            }
        } finally {
            database.deleteDB();
        }
    }

    @Test
    public void testResetDropsUpdatesQueuedBeforeIdentify() throws Exception {
        QueueDatabase database = new QueueDatabase(mContext);
        try {
            MixpanelAPI mixpanel = newQueueApi(database);
            mixpanel.getPeople().set("plan", "pro");
            database.awaitWrites(1);
            mixpanel.reset();
            database.awaitWrites(1);
            mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
            database.awaitWrites(1);

            String token = mixpanel.getToken();
            assertNull(database.generateDataString(MPDbAdapter.Table.ANONYMOUS_PEOPLE, token));
            assertNull(database.generateDataString(MPDbAdapter.Table.GROUPS, token));
        } finally {
            database.deleteDB();
        }
    }

    @Test
    public void testMovingQueuedUpdatesReportsRowsMoved() throws JSONException {
        MPDbAdapter adapter = new MPDbAdapter(mContext, "HierarchicalGroups-" + UUID.randomUUID(),
                MPConfig.getInstance(mContext, null));
        try {
            assertEquals(0, adapter.pushAnonymousUpdatesToUserProfileDb("TOKEN", "user_123", "acme"));

            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("plan", "pro")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            assertEquals(1, adapter.pushAnonymousUpdatesToUserProfileDb("TOKEN", "user_123", "acme"));

            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("plan", "team")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            adapter.addJSON(new JSONObject().put("$unset", new JSONArray().put("team")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            assertEquals(2, adapter.pushAnonymousUpdatesToUserProfileDb("TOKEN", "user_123", "acme"));
        } finally {
            adapter.deleteDB();
        }
    }

    @Test
    public void testOneUpdateBeforeIdentifySchedulesGroupsFlush() throws Exception {
        QueueDatabase database = new QueueDatabase(mContext);
        Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.FlushInterval", 0);
        try {
            MixpanelAPI mixpanel = newQueueApi(database, new MPConfig(metaData, mContext, null));
            mixpanel.getPeople().set("plan", "pro");
            database.awaitWrites(1);
            mixpanel.identify(new MixpanelIdentity("user_123", "acme"));
            database.awaitWrites(1);

            // Nothing but the move schedules a flush here
            FlagsRequest request = takeRequestContaining("/groups");
            JSONArray sent = new JSONArray(Base64Coder.decodeString((String) request.params.get("data")));
            assertEquals(1, sent.length());
            JSONObject message = sent.getJSONObject(0);
            assertEquals("pro", message.getJSONObject("$set").getString("plan"));
            assertEquals("$user_id", message.getString("$group_key"));
            assertEquals("user_123", message.getString("$group_id"));
            assertEquals("acme", message.getString("$company_id"));
            stopWorker(mixpanel.getAnalyticsMessages());
        } finally {
            database.deleteDB();
        }
    }

    // Stops a worker started for one test, which would otherwise outlive it
    private static void stopWorker(AnalyticsMessages messages) {
        messages.hardKill();
        LooperTestUtils.idleAllLoopers();
    }

    private FlagsRequest takeRequestContaining(String endpointPart) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            LooperTestUtils.idleAllLoopers();
            FlagsRequest request = mFlagsRequests.poll(100, TimeUnit.MILLISECONDS);
            if (request != null && request.endpointUrl.contains(endpointPart)) {
                return request;
            }
        }
        throw new AssertionError("No request to " + endpointPart);
    }

    @Test
    public void testMovedUpdatesDontDeleteUnsentGroupUpdates() throws Exception {
        Bundle metaData = new Bundle();
        metaData.putInt("com.mixpanel.android.MPConfig.FlushBatchSize", 2);
        MPDbAdapter adapter = new MPDbAdapter(mContext, "HierarchicalGroups-" + UUID.randomUUID(),
                new MPConfig(metaData, mContext, null));
        try {
            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("plan", "pro")), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            Thread.sleep(5);
            adapter.addJSON(new JSONObject().put("$set", new JSONObject().put("seats", 3)), "TOKEN",
                    MPDbAdapter.Table.ANONYMOUS_PEOPLE);
            Thread.sleep(5);
            // A company profile update queued after the user's updates but before identify moves them
            adapter.addJSON(new JSONObject()
                            .put("$set", new JSONObject().put("tier", "gold"))
                            .put("$group_key", "$company_id")
                            .put("$group_id", "acme"),
                    "TOKEN", MPDbAdapter.Table.GROUPS);
            Thread.sleep(5);
            adapter.pushAnonymousUpdatesToUserProfileDb("TOKEN", "user_123", "acme");

            // Send in batches of two, deleting what each batch sent as the worker does
            List<JSONObject> sent = new ArrayList<>();
            String[] batch;
            while ((batch = adapter.generateDataString(MPDbAdapter.Table.GROUPS, "TOKEN")) != null) {
                JSONArray messages = new JSONArray(batch[1]);
                for (int i = 0; i < messages.length(); i++) {
                    sent.add(messages.getJSONObject(i));
                }
                adapter.cleanupEvents(batch[0], MPDbAdapter.Table.GROUPS, "TOKEN");
            }

            assertEquals(3, sent.size());
            assertEquals("$company_id", sent.get(0).getString("$group_key"));
            assertEquals("gold", sent.get(0).getJSONObject("$set").getString("tier"));
            // The moved updates keep the order they were queued in
            assertEquals("$user_id", sent.get(1).getString("$group_key"));
            assertEquals("pro", sent.get(1).getJSONObject("$set").getString("plan"));
            assertEquals("$user_id", sent.get(2).getString("$group_key"));
            assertEquals(3, sent.get(2).getJSONObject("$set").getInt("seats"));
        } finally {
            adapter.deleteDB();
        }
    }

    // An identify that runs while an update before it is being queued must queue its move after
    // that update, or the update would wait in the anonymous queue for the next identify.
    @Test
    public void testUpdateRacingIdentifyIsQueuedBeforeTheMove() throws Exception {
        final List<String> queued = Collections.synchronizedList(new ArrayList<String>());
        final MixpanelAPI[] api = new MixpanelAPI[1];
        final Thread[] identifier = new Thread[1];
        final AnalyticsMessages messages = new AnalyticsMessages(mContext, MPConfig.getInstance(mContext, null)) {
            @Override
            public void peopleMessage(PeopleDescription description) {
                if (identifier[0] == null) {
                    // The update has chosen the anonymous queue. Identify on another thread now,
                    // giving it time to finish before the update is queued if nothing stops it.
                    identifier[0] = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            api[0].identify(new MixpanelIdentity("user_123", "acme"));
                        }
                    });
                    identifier[0].start();
                    try {
                        identifier[0].join(500);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                queued.add(description.isAnonymous() ? "anonymous update" : "people update");
            }

            @Override
            public void groupMessage(GroupDescription description) {
                queued.add("user profile update");
            }

            @Override
            public void pushAnonymousPeopleMessage(PushAnonymousPeopleDescription description) {
                queued.add("move");
            }

            @Override
            public void eventsMessage(EventDescription description) {}
        };
        MixpanelOptions options = new MixpanelOptions.Builder().hierarchicalGroups(true).build();
        api[0] = new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return messages;
            }
        };

        api[0].getPeople().set("plan", "pro");
        identifier[0].join(10000);
        assertFalse(identifier[0].isAlive());
        api[0].getPeople().set("seen", true);

        List<String> expected = new ArrayList<>();
        expected.add("anonymous update");
        expected.add("move");
        expected.add("user profile update");
        assertEquals(expected, queued);
        stopWorker(messages);
    }

    // Feature flags: requests send $user_id and $company_id once a company is identified

    private static final String FLAGS_RESPONSE =
            "{\"flags\":{\"checkout\":{\"variant_key\":\"on\",\"variant_value\":true,"
                    + "\"experiment_id\":\"exp_1\",\"is_experiment_active\":true}},"
                    + "\"pending_first_time_events\":[{\"flag_key\":\"upgrade_banner\",\"flag_id\":\"flag_1\","
                    + "\"project_id\":123,\"first_time_event_hash\":\"HASH\",\"event_name\":\"Upgraded\","
                    + "\"pending_variant\":{\"variant_key\":\"shown\",\"variant_value\":true}}]}";

    private static class FlagsRequest {
        final String endpointUrl;
        final Map<String, Object> params;
        final byte[] body;

        FlagsRequest(String endpointUrl, Map<String, Object> params, byte[] body) {
            this.endpointUrl = endpointUrl;
            this.params = params;
            this.body = body;
        }
    }

    private final BlockingQueue<FlagsRequest> mFlagsRequests = new LinkedBlockingQueue<>();

    private final RemoteService mFlagsService = new RemoteService() {
        @Override
        public boolean isOnline(Context context, OfflineMode offlineMode) {
            return true;
        }

        @Override
        public void checkIsServerBlocked() {}

        @Override
        public RequestResult performRequest(
                @NonNull String endpointUrl,
                @Nullable ProxyServerInteractor interactor,
                @Nullable Map<String, Object> params,
                @Nullable Map<String, String> headers,
                @Nullable byte[] requestBodyBytes,
                @Nullable SSLSocketFactory socketFactory) {
            return performRequest(
                    HttpMethod.POST, endpointUrl, interactor, params, headers, requestBodyBytes, socketFactory);
        }

        @Override
        public RequestResult performRequest(
                @NonNull HttpMethod method,
                @NonNull String endpointUrl,
                @Nullable ProxyServerInteractor interactor,
                @Nullable Map<String, Object> params,
                @Nullable Map<String, String> headers,
                @Nullable byte[] requestBodyBytes,
                @Nullable SSLSocketFactory socketFactory) {
            mFlagsRequests.add(new FlagsRequest(endpointUrl, params, requestBodyBytes));
            String response = endpointUrl.endsWith("/first-time-events") ? "{}" : FLAGS_RESPONSE;
            return RequestResult.success(response.getBytes(StandardCharsets.UTF_8), endpointUrl);
        }
    };

    private MixpanelAPI newFlagsApi(boolean hierarchicalGroups) {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .hierarchicalGroups(hierarchicalGroups)
                .featureFlagOptions(new FeatureFlagOptions.Builder().enabled(true).build())
                .build();
        return new TestUtils.CleanMixpanelAPI(
                mContext, new TestUtils.EmptyPreferences(mContext), UUID.randomUUID().toString(), options) {
            @Override
            protected AnalyticsMessages getAnalyticsMessages() {
                return mCapturing;
            }

            @Override
            RemoteService getHttpService() {
                return mFlagsService;
            }
        };
    }

    /** Waits for the next request whose endpoint ends with the given suffix. */
    private FlagsRequest takeFlagsRequest(String endpointSuffix) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            LooperTestUtils.idleAllLoopers();
            FlagsRequest request = mFlagsRequests.poll(100, TimeUnit.MILLISECONDS);
            if (request != null && request.endpointUrl.endsWith(endpointSuffix)) {
                return request;
            }
        }
        throw new AssertionError("No request to " + endpointSuffix);
    }

    private JSONObject fetchFlagsContext(MixpanelAPI mixpanel) throws Exception {
        mixpanel.getFlags().loadFlags();
        FlagsRequest request = takeFlagsRequest("/flags/");
        return new JSONObject((String) request.params.get("context"));
    }

    private void waitForFlagsReady(MixpanelAPI mixpanel) {
        for (int i = 0; i < 50 && !mixpanel.getFlags().areFlagsReady(); i++) {
            LooperTestUtils.idleAllLoopers();
        }
        assertTrue(mixpanel.getFlags().areFlagsReady());
    }

    private static List<String> keys(JSONObject json) {
        List<String> keys = new ArrayList<>();
        for (Iterator<String> it = json.keys(); it.hasNext(); ) {
            keys.add(it.next());
        }
        Collections.sort(keys);
        return keys;
    }

    @Test
    public void testFlagsContextCarriesUserAndCompanyAfterIdentify() throws Exception {
        MixpanelAPI mixpanel = newFlagsApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "company_123"));

        JSONObject context = fetchFlagsContext(mixpanel);
        assertEquals("user_123", context.getString("distinct_id"));
        assertEquals(mixpanel.getAnonymousId(), context.getString("device_id"));
        assertEquals("user_123", context.getString("$user_id"));
        assertEquals("company_123", context.getString("$company_id"));
        assertEquals(4, context.length());
    }

    @Test
    public void testFlagsContextBeforeIdentifyIsUnchanged() throws Exception {
        MixpanelAPI mixpanel = newFlagsApi(true);

        JSONObject context = fetchFlagsContext(mixpanel);
        assertEquals(mixpanel.getDistinctId(), context.getString("distinct_id"));
        assertEquals("$device:" + mixpanel.getAnonymousId(), context.getString("distinct_id"));
        assertEquals(mixpanel.getAnonymousId(), context.getString("device_id"));
        assertEquals(java.util.Arrays.asList("device_id", "distinct_id"), keys(context));
    }

    @Test
    public void testFlagsContextUnchangedWhenDisabled() throws Exception {
        MixpanelAPI mixpanel = newFlagsApi(false);
        mixpanel.identify(new MixpanelIdentity("user_123"));

        JSONObject context = fetchFlagsContext(mixpanel);
        assertEquals("user_123", context.getString("distinct_id"));
        assertEquals(mixpanel.getAnonymousId(), context.getString("device_id"));
        assertEquals(java.util.Arrays.asList("device_id", "distinct_id"), keys(context));
    }

    @Test
    public void testFirstTimeEventBodyCarriesUserAndCompany() throws Exception {
        MixpanelAPI mixpanel = newFlagsApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "company_123"));
        mixpanel.getFlags().loadFlags();
        takeFlagsRequest("/flags/");
        waitForFlagsReady(mixpanel);

        mixpanel.track("Upgraded");
        FlagsRequest request = takeFlagsRequest("/flags/flag_1/first-time-events");
        JSONObject body = new JSONObject(new String(request.body, StandardCharsets.UTF_8));
        assertFalse(body.has("distinct_id"));
        assertEquals(123, body.getLong("project_id"));
        assertEquals("HASH", body.getString("first_time_event_hash"));
        assertEquals("user_123", body.getString("$user_id"));
        assertEquals("company_123", body.getString("$company_id"));
        assertEquals(4, body.length());
    }

    @Test
    public void testExperimentStartedCarriesUserAndCompany() throws Exception {
        MixpanelAPI mixpanel = newFlagsApi(true);
        mixpanel.identify(new MixpanelIdentity("user_123", "company_123"));
        mixpanel.getFlags().loadFlags();
        takeFlagsRequest("/flags/");
        waitForFlagsReady(mixpanel);

        MixpanelFlagVariant variant =
                mixpanel.getFlags().getVariantSync("checkout", new MixpanelFlagVariant("fallback", null));
        assertEquals("on", variant.key);
        JSONObject props = null;
        for (int i = 0; i < 50 && props == null; i++) {
            ShadowLooper.idleMainLooper();
            LooperTestUtils.idleAllLoopers();
            if (countEvents("$experiment_started") > 0) {
                props = lastEventProperties("$experiment_started");
            }
        }
        assertNotNull("$experiment_started was not tracked", props);
        assertEquals("checkout", props.getString("Experiment name"));
        assertFalse(props.has("distinct_id"));
        assertEquals("user_123", props.getString("$user_id"));
        assertEquals("company_123", props.getString("$company_id"));
    }
}
