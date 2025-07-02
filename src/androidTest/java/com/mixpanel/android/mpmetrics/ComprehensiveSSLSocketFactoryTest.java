package com.mixpanel.android.mpmetrics;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.UUID;
import javax.net.ssl.SSLSocketFactory;

/**
 * Comprehensive test that verifies the MPConfig caching fix for GitHub issue #855.
 * This test ensures that users can now successfully set SSLSocketFactory on MPConfig instances
 * and have those settings apply to MixpanelAPI instances.
 */
@RunWith(AndroidJUnit4.class)
public class ComprehensiveSSLSocketFactoryTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        // Start each test with a clean cache to avoid test interference
        MPConfig.clearInstanceCache();
    }

    /**
     * Test the exact scenario described in GitHub issue #855.
     * This is the primary use case that was broken before the fix.
     */
    @Test
    public void testGitHubIssue855_SSLSocketFactoryAssignment() {
        String instanceName = "ssl-test-instance";
        String token = UUID.randomUUID().toString();
        
        // Step 1: User tries to configure SSL socket factory
        // Before fix: This would return a different instance than what MixpanelAPI uses
        MPConfig config = MPConfig.getInstance(mContext, instanceName);
        SSLSocketFactory originalFactory = config.getSSLSocketFactory();
        assertNotNull("SSL socket factory should be initialized", originalFactory);
        
        // User sets a custom SSL socket factory (we'll use the original for this test)
        config.setSSLSocketFactory(originalFactory);
        
        // Step 2: User creates MixpanelAPI with the same instance name
        // Before fix: This would create a new MPConfig internally, ignoring user's SSL setting
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(mContext, token, false, instanceName, false);
        
        // Step 3: Verify the fix works
        MPConfig internalConfig = mixpanel.getMPConfig();
        
        // THE FIX: These should be the same instance
        assertSame("User config and MixpanelAPI config should be the same instance", 
                   config, internalConfig);
        
        // SSL socket factory should be preserved
        assertSame("SSL socket factory should be preserved", 
                   originalFactory, internalConfig.getSSLSocketFactory());
    }

    /**
     * Test that the caching works correctly for default instances (null instance name).
     */
    @Test
    public void testDefaultInstanceCaching() {
        String token = UUID.randomUUID().toString();
        
        // Get default config and modify it
        MPConfig defaultConfig = MPConfig.getInstance(mContext, null);
        SSLSocketFactory customFactory = defaultConfig.getSSLSocketFactory();
        defaultConfig.setSSLSocketFactory(customFactory);
        
        // Create MixpanelAPI with default instance (no instance name)
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(mContext, token, false);
        
        // Should use the same config instance
        assertSame("Default instance should be cached correctly", 
                   defaultConfig, mixpanel.getMPConfig());
    }

    /**
     * Test that different instance names get different config instances.
     */
    @Test
    public void testInstanceIsolation() {
        MPConfig config1 = MPConfig.getInstance(mContext, "instance1");
        MPConfig config2 = MPConfig.getInstance(mContext, "instance2");
        MPConfig defaultConfig = MPConfig.getInstance(mContext, null);
        
        // All should be different instances
        assertNotSame("Different instance names should have different configs", config1, config2);
        assertNotSame("Named instance should differ from default", config1, defaultConfig);
        assertNotSame("Named instance should differ from default", config2, defaultConfig);
        
        // But same names should return same instances
        MPConfig config1Again = MPConfig.getInstance(mContext, "instance1");
        assertSame("Same instance name should return same config", config1, config1Again);
    }

    /**
     * Test thread safety of the caching mechanism.
     */
    @Test
    public void testThreadSafety() throws InterruptedException {
        final String instanceName = "thread-test";
        final MPConfig[] configs = new MPConfig[10];
        final Thread[] threads = new Thread[10];
        
        // Create multiple threads that try to get the same config instance
        for (int i = 0; i < threads.length; i++) {
            final int index = i;
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    configs[index] = MPConfig.getInstance(mContext, instanceName);
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // All threads should have gotten the same instance
        for (int i = 1; i < configs.length; i++) {
            assertSame("All threads should get the same config instance", configs[0], configs[i]);
        }
    }

    /**
     * Test that the cache survives multiple MixpanelAPI creations.
     */
    @Test
    public void testCachePersistenceAcrossMultipleMixpanelInstances() {
        String instanceName = "persistence-test";
        String token1 = UUID.randomUUID().toString();
        String token2 = UUID.randomUUID().toString();
        
        // Get config and modify it
        MPConfig config = MPConfig.getInstance(mContext, instanceName);
        SSLSocketFactory factory = config.getSSLSocketFactory();
        config.setSSLSocketFactory(factory);
        
        // Create first MixpanelAPI instance
        MixpanelAPI mixpanel1 = MixpanelAPI.getInstance(mContext, token1, false, instanceName, false);
        assertSame("First MixpanelAPI should use cached config", config, mixpanel1.getMPConfig());
        
        // Create second MixpanelAPI instance with different token but same instance name
        MixpanelAPI mixpanel2 = MixpanelAPI.getInstance(mContext, token2, false, instanceName, false);
        assertSame("Second MixpanelAPI should use same cached config", config, mixpanel2.getMPConfig());
        
        // Both MixpanelAPI instances should use the same config
        assertSame("Both MixpanelAPI instances should share the same config", 
                   mixpanel1.getMPConfig(), mixpanel2.getMPConfig());
    }
}