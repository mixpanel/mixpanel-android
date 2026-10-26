package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Initializes the SDK after an activity has resumed, as cross-platform wrappers do, and checks
 * that the first trip to the background flushes. Runs against the real ProcessLifecycleOwner,
 * which the JVM tests have to stub.
 */
@RunWith(AndroidJUnit4.class)
public class DeferredInitFlushInstrumentedTest {

  @Test
  public void testFirstBackgroundFlushesWhenInitializedAfterActivityResumed() throws Exception {
    final Context context = ApplicationProvider.getApplicationContext();
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      final AtomicInteger flushCount = new AtomicInteger();
      final MixpanelAPI mixpanel =
          new TestUtils.CleanMixpanelAPI(
              context, new TestUtils.EmptyPreferences(context), UUID.randomUUID().toString()) {
            @Override
            public void flush() {
              flushCount.incrementAndGet();
            }
          };
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();

      assertTrue(mixpanel.isAppInForeground());

      // A real Home press: ActivityScenario.moveToState stops the activity by starting another
      // activity in this process, which keeps the app in the foreground.
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .executeShellCommand("input keyevent KEYCODE_HOME")
          .close();
      waitForState(scenario, Lifecycle.State.CREATED);
      Thread.sleep(MixpanelActivityLifecycleCallbacks.CHECK_DELAY * 2);
      InstrumentationRegistry.getInstrumentation().waitForIdleSync();

      assertEquals(1, flushCount.get());
    }
  }

  private static void waitForState(ActivityScenario<?> scenario, Lifecycle.State state)
      throws InterruptedException {
    final long deadline = System.currentTimeMillis() + 5000;
    while (scenario.getState() != state && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    assertEquals(state, scenario.getState());
  }
}
