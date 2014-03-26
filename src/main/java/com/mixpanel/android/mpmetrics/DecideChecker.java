package com.mixpanel.android.mpmetrics;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/* package */ class DecideChecker {

    /* package */ static class Result {
        public Result() {
            surveys = new ArrayList<Survey>();
            notifications = new ArrayList<InAppNotification>();
        }
        public final List<Survey> surveys;
        public final List<InAppNotification> notifications;
    }

    public DecideChecker(final Context context, final MPConfig config) {
        mContext = context;
        mConfig = config;
        mChecks = new LinkedList<DecideUpdates>();
    }

    public void addDecideCheck(final DecideUpdates check) {
        mChecks.add(check);
    }

    public void runDecideChecks(final ServerMessage poster) {
        final Iterator<DecideUpdates> itr = mChecks.iterator();
        while (itr.hasNext()) {
            final DecideUpdates updates = itr.next();
            if (updates.isDestroyed()) {
                itr.remove();
            } else {
                final Result result = runDecideCheck(updates.getToken(), updates.getDistinctId(), poster);
                updates.reportResults(result.surveys, result.notifications);
            }
        }
    }

    private Result runDecideCheck(final String token, final String distinctId, final ServerMessage poster) {
        final String responseString = getDecideResponseFromServer(token, distinctId, poster);
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Mixpanel decide server response was\n" + responseString);

        Result parsed = new Result();
        if (null != responseString) {
            parsed = parseDecideResponse(responseString);
        }

        final Iterator<InAppNotification> notificationIterator = parsed.notifications.iterator();
        while (notificationIterator.hasNext()) {
            final InAppNotification notification = notificationIterator.next();
            final Bitmap image = getNotificationImage(notification, mContext, poster);
            if (null == image) {
                Log.i(LOGTAG, "Could not retrieve image for notification, will not show the notification.");
                notificationIterator.remove();
            } else {
                notification.setImage(image);
            }
        }

        return parsed;
    }// runDecideCheck

    /* package */ static Result parseDecideResponse(String responseString) {
        JSONObject response;
        final Result ret = new Result();

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

        if (null != surveys) {
            final int surveysToRead = Math.min(surveys.length(), MPConfig.MAX_UPDATE_CACHE_ELEMENT_COUNT);
            for (int i = 0; i < surveysToRead; i++) {
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
        }

        JSONArray notifications = null;
        if (response.has("notifications")) {
            try {
                notifications = response.getJSONArray("notifications");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for notifications: " + response);
            }
        }

        if (null != notifications) {
            final int notificationsToRead = Math.min(notifications.length(), MPConfig.MAX_UPDATE_CACHE_ELEMENT_COUNT);
            for (int i = 0; null != notifications && i < notificationsToRead; i++) {
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

    private static Bitmap getNotificationImage(InAppNotification notification, Context context, ServerMessage poster) {
        Bitmap ret = null;
        String imageUrl = notification.getImage2xUrl();
        if (MPConfig.DEBUG) Log.d(LOGTAG, "Downloading image from URL " + imageUrl);
        final ServerMessage.Result result = poster.get(context, imageUrl, null);
        if (result.getStatus() != ServerMessage.Status.SUCCEEDED) {
            Log.i(LOGTAG, "Could not access image at " + imageUrl + ", notification will not be shown");
        } else {
            final byte[] imageBytes = result.getResponseBytes();
            ret = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        return ret;
    }

    private final MPConfig mConfig;
    private final Context mContext;
    private final List<DecideUpdates> mChecks;

    private static final String LOGTAG = "MixpanelAPI DecideChecker";
}
