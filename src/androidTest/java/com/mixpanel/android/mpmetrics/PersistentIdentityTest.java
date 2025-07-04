package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.regex.Pattern;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class PersistentIdentityTest {

  @Before
  public void setUp() {
    SharedPreferences referrerPrefs =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_REFERRER_PREFERENCES, Context.MODE_PRIVATE);
    SharedPreferences.Editor referrerEditor = referrerPrefs.edit();
    referrerEditor.clear();
    referrerEditor.putString("referrer", "REFERRER");
    referrerEditor.putString("utm_source", "SOURCE VALUE");
    referrerEditor.putString("utm_medium", "MEDIUM VALUE");
    referrerEditor.putString("utm_campaign", "CAMPAIGN NAME VALUE");
    referrerEditor.putString("utm_content", "CONTENT VALUE");
    referrerEditor.putString("utm_term", "TERM VALUE");
    referrerEditor.commit();

    SharedPreferences testPreferences =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
    SharedPreferences.Editor prefsEditor = testPreferences.edit();
    prefsEditor.clear();
    prefsEditor.putString("events_distinct_id", "EVENTS DISTINCT ID");
    prefsEditor.putString("people_distinct_id", "PEOPLE DISTINCT ID");
    prefsEditor.putString("push_id", "PUSH ID");
    prefsEditor.putString("super_properties", "{\"thing\": \"superprops\"}");
    prefsEditor.commit();

    SharedPreferences timeEventsPreferences =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_TIME_EVENTS_PREFERENCES, Context.MODE_PRIVATE);
    SharedPreferences.Editor timeEventsEditor = timeEventsPreferences.edit();
    timeEventsEditor.clear();
    timeEventsEditor.commit();

    SharedPreferencesLoader loader = new SharedPreferencesLoader();
    Future<SharedPreferences> referrerLoader =
        loader.loadPreferences(
            InstrumentationRegistry.getInstrumentation().getContext(),
            TEST_REFERRER_PREFERENCES,
            null);
    Future<SharedPreferences> testLoader =
        loader.loadPreferences(
            InstrumentationRegistry.getInstrumentation().getContext(), TEST_PREFERENCES, null);
    Future<SharedPreferences> timeEventsLoader =
        loader.loadPreferences(
            InstrumentationRegistry.getInstrumentation().getContext(),
            TEST_TIME_EVENTS_PREFERENCES,
            null);
    Future<SharedPreferences> mixpanelLoader =
        loader.loadPreferences(
            InstrumentationRegistry.getInstrumentation().getContext(),
            TEST_MIXPANEL_PREFERENCES,
            null);

    mPersistentIdentity =
        new PersistentIdentity(referrerLoader, testLoader, timeEventsLoader, mixpanelLoader);
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
    PersistentIdentity.writeReferrerPrefs(
        InstrumentationRegistry.getInstrumentation().getContext(),
        TEST_REFERRER_PREFERENCES,
        newPrefs);

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
    final SharedPreferences testPreferences =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
    testPreferences.edit().clear().commit();
    final String eventsId = mPersistentIdentity.getEventsDistinctId();
    assertTrue(
        Pattern.matches(
            "^\\$device:[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}$",
            eventsId));

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
    final SharedPreferences testPreferences =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
    testPreferences.edit().clear().commit();
    final String peopleId = mPersistentIdentity.getPeopleDistinctId();
    assertNull(peopleId);

    mPersistentIdentity.setPeopleDistinctId("TEST ID TO SET");
    final String heardId = mPersistentIdentity.getPeopleDistinctId();
    assertEquals("TEST ID TO SET", heardId);

    final String storedId = testPreferences.getString("people_distinct_id", "NOPE");
    assertEquals("TEST ID TO SET", storedId);
  }

  public void testGeneratedAnonymousId() {
    SharedPreferences testPreferences =
        InstrumentationRegistry.getInstrumentation()
            .getContext()
            .getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE);
    testPreferences.edit().remove("events_distinct_id").commit();

    final String generatedAnonymousId = mPersistentIdentity.getAnonymousId();
    assertNotNull(generatedAnonymousId);

    // before identifying the anonymous identity is equal to generated distinct_id
    final String eventsDistinctId = mPersistentIdentity.getEventsDistinctId();
    assertEquals(
        "eventsDistinctId should be same as anonymousId before identify",
        generatedAnonymousId,
        eventsDistinctId);

    mPersistentIdentity.setEventsDistinctId("identified_id");
    assertNotSame(
        "anonymous id doesn't differ from eventsDistinctId post identify",
        generatedAnonymousId,
        mPersistentIdentity.getEventsDistinctId());
  }

  @Test
  public void testHadPersistedDistinctId() {
    final String eventsDistinctId = mPersistentIdentity.getEventsDistinctId();
    assertNotNull("events distinct id is not null", eventsDistinctId);
    assertNull("no anonymous id yet", mPersistentIdentity.getAnonymousId());

    mPersistentIdentity.setAnonymousIdIfAbsent("anon_id");

    assertNotNull("anonymous id cannot be null", mPersistentIdentity.getAnonymousId());
    assertTrue(
        "hadPersistedDistinctId cannot be false", mPersistentIdentity.getHadPersistedDistinctId());
  }

  private PersistentIdentity mPersistentIdentity;
  private static final String TEST_PREFERENCES = "TEST PERSISTENT PROPERTIES PREFS";
  private static final String TEST_REFERRER_PREFERENCES = "TEST REFERRER PREFS";
  private static final String TEST_TIME_EVENTS_PREFERENCES = "TEST TIME EVENTS PREFS";
  private static final String TEST_MIXPANEL_PREFERENCES = "TEST MIXPANELPREFS";
}
