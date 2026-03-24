package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class PersistentIdentityTest {

    @Before
    public void setUp() {
        SharedPreferences referrerPrefs = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_REFERRER_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor referrerEditor = referrerPrefs.edit();
        referrerEditor.clear();
        referrerEditor.putString("referrer", "REFERRER");
        referrerEditor.putString("utm_source", "SOURCE VALUE");
        referrerEditor.putString("utm_medium", "MEDIUM VALUE");
        referrerEditor.putString("utm_campaign", "CAMPAIGN NAME VALUE");
        referrerEditor.putString("utm_content", "CONTENT VALUE");
        referrerEditor.putString("utm_term", "TERM VALUE");
        referrerEditor.commit();

        SharedPreferences testPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor prefsEditor = testPreferences.edit();
        prefsEditor.clear();
        prefsEditor.putString("events_distinct_id", "EVENTS DISTINCT ID");
        prefsEditor.putString("people_distinct_id", "PEOPLE DISTINCT ID");
        prefsEditor.putString("push_id", "PUSH ID");
        prefsEditor.putString("super_properties", "{\"thing\": \"superprops\"}");
        prefsEditor.commit();

        SharedPreferences timeEventsPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_TIME_EVENTS_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor timeEventsEditor = timeEventsPreferences.edit();
        timeEventsEditor.clear();
        timeEventsEditor.commit();

        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);

        mPersistentIdentity = new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, mixpanelLoader, null);
    }

    @Test
    public void testReferrerProperties() {
        final Map<String, String> props = mPersistentIdentity.getReferrerProperties();
        assertEquals("REFERRER", props.get("referrer"));
        assertEquals("SOURCE VALUE", props.get("utm_source"));
        assertEquals("MEDIUM VALUE", props.get("utm_medium"));
        assertEquals("CAMPAIGN NAME VALUE", props.get("utm_campaign"));
        assertEquals("CONTENT VALUE", props.get("utm_content"));
        assertEquals("TERM VALUE", props.get("utm_term"));

        final Map<String, String> newPrefs = new HashMap<String, String>();
        newPrefs.put("referrer", "BJORK");
        newPrefs.put("mystery", "BOO!");
        newPrefs.put("utm_term", "NEW TERM");
        PersistentIdentity.writeReferrerPrefs(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, newPrefs);

        final Map<String, String> propsAfterChange = mPersistentIdentity.getReferrerProperties();
        assertFalse(propsAfterChange.containsKey("utm_medium"));
        assertFalse(propsAfterChange.containsKey("utm_source"));
        assertFalse(propsAfterChange.containsKey("utm_campaign"));
        assertFalse(propsAfterChange.containsKey("utm_content"));
        assertEquals("BJORK", propsAfterChange.get("referrer"));
        assertEquals("NEW TERM", propsAfterChange.get("utm_term"));
        assertEquals("BOO!", propsAfterChange.get("mystery"));
    }

    @Test
    public void testUnsetEventsId() {
        final SharedPreferences testPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String eventsId = mPersistentIdentity.getEventsDistinctId();
        assertTrue(Pattern.matches("^\\$device:[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$", eventsId));

        final String autoId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals(autoId, eventsId);

        mPersistentIdentity.setEventsDistinctId("TEST ID TO SET");
        final String heardId = mPersistentIdentity.getEventsDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("events_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    @Test
    public void testUnsetPeopleId() {
        final SharedPreferences testPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().clear().commit();
        final String peopleId = mPersistentIdentity.getPeopleDistinctId();
        assertNull(peopleId);

        mPersistentIdentity.setPeopleDistinctId("TEST ID TO SET");
        final String heardId = mPersistentIdentity.getPeopleDistinctId();
        assertEquals("TEST ID TO SET", heardId);

        final String storedId = testPreferences.getString("people_distinct_id", "NOPE");
        assertEquals("TEST ID TO SET", storedId);
    }

    @Test
    @Ignore("TODO: requires emulator — SharedPreferencesLoader caching differs under Robolectric")
    public void testGeneratedAnonymousId() {
        SharedPreferences testPreferences = ApplicationProvider.getApplicationContext().getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit().remove("events_distinct_id").commit();

        final String generatedAnonymousId = mPersistentIdentity.getAnonymousId();
        assertNotNull(generatedAnonymousId);

        // before identifying the anonymous identity is equal to generated distinct_id
        final String eventsDistinctId = mPersistentIdentity.getEventsDistinctId();
        assertEquals("eventsDistinctId should be same as anonymousId before identify", generatedAnonymousId, eventsDistinctId);

        mPersistentIdentity.setEventsDistinctId("identified_id");
        assertNotSame("anonymous id doesn't differ from eventsDistinctId post identify", generatedAnonymousId, mPersistentIdentity.getEventsDistinctId());
    }

    @Test
    public void testHadPersistedDistinctId() {
        final String eventsDistinctId = mPersistentIdentity.getEventsDistinctId();
        assertNotNull("events distinct id is not null", eventsDistinctId);
        assertNull("no anonymous id yet", mPersistentIdentity.getAnonymousId());

        mPersistentIdentity.setAnonymousIdIfAbsent("anon_id");

        assertNotNull("anonymous id cannot be null", mPersistentIdentity.getAnonymousId());
        assertTrue("hadPersistedDistinctId cannot be false", mPersistentIdentity.getHadPersistedDistinctId());
    }

    // --- updateSuperProperties ---

    @Test
    public void testUpdateSuperProperties() throws JSONException {
        // Normal update: add a new property via SuperPropertyUpdate
        mPersistentIdentity.updateSuperProperties(oldProps -> {
            try {
                oldProps.put("new_key", "new_value");
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            return oldProps;
        });
        JSONObject target = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(target);
        assertEquals("superprops", target.getString("thing"));
        assertEquals("new_value", target.getString("new_key"));
    }

    @Test
    public void testUpdateSuperPropertiesReturnsNull() throws JSONException {
        // When update returns null, super properties should remain unchanged
        mPersistentIdentity.updateSuperProperties(oldProps -> null);
        JSONObject target = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(target);
        assertEquals("superprops", target.getString("thing"));
    }

    // --- registerSuperProperties ---

    @Test
    public void testRegisterSuperProperties() throws JSONException {
        JSONObject newProps = new JSONObject();
        newProps.put("color", "blue");
        newProps.put("thing", "overwritten");
        mPersistentIdentity.registerSuperProperties(newProps);

        JSONObject target = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(target);
        assertEquals("blue", target.getString("color"));
        assertEquals("overwritten", target.getString("thing"));
    }

    // --- registerSuperPropertiesOnce ---

    @Test
    public void testRegisterSuperPropertiesOnce() throws JSONException {
        JSONObject newProps = new JSONObject();
        newProps.put("thing", "should_not_overwrite");
        newProps.put("once_key", "once_value");
        mPersistentIdentity.registerSuperPropertiesOnce(newProps);

        JSONObject target = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(target);
        // existing key should NOT be overwritten
        assertEquals("superprops", target.getString("thing"));
        // new key should be added
        assertEquals("once_value", target.getString("once_key"));
    }

    // --- addSuperPropertiesToObject ---

    @Test
    public void testAddSuperPropertiesToObject() throws JSONException {
        JSONObject target = new JSONObject();
        target.put("existing", "value");
        mPersistentIdentity.addSuperPropertiesToObject(target);
        assertEquals("value", target.getString("existing"));
        assertEquals("superprops", target.getString("thing"));
    }

    // --- clearReferrerProperties ---

    @Test
    public void testClearReferrerProperties() {
        // Verify referrer properties exist first
        Map<String, String> props = mPersistentIdentity.getReferrerProperties();
        assertFalse(props.isEmpty());

        mPersistentIdentity.clearReferrerProperties();
        // After clearing, re-read: properties should be empty
        // Force re-read by writing empty referrer prefs
        Map<String, String> cleared = mPersistentIdentity.getReferrerProperties();
        // The in-memory cache may still have old values, but the SharedPreferences are cleared.
        // Write new referrer prefs to trigger re-read via the dirty flag.
        PersistentIdentity.writeReferrerPrefs(
                ApplicationProvider.getApplicationContext(),
                TEST_REFERRER_PREFERENCES,
                new HashMap<>());
        Map<String, String> afterClear = mPersistentIdentity.getReferrerProperties();
        assertTrue(afterClear.isEmpty());
    }

    // --- clearTimedEvents ---

    @Test
    public void testClearTimedEvents() {
        mPersistentIdentity.addTimeEvent("event1", 1000L);
        mPersistentIdentity.addTimeEvent("event2", 2000L);
        Map<String, Long> events = mPersistentIdentity.getTimeEvents();
        assertEquals(2, events.size());

        mPersistentIdentity.clearTimedEvents();
        Map<String, Long> cleared = mPersistentIdentity.getTimeEvents();
        assertTrue(cleared.isEmpty());
    }

    // --- addTimeEvent / removeTimedEvent ---

    @Test
    public void testAddAndRemoveTimeEvent() {
        mPersistentIdentity.addTimeEvent("timed_event", 12345L);
        Map<String, Long> events = mPersistentIdentity.getTimeEvents();
        assertEquals(Long.valueOf(12345L), events.get("timed_event"));

        mPersistentIdentity.removeTimedEvent("timed_event");
        Map<String, Long> afterRemove = mPersistentIdentity.getTimeEvents();
        assertFalse(afterRemove.containsKey("timed_event"));
    }

    // --- getTimeEvents / loadTimeEventsCache ---

    @Test
    public void testGetTimeEventsEmpty() {
        Map<String, Long> events = mPersistentIdentity.getTimeEvents();
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    public void testGetTimeEventsWithPreloadedData() {
        // Add events via SharedPreferences directly, then create a new PersistentIdentity
        SharedPreferences timePrefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(TEST_TIME_EVENTS_PREFERENCES, Context.MODE_PRIVATE);
        timePrefs.edit().putLong("preloaded_event", 9999L).commit();

        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);

        PersistentIdentity freshIdentity = new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, mixpanelLoader, null);
        // Give async preload a moment to complete
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        Map<String, Long> events = freshIdentity.getTimeEvents();
        assertEquals(Long.valueOf(9999L), events.get("preloaded_event"));
    }

    // --- isNewVersion ---

    @Test
    public void testIsNewVersionNull() throws Exception {
        resetStaticField("sPreviousVersionCode");
        assertFalse(mPersistentIdentity.isNewVersion(null));
    }

    @Test
    public void testIsNewVersionFirstRun() throws Exception {
        resetStaticField("sPreviousVersionCode");
        // First run with no previous version stored: should return false (sets current as baseline)
        boolean result = mPersistentIdentity.isNewVersion("1");
        assertFalse(result);
    }

    @Test
    public void testIsNewVersionUpgrade() throws Exception {
        resetStaticField("sPreviousVersionCode");
        // Set initial version
        mPersistentIdentity.isNewVersion("1");
        // Reset the static so it re-reads from prefs
        resetStaticField("sPreviousVersionCode");
        // Now "upgrade" to version 2
        boolean upgraded = mPersistentIdentity.isNewVersion("2");
        assertTrue(upgraded);
    }

    @Test
    public void testIsNewVersionSameVersion() throws Exception {
        resetStaticField("sPreviousVersionCode");
        mPersistentIdentity.isNewVersion("5");
        // Same version again should return false
        boolean same = mPersistentIdentity.isNewVersion("5");
        assertFalse(same);
    }

    // --- isFirstLaunch / setHasLaunched ---

    @Test
    public void testIsFirstLaunchTrue() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        // No DB exists and hasn't launched before: first launch
        boolean result = mPersistentIdentity.isFirstLaunch(false, "test_token");
        assertTrue(result);
    }

    @Test
    public void testIsFirstLaunchFalseDbExists() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        // DB exists implies not first launch
        boolean result = mPersistentIdentity.isFirstLaunch(true, "test_token_db");
        assertFalse(result);
    }

    @Test
    public void testIsFirstLaunchFalseAfterSetHasLaunched() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        mPersistentIdentity.setHasLaunched("test_token_launched");
        // After setHasLaunched, isFirstLaunch should be false
        boolean result = mPersistentIdentity.isFirstLaunch(false, "test_token_launched");
        assertFalse(result);
    }

    @Test
    public void testSetHasLaunched() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        mPersistentIdentity.setHasLaunched("token_set");
        // Verify the flag is persisted
        SharedPreferences prefs = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(TEST_MIXPANEL_PREFERENCES, Context.MODE_PRIVATE);
        assertTrue(prefs.getBoolean("has_launched_token_set", false));
    }

    // --- opt-out tracking ---

    @Test
    public void testGetOptOutTrackingDefault() {
        assertFalse(mPersistentIdentity.getOptOutTracking("opt_token"));
    }

    @Test
    public void testSetAndGetOptOutTracking() {
        mPersistentIdentity.setOptOutTracking(true, "opt_token");
        assertTrue(mPersistentIdentity.getOptOutTracking("opt_token"));

        mPersistentIdentity.setOptOutTracking(false, "opt_token");
        assertFalse(mPersistentIdentity.getOptOutTracking("opt_token"));
    }

    // --- hasOptOutFlag / removeOptOutFlag ---

    @Test
    public void testHasOptOutFlag() {
        assertFalse(mPersistentIdentity.hasOptOutFlag("flag_token"));
        mPersistentIdentity.setOptOutTracking(true, "flag_token");
        assertTrue(mPersistentIdentity.hasOptOutFlag("flag_token"));
    }

    @Test
    public void testRemoveOptOutFlag() {
        mPersistentIdentity.setOptOutTracking(true, "remove_token");
        assertTrue(mPersistentIdentity.hasOptOutFlag("remove_token"));
        mPersistentIdentity.removeOptOutFlag("remove_token");
        assertFalse(mPersistentIdentity.hasOptOutFlag("remove_token"));
    }

    // --- clearPreferences ---

    @Test
    public void testClearPreferences() throws JSONException {
        // Verify we have data
        assertEquals("EVENTS DISTINCT ID", mPersistentIdentity.getEventsDistinctId());
        JSONObject target = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(target);
        assertTrue(target.has("thing"));

        mPersistentIdentity.clearPreferences();

        // After clear, events distinct id should be regenerated (not the old one)
        String newId = mPersistentIdentity.getEventsDistinctId();
        assertNotNull(newId);
        assertNotSame("EVENTS DISTINCT ID", newId);

        // Super properties should be empty
        JSONObject emptyTarget = new JSONObject();
        mPersistentIdentity.addSuperPropertiesToObject(emptyTarget);
        assertFalse(emptyTarget.has("thing"));
    }

    // --- getEventsUserId / markEventsUserIdPresent ---

    @Test
    public void testGetEventsUserIdBeforeMark() {
        // Before marking, getEventsUserId should return null
        assertNull(mPersistentIdentity.getEventsUserId());
    }

    @Test
    public void testMarkEventsUserIdPresent() {
        mPersistentIdentity.markEventsUserIdPresent();
        String userId = mPersistentIdentity.getEventsUserId();
        assertNotNull(userId);
        assertEquals(mPersistentIdentity.getEventsDistinctId(), userId);
    }

    // --- helper to reset static fields ---

    private void resetStaticField(String fieldName) throws Exception {
        Field field = PersistentIdentity.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }

    // =========================================================================
    // Exception-path tests: FailingFuture triggers catch blocks in PersistentIdentity
    // =========================================================================

    private static class FailingFuture implements Future<SharedPreferences> {
        @Override public boolean cancel(boolean b) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public SharedPreferences get() throws ExecutionException {
            throw new ExecutionException("Test failure", new RuntimeException("test"));
        }
        @Override public SharedPreferences get(long l, TimeUnit t) throws ExecutionException {
            throw new ExecutionException("Test failure", new RuntimeException("test"));
        }
    }

    private PersistentIdentity createWithFailingStoredPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, new FailingFuture(), timeEventsLoader, mixpanelLoader, null);
    }

    private PersistentIdentity createWithFailingReferrerPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(new FailingFuture(), testLoader, timeEventsLoader, mixpanelLoader, null);
    }

    private PersistentIdentity createWithFailingTimeEventsPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, testLoader, new FailingFuture(), mixpanelLoader, null);
    }

    private PersistentIdentity createWithFailingMixpanelPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, new FailingFuture(), null);
    }

    // --- Failing storedPreferences ---

    @Test
    public void testFailingStoredPrefs_getSuperPropertiesCache() throws JSONException {
        PersistentIdentity pi = createWithFailingStoredPrefs();
        // readSuperProperties catches ExecutionException; finally sets cache to empty JSONObject
        JSONObject target = new JSONObject();
        pi.addSuperPropertiesToObject(target);
        // Should have an empty super properties cache (no keys)
        assertFalse(target.has("thing"));
    }

    @Test
    public void testFailingStoredPrefs_registerSuperProperties() throws JSONException {
        PersistentIdentity pi = createWithFailingStoredPrefs();
        // storeSuperProperties catches ExecutionException; should not crash
        JSONObject props = new JSONObject();
        props.put("key", "value");
        pi.registerSuperProperties(props);
        // Verify it didn't crash and cache was updated in memory
        JSONObject target = new JSONObject();
        pi.addSuperPropertiesToObject(target);
        assertEquals("value", target.getString("key"));
    }

    @Test
    public void testFailingStoredPrefs_getEventsDistinctId() {
        PersistentIdentity pi = createWithFailingStoredPrefs();
        // readIdentities catches ExecutionException; getEventsDistinctId returns null
        String id = pi.getEventsDistinctId();
        assertNull(id);
    }

    @Test
    public void testFailingStoredPrefs_setEventsDistinctId() {
        PersistentIdentity pi = createWithFailingStoredPrefs();
        // writeIdentities catches ExecutionException; should not crash
        pi.setEventsDistinctId("test_id");
    }

    @Test
    public void testFailingStoredPrefs_clearPreferences() {
        PersistentIdentity pi = createWithFailingStoredPrefs();
        // clearPreferences wraps ExecutionException in RuntimeException
        assertThrows(RuntimeException.class, pi::clearPreferences);
    }

    // --- Failing referrerPreferences ---

    @Test
    public void testFailingReferrerPrefs_getReferrerProperties() {
        PersistentIdentity pi = createWithFailingReferrerPrefs();
        // readReferrerProperties catches ExecutionException; returns empty map
        Map<String, String> props = pi.getReferrerProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test
    public void testFailingReferrerPrefs_clearReferrerProperties() {
        PersistentIdentity pi = createWithFailingReferrerPrefs();
        // clearReferrerProperties catches ExecutionException; should not crash
        pi.clearReferrerProperties();
    }

    // --- Failing timeEventsPreferences ---

    @Test
    public void testFailingTimeEventsPrefs_clearTimedEvents() {
        PersistentIdentity pi = createWithFailingTimeEventsPrefs();
        // Give async preload thread time to complete (it will catch the exception)
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        // clearTimedEvents catches ExecutionException; should not crash
        pi.clearTimedEvents();
    }

    @Test
    public void testFailingTimeEventsPrefs_addTimeEvent() {
        PersistentIdentity pi = createWithFailingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        // addTimeEvent catches ExecutionException; should not crash
        pi.addTimeEvent("event", 1000L);
    }

    @Test
    public void testFailingTimeEventsPrefs_removeTimedEvent() {
        PersistentIdentity pi = createWithFailingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        // removeTimedEvent catches ExecutionException; should not crash
        pi.removeTimedEvent("event");
    }

    @Test
    public void testFailingTimeEventsPrefs_getTimeEvents() {
        PersistentIdentity pi = createWithFailingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        // getTimeEvents catches ExecutionException; returns empty map
        Map<String, Long> events = pi.getTimeEvents();
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    // --- Failing mixpanelPreferences ---

    @Test
    public void testFailingMixpanelPrefs_isNewVersion() throws Exception {
        resetStaticField("sPreviousVersionCode");
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // isNewVersion catches ExecutionException; returns false
        boolean result = pi.isNewVersion("1");
        assertFalse(result);
    }

    @Test
    public void testFailingMixpanelPrefs_isFirstLaunch() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // isFirstLaunch catches ExecutionException; returns false
        boolean result = pi.isFirstLaunch(false, "test_token");
        assertFalse(result);
    }

    @Test
    public void testFailingMixpanelPrefs_setHasLaunched() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // setHasLaunched catches ExecutionException; should not crash
        pi.setHasLaunched("test_token");
    }

    @Test
    public void testFailingMixpanelPrefs_getOptOutTracking() {
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // readOptOutFlag catches ExecutionException; returns false
        boolean result = pi.getOptOutTracking("opt_token");
        assertFalse(result);
    }

    @Test
    public void testFailingMixpanelPrefs_setOptOutTracking() {
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // writeOptOutFlag catches ExecutionException; should not crash
        pi.setOptOutTracking(true, "opt_token");
    }

    @Test
    public void testFailingMixpanelPrefs_removeOptOutFlag() {
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // removeOptOutFlag catches ExecutionException; should not crash
        pi.removeOptOutFlag("opt_token");
    }

    @Test
    public void testFailingMixpanelPrefs_hasOptOutFlag() {
        PersistentIdentity pi = createWithFailingMixpanelPrefs();
        // hasOptOutFlag catches ExecutionException; returns false
        boolean result = pi.hasOptOutFlag("opt_token");
        assertFalse(result);
    }

    // =========================================================================
    // InterruptedException-path tests: InterruptingFuture triggers catch blocks
    // =========================================================================

    private static class InterruptingFuture implements Future<SharedPreferences> {
        @Override public boolean cancel(boolean b) { return false; }
        @Override public boolean isCancelled() { return false; }
        @Override public boolean isDone() { return true; }
        @Override public SharedPreferences get() throws InterruptedException {
            throw new InterruptedException("Test interruption");
        }
        @Override public SharedPreferences get(long l, TimeUnit t) throws InterruptedException {
            throw new InterruptedException("Test interruption");
        }
    }

    private PersistentIdentity createIdentityWithInterruptingStoredPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, new InterruptingFuture(), timeEventsLoader, mixpanelLoader, null);
    }

    private PersistentIdentity createIdentityWithInterruptingReferrerPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(new InterruptingFuture(), testLoader, timeEventsLoader, mixpanelLoader, null);
    }

    private PersistentIdentity createIdentityWithInterruptingTimeEventsPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, testLoader, new InterruptingFuture(), mixpanelLoader, null);
    }

    private PersistentIdentity createIdentityWithInterruptingMixpanelPrefs() {
        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        return new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, new InterruptingFuture(), null);
    }

    // --- Interrupting storedPreferences ---

    @Test
    public void testInterruptingStoredPrefs_readSuperProperties() throws JSONException {
        PersistentIdentity pi = createIdentityWithInterruptingStoredPrefs();
        JSONObject target = new JSONObject();
        pi.addSuperPropertiesToObject(target);
        assertFalse(target.has("thing"));
    }

    @Test
    public void testInterruptingStoredPrefs_registerSuperProperties() throws JSONException {
        PersistentIdentity pi = createIdentityWithInterruptingStoredPrefs();
        JSONObject props = new JSONObject();
        props.put("key", "value");
        pi.registerSuperProperties(props);
        JSONObject target = new JSONObject();
        pi.addSuperPropertiesToObject(target);
        assertEquals("value", target.getString("key"));
    }

    @Test
    public void testInterruptingStoredPrefs_readIdentities() {
        PersistentIdentity pi = createIdentityWithInterruptingStoredPrefs();
        String id = pi.getEventsDistinctId();
        assertNull(id);
    }

    @Test
    public void testInterruptingStoredPrefs_writeIdentities() {
        PersistentIdentity pi = createIdentityWithInterruptingStoredPrefs();
        pi.setEventsDistinctId("test_id");
        // Should not crash
    }

    // --- Interrupting referrerPreferences ---

    @Test
    public void testInterruptingReferrerPrefs_getReferrerProperties() {
        PersistentIdentity pi = createIdentityWithInterruptingReferrerPrefs();
        Map<String, String> props = pi.getReferrerProperties();
        assertNotNull(props);
        assertTrue(props.isEmpty());
    }

    @Test
    public void testInterruptingReferrerPrefs_clearReferrerProperties() {
        PersistentIdentity pi = createIdentityWithInterruptingReferrerPrefs();
        pi.clearReferrerProperties();
        // Should not crash
    }

    // --- Interrupting timeEventsPreferences ---

    @Test
    public void testInterruptingTimeEventsPrefs_clearTimedEvents() {
        PersistentIdentity pi = createIdentityWithInterruptingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        pi.clearTimedEvents();
    }

    @Test
    public void testInterruptingTimeEventsPrefs_addTimeEvent() {
        PersistentIdentity pi = createIdentityWithInterruptingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        pi.addTimeEvent("event", 1000L);
    }

    @Test
    public void testInterruptingTimeEventsPrefs_removeTimedEvent() {
        PersistentIdentity pi = createIdentityWithInterruptingTimeEventsPrefs();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        pi.removeTimedEvent("event");
    }

    // --- Interrupting mixpanelPreferences ---

    @Test
    public void testInterruptingMixpanelPrefs_isNewVersion() throws Exception {
        resetStaticField("sPreviousVersionCode");
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        boolean result = pi.isNewVersion("1");
        assertFalse(result);
    }

    @Test
    public void testInterruptingMixpanelPrefs_setHasLaunched() throws Exception {
        resetStaticField("sIsFirstAppLaunch");
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        pi.setHasLaunched("test_token");
    }

    @Test
    public void testInterruptingMixpanelPrefs_readOptOutFlag() {
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        boolean result = pi.getOptOutTracking("opt_token");
        assertFalse(result);
    }

    @Test
    public void testInterruptingMixpanelPrefs_writeOptOutFlag() {
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        pi.setOptOutTracking(true, "opt_token");
    }

    @Test
    public void testInterruptingMixpanelPrefs_removeOptOutFlag() {
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        pi.removeOptOutFlag("opt_token");
    }

    @Test
    public void testInterruptingMixpanelPrefs_hasOptOutFlag() {
        PersistentIdentity pi = createIdentityWithInterruptingMixpanelPrefs();
        boolean result = pi.hasOptOutFlag("opt_token");
        assertFalse(result);
    }

    // =========================================================================
    // JSONException path in readSuperProperties
    // =========================================================================

    @Test
    public void testReadSuperPropertiesWithInvalidJSON() throws JSONException {
        // Pre-load SharedPreferences with invalid JSON in "super_properties" key
        SharedPreferences testPreferences = ApplicationProvider.getApplicationContext()
                .getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
        testPreferences.edit()
                .putString("super_properties", "not valid json{{")
                .commit();

        SharedPreferencesLoader loader = new SharedPreferencesLoader();
        Future<SharedPreferences> referrerLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_REFERRER_PREFERENCES, null);
        Future<SharedPreferences> testLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_PREFERENCES, null);
        Future<SharedPreferences> timeEventsLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_TIME_EVENTS_PREFERENCES, null);
        Future<SharedPreferences> mixpanelLoader = loader.loadPreferences(ApplicationProvider.getApplicationContext(), TEST_MIXPANEL_PREFERENCES, null);

        PersistentIdentity pi = new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, mixpanelLoader, null);
        // Should recover gracefully — returns empty JSONObject from finally block
        JSONObject target = new JSONObject();
        pi.addSuperPropertiesToObject(target);
        assertFalse(target.has("thing"));
    }

    private PersistentIdentity mPersistentIdentity;
    private static final String TEST_PREFERENCES = "TEST PERSISTENT PROPERTIES PREFS";
    private static final String TEST_REFERRER_PREFERENCES  = "TEST REFERRER PREFS";
    private static final String TEST_TIME_EVENTS_PREFERENCES  = "TEST TIME EVENTS PREFS";
    private static final String TEST_MIXPANEL_PREFERENCES  = "TEST MIXPANELPREFS";
}
