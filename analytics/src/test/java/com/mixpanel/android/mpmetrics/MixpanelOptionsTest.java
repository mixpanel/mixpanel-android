package com.mixpanel.android.mpmetrics;

import com.mixpanel.android.util.ProxyServerInteractor;

import org.json.JSONObject;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        assertNotNull(options.getExcludeProperties());
        assertTrue(options.getExcludeProperties().isEmpty());
    }

    @Test
    public void testExcludeProperties() {
        Set<String> exclude = new HashSet<>();
        exclude.add("$screen_height");
        exclude.add("$lib_version");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(exclude)
                .build();

        assertEquals(2, options.getExcludeProperties().size());
        assertTrue(options.getExcludeProperties().contains("$screen_height"));
        assertTrue(options.getExcludeProperties().contains("$lib_version"));
    }

    @Test
    public void testExcludePropertiesDefensiveCopy() {
        Set<String> exclude = new HashSet<>();
        exclude.add("$screen_height");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(exclude)
                .build();

        // Mutating the source should not affect the stored set.
        exclude.add("$lib_version");
        assertEquals(1, options.getExcludeProperties().size());
        assertTrue(options.getExcludeProperties().contains("$screen_height"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testExcludePropertiesGetterIsImmutable() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(Collections.singleton("$screen_height"))
                .build();
        options.getExcludeProperties().add("anything");
    }

    @Test
    public void testNullExcludeProperties() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(null)
                .build();

        assertNotNull(options.getExcludeProperties());
        assertTrue(options.getExcludeProperties().isEmpty());
    }

    @Test
    public void testEmptyExcludeProperties() {
        MixpanelOptions options = new MixpanelOptions.Builder()
                .excludeProperties(Collections.emptySet())
                .build();

        assertNotNull(options.getExcludeProperties());
        assertTrue(options.getExcludeProperties().isEmpty());
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
    public void testFeatureFlagsContextGetterDefensiveCopy() throws Exception {
        JSONObject context = new JSONObject();
        context.put("key", "original");

        MixpanelOptions options = new MixpanelOptions.Builder()
                .featureFlagsContext(context)
                .build();

        // Modify returned copy - should not affect internal state
        JSONObject copy = options.getFeatureFlagsContext();
        copy.put("key", "modified");

        assertEquals("original", options.getFeatureFlagsContext().getString("key"));
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

    @Test
    public void testServerURLWithProxyInteractor() {
        ProxyServerInteractor interactor = new ProxyServerInteractor() {
            @Override
            public Map<String, String> getProxyRequestHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization", "Bearer test-token");
                return headers;
            }

            @Override
            public void onProxyResponse(String apiPath, int responseCode) {
            }
        };

        MixpanelOptions options = new MixpanelOptions.Builder()
                .serverURL("https://proxy.example.com", interactor)
                .build();

        assertEquals("https://proxy.example.com", options.getServerURL());
        assertNotNull(options.getProxyServerInteractor());
        Map<String, String> headers = options.getProxyServerInteractor().getProxyRequestHeaders();
        assertEquals("Bearer test-token", headers.get("Authorization"));
    }
}
