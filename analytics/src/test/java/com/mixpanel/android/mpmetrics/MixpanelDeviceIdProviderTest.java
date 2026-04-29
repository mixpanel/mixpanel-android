package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests for the DeviceIdProvider feature.
 *
 * This test class follows TDD principles - tests are written first to define
 * expected behavior, then implementation is added to make them pass.
 */
@RunWith(RobolectricTestRunner.class)
public class MixpanelDeviceIdProviderTest {

    private TestUtils.EmptyPreferences mMockPreferences;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = ApplicationProvider.getApplicationContext();
        mMockPreferences = new TestUtils.EmptyPreferences(mContext);

        // Kill any existing AnalyticsMessages to ensure clean state
        AnalyticsMessages messages = AnalyticsMessages.getInstance(
                mContext, MPConfig.getInstance(mContext, null));
        messages.hardKill();
        Thread.sleep(500);
    }

    @After
    public void tearDown() throws Exception {
        // Clean up any singleton instances
        AnalyticsMessages messages = AnalyticsMessages.getInstance(
                mContext, MPConfig.getInstance(mContext, null));
        messages.hardKill();
        Thread.sleep(200);
    }

    private void clearPreferencesForToken(String token) {
        String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + token;
        SharedPreferences prefs = mContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();

        String timeEventsPrefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI.TimeEvents_" + token;
        SharedPreferences timePrefs = mContext.getSharedPreferences(timeEventsPrefsName, Context.MODE_PRIVATE);
        timePrefs.edit().clear().commit();

        String mixpanelPrefsName = "com.mixpanel.android.mpmetrics.Mixpanel";
        SharedPreferences mpPrefs = mContext.getSharedPreferences(mixpanelPrefsName, Context.MODE_PRIVATE);
        mpPrefs.edit().clear().commit();
    }

    // ========================================================================
    // Test Group 1: Basic Provider Functionality
    // ========================================================================

    /**
     * Test 1.1: Verify provider is called on first identity access (lazy loading) and value is used
     */
    @Test
    public void testDeviceIdProviderIsCalledOnFirstAccess() {
        final AtomicInteger callCount = new AtomicInteger(0);
        final String customDeviceId = "custom-device-id-12345";
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    callCount.incrementAndGet();
                    return customDeviceId;
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Provider should not be called until first identity access (lazy loading)
        assertEquals("deviceIdProvider should not be called until first access", 0, callCount.get());

        // Trigger lazy load by accessing identity
        String anonymousId = mixpanel.getAnonymousId();

        assertTrue("deviceIdProvider should be called on first access", callCount.get() >= 1);
        assertEquals("anonymousId should be set from provider", customDeviceId, anonymousId);
        assertEquals("distinctId should use provider value with prefix",
                "$device:" + customDeviceId, mixpanel.getDistinctId());
    }

    /**
     * Test 1.2: Verify backward compatibility when no provider is set
     */
    @Test
    public void testNilProviderUsesDefaultBehavior() {
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(null)
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Should use UUID or other default - just verify it's not null/empty
        assertNotNull("anonymousId should be generated", mixpanel.getAnonymousId());
        assertFalse("anonymousId should not be empty", mixpanel.getAnonymousId().isEmpty());
        assertTrue("distinctId should have device prefix",
                mixpanel.getDistinctId().startsWith("$device:"));
    }

    /**
     * Test 1.3: Verify that the SDK identity values use the provider-generated device ID
     * Events will use these same identity values.
     */
    @Test
    public void testDeviceIdProviderValueUsedInEvents() throws Exception {
        final String customDeviceId = "my-custom-device-123";
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> customDeviceId)
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Verify the identity values that events will use
        assertEquals("anonymousId (used as $device_id in events) should be provider value",
                customDeviceId, mixpanel.getAnonymousId());
        assertEquals("distinctId (used as distinct_id in events) should use provider value with prefix",
                "$device:" + customDeviceId, mixpanel.getDistinctId());

        // Track an event to ensure no crash
        mixpanel.track("Test Event");

        // Verify identity values remain consistent after tracking
        assertEquals("anonymousId should remain consistent after tracking",
                customDeviceId, mixpanel.getAnonymousId());
        assertEquals("distinctId should remain consistent after tracking",
                "$device:" + customDeviceId, mixpanel.getDistinctId());
    }

    // ========================================================================
    // Test Group 2: Reset Behavior
    // ========================================================================

    /**
     * Test 2.1: Verify that reset() calls the provider to get a new device ID
     */
    @Test
    public void testResetCallsDeviceIdProvider() {
        final AtomicInteger callCount = new AtomicInteger(0);
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    int count = callCount.incrementAndGet();
                    return "device-id-" + count;
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        int initialCallCount = callCount.get();
        String initialDeviceId = mixpanel.getAnonymousId();

        mixpanel.reset();

        assertTrue("Provider should be called again on reset", callCount.get() > initialCallCount);
        assertNotEquals("Device ID should change after reset", initialDeviceId, mixpanel.getAnonymousId());
    }

    /**
     * Test 2.2: Demonstrate that returning the same value = "never reset"
     */
    @Test
    public void testProviderReturningSameValuePersistsAcrossReset() {
        final String persistentDeviceId = "persistent-device-id";
        final AtomicInteger callCount = new AtomicInteger(0);
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    callCount.incrementAndGet();
                    return persistentDeviceId;  // Always return same value
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        int callCountAfterInit = callCount.get();

        mixpanel.reset();
        mixpanel.reset();

        // Provider is called on each reset
        assertTrue("Provider should be called on each reset", callCount.get() > callCountAfterInit);
        assertEquals("Device ID should persist when provider returns same value",
                persistentDeviceId, mixpanel.getAnonymousId());
    }

    /**
     * Test 2.3: Demonstrate that returning different values = "reset behavior"
     */
    @Test
    public void testProviderReturningDifferentValueResetsDeviceId() {
        final AtomicInteger callCount = new AtomicInteger(0);
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    int count = callCount.incrementAndGet();
                    return "device-id-" + count;  // Different value each time
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        String initialDeviceId = mixpanel.getAnonymousId();
        assertNotNull("Should have initial device ID", initialDeviceId);

        mixpanel.reset();

        assertNotEquals("Device ID should change when provider returns new value",
                initialDeviceId, mixpanel.getAnonymousId());
    }

    // ========================================================================
    // Test Group 3: OptOutTracking Behavior
    // ========================================================================

    /**
     * Test 3.1: Verify that optOutTracking() uses the provider
     */
    @Test
    public void testOptOutTrackingCallsDeviceIdProvider() {
        final AtomicInteger callCount = new AtomicInteger(0);
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    int count = callCount.incrementAndGet();
                    return "opt-out-device-" + count;
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        int callCountAfterInit = callCount.get();
        String initialDeviceId = mixpanel.getAnonymousId();

        mixpanel.optOutTracking();

        assertTrue("Provider should be called on optOutTracking", callCount.get() > callCountAfterInit);
        assertNotEquals("Device ID should change after opt-out when provider returns different value",
                initialDeviceId, mixpanel.getAnonymousId());
    }

    // ========================================================================
    // Test Group 4: Migration/Warning Behavior
    // ========================================================================

    /**
     * Test 4.1: Verify behavior when provider replaces existing ID
     * When persisted identity exists, the provider is called for comparison but
     * the persisted value should be used to preserve identity continuity.
     */
    @Test
    public void testPersistedIdentityPreservedWhenProviderDiffers() throws Exception {
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        // First, create an instance WITHOUT a provider to establish persisted identity
        // Use CleanMixpanelAPI which clears prefs first, then creates fresh identity
        MixpanelAPI mixpanel1 = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token);

        String originalAnonymousId = mixpanel1.getAnonymousId();
        assertNotNull("Should have original anonymous ID", originalAnonymousId);

        // Force persistence
        mixpanel1.flush();
        Thread.sleep(500);

        // Reinitialize WITH a provider that returns a DIFFERENT value
        // Use regular MixpanelAPI.getInstance() to read persisted identity
        // (Not CleanMixpanelAPI which would clear the prefs)
        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> "completely-different-device-id")
                .build();

        MixpanelAPI mixpanel2 = MixpanelAPI.getInstance(mContext, token, false, options);

        // The persisted value should be used (not the provider value)
        // This test documents expected behavior - the warning is logged internally
        assertEquals("Should use persisted anonymousId to preserve identity",
                originalAnonymousId, mixpanel2.getAnonymousId());
    }

    /**
     * Test 4.2: Verify no issues when provider matches existing ID
     */
    @Test
    public void testNoIssueWhenProviderMatchesExistingAnonymousId() throws Exception {
        final String persistentId = "always-the-same-id";
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> persistentId)
                .build();

        // First init
        MixpanelAPI mixpanel1 = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        assertEquals(persistentId, mixpanel1.getAnonymousId());
        mixpanel1.flush();
        Thread.sleep(500);

        // Second init with same provider returning same value
        MixpanelAPI mixpanel2 = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Should work without issues
        assertEquals("Should use same ID without issues", persistentId, mixpanel2.getAnonymousId());
    }

    // ========================================================================
    // Test Group 5: Persistence Behavior
    // ========================================================================

    /**
     * Test 5.1: Provider IS called when there's no persisted identity
     */
    @Test
    public void testProviderCalledWhenNoPersistedIdentity() {
        final AtomicInteger callCount = new AtomicInteger(0);
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    callCount.incrementAndGet();
                    return "fresh-device-id";
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Trigger lazy loading by accessing identity
        String anonymousId = mixpanel.getAnonymousId();

        assertTrue("Provider should be called when no persisted identity", callCount.get() >= 1);
        assertEquals("fresh-device-id", anonymousId);
    }

    // ========================================================================
    // Test Group 6: Edge Cases
    // ========================================================================

    /**
     * Test 6.1: Provider returning empty string should fall back to default
     */
    @Test
    public void testProviderReturningEmptyStringFallsBackToDefault() {
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> "")  // Empty string
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Should fall back to default behavior (UUID)
        assertNotNull("Should have anonymousId", mixpanel.getAnonymousId());
        assertFalse("Should not have empty anonymousId - should fall back to default",
                mixpanel.getAnonymousId().isEmpty());
    }

    /**
     * Test 6.2: Provider throwing exception should fall back to default
     */
    @Test
    public void testProviderThrowingExceptionFallsBackToDefault() {
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> {
                    throw new RuntimeException("Simulated provider failure");
                })
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Should fall back to default behavior without crashing
        assertNotNull("Should have anonymousId even if provider throws", mixpanel.getAnonymousId());
        assertFalse("Should not have empty anonymousId",
                mixpanel.getAnonymousId().isEmpty());
    }

    /**
     * Test 6.3: Verify identify() works correctly with provider-generated device ID
     */
    @Test
    public void testIdentifyWithDeviceIdProvider() {
        final String customDeviceId = "provider-device-id";
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> customDeviceId)
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Before identify
        assertEquals("distinctId should use provider value before identify",
                "$device:" + customDeviceId, mixpanel.getDistinctId());

        // After identify
        String userId = "user@example.com";
        mixpanel.identify(userId);

        assertEquals("distinctId should be userId after identify", userId, mixpanel.getDistinctId());
        assertEquals("anonymousId should still be provider value after identify",
                customDeviceId, mixpanel.getAnonymousId());
    }

    /**
     * Test 6.4: Multiple instances can have different providers
     */
    @Test
    public void testMultipleInstancesWithDifferentProviders() {
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();
        clearPreferencesForToken(token1);
        clearPreferencesForToken(token2);

        MixpanelOptions options1 = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> "instance-1-device")
                .build();

        MixpanelOptions options2 = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> "instance-2-device")
                .build();

        MixpanelAPI instance1 = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token1, options1);
        MixpanelAPI instance2 = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token2, options2);

        assertEquals("Instance 1 should use its provider",
                "instance-1-device", instance1.getAnonymousId());
        assertEquals("Instance 2 should use its provider",
                "instance-2-device", instance2.getAnonymousId());
        assertNotEquals("Instances should have different device IDs",
                instance1.getAnonymousId(), instance2.getAnonymousId());
    }

    /**
     * Test 6.5: Provider returning whitespace-only string should fall back to default
     */
    @Test
    public void testProviderReturningWhitespaceOnlyFallsBackToDefault() {
        String token = UUID.randomUUID().toString();
        clearPreferencesForToken(token);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(() -> "   \t\n  ")  // Whitespace only
                .build();

        MixpanelAPI mixpanel = new TestUtils.CleanMixpanelAPI(
                mContext, mMockPreferences, token, options);

        // Should fall back to default behavior (UUID)
        assertNotNull("Should have anonymousId", mixpanel.getAnonymousId());
        assertFalse("Should not have empty anonymousId - should fall back to default",
                mixpanel.getAnonymousId().isEmpty());
        assertFalse("Should not have whitespace-only anonymousId",
                mixpanel.getAnonymousId().trim().isEmpty());
    }
}
