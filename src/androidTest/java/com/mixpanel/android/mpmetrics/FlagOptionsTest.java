package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.ProxyServerInteractor;
import com.mixpanel.android.util.RemoteService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class FlagOptionsTest {

  private Future<SharedPreferences> mMockPreferences;

  private static final int POLL_WAIT_SECONDS = 10;

  @Before
  public void setUp() throws Exception {
    mMockPreferences =
        new TestUtils.EmptyPreferences(InstrumentationRegistry.getInstrumentation().getContext());
    AnalyticsMessages messages =
        AnalyticsMessages.getInstance(
            InstrumentationRegistry.getInstrumentation().getContext(),
            MPConfig.getInstance(InstrumentationRegistry.getInstrumentation().getContext(), null));
    messages.hardKill();
    Thread.sleep(2000);
  }

  // -----------------------------------------------------------------------
  // FlagOptions defaults
  // -----------------------------------------------------------------------

  @Test
  public void testFlagOptions_DefaultValues() {
    FlagOptions defaults = new FlagOptions.Builder().build();

    assertFalse("enabled should default to false", defaults.isEnabled());
    assertNotNull("context should not be null", defaults.getContext());
    assertEquals("context should be empty", 0, defaults.getContext().length());
    assertTrue("loadOnFirstForeground should default to true", defaults.shouldLoadOnFirstForeground());
  }

  // -----------------------------------------------------------------------
  // FlagOptions custom values
  // -----------------------------------------------------------------------

  @Test
  public void testFlagOptions_CustomValues() throws JSONException {
    JSONObject context = new JSONObject();
    context.put("plan", "enterprise");
    context.put("beta", true);

    FlagOptions options = new FlagOptions.Builder()
        .setEnabled(true)
        .setContext(context)
        .setLoadOnFirstForeground(false)
        .build();

    assertTrue("enabled should be true", options.isEnabled());
    assertEquals("enterprise", options.getContext().getString("plan"));
    assertTrue(options.getContext().getBoolean("beta"));
    assertFalse("loadOnFirstForeground should be false", options.shouldLoadOnFirstForeground());
  }

  // -----------------------------------------------------------------------
  // FlagOptions defensive copy
  // -----------------------------------------------------------------------

  @Test
  public void testFlagOptions_ContextDefensivelyCopied() throws JSONException {
    JSONObject original = new JSONObject();
    original.put("key", "original_value");

    FlagOptions options = new FlagOptions.Builder()
        .setContext(original)
        .build();

    // Mutate the original JSONObject after building
    original.put("key", "mutated_value");
    original.put("new_key", "new_value");

    // The FlagOptions context should still have the original value
    assertEquals("original_value", options.getContext().getString("key"));
    assertFalse(
        "new_key should not appear in the defensively copied context",
        options.getContext().has("new_key"));
  }

  // -----------------------------------------------------------------------
  // MixpanelOptions integration: FlagOptions overrides flat params
  // -----------------------------------------------------------------------

  @Test
  public void testMixpanelOptions_FlagOptionsOverridesFlat() throws JSONException {
    JSONObject flagOptionsContext = new JSONObject();
    flagOptionsContext.put("source", "flagOptions");

    FlagOptions flagOptions = new FlagOptions.Builder()
        .setEnabled(true)
        .setContext(flagOptionsContext)
        .setLoadOnFirstForeground(false)
        .build();

    JSONObject flatContext = new JSONObject();
    flatContext.put("source", "flat");

    MixpanelOptions options = new MixpanelOptions.Builder()
        .featureFlagsEnabled(false)
        .featureFlagsContext(flatContext)
        .setFlagOptions(flagOptions)
        .build();

    // FlagOptions should take precedence
    FlagOptions retrieved = options.getFlagOptions();
    assertNotNull("getFlagOptions() should not be null", retrieved);
    assertTrue("enabled should come from FlagOptions (true)", retrieved.isEnabled());
    assertEquals("flagOptions", retrieved.getContext().getString("source"));
    assertFalse("loadOnFirstForeground should come from FlagOptions (false)",
        retrieved.shouldLoadOnFirstForeground());

    // Deprecated getters should also reflect FlagOptions values
    assertTrue("deprecated areFeatureFlagsEnabled() should delegate to FlagOptions",
        options.areFeatureFlagsEnabled());
    assertEquals("deprecated getFeatureFlagsContext() should delegate to FlagOptions",
        "flagOptions", options.getFeatureFlagsContext().getString("source"));
  }

  // -----------------------------------------------------------------------
  // MixpanelOptions integration: flat params feed into FlagOptions
  // -----------------------------------------------------------------------

  @Test
  public void testMixpanelOptions_FlatParamsFeedIntoFlagOptions() throws JSONException {
    JSONObject flatContext = new JSONObject();
    flatContext.put("env", "staging");

    MixpanelOptions options = new MixpanelOptions.Builder()
        .featureFlagsEnabled(true)
        .featureFlagsContext(flatContext)
        .build();

    // When FlagOptions is NOT explicitly set, getFlagOptions() should
    // return a FlagOptions built from the flat params
    FlagOptions retrieved = options.getFlagOptions();
    assertNotNull("getFlagOptions() should not be null", retrieved);
    assertTrue("enabled should come from flat param (true)", retrieved.isEnabled());
    assertEquals("staging", retrieved.getContext().getString("env"));
    assertTrue("loadOnFirstForeground should default to true when auto-constructed",
        retrieved.shouldLoadOnFirstForeground());
  }

  // -----------------------------------------------------------------------
  // MixpanelOptions integration: default FlagOptions
  // -----------------------------------------------------------------------

  @Test
  public void testMixpanelOptions_DefaultFlagOptions() {
    MixpanelOptions options = new MixpanelOptions.Builder().build();

    FlagOptions retrieved = options.getFlagOptions();
    assertNotNull("getFlagOptions() should not be null", retrieved);
    assertFalse("enabled should default to false", retrieved.isEnabled());
    assertEquals("context should be empty", 0, retrieved.getContext().length());
    assertTrue("loadOnFirstForeground should default to true", retrieved.shouldLoadOnFirstForeground());
  }

  // -----------------------------------------------------------------------
  // Behavioral: loadOnFirstForeground=true auto-loads flags
  // -----------------------------------------------------------------------

  @Test
  public void testLoadOnFirstForeground_True_AutoLoadsFlags() throws Exception {
    final List<String> flagsEndpointCalls = new ArrayList<>();

    FlagOptions flagOptions = new FlagOptions.Builder()
        .setEnabled(true)
        .setLoadOnFirstForeground(true)
        .build();

    MixpanelOptions mpOptions = new MixpanelOptions.Builder()
        .setFlagOptions(flagOptions)
        .build();

    // Create MixpanelAPI before launching activity so lifecycle callbacks are registered
    MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            mMockPreferences,
            "Test loadOnFirstForeground true",
            mpOptions) {
          @Override
          protected RemoteService getHttpService() {
            return new HttpService() {
              @Override
              public RemoteService.RequestResult performRequest(
                  String endpointUrl,
                  ProxyServerInteractor interactor,
                  Map<String, Object> params,
                  Map<String, String> headers,
                  byte[] requestBodyBytes,
                  SSLSocketFactory socketFactory)
                  throws ServiceUnavailableException, IOException {
                return performRequest(
                    RemoteService.HttpMethod.POST, endpointUrl, interactor, params,
                    headers, requestBodyBytes, socketFactory);
              }

              @Override
              public RemoteService.RequestResult performRequest(
                  @NonNull RemoteService.HttpMethod method,
                  @NonNull String endpointUrl,
                  @Nullable ProxyServerInteractor interactor,
                  @Nullable Map<String, Object> params,
                  @Nullable Map<String, String> headers,
                  @Nullable byte[] requestBodyBytes,
                  @Nullable SSLSocketFactory socketFactory)
                  throws ServiceUnavailableException, IOException {
                if (endpointUrl != null && endpointUrl.contains("/flags/")) {
                  flagsEndpointCalls.add(endpointUrl);
                }
                return RemoteService.RequestResult.success(
                    "{\"flags\":{}}".getBytes(), endpointUrl);
              }
            };
          }
        };

    // Clear any calls that may have occurred during construction
    flagsEndpointCalls.clear();

    // Launch activity to trigger onForeground() -> loadFlags()
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      // Give async flag loading time to execute
      Thread.sleep(1000);

      assertTrue(
          "Flags endpoint should have been called when loadOnFirstForeground is true",
          flagsEndpointCalls.size() >= 1);
    }
  }

  // -----------------------------------------------------------------------
  // Behavioral: loadOnFirstForeground=false does NOT auto-load flags
  // -----------------------------------------------------------------------

  @Test
  public void testLoadOnFirstForeground_False_DoesNotAutoLoadFlags() throws Exception {
    final List<String> flagsEndpointCalls = new ArrayList<>();

    FlagOptions flagOptions = new FlagOptions.Builder()
        .setEnabled(true)
        .setLoadOnFirstForeground(false)
        .build();

    MixpanelOptions mpOptions = new MixpanelOptions.Builder()
        .setFlagOptions(flagOptions)
        .build();

    // Create MixpanelAPI before launching activity so lifecycle callbacks are registered
    MixpanelAPI mixpanel =
        new TestUtils.CleanMixpanelAPI(
            InstrumentationRegistry.getInstrumentation().getTargetContext(),
            mMockPreferences,
            "Test loadOnFirstForeground false",
            mpOptions) {
          @Override
          protected RemoteService getHttpService() {
            return new HttpService() {
              @Override
              public RemoteService.RequestResult performRequest(
                  String endpointUrl,
                  ProxyServerInteractor interactor,
                  Map<String, Object> params,
                  Map<String, String> headers,
                  byte[] requestBodyBytes,
                  SSLSocketFactory socketFactory)
                  throws ServiceUnavailableException, IOException {
                return performRequest(
                    RemoteService.HttpMethod.POST, endpointUrl, interactor, params,
                    headers, requestBodyBytes, socketFactory);
              }

              @Override
              public RemoteService.RequestResult performRequest(
                  @NonNull RemoteService.HttpMethod method,
                  @NonNull String endpointUrl,
                  @Nullable ProxyServerInteractor interactor,
                  @Nullable Map<String, Object> params,
                  @Nullable Map<String, String> headers,
                  @Nullable byte[] requestBodyBytes,
                  @Nullable SSLSocketFactory socketFactory)
                  throws ServiceUnavailableException, IOException {
                if (endpointUrl != null && endpointUrl.contains("/flags/")) {
                  flagsEndpointCalls.add(endpointUrl);
                }
                return RemoteService.RequestResult.success(
                    "{\"flags\":{}}".getBytes(), endpointUrl);
              }
            };
          }
        };

    // Clear any calls that may have occurred during construction
    flagsEndpointCalls.clear();

    // Launch activity to trigger onForeground()
    try (ActivityScenario<TestActivity> scenario = ActivityScenario.launch(TestActivity.class)) {
      // Give enough time for any async flag loading that might happen
      Thread.sleep(1000);

      assertEquals(
          "Flags endpoint should NOT have been called when loadOnFirstForeground is false",
          0, flagsEndpointCalls.size());
    }
  }
}
