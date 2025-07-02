package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

import java.util.UUID;

import javax.net.ssl.SSLSocketFactory;

/**
 * Test class to demonstrate and verify the fix for the SSL socket factory issue.
 * This test validates that when a user calls MPConfig.getInstance() to set an SSLSocketFactory,
 * the same instance is used by MixpanelAPI internally.
 */
@RunWith(AndroidJUnit4.class)
public class SSLSocketFactoryTest {

    @Test
    public void testSSLSocketFactoryConsistencyWithMixpanelAPI() {
        // Clear cache first to ensure clean state
        MPConfig.clearInstanceCache();
        
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String instanceName = "ssl-consistency-test";
        String fakeToken = UUID.randomUUID().toString();
        
        // Step 1: User calls MPConfig.getInstance() to configure SSL
        MPConfig userConfig = MPConfig.getInstance(context, instanceName);
        SSLSocketFactory originalFactory = userConfig.getSSLSocketFactory();
        
        // Simulate setting a custom SSL socket factory (we'll use the original for this test)
        userConfig.setSSLSocketFactory(originalFactory);
        
        // Step 2: User creates MixpanelAPI instance with the same instanceName
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, fakeToken, false, instanceName, false);
        
        // Step 3: Verify that MixpanelAPI uses the same MPConfig instance
        MPConfig mixpanelConfig = mixpanel.getMPConfig();
        
        // This should now pass with our fix - same instance should be returned
        assertTrue("MPConfig.getInstance() should return the same instance that MixpanelAPI uses", 
                   userConfig == mixpanelConfig);
        
        // This should also pass - the SSL socket factory should be the same
        assertTrue("SSLSocketFactory should be consistent between user config and MixpanelAPI config",
                   userConfig.getSSLSocketFactory() == mixpanelConfig.getSSLSocketFactory());
    }

    @Test
    public void testDifferentInstanceNamesGetDifferentConfigs() {
        // Clear cache first to ensure clean state
        MPConfig.clearInstanceCache();
        
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        
        // Get configs for different instance names
        MPConfig config1 = MPConfig.getInstance(context, "instance1");
        MPConfig config2 = MPConfig.getInstance(context, "instance2");
        MPConfig config3 = MPConfig.getInstance(context, null); // default instance
        
        // All should be different instances
        assertTrue("Different instance names should return different MPConfig instances",
                   config1 != config2);
        assertTrue("Named instance should be different from default instance",
                   config1 != config3);
        assertTrue("Different named instances should be different from each other",
                   config2 != config3);
        
        // But getting the same instance name multiple times should return the same instance
        MPConfig config1Again = MPConfig.getInstance(context, "instance1");
        assertTrue("Same instance name should return the same MPConfig instance",
                   config1 == config1Again);
    }
}