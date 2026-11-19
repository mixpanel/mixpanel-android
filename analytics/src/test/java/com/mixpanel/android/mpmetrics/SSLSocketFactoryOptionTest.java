package com.mixpanel.android.mpmetrics;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import java.util.concurrent.Future;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/**
 * Covers {@link MixpanelOptions.Builder#sslSocketFactory}: the factory must reach the config used
 * by the instance's network layer, and leaving it unset must keep the SDK default.
 */
@RunWith(RobolectricTestRunner.class)
public class SSLSocketFactoryOptionTest {

    private Context mContext;
    private Future<SharedPreferences> mPrefs;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        mPrefs = new TestUtils.EmptyPreferences(mContext);
    }

    @After
    public void tearDown() throws Exception {
        // Stop the AnalyticsMessages workers these tests start so they don't outlive the test.
        AnalyticsMessages.hardKillAllInstancesForTesting(mContext);
    }

    @Test
    public void factoryFromOptionsIsUsedByConfigAndAnalyticsMessages() throws Exception {
        SSLSocketFactory factory = newSSLSocketFactory();
        MixpanelOptions options = new MixpanelOptions.Builder().sslSocketFactory(factory).build();

        MixpanelAPI api = new TestUtils.CleanMixpanelAPI(mContext, mPrefs, "ssl_option_token", options);

        assertSame(factory, api.getMPConfig().getSSLSocketFactory());
        assertSame(factory, api.getAnalyticsMessages().mConfig.getSSLSocketFactory());
    }

    @Test
    public void withoutOptionTheDefaultFactoryIsKept() throws Exception {
        SSLSocketFactory unrelated = newSSLSocketFactory();
        MixpanelAPI api =
                new TestUtils.CleanMixpanelAPI(
                        mContext, mPrefs, "ssl_default_token", new MixpanelOptions.Builder().build());

        SSLSocketFactory actual = api.getMPConfig().getSSLSocketFactory();
        assertNotNull(actual);
        assertNotSame(unrelated, actual);
    }

    @Test
    public void factoryIsScopedToItsOwnInstance() throws Exception {
        SSLSocketFactory factory = newSSLSocketFactory();
        MixpanelAPI withFactory =
                new TestUtils.CleanMixpanelAPI(
                        mContext,
                        mPrefs,
                        "ssl_scoped_a",
                        new MixpanelOptions.Builder().sslSocketFactory(factory).build());
        MixpanelAPI withoutFactory =
                new TestUtils.CleanMixpanelAPI(
                        mContext, mPrefs, "ssl_scoped_b", new MixpanelOptions.Builder().build());

        assertSame(factory, withFactory.getMPConfig().getSSLSocketFactory());
        assertNotSame(factory, withoutFactory.getMPConfig().getSSLSocketFactory());
    }

    private static SSLSocketFactory newSSLSocketFactory() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, null, null);
        return sslContext.getSocketFactory();
    }
}
