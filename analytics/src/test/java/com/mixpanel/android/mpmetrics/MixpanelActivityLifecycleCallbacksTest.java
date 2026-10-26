package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for flushing on background when the SDK is initialized after an activity has resumed.
 */
@RunWith(RobolectricTestRunner.class)
public class MixpanelActivityLifecycleCallbacksTest implements LifecycleOwner {

    private TestUtils.EmptyPreferences mMockPreferences;
    private Context mContext;
    private LifecycleRegistry mProcessLifecycle;
    private ActivityController<TestActivity> mActivity;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mMockPreferences = new TestUtils.EmptyPreferences(mContext);
        // Robolectric does not initialize ProcessLifecycleOwner, so the tests drive this one
        mProcessLifecycle = new LifecycleRegistry(this);
        mProcessLifecycle.setCurrentState(Lifecycle.State.CREATED);
        mActivity = Robolectric.buildActivity(TestActivity.class);
    }

    @After
    public void tearDown() {
        mActivity.pause().stop().destroy();
        idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);
    }

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mProcessLifecycle;
    }

    @Test
    public void testFlushesOnBackgroundWhenInitializedAfterActivityResumed() {
        mActivity.setup();
        mProcessLifecycle.setCurrentState(Lifecycle.State.RESUMED);

        TestMixpanelAPI mixpanel = new TestMixpanelAPI();
        idleMainLooper(0);
        assertTrue(mixpanel.isAppInForeground());

        background();
        mProcessLifecycle.setCurrentState(Lifecycle.State.STARTED);
        assertFalse(mixpanel.isAppInForeground());
        assertEquals(1, mixpanel.flushCount.get());

        mActivity.resume();
        idleMainLooper(0);
        assertEquals(1, mixpanel.foregroundCount.get());

        background();
        assertEquals(2, mixpanel.flushCount.get());
    }

    @Test
    public void testFlushesOnBackgroundWhenInitializedBeforeActivityResumed() {
        TestMixpanelAPI mixpanel = new TestMixpanelAPI();
        idleMainLooper(0);
        assertFalse(mixpanel.isAppInForeground());

        mProcessLifecycle.setCurrentState(Lifecycle.State.RESUMED);
        mActivity.setup();
        idleMainLooper(0);
        assertTrue(mixpanel.isAppInForeground());
        assertEquals(1, mixpanel.foregroundCount.get());

        background();
        assertEquals(1, mixpanel.flushCount.get());
    }

    @Test
    public void testActivityPausedBeforeSeedStartsNewSessionOnResume() {
        mActivity.setup();
        mProcessLifecycle.setCurrentState(Lifecycle.State.RESUMED);

        TestMixpanelAPI mixpanel = new TestMixpanelAPI();
        background();
        assertFalse(mixpanel.isAppInForeground());

        mActivity.resume();
        idleMainLooper(0);
        assertEquals(1, mixpanel.foregroundCount.get());

        background();
        assertEquals(1, mixpanel.flushCount.get());
    }

    @Test
    public void testProcessPauseClearsSeedWhenActivityPausedBeforeRegistration() {
        // The process lifecycle stays RESUMED for a short delay after the last activity pauses
        mActivity.setup().pause();
        mProcessLifecycle.setCurrentState(Lifecycle.State.RESUMED);

        TestMixpanelAPI mixpanel = new TestMixpanelAPI();
        idleMainLooper(0);
        assertTrue(mixpanel.isAppInForeground());

        mProcessLifecycle.setCurrentState(Lifecycle.State.STARTED);
        assertFalse(mixpanel.isAppInForeground());
        assertEquals(1, mixpanel.flushCount.get());

        mActivity.resume();
        idleMainLooper(0);
        assertEquals(1, mixpanel.foregroundCount.get());
    }

    private void background() {
        mActivity.pause();
        idleMainLooper(MixpanelActivityLifecycleCallbacks.CHECK_DELAY);
    }

    private static void idleMainLooper(long delayMillis) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(delayMillis));
    }

    private class TestMixpanelAPI extends TestUtils.CleanMixpanelAPI {
        final AtomicInteger flushCount = new AtomicInteger();
        final AtomicInteger foregroundCount = new AtomicInteger();

        TestMixpanelAPI() {
            super(mContext, mMockPreferences, UUID.randomUUID().toString());
        }

        @Override
        Lifecycle getProcessLifecycle() {
            return mProcessLifecycle;
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
