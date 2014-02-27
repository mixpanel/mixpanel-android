package com.mixpanel.android.mpmetrics;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

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

        public void callback(final List<Survey> surveys, final List<InAppNotification> notifications) {
            mDecideCallbacks.foundResults(surveys, notifications);
        }

        private final DecideCallbacks mDecideCallbacks;
        private final String mDistinctId;
        private final String mToken;
    }

    public DecideChecker(Context context, MPConfig config) {
        mContext = context;
        mConfig = config;
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

        check.callback(parsed.surveys, parsed.notifications);
    }// runDecideCheck

    /* package */ static class ParseResult {
        public ParseResult() {
            surveys = new ArrayList<Survey>();
            notifications = new ArrayList<InAppNotification>();
        }
        public final List<Survey> surveys;
        public final List<InAppNotification> notifications;
    }

    /* package */ static ParseResult parseDecideResponse(String responseString) {
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
                final JSONObject surveyJson = surveys.getJSONObject(i);
                final Survey survey = new Survey(surveyJson);
                ret.surveys.add(survey);
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
                final JSONObject notificationJson = notifications.getJSONObject(i);
                final InAppNotification notification = new InAppNotification(notificationJson);
                ret.notifications.add(notification);
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

        final ServerMessage.Result result = poster.get(mContext, endpointUrl, fallbackUrl);
        if (result.getStatus() != ServerMessage.Status.SUCCEEDED) {
            if (MPConfig.DEBUG) Log.d(LOGTAG, "Couldn't reach Mixpanel to check for Surveys. (Or user doesn't exist yet)");
            return null;
        }
        return result.getResponse();
    }

    private final MPConfig mConfig;
    private final Context mContext;

    private static final String LOGTAG = "MixpanelAPI DecideChecker";
}
