package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    final BlockingQueue<AnalyticsMessages.EventDescription> messages =
        new LinkedBlockingQueue<>();

    final AnalyticsMessages listener =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            new TestUtils.TestMPConfig(InstrumentationRegistry.getInstrumentation().getContext()),
            true);
    listener.setOnEventListener(
        new AnalyticsMessages.OnEventListener() {
          @Override
          public void onEvent(AnalyticsMessages.EventDescription event) {
            messages.add(event);
          }
        });

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN",
            listener);

    JSONObject props = new JSONObject();
    props.put("extra_prop", "extra_value");

    mixpanel.screenView("HomeScreen", props);

    final AnalyticsMessages.EventDescription message =
        messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_view", message.getEventName());

    final JSONObject eventProps = message.getProperties();
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
    assertEquals("extra_value", eventProps.getString("extra_prop"));
    assertTrue(eventProps.has("$screen_height"));
  }

  @Test
  public void testScreenViewWithoutProperties() throws InterruptedException, JSONException {
    final BlockingQueue<AnalyticsMessages.EventDescription> messages =
        new LinkedBlockingQueue<>();

    final AnalyticsMessages listener =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            new TestUtils.TestMPConfig(InstrumentationRegistry.getInstrumentation().getContext()),
            true);
    listener.setOnEventListener(
        new AnalyticsMessages.OnEventListener() {
          @Override
          public void onEvent(AnalyticsMessages.EventDescription event) {
            messages.add(event);
          }
        });

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN",
            listener);

    mixpanel.screenView("HomeScreen");

    final AnalyticsMessages.EventDescription message =
        messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_view", message.getEventName());

    final JSONObject eventProps = message.getProperties();
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
  }

  @Test
  public void testScreenViewNullScreenName() throws InterruptedException {
    final BlockingQueue<AnalyticsMessages.EventDescription> messages =
        new LinkedBlockingQueue<>();

    final AnalyticsMessages listener =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            new TestUtils.TestMPConfig(InstrumentationRegistry.getInstrumentation().getContext()),
            true);
    listener.setOnEventListener(
        new AnalyticsMessages.OnEventListener() {
          @Override
          public void onEvent(AnalyticsMessages.EventDescription event) {
            messages.add(event);
          }
        });

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN",
            listener);

    mixpanel.screenView(null);
    mixpanel.screenView("");
    mixpanel.screenView("   ");

    final AnalyticsMessages.EventDescription message =
        messages.poll(1, TimeUnit.SECONDS);
    assertNull(message);
  }

  @Test
  public void testScreenLeave() throws InterruptedException, JSONException {
    final BlockingQueue<AnalyticsMessages.EventDescription> messages =
        new LinkedBlockingQueue<>();

    final AnalyticsMessages listener =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            new TestUtils.TestMPConfig(InstrumentationRegistry.getInstrumentation().getContext()),
            true);
    listener.setOnEventListener(
        new AnalyticsMessages.OnEventListener() {
          @Override
          public void onEvent(AnalyticsMessages.EventDescription event) {
            messages.add(event);
          }
        });

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN",
            listener);

    mixpanel.screenLeave("HomeScreen");

    final AnalyticsMessages.EventDescription message =
        messages.poll(POLL_WAIT_SECONDS, TimeUnit.SECONDS);
    assertNotNull(message);
    assertEquals("$mp_page_leave", message.getEventName());

    final JSONObject eventProps = message.getProperties();
    assertEquals("HomeScreen", eventProps.getString("current_page_title"));
  }

  @Test
  public void testScreenLeaveNullScreenName() throws InterruptedException {
    final BlockingQueue<AnalyticsMessages.EventDescription> messages =
        new LinkedBlockingQueue<>();

    final AnalyticsMessages listener =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            new TestUtils.TestMPConfig(InstrumentationRegistry.getInstrumentation().getContext()),
            true);
    listener.setOnEventListener(
        new AnalyticsMessages.OnEventListener() {
          @Override
          public void onEvent(AnalyticsMessages.EventDescription event) {
            messages.add(event);
          }
        });

    final MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getContext(),
            mMockPreferences,
            "TEST_TOKEN",
            listener);

    mixpanel.screenLeave(null);

    final AnalyticsMessages.EventDescription message =
        messages.poll(1, TimeUnit.SECONDS);
    assertNull(message);
  }
}
