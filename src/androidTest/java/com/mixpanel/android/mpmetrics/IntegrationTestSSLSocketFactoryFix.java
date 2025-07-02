package com.mixpanel.android.mpmetrics;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
import java.util.UUID;
import javax.net.ssl.SSLSocketFactory;

/**
 * Integration test that demonstrates the exact scenario described in the GitHub issue.
 * Before the fix: Setting SSLSocketFactory would not work because different instances were returned.
 * After the fix: Setting SSLSocketFactory works because the same instance is returned and used.
 */
@RunWith(AndroidJUnit4.class)
public class IntegrationTestSSLSocketFactoryFix {

    @Test
    public void testGitHubIssueScenario() {
        // Clear cache to start fresh
        MPConfig.clearInstanceCache();
        
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String instanceName = "my-custom-instance";
        String fakeToken = UUID.randomUUID().toString();
        
        // Step 1: This is what users were trying to do (the old broken way described in the issue)
        // Before our fix, this would create a new MPConfig instance that would not be used by MixpanelAPI
        MPConfig userConfig = MPConfig.getInstance(context, instanceName);
        
        // Get the original SSL socket factory
        SSLSocketFactory originalFactory = userConfig.getSSLSocketFactory();
        assertNotNull("Original SSL socket factory should not be null", originalFactory);
        
        // Simulate setting a custom SSL socket factory
        // For this test we just set it to the original factory to verify consistency
        userConfig.setSSLSocketFactory(originalFactory);
        
        // Step 2: User creates MixpanelAPI instance with the same instance name
        // Before the fix: This would create a new MPConfig instance, ignoring the user's configuration
        // After the fix: This should use the same MPConfig instance that the user configured
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, fakeToken, false, instanceName, false);
        
        // Step 3: Verify the fix - both should reference the same MPConfig instance
        MPConfig internalConfig = mixpanel.getMPConfig();
        
        // THE FIX: These should now be the same instance
        assertSame("MPConfig.getInstance() should return the same instance used by MixpanelAPI", 
                   userConfig, internalConfig);
        
        // This means the SSL socket factory setting is preserved
        assertSame("SSL socket factory should be preserved across getInstance calls",
                   userConfig.getSSLSocketFactory(), internalConfig.getSSLSocketFactory());
        
        // Step 4: Verify that getting the config again still returns the same instance
        MPConfig userConfig2 = MPConfig.getInstance(context, instanceName);
        assertSame("Subsequent calls to MPConfig.getInstance() should return the same cached instance",
                   userConfig, userConfig2);
    }

    @Test 
    public void testDefaultInstanceIsConsistent() {
        // Clear cache to start fresh
        MPConfig.clearInstanceCache();
        
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        String fakeToken = UUID.randomUUID().toString();
        
        // Test with default instance (null instanceName)
        MPConfig userConfig = MPConfig.getInstance(context, null);
        SSLSocketFactory customFactory = userConfig.getSSLSocketFactory();
        userConfig.setSSLSocketFactory(customFactory);
        
        // Create MixpanelAPI with default instance (no instanceName specified)
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(context, fakeToken, false);
        MPConfig internalConfig = mixpanel.getMPConfig();
        
        // Should be the same instance for default case too
        assertSame("Default MPConfig instance should be consistent", userConfig, internalConfig);
    }
}