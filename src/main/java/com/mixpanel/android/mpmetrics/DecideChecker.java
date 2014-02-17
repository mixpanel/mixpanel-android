package com.mixpanel.android.mpmetrics;

import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Set;

/* package */ class DecideChecker {

    /**
     * All fields DecideCheck must return non-null values.
     */
    public static class DecideCheck {
        public DecideCheck(final DecideCallbacks decideCallbacks, final String distinctId, final String token) {
            mDecideCallbacks = decideCallbacks;
            mDistinctId = distinctId;
            mToken = token;
        }

        public String getDistinctId() { return mDistinctId; }
        public String getToken() { return mToken; }

        public void callback(final Survey survey, final InAppNotification notification) {
            // We don't want to run client callback code inside of
            // the Mixpanel working thread or UI thread.
            // (because it may take a long time, throw runtime exceptions, etc.)
            final DecideCallbacks callbacks = mDecideCallbacks;
            final Runnable task = new Runnable() {
                @Override
                public void run() {
                    callbacks.foundResults(survey, notification);
                }
            };
            if (Build.VERSION.SDK_INT >= 11) {
                AsyncTask.execute(task);
            } else {
                final Thread callbackThread = new Thread(task);
                callbackThread.run();
            }
        }

        private final DecideCallbacks mDecideCallbacks;
        private final String mDistinctId;
        private final String mToken;
    }

    public DecideChecker(MPConfig config) {
        mConfig = config;
        mSeenSurveys = new HashSet<Integer>();
        mSeenNotifications = new HashSet<Integer>();
    }

    /**
     * Will call check's callback with one survey and
     */
    public void runDecideCheck(final DecideCheck check, final ServerMessage poster) {
        final String responseString = getDecideResponseFromServer(check.getToken(), check.getDistinctId(), poster);
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Mixpanel decide server response was\n" + responseString);

        ParseResult parsed = new ParseResult();
        if (null != responseString) {
            parsed = parseDecideResponse(responseString);
        }

        if (null != parsed.survey && ! MPConfig.DONT_SEND_SURVEYS) {
            mSeenSurveys.add(parsed.survey.getId());
        }

        if (null != parsed.notification && ! MPConfig.DONT_SEND_SURVEYS) {
            mSeenNotifications.add(parsed.notification.getId());
        }

        check.callback(parsed.survey, parsed.notification);
    }// runDecideCheck

    private ParseResult parseDecideResponse(String responseString) {
        JSONObject response;
        final ParseResult ret = new ParseResult();

        try {
            response = new JSONObject(responseString);
        } catch (final JSONException e) {
            Log.e(LOGTAG, "Mixpanel endpoint returned unparsable result:\n" + responseString, e);
            return ret;
        }

        JSONArray surveys = null;
        if (response.has("surveys")) {
            try {
                surveys = response.getJSONArray("surveys");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for surveys: " + response);
            }
        }

        for (int i = 0; null != surveys && i < surveys.length(); i++) {
            try {
                final JSONObject candidateJson = surveys.getJSONObject(i);
                final Survey candidate = new Survey(candidateJson);
                if (! mSeenSurveys.contains(candidate.getId())) {
                    ret.survey = candidate;
                    break;
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Received a strange response from surveys service: " + surveys.toString());
            } catch (final BadDecideObjectException e) {
                Log.e(LOGTAG, "Received a strange response from surveys service: " + surveys.toString());
            }
        }

        JSONArray notifications = null;
        if (response.has("notifications")) {
            try {
                notifications = response.getJSONArray("notifications");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for notifications: " + response);
            }
        }

        for (int i = 0; null != notifications && i < notifications.length(); i++) {
            try {
                final JSONObject candidateJson = surveys.getJSONObject(i);
                final InAppNotification candidate = new InAppNotification(candidateJson);
                if (! mSeenNotifications.contains(candidate.getId())) {
                    ret.notification = candidate;
                    break;
                }
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
            } catch (final BadDecideObjectException e) {
                Log.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
            }
        }

        return ret;
    }

    private String getDecideResponseFromServer(String unescapedToken, String unescapedDistinctId, ServerMessage poster) {
        String escapedToken;
        String escapedId;
        try {
            escapedToken = URLEncoder.encode(unescapedToken, "utf-8");
            escapedId = URLEncoder.encode(unescapedDistinctId, "utf-8");
        } catch(final UnsupportedEncodingException e) {
            throw new RuntimeException("Mixpanel library requires utf-8 string encoding to be available", e);
        }
        final String checkQuery = new StringBuilder()
                .append("?version=1&lib=android&token=")
                .append(escapedToken)
                .append("&distinct_id=")
                .append(escapedId)
                .toString();
        final String endpointUrl = mConfig.getDecideEndpoint() + checkQuery;
        final String fallbackUrl = mConfig.getDecideFallbackEndpoint() + checkQuery;

        Log.d(LOGTAG, "Querying decide server at " + endpointUrl);
        Log.d(LOGTAG, "    (with fallback " + fallbackUrl + ")");

        final ServerMessage.Result result = poster.get(endpointUrl, fallbackUrl);
        if (result.getStatus() != ServerMessage.Status.SUCCEEDED) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Couldn't reach Mixpanel to check for Surveys. (Or user doesn't exist yet)");
            return null;
        }
        return result.getResponse();
    }

    private static class ParseResult {
        public Survey survey;
        public InAppNotification notification;
    }

    private final Set<Integer> mSeenSurveys;
    private final Set<Integer> mSeenNotifications;
    private final MPConfig mConfig;

    private static final String LOGTAG = "MixpanelAPI DecideChecker";
}
