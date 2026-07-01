package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.RemoteService;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Regression coverage for the multi-token/no-instanceName isolation bug where two MixpanelAPI
 * instances configured with different tokens (and no explicit {@code instanceName}) would share
 * a single AnalyticsMessages worker and HttpService, causing events for the second instance to be
 * POSTed to the first instance's server URL.
 */
@RunWith(RobolectricTestRunner.class)
public class MultiInstanceIsolationTest {

    private static final String EU_URL = "https://api-eu.mixpanel.com";
    private static final String US_URL = "https://api.mixpanel.com";

    private Context mContext;
    private Future<SharedPreferences> mPrefs;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPrefs = new TestUtils.EmptyPreferences(mContext);
    }

    @Test
    public void differentTokensGetIndependentWorkersAndPosters() {
        MixpanelOptions euOptions = new MixpanelOptions.Builder().serverURL(EU_URL).build();
        MixpanelOptions usOptions = new MixpanelOptions.Builder().serverURL(US_URL).build();

        MixpanelAPI euApi = new TestUtils.CleanMixpanelAPI(mContext, mPrefs, "eu_token", euOptions);
        MixpanelAPI usApi = new TestUtils.CleanMixpanelAPI(mContext, mPrefs, "us_token", usOptions);

        AnalyticsMessages euMessages = euApi.getAnalyticsMessages();
        AnalyticsMessages usMessages = usApi.getAnalyticsMessages();

        assertNotSame(
                "AnalyticsMessages must not be shared across tokens when no instanceName is set",
                euMessages,
                usMessages);

        assertTrue(euMessages.mConfig.getEventsEndpoint().startsWith(EU_URL));
        assertTrue(usMessages.mConfig.getEventsEndpoint().startsWith(US_URL));

        RemoteService euPoster = euMessages.getPoster();
        RemoteService usPoster = usMessages.getPoster();

        assertNotSame("Each worker must own its own HttpService", euPoster, usPoster);
        assertEquals("api-eu.mixpanel.com", ((HttpService) euPoster).getServerHost());
        assertEquals("api.mixpanel.com", ((HttpService) usPoster).getServerHost());
    }

    @Test
    public void setServerURLRefreshesPosterServerHost() {
        MixpanelAPI api = new TestUtils.CleanMixpanelAPI(mContext, mPrefs, "single_token");
        AnalyticsMessages messages = api.getAnalyticsMessages();

        HttpService initial = (HttpService) messages.getPoster();
        String initialHost = initial.getServerHost();

        api.setServerURL(EU_URL);
        HttpService after = (HttpService) messages.getPoster();

        assertEquals("Poster instance should be reused, not recreated", initial, after);
        assertNotSame(
                "serverHost must reflect the updated endpoint", initialHost, after.getServerHost());
        assertEquals("api-eu.mixpanel.com", after.getServerHost());
    }
}
