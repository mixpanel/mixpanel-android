package com.mixpanel.android.mpmetrics;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class MixpanelOptionsTest {

    @Test
    public void testDefaultBuilderValues() {
        MixpanelOptions options = new MixpanelOptions.Builder().build();

        assertNull(options.getInstanceName());
        assertFalse(options.isOptOutTrackingDefault());
        assertNull(options.getSuperProperties());
        assertFalse(options.areFeatureFlagsEnabled());
        assertNotNull(options.getFeatureFlagsContext());
        assertEquals(0, options.getFeatureFlagsContext().length());
        assertNull(options.getDeviceIdProvider());
        assertNull(options.getServerURL());
        assertNull(options.getProxyServerInteractor());
    }

    @Test
    public void testInstanceName() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .instanceName("test_instance")
                .build();

        assertEquals("test_instance", options.getInstanceName());
    }

    @Test
    public void testOptOutTrackingDefault() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .optOutTrackingDefault(true)
                .build();

        assertTrue(options.isOptOutTrackingDefault());
    }

    @Test
    public void testSuperProperties() throws Exception {
        JSONObject props = new JSONObject();
        props.put("plan", "premium");
        props.put("version", 2);

        MixpanelOptions options = new MixpanelOptions.Builder()
                .superProperties(props)
                .build();

        JSONObject result = options.getSuperProperties();
        assertNotNull(result);
        assertEquals("premium", result.getString("plan"));
        assertEquals(2, result.getInt("version"));
    }

    @Test
    public void testSuperPropertiesDefensiveCopy() throws Exception {
        JSONObject props = new JSONObject();
        props.put("key", "original");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .superProperties(props)
                .build();

        // Modify original - should not affect options
        props.put("key", "modified");

        JSONObject result = options.getSuperProperties();
        assertEquals("original", result.getString("key"));
    }

    @Test
    public void testSuperPropertiesGetterDefensiveCopy() throws Exception {
        JSONObject props = new JSONObject();
        props.put("key", "original");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .superProperties(props)
                .build();

        // Modify returned copy - should not affect internal state
        JSONObject copy = options.getSuperProperties();
        copy.put("key", "modified");

        assertEquals("original", options.getSuperProperties().getString("key"));
    }

    @Test
    public void testNullSuperProperties() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .superProperties(null)
                .build();

        assertNull(options.getSuperProperties());
    }

    @Test
    public void testFeatureFlagsEnabled() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .featureFlagsEnabled(true)
                .build();

        assertTrue(options.areFeatureFlagsEnabled());
    }

    @Test
    public void testFeatureFlagsContext() throws Exception {
        JSONObject context = new JSONObject();
        context.put("user_type", "beta");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .featureFlagsContext(context)
                .build();

        JSONObject result = options.getFeatureFlagsContext();
        assertNotNull(result);
        assertEquals("beta", result.getString("user_type"));
    }

    @Test
    public void testFeatureFlagsContextDefensiveCopy() throws Exception {
        JSONObject context = new JSONObject();
        context.put("key", "original");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .featureFlagsContext(context)
                .build();

        // Modify original
        context.put("key", "modified");

        assertEquals("original", options.getFeatureFlagsContext().getString("key"));
    }

    @Test
    public void testNullFeatureFlagsContext() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .featureFlagsContext(null)
                .build();

        assertNotNull(options.getFeatureFlagsContext());
        assertEquals(0, options.getFeatureFlagsContext().length());
    }

    @Test
    public void testServerURL() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .serverURL("https://api-eu.mixpanel.com")
                .build();

        assertEquals("https://api-eu.mixpanel.com", options.getServerURL());
    }

    @Test
    public void testBuilderChaining() throws Exception {
        JSONObject props = new JSONObject();
        props.put("plan", "free");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .instanceName("my_instance")
                .optOutTrackingDefault(false)
                .superProperties(props)
                .featureFlagsEnabled(true)
                .serverURL("https://custom.example.com")
                .build();

        assertEquals("my_instance", options.getInstanceName());
        assertFalse(options.isOptOutTrackingDefault());
        assertNotNull(options.getSuperProperties());
        assertTrue(options.areFeatureFlagsEnabled());
        assertEquals("https://custom.example.com", options.getServerURL());
    }

    @Test
    public void testDeviceIdProvider() {
        DeviceIdProvider provider = () -> "custom-device-id";

        MixpanelOptions options = new MixpanelOptions.Builder()
                .deviceIdProvider(provider)
                .build();

        assertNotNull(options.getDeviceIdProvider());
        assertEquals("custom-device-id", options.getDeviceIdProvider().getDeviceId());
    }
}
