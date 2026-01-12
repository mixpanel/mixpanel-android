package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.Future;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Instrumented tests for custom device ID functionality.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class CustomDeviceIdTest {

    private Future<SharedPreferences> mMockPreferences;

    @Before
    public void setUp() throws Exception {
        mMockPreferences = new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());
        AnalyticsMessages messages = AnalyticsMessages.getInstance(
                InstrumentationRegistry.getInstrumentation().getContext(),
                MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext(), null));
        messages.hardKill();
        Thread.sleep(2000);
    }

    @Test
    public void testCustomDeviceIdAtInitialization() {
        String customId = "my-custom-device-123";
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, customId);

        // Verify the anonymous ID is our custom ID
        assertEquals(customId, mixpanel.getAnonymousId());

        // Verify distinct_id has $device: prefix
        assertEquals("$device:" + customId, mixpanel.getDistinctId());
    }

    @Test
    public void testCustomDeviceIdPreservedOnReset() {
        String customId = "preserved-device-456";
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, customId);

        // Verify initial state
        assertEquals(customId, mixpanel.getAnonymousId());
        String originalDistinctId = mixpanel.getDistinctId();

        // Identify a user
        mixpanel.identify("user@example.com");
        assertEquals("user@example.com", mixpanel.getDistinctId());

        // Reset - custom device_id should be preserved
        mixpanel.reset();

        // Verify the custom device_id is preserved after reset
        assertEquals(customId, mixpanel.getAnonymousId());
        assertEquals(originalDistinctId, mixpanel.getDistinctId());
        assertEquals("$device:" + customId, mixpanel.getDistinctId());
    }

    @Test
    public void testNullDeviceIdFallsBackToUUID() {
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, null);

        // Should have a UUID-format anonymous ID
        String anonymousId = mixpanel.getAnonymousId();
        assertNotNull(anonymousId);
        assertTrue("Should be UUID format",
                anonymousId.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}"));
    }

    @Test
    public void testEmptyDeviceIdFallsBackToUUID() {
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, "");

        String anonymousId = mixpanel.getAnonymousId();
        assertNotNull(anonymousId);
        assertFalse(anonymousId.isEmpty());
        // Should be UUID format since empty string was rejected
        assertTrue("Should be UUID format after empty string rejected",
                anonymousId.matches("[A-Fa-f0-9]{8}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}-[A-Fa-f0-9]{12}"));
    }

    @Test
    public void testDeviceIdStartingWithDollarRejected() {
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, "$invalid-device-id");

        // Should have fallen back to UUID, not use the invalid ID
        String anonymousId = mixpanel.getAnonymousId();
        assertNotEquals("$invalid-device-id", anonymousId);
        assertFalse("Should not start with $", anonymousId.startsWith("$"));
    }

    @Test
    public void testWhitespaceOnlyDeviceIdRejected() {
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, "   ");

        // Should have fallen back to UUID
        String anonymousId = mixpanel.getAnonymousId();
        assertNotNull(anonymousId);
        assertFalse("Should not be whitespace only", anonymousId.trim().isEmpty());
    }

    @Test
    public void testResetWithoutCustomIdGeneratesNewUUID() {
        String fakeToken = UUID.randomUUID().toString();

        // Create without custom device ID
        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                InstrumentationRegistry.getInstrumentation().getContext(),
                mMockPreferences,
                fakeToken);

        String originalAnonymousId = mixpanel.getAnonymousId();
        assertNotNull(originalAnonymousId);

        // Reset should generate new UUID
        mixpanel.reset();

        String newAnonymousId = mixpanel.getAnonymousId();
        assertNotNull(newAnonymousId);
        assertNotEquals("Reset should generate new UUID when no custom ID was set",
                originalAnonymousId, newAnonymousId);
    }

    @Test
    public void testDistinctIdFormatWithCustomDeviceId() {
        String customId = "format-test-device-789";
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, customId);

        // distinct_id should start with $device:
        assertThat(mixpanel.getDistinctId(), startsWith("$device:"));

        // distinct_id should be "$device:" + customId
        assertEquals("$device:" + customId, mixpanel.getDistinctId());
    }

    @Test
    public void testCustomDeviceIdTrimmed() {
        String customIdWithSpaces = "  trimmed-device  ";
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, customIdWithSpaces);

        // Should be trimmed
        assertEquals("trimmed-device", mixpanel.getAnonymousId());
    }

    @Test
    public void testIdentifyThenResetPreservesCustomDeviceId() {
        String customId = "persist-through-identify";
        String fakeToken = UUID.randomUUID().toString();

        MixpanelAPI mixpanel = createCleanMixpanelWithCustomDeviceId(fakeToken, customId);

        // Initial state with custom device ID
        assertEquals(customId, mixpanel.getAnonymousId());
        assertEquals("$device:" + customId, mixpanel.getDistinctId());

        // Identify changes distinct_id to user ID
        mixpanel.identify("user123");
        assertEquals("user123", mixpanel.getDistinctId());

        // Anonymous ID should still be the custom device ID
        assertEquals(customId, mixpanel.getAnonymousId());

        // Reset clears user identity but preserves custom device ID
        mixpanel.reset();
        assertEquals(customId, mixpanel.getAnonymousId());
        assertEquals("$device:" + customId, mixpanel.getDistinctId());
    }

    /**
     * Helper method to create a clean MixpanelAPI instance with a custom device ID.
     */
    private MixpanelAPI createCleanMixpanelWithCustomDeviceId(String token, String customDeviceId) {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceId(customDeviceId)
                .featureFlagsEnabled(true)
                .build();

        return new CleanMixpanelAPIWithOptions(context, mMockPreferences, token, options);
    }

    /**
     * Extended CleanMixpanelAPI that accepts MixpanelOptions.
     */
    private static class CleanMixpanelAPIWithOptions extends MixpanelAPI {
        public CleanMixpanelAPIWithOptions(Context context, Future<SharedPreferences> referrerPreferences,
                                           String token, MixpanelOptions options) {
            super(context, referrerPreferences, token,
                    MPConfig.getInstance(context, options.getInstanceName()),
                    options, false);
        }

        @Override
        /* package */ PersistentIdentity getPersistentIdentity(Context context,
                                                                Future<SharedPreferences> referrerPreferences,
                                                                String token, String instanceName) {
            String instanceKey = instanceName != null ? instanceName : token;
            final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + instanceKey;
            final SharedPreferences ret = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
            ret.edit().clear().commit();

            final String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + instanceKey;
            final SharedPreferences timeSharedPrefs = context.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE);
            timeSharedPrefs.edit().clear().commit();

            final String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
            final SharedPreferences mpSharedPrefs = context.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
            mpSharedPrefs.edit().clear().putBoolean(token, true).putBoolean("has_launched", true).apply();

            return super.getPersistentIdentity(context, referrerPreferences, token, instanceName);
        }

        @Override
        /* package */ boolean sendAppOpen() {
            return false;
        }
    }
}
