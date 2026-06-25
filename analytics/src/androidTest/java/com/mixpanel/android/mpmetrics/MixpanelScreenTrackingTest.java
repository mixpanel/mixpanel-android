package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class MixpanelScreenTrackingTest {

  private Future<android.content.SharedPreferences> mMockPreferences;
  private static final int POLL_WAIT_SECONDS = 10;

  @Before
  public void setUp() throws Exception {
    mMockPreferences =
        new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());
    AnalyticsMessages messages =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext(), null));
    messages.hardKill();
    Thread.sleep(2000);
  }

  @Test
  public void testScreenView() throws InterruptedException, JSONException {
    final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<>();

    final MPDbAdapter dbMock =
        new MPDbAdapter(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
            if (table == MPDbAdapter.Table.EVENTS) {
              messages.add(message);
            }
            return 1;
          }
        };

    final AnalyticsMessages analyticsMessages =
        new AnalyticsMessages(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public MPDbAdapter makeDbAdapter(Context context) {
            return dbMock;
          }
        };

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN") {
          @Override
          protected AnalyticsMessages getAnalyticsMessages() {
            return analyticsMessages;
          }
        };

    JSONObject props = new JSONObject();
    props.put("extra_prop", "extra_value");

    mixpanel.trackScreenView("HomeScreen", props);

    final JSONObject message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_view", message.getString("event"));

    final JSONObject eventProps = message.getJSONObject("properties");
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
    assertEquals("extra_value", eventProps.getString("extra_prop"));
    assertTrue(eventProps.has("$screen_height"));
  }

  @Test
  public void testScreenViewWithoutProperties() throws InterruptedException, JSONException {
    final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<>();

    final MPDbAdapter dbMock =
        new MPDbAdapter(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
            if (table == MPDbAdapter.Table.EVENTS) {
              messages.add(message);
            }
            return 1;
          }
        };

    final AnalyticsMessages analyticsMessages =
        new AnalyticsMessages(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public MPDbAdapter makeDbAdapter(Context context) {
            return dbMock;
          }
        };

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN") {
          @Override
          protected AnalyticsMessages getAnalyticsMessages() {
            return analyticsMessages;
          }
        };

    mixpanel.trackScreenView("HomeScreen");

    final JSONObject message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_view", message.getString("event"));

    final JSONObject eventProps = message.getJSONObject("properties");
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
  }

  @Test
  public void testScreenLeave() throws InterruptedException, JSONException {
    final BlockingQueue<JSONObject> messages = new LinkedBlockingQueue<>();

    final MPDbAdapter dbMock =
        new MPDbAdapter(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public int addJSON(JSONObject message, String token, MPDbAdapter.Table table) {
            if (table == MPDbAdapter.Table.EVENTS) {
              messages.add(message);
            }
            return 1;
          }
        };

    final AnalyticsMessages analyticsMessages =
        new AnalyticsMessages(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(), null)) {
          @Override
          public MPDbAdapter makeDbAdapter(Context context) {
            return dbMock;
          }
        };

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN") {
          @Override
          protected AnalyticsMessages getAnalyticsMessages() {
            return analyticsMessages;
          }
        };

    mixpanel.trackScreenLeave("HomeScreen");

    final JSONObject message = messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_leave", message.getString("event"));

    final JSONObject eventProps = message.getJSONObject("properties");
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
  }

}
