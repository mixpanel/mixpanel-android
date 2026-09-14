package com.mixpanel.android.mpmetrics;

import android.os.Looper;
import com.mixpanel.android.util.MPLog;
import java.time.Duration;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;

/**
 * Safe replacements for the ad-hoc "idle every looper in the JVM" helpers that
 * HttpTest and FeatureFlagManagerTest each used to carry.
 *
 * <p>The naive version deadlocks. {@link ShadowLooper#getAllLoopers()} returns every looper ever
 * created in this JVM, and Robolectric's paused-looper implementation idles a <em>background</em>
 * looper by posting a control runnable to that looper's thread and blocking on an unbounded {@code
 * CountDownLatch.await()}. When the target thread has already exited — which happens constantly,
 * because these tests leak an AnalyticsMessages worker per test method — nothing ever counts the
 * latch down and the test JVM hangs until CI kills the job:
 *
 * <pre>
 *   "SDK 34 Main Thread"  WAITING (parking)
 *     CountDownLatch.await()
 *     ShadowPausedLooper$ControlRunnable.waitTillComplete(ShadowPausedLooper.java:554)
 *     ShadowPausedLooper.executeOnLooper(ShadowPausedLooper.java:640)
 *     ShadowPausedLooper.idle(ShadowPausedLooper.java:104)
 * </pre>
 *
 * <p>A {@code try/catch (RuntimeException)} does not help: a dead looper does not throw, it blocks.
 * Three guards are applied instead: skip loopers whose thread is gone, bound whatever is left with
 * a timeout, and retire a looper permanently once it has blown that timeout. The last one matters
 * because these helpers are called in tight loops — without it a single wedged looper costs the
 * timeout on every one of those calls and the suite still runs for hours.
 */
final class LooperTestUtils {
  private static final String LOGTAG = "MixpanelAPI.LooperTestUtils";

  /** Generous enough for a healthy looper, short enough that CI fails instead of hanging. */
  private static final long IDLE_TIMEOUT_SECONDS = 5;

  /**
   * Loopers that have already blown the timeout once. A wedged looper stays wedged, and the
   * helpers here are called a lot -- flushAllLoopers() alone idles every looper 20 times, and
   * FeatureFlagManagerTest reaches it from 34 call sites. Retrying a wedged looper each time
   * would turn one bad looper into hours of wall clock and still bust the workflow timeout, so
   * the first timeout retires it for the rest of the JVM.
   */
  private static final Set<Looper> WEDGED_LOOPERS =
      Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

  private static final ExecutorService IDLE_EXECUTOR =
      Executors.newCachedThreadPool(
          runnable -> {
            final Thread thread = new Thread(runnable, "LooperTestUtils-idle");
            // Must never hold the test JVM open, which is the failure mode this class exists for.
            thread.setDaemon(true);
            return thread;
          });

  private LooperTestUtils() {}

  /** Idles the main looper and every live background looper once. */
  static void idleAllLoopers() {
    ShadowLooper.idleMainLooper();
    forEachLiveLooper(looper -> Shadows.shadowOf(looper).idle());
  }

  /** Advances the main looper and every live background looper by {@code duration}. */
  static void idleAllLoopersFor(Duration duration) {
    forEachLiveLooper(looper -> Shadows.shadowOf(looper).idleFor(duration));
  }

  private static void forEachLiveLooper(LooperAction action) {
    for (final Looper looper : ShadowLooper.getAllLoopers()) {
      final Thread thread = looper.getThread();
      // A looper whose thread has exited can never run the control runnable that
      // ShadowPausedLooper posts, so idling it would block forever. Leaked workers from
      // earlier tests in this JVM are the common case.
      if (thread == null || !thread.isAlive() || WEDGED_LOOPERS.contains(looper)) {
        continue;
      }

      if (looper == Looper.getMainLooper()) {
        // Robolectric handles the main looper on the test thread itself; going through the
        // executor would only change which thread reports the failure.
        runGuarded(looper, action);
        continue;
      }

      // The thread was alive a moment ago, but a looper quit between that check and now would
      // still wedge us, so bound the wait.
      final Future<?> pending = IDLE_EXECUTOR.submit(() -> runGuarded(looper, action));
      try {
        pending.get(IDLE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        pending.cancel(true);
        if (WEDGED_LOOPERS.add(looper)) {
          MPLog.w(
              LOGTAG,
              "Looper " + looper + " did not idle within " + IDLE_TIMEOUT_SECONDS
                  + "s; skipping it for the rest of this JVM",
              e);
        }
      } catch (Exception e) {
        MPLog.w(LOGTAG, "Failed to idle looper " + looper, e);
      }
    }
  }

  private static void runGuarded(Looper looper, LooperAction action) {
    try {
      action.run(looper);
    } catch (RuntimeException e) {
      // Stale loopers legitimately throw once their test's resources are torn down.
      MPLog.w(LOGTAG, "Ignoring exception while idling looper " + looper, e);
    }
  }

  private interface LooperAction {
    void run(Looper looper);
  }
}
