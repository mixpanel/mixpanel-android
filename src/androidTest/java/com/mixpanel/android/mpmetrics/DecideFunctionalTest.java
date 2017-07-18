package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.AndroidTestCase;

import com.mixpanel.android.util.HttpService;
import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.RemoteService;
import com.mixpanel.android.viewcrawler.UpdatesFromMixpanel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import javax.net.ssl.SSLSocketFactory;

public class DecideFunctionalTest extends AndroidTestCase {

    public void setUp() throws InterruptedException {
        final SharedPreferences referrerPreferences = getContext().getSharedPreferences("MIXPANEL_TEST_PREFERENCES", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = referrerPreferences.edit();
        editor.clear();
        editor.commit();

        mMockPreferences = new Future<SharedPreferences>() {
            @Override
            public boolean cancel(final boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public SharedPreferences get() throws InterruptedException, ExecutionException {
                return referrerPreferences;
            }

            @Override
            public SharedPreferences get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return referrerPreferences;
            }
        };

        mExpectations = new Expectations();
        mMockPoster = new HttpService() {
            @Override
            public byte[] performRequest(String endpointUrl, Map<String, Object> params, SSLSocketFactory socketFactory) {
                return mExpectations.setExpectationsRequest(endpointUrl, params);
            }
        };

        mMockConfig = new MPConfig(new Bundle(), getContext()) {
            @Override
            public boolean getAutoShowMixpanelUpdates() {
                return false;
            }
        };

        mMockMessages = new AnalyticsMessages(getContext()) {
            @Override
            protected RemoteService getPoster() {
                return mMockPoster;
            }

            @Override
            protected MPConfig getConfig(Context context) { return mMockConfig; }

            // this is to pass the mock poster to image store
            @Override
            protected Worker createWorker() {
                return new Worker() {
                    @Override
                    protected Handler restartWorkerThread() {
                        final HandlerThread thread = new HandlerThread("com.mixpanel.android.AnalyticsWorker", Thread.MIN_PRIORITY);
                        thread.start();
                        final Handler ret = new AnalyticsMessageHandler(thread.getLooper()) {
                            @Override
                            protected DecideChecker createDecideChecker() {
                                return new DecideChecker(mContext, mConfig, new SystemInformation(mContext)) {
                                    @Override
                                    protected ImageStore createImageStore(final Context context) {
                                        return new ImageStore(context, "MixpanelAPI.Images.DecideChecker", mMockPoster);
                                    }
                                };
                            }
                        };
                        return ret;
                    }
                };
            }
        };

        try {
            SystemInformation systemInformation = new SystemInformation(mContext);

            final StringBuilder queryBuilder = new StringBuilder();
            queryBuilder.append("&properties=");
            JSONObject properties = new JSONObject();
            properties.putOpt("$android_lib_version", MPConfig.VERSION);
            properties.putOpt("$android_app_version", systemInformation.getAppVersionName());
            properties.putOpt("$android_version", Build.VERSION.RELEASE);
            properties.putOpt("$android_app_release", systemInformation.getAppVersionCode());
            properties.putOpt("$android_device_model", Build.MODEL);
            queryBuilder.append(URLEncoder.encode(properties.toString(), "utf-8"));
            mAppProperties = queryBuilder.toString();
        } catch (Exception e) {}
    }

    public void testDecideChecks() {
        // Should not make any requests on construction if the user has not been identified
        mExpectations.expect("ALWAYS WRONG", "ALWAYS WRONG");

        MixpanelAPI api = new TestUtils.CleanMixpanelAPI(getContext(), mMockPreferences, "TEST TOKEN") {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            UpdatesFromMixpanel constructUpdatesFromMixpanel(final Context context, final String token) {
                return new MockUpdates();
            }

            @Override
            DecideMessages constructDecideUpdates(String token, DecideMessages.OnNewResultsListener listener, UpdatesFromMixpanel binder) {
                return new MockMessages(token, listener, binder, new HashSet<Integer>());
            }
        };

        // Should make a request on identify
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN&distinct_id=DECIDE+CHECKS+ID+1" + mAppProperties,
             "{" +
                  "\"notifications\":[{\"id\": 119911, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]," +
                  "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
             "}"
        );
        api.getPeople().identify("DECIDE CHECKS ID 1");
        mExpectations.checkExpectations();

        // We should be done, and Updates should have our goodies waiting
        {
            final InAppNotification shouldExistNotification = api.getPeople().getNotificationIfAvailable();
            assertEquals(shouldExistNotification.getId(), 119911);
        }

        assertNull(api.getPeople().getNotificationIfAvailable());

        // We should run a new check on every flush (right before the flush)
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN&distinct_id=DECIDE+CHECKS+ID+1" + mAppProperties,
            "{" +
                    "\"notifications\":[{\"id\": 3333, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
         );
        api.flush();
        mExpectations.checkExpectations();

        {
            final InAppNotification shouldExistNotification = api.getPeople().getNotificationIfAvailable();
            assertEquals(shouldExistNotification.getId(), 3333);
        }

        assertNull(api.getPeople().getNotificationIfAvailable());

        // We should check, but IGNORE repeated objects when we see them come through
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN&distinct_id=DECIDE+CHECKS+ID+1" + mAppProperties,
            "{" +
                    "\"notifications\":[{\"id\": 119911, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );
        api.flush();
        mExpectations.checkExpectations();
        assertNull(api.getPeople().getNotificationIfAvailable());

        // Seen never changes, even if we re-identify
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+TOKEN&distinct_id=DECIDE+CHECKS+ID+2" + mAppProperties,
            "{" +
                    "\"notifications\":[{\"id\": 119911, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );
        api.getPeople().identify("DECIDE CHECKS ID 2");
        api.flush();

        mExpectations.checkExpectations();

        assertNull(api.getPeople().getNotificationIfAvailable());
    }

    public void testDecideChecksOnConstruction() {
        final String useToken = "TEST IDENTIFIED ON CONSTRUCTION";

        final String prefsName = "com.mixpanel.android.mpmetrics.MixpanelAPI_" + useToken;
        final SharedPreferences ret = getContext().getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        final SharedPreferences.Editor editor = ret.edit();
        editor.putString("people_distinct_id", "Present Before Construction");
        editor.commit();

        // We should run a check on construction if we are constructed with a people distinct id
        mExpectations.expect(
            "https://decide.mixpanel.com/decide?version=1&lib=android&token=TEST+IDENTIFIED+ON+CONSTRUCTION&distinct_id=Present+Before+Construction" + mAppProperties,
            "{" +
                    "\"notifications\":[{\"id\": 3333, \"message_id\": 4321, \"type\": \"takeover\", \"body\": \"Hook me up, yo!\", \"body_color\": 4294901760, \"title\": null, \"title_color\": 4278255360, \"image_url\": \"http://mixpanel.com/Balok.jpg\", \"bg_color\": 3909091328, \"close_color\": 4294967295, \"extras\": {\"image_fade\": true},\"buttons\": [{\"text\": \"Button!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}, {\"text\": \"Button 2!\", \"text_color\": 4278190335, \"bg_color\": 4294967040, \"border_color\": 4278255615, \"cta_url\": \"hellomixpanel://deeplink/howareyou\"}]}]," +
                    "\"event_bindings\": [{\"event_name\":\"EVENT NAME\",\"path\":[{\"index\":0,\"view_class\":\"com.android.internal.policy.impl.PhoneWindow.DecorView\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarOverlayLayout\"},{\"index\":0,\"view_class\":\"com.android.internal.widget.ActionBarContainer\"}],\"target_activity\":\"ACTIVITY\",\"event_type\":\"EVENT TYPE\"}]" +
            "}"
        );

        MixpanelAPI api = new MixpanelAPI(getContext(), mMockPreferences, useToken) {
            @Override
            AnalyticsMessages getAnalyticsMessages() {
                return mMockMessages;
            }

            @Override
            DecideMessages constructDecideUpdates(String token, DecideMessages.OnNewResultsListener listener, UpdatesFromMixpanel binder) {
                return new MockMessages(token, listener, binder, new HashSet<Integer>());
            }

            @Override
            boolean sendAppOpen() {
                return false;
            }
        };

        mExpectations.checkExpectations();
        final InAppNotification foundNotification = api.getPeople().getNotificationIfAvailable();

        assertEquals(foundNotification.getId(), 3333);
    }

    private static class Expectations {
        public Expectations() {
            final ByteArrayOutputStream imageStream = new ByteArrayOutputStream();
            final Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            final Bitmap testBitmap = Bitmap.createBitmap(100, 100, conf);
            testBitmap.compress(Bitmap.CompressFormat.JPEG, 50, imageStream);
            imageBytes = imageStream.toByteArray();
        }

        public synchronized void expect(String url, String response) {
            mExpectUrl = url;
            mResponse = response;
            badUrl = null;
            badParams = null;
            mResultsFound = false;
            resultsBad = false;
        }

        public void checkExpectations() {
            final long startWaiting = System.currentTimeMillis();
            final long timeout = 1000;
            while (true) {
                try {
                    synchronized (this) {
                        if (mResultsFound) {
                            if (resultsBad) {
                                fail("Unexpected URL " + badUrl + " in MixpanelAPI (expected " + mExpectUrl + ")\n" +
                                        "Got params " + badParams);
                            }

                            break;
                        }
                        this.wait(timeout);
                    }
                } catch (InterruptedException e) {
                    ; // Next iteration
                }

                if (startWaiting + (2 * timeout) < System.currentTimeMillis()) {
                    fail("Test timed out waiting on expectation " + this);
                    break;
                }
            }
        }

        public synchronized byte[] setExpectationsRequest(final String endpointUrl, Map<String, Object> params) {
            if (endpointUrl.equals(mExpectUrl)) {
                return TestUtils.bytes(mResponse);
            } else if (Pattern.matches("^http://mixpanel.com/Balok.{0,3}\\.jpg$", endpointUrl)) {
                return imageBytes;
            } else {
                badUrl = endpointUrl;
                badParams = params;
                resultsBad = true;
                return "{}".getBytes();
            }
        }

        public synchronized void resolve() {
            mResultsFound = true;
            this.notify();
        }

        public synchronized String toString() {
            return "Expectations(" + mExpectUrl + ", " + mResponse + ", " + mResultsFound + ")";
        }

        private String mExpectUrl = null;
        private String mResponse = null;
        private String badUrl = null;
        private Map<String, Object> badParams = null;
        private boolean mResultsFound = false;
        private boolean resultsBad = false;
        private byte[] imageBytes;
    }

    private class MockMessages extends DecideMessages {
        public MockMessages(final String token, final OnNewResultsListener listener, final UpdatesFromMixpanel binder, HashSet<Integer> seenNotificationIds) {
            super(getContext(), token, listener, binder, seenNotificationIds);
        }

        @Override
        public void reportResults(List<InAppNotification> newNotifications, JSONArray newBindings, JSONArray variants, boolean isAutomaticEvents) {
            super.reportResults(newNotifications, newBindings, variants, isAutomaticEvents);
            mExpectations.resolve();
        }
    }

    private class MockUpdates implements UpdatesFromMixpanel {
        @Override
        public void startUpdates() {
            ;
        }

        @Override
        public void setEventBindings(JSONArray bindings) {
            ; // TODO we need to test that (possibly empty, never null) bindings come through
        }

        @Override
        public void setVariants(JSONArray variants) {
            ;
        }

        @Override
        public Tweaks getTweaks() {
            return null;
        }

        @Override
        public void addOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

        }

        @Override
        public void removeOnMixpanelTweaksUpdatedListener(OnMixpanelTweaksUpdatedListener listener) {

        }
    }

    private MPConfig mMockConfig;
    private Future<SharedPreferences> mMockPreferences;
    private Expectations mExpectations;
    private RemoteService mMockPoster;
    private AnalyticsMessages mMockMessages;
    private String mAppProperties;
}
