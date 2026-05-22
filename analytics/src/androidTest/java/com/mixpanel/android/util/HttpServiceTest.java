package com.mixpanel.android.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.UnknownHostException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class HttpServiceTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
    }

    @Test
    public void testNetworkErrorListener_SetAfterGetInstance_FiresOnUnknownHost()
            throws InterruptedException {
        final BlockingQueue<Exception> errors = new LinkedBlockingQueue<>();

        // Unique token avoids the MixpanelAPI singleton cache from prior tests.
        final String token = "net-err-listener-" + UUID.randomUUID();
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(mContext, token, false);

        // Point at a host guaranteed to fail DNS resolution. .invalid is reserved by RFC 2606.
        mixpanel.setServerURL("https://api.mixpanel-fail.invalid");

        // Listener registered AFTER getInstance() must still propagate to the cached HttpService.
        mixpanel.setNetworkErrorListener(new MixpanelNetworkErrorListener() {
            @Override
            public void onNetworkError(String endpointUrl, String ipAddress, long durationMillis,
                                       long uncompressedBodySize, long compressedBodySize,
                                       int responseCode, String responseMessage,
                                       Exception exception) {
                errors.add(exception);
            }
        });

        mixpanel.track("listener event");
        mixpanel.flush();

        // HttpService retries up to 3 times with progressive backoff (100/200/300ms) plus DNS
        // resolution time per attempt. 20s comfortably covers the worst case.
        Exception captured = errors.poll(20, TimeUnit.SECONDS);
        assertNotNull("Listener should fire when set after getInstance()", captured);
        assertTrue(
                "Expected UnknownHostException, got: " + captured,
                captured instanceof UnknownHostException
                        || captured.getCause() instanceof UnknownHostException);
    }
}
