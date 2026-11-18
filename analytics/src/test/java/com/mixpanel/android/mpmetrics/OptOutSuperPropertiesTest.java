package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * Super properties registered while the user is opted out are kept in memory and applied when the
 * user opts back in, without being written to SharedPreferences in the meantime.
 */
@RunWith(RobolectricTestRunner.class)
public class OptOutSuperPropertiesTest {

    private static final String TOKEN = "Opt Out Super Properties Test Token";
    private static final String STORED_PREFS_NAME =
            "com.mixpanel.android.mpmetrics.MixpanelAPI_" + MPConfig.resolveInstanceKey(null, TOKEN);

    private Context mContext;
    private Future<SharedPreferences> mReferrerPrefs;
    private final List<AnalyticsMessages.EventDescription> mEvents =
            Collections.synchronizedList(new ArrayList<AnalyticsMessages.EventDescription>());
    private AnalyticsMessages mAnalyticsMessages;
    private PersistentIdentity mPersistentIdentity;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mReferrerPrefs = new TestUtils.EmptyPreferences(mContext);
        mAnalyticsMessages =
                new AnalyticsMessages(mContext, MPConfig.getInstance(mContext, null)) {
                    @Override
                    public void eventsMessage(EventDescription eventDescription) {
                        mEvents.add(eventDescription);
                    }
                };
    }

    @After
    public void tearDown() {
        if (mPersistentIdentity != null) {
            mPersistentIdentity.clearPreferences();
            mPersistentIdentity.removeOptOutFlag(TOKEN);
            mPersistentIdentity = null;
        }
    }

    private MixpanelAPI newMixpanel(MixpanelOptions options) {
        return new TestUtils.CleanMixpanelAPI(mContext, mReferrerPrefs, TOKEN, options) {
            @Override
            PersistentIdentity getPersistentIdentity(
                    Context context,
                    Future<SharedPreferences> referrerPreferences,
                    String token,
                    String instanceName,
                    DeviceIdProvider deviceIdProvider) {
                mPersistentIdentity =
                        super.getPersistentIdentity(
                                context, referrerPreferences, token, instanceName, deviceIdProvider);
                return mPersistentIdentity;
            }

            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mAnalyticsMessages;
            }
        };
    }

    private MixpanelAPI newOptedOutMixpanel() {
        MixpanelAPI mixpanel = newMixpanel(new MixpanelOptions.Builder().build());
        mixpanel.optOutTracking();
        assertTrue(mixpanel.hasOptedOutTracking());
        mEvents.clear();
        return mixpanel;
    }

    private String storedSuperProperties() {
        return mContext.getSharedPreferences(STORED_PREFS_NAME, Context.MODE_PRIVATE)
                .getString("super_properties", null);
    }

    private JSONObject lastEventProperties(String eventName) {
        for (int i = mEvents.size() - 1; i >= 0; i--) {
            if (eventName.equals(mEvents.get(i).getEventName())) {
                return mEvents.get(i).getProperties();
            }
        }
        throw new AssertionError("No event named " + eventName);
    }

    @Test
    public void testRegisterWhileOptedOutIsNotPersisted() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        String storedBefore = storedSuperProperties();

        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));
        Map<String, Object> map = new HashMap<>();
        map.put("source", "map");
        mixpanel.registerSuperPropertiesMap(map);
        mixpanel.registerSuperPropertiesOnce(new JSONObject().put("once", 1));
        mixpanel.registerSuperPropertiesOnceMap(map);
        mixpanel.unregisterSuperProperty("plan");

        assertEquals(storedBefore, storedSuperProperties());
        // getSuperProperties() keeps reporting only what is persisted while opted out.
        assertEquals(0, mixpanel.getSuperProperties().length());
    }

    @Test
    public void testOptInAppliesPendingSuperPropertiesToOptInAndLaterEvents() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));
        Map<String, Object> map = new HashMap<>();
        map.put("source", "map");
        mixpanel.registerSuperPropertiesMap(map);

        mixpanel.optInTracking();
        mixpanel.track("After Opt In");

        JSONObject optIn = lastEventProperties("$opt_in");
        assertEquals("pro", optIn.getString("plan"));
        assertEquals("map", optIn.getString("source"));
        JSONObject after = lastEventProperties("After Opt In");
        assertEquals("pro", after.getString("plan"));
        assertEquals("map", after.getString("source"));

        JSONObject persisted = new JSONObject(storedSuperProperties());
        assertEquals("pro", persisted.getString("plan"));
        assertEquals("map", persisted.getString("source"));
        assertEquals("pro", mixpanel.getSuperProperties().getString("plan"));
    }

    @Test
    public void testOptOutDiscardsPendingSuperProperties() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));

        mixpanel.optOutTracking();
        mixpanel.optInTracking();

        assertFalse(lastEventProperties("$opt_in").has("plan"));
        assertFalse(mixpanel.getSuperProperties().has("plan"));
    }

    @Test
    public void testResetDiscardsPendingSuperProperties() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));

        mixpanel.reset();
        assertTrue(mixpanel.hasOptedOutTracking());
        mixpanel.optInTracking();

        assertFalse(lastEventProperties("$opt_in").has("plan"));
        assertFalse(mixpanel.getSuperProperties().has("plan"));
    }

    @Test
    public void testClearSuperPropertiesDiscardsPendingSuperProperties() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));

        mixpanel.clearSuperProperties();
        mixpanel.registerSuperProperties(new JSONObject().put("kept", true));
        mixpanel.optInTracking();

        JSONObject optIn = lastEventProperties("$opt_in");
        assertFalse(optIn.has("plan"));
        assertTrue(optIn.getBoolean("kept"));
    }

    @Test
    public void testOptionsSuperPropertiesWithOptOutTrackingDefault() throws JSONException {
        MixpanelAPI mixpanel =
                newMixpanel(
                        new MixpanelOptions.Builder()
                                .optOutTrackingDefault(true)
                                .superProperties(new JSONObject().put("app_flavor", "free"))
                                .build());
        assertTrue(mixpanel.hasOptedOutTracking());
        assertFalse(storedSuperProperties() != null
                && new JSONObject(storedSuperProperties()).has("app_flavor"));

        mixpanel.optInTracking();
        mixpanel.track("After Opt In");

        assertEquals("free", lastEventProperties("$opt_in").getString("app_flavor"));
        assertEquals("free", lastEventProperties("After Opt In").getString("app_flavor"));
    }

    @Test
    public void testOptionsSuperPropertiesWithoutOptOutAreRegisteredImmediately()
            throws JSONException {
        MixpanelAPI mixpanel =
                newMixpanel(
                        new MixpanelOptions.Builder()
                                .superProperties(new JSONObject().put("app_flavor", "free"))
                                .build());
        assertFalse(mixpanel.hasOptedOutTracking());
        assertEquals("free", new JSONObject(storedSuperProperties()).getString("app_flavor"));
    }

    @Test
    public void testRegisterOnceKeepsOnceSemanticsAgainstPersisted() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        // A property already in the persisted store when the pending changes are applied.
        mPersistentIdentity.registerSuperProperties(new JSONObject().put("existing", "persisted"));

        mixpanel.registerSuperPropertiesOnce(
                new JSONObject().put("existing", "pending").put("fresh", "once"));
        mixpanel.optInTracking();

        JSONObject optIn = lastEventProperties("$opt_in");
        assertEquals("persisted", optIn.getString("existing"));
        assertEquals("once", optIn.getString("fresh"));
    }

    @Test
    public void testPendingOperationsApplyInCallOrder() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(new JSONObject().put("a", 1).put("b", 1));
        mixpanel.unregisterSuperProperty("a");
        mixpanel.registerSuperPropertiesOnce(new JSONObject().put("b", 2).put("c", 2));
        mixpanel.unregisterSuperProperty("c");
        mixpanel.registerSuperProperties(new JSONObject().put("c", 3));
        mixpanel.registerSuperPropertiesOnce(new JSONObject().put("a", 4));

        mixpanel.optInTracking();

        JSONObject props = mixpanel.getSuperProperties();
        assertEquals(4, props.getInt("a"));
        assertEquals(1, props.getInt("b"));
        assertEquals(3, props.getInt("c"));
    }

    @Test
    public void testPendingPropertiesAreCopiedAtCallTime() throws JSONException {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        JSONObject props = new JSONObject().put("plan", "pro");
        mixpanel.registerSuperProperties(props);
        props.put("plan", "mutated");
        props.put("late", true);

        mixpanel.optInTracking();

        JSONObject optIn = lastEventProperties("$opt_in");
        assertEquals("pro", optIn.getString("plan"));
        assertFalse(optIn.has("late"));
    }

    @Test
    public void testNullArgumentsWhileOptedOutDoNotCrash() {
        MixpanelAPI mixpanel = newOptedOutMixpanel();
        mixpanel.registerSuperProperties(null);
        mixpanel.registerSuperPropertiesOnce(null);
        mixpanel.registerSuperPropertiesMap(null);
        mixpanel.registerSuperPropertiesOnceMap(null);
        mixpanel.unregisterSuperProperty(null);

        mixpanel.optInTracking();
        assertNotNull(lastEventProperties("$opt_in"));
    }

    @Test
    public void testRegisterWhileOptedInIsUnchanged() throws JSONException {
        MixpanelAPI mixpanel = newMixpanel(new MixpanelOptions.Builder().build());
        mixpanel.registerSuperProperties(new JSONObject().put("plan", "pro"));
        assertEquals("pro", new JSONObject(storedSuperProperties()).getString("plan"));

        mixpanel.track("Event");
        assertEquals("pro", lastEventProperties("Event").getString("plan"));
    }
}
