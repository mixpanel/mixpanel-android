package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import androidx.test.core.app.ApplicationProvider;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

/**
 * The SDK flushes when the app goes to the background. That must also hold when the SDK is
 * initialized after an activity has already resumed (deferred init, cross-platform wrappers such
 * as mixpanel-flutter), because registerActivityLifecycleCallbacks does not replay
 * onActivityResumed for an activity that is already resumed.
 *
 * <p>Robolectric does not initialize ProcessLifecycleOwner, so each test states the process
 * lifecycle the SDK would observe through {@link MixpanelAPI#isProcessResumed()}.
 */
@RunWith(RobolectricTestRunner.class)
public class MixpanelActivityLifecycleCallbacksTest {

  private Context mContext;
  private Future<SharedPreferences> mPreferences;
  private ActivityController<TestActivity> mActivity;
  private boolean mProcessResumed;

  @Before
  public void setUp() {
    mContext = ApplicationProvider.getApplicationContext();
    mPreferences = new TestUtils.EmptyPreferences(mContext);
  }

  @After
  public void tearDown() {
    if (mActivity != null) {
      mActivity.pause().stop().destroy();
      idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);
    }
  }

  @Test
  public void testFlushesOnFirstBackgroundWhenInitializedAfterActivityResumed() {
    mActivity = Robolectric.buildActivity(TestActivity.class).setup();
    mProcessResumed = true;

    FlushCountingMixpanelAPI mixpanel = new FlushCountingMixpanelAPI();
    idleMainLooper(0);

    assertTrue(mixpanel.isAppInForeground());

    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);

    assertEquals(1, mixpanel.flushCount.get());
    assertFalse(mixpanel.isAppInForeground());
  }

  @Test
  public void testFlushesAgainAfterReturningToForeground() {
    mActivity = Robolectric.buildActivity(TestActivity.class).setup();
    mProcessResumed = true;

    FlushCountingMixpanelAPI mixpanel = new FlushCountingMixpanelAPI();
    idleMainLooper(0);

    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);
    mActivity.resume();
    idleMainLooper(0);
    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);

    assertEquals(2, mixpanel.flushCount.get());
  }

  @Test
  public void testFlushesOnBackgroundWhenInitializedBeforeActivityResumed() {
    FlushCountingMixpanelAPI mixpanel = new FlushCountingMixpanelAPI();
    idleMainLooper(0);

    assertFalse(mixpanel.isAppInForeground());

    mActivity = Robolectric.buildActivity(TestActivity.class).setup();
    idleMainLooper(0);
    assertTrue(mixpanel.isAppInForeground());

    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);

    assertEquals(1, mixpanel.flushCount.get());
  }

  @Test
  public void testFlushesOnBackgroundWhenInitializedDuringActivityCreate() {
    mActivity = Robolectric.buildActivity(TestActivity.class).create();

    FlushCountingMixpanelAPI mixpanel = new FlushCountingMixpanelAPI();
    mActivity.start().resume().visible();
    mProcessResumed = true;
    idleMainLooper(0);

    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);

    assertEquals(1, mixpanel.flushCount.get());
  }

  @Test
  public void testActivityCallbackBeforeSeedWins() {
    mActivity = Robolectric.buildActivity(TestActivity.class).setup();
    // The process lifecycle reports RESUMED for 700 ms after the last activity pauses, so an
    // activity can pause between registration and the posted seed while the process still looks
    // resumed. The activity callback must win, or the next resume would not start a new session.
    mProcessResumed = true;

    FlushCountingMixpanelAPI mixpanel = new FlushCountingMixpanelAPI();
    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);

    assertFalse(mixpanel.isAppInForeground());

    mActivity.resume();
    idleMainLooper(0);
    assertEquals(1, mixpanel.foregroundCount.get());

    mActivity.pause();
    idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);
    assertEquals(1, mixpanel.flushCount.get());
  }

  private static void idleMainLooper(long delayMillis) {
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delayMillis));
  }

  private class FlushCountingMixpanelAPI extends TestUtils.CleanMixpanelAPI {
    final AtomicInteger flushCount = new AtomicInteger();
    final AtomicInteger foregroundCount = new AtomicInteger();

    FlushCountingMixpanelAPI() {
      super(mContext, mPreferences, UUID.randomUUID().toString());
    }

    @Override
    boolean isProcessResumed() {
      return mProcessResumed;
    }

    @Override
    void onForeground() {
      foregroundCount.incrementAndGet();
      super.onForeground();
    }

    @Override
    public void flush() {
      flushCount.incrementAndGet();
    }
  }
}
