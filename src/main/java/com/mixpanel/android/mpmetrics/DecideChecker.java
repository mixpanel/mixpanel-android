package com.mixpanel.android.mpmetrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.os.Build;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.RemoteService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.net.ssl.SSLSocketFactory;

/* package */ class DecideChecker {

    /* package */ static class Result {
        public Result() {
            surveys = new ArrayList<Survey>();
            notifications = new ArrayList<InAppNotification>();
            eventBindings = EMPTY_JSON_ARRAY;
            variants = EMPTY_JSON_ARRAY;
        }

        public final List<Survey> surveys;
        public final List<InAppNotification> notifications;
        public JSONArray eventBindings;
        public JSONArray variants;
    }

    public DecideChecker(final Context context, final MPConfig config) {
        mContext = context;
        mConfig = config;
        mChecks = new LinkedList<DecideMessages>();
        mImageStore = createImageStore(context);
    }

    protected ImageStore createImageStore(final Context context) {
        return new ImageStore(context, "DecideChecker");
    }

    public void addDecideCheck(final DecideMessages check) {
        mChecks.add(check);
    }

    public void runDecideChecks(final RemoteService poster) throws RemoteService.ServiceUnavailableException {
        final Iterator<DecideMessages> itr = mChecks.iterator();
        while (itr.hasNext()) {
            final DecideMessages updates = itr.next();
            final String distinctId = updates.getDistinctId();
            try {
                final Result result = runDecideCheck(updates.getToken(), distinctId, poster);
                updates.reportResults(result.surveys, result.notifications, result.eventBindings, result.variants);
            } catch (final UnintelligibleMessageException e) {
                Log.e(LOGTAG, e.getMessage(), e);
            }
        }
    }

    /* package */ static class UnintelligibleMessageException extends Exception {
        private static final long serialVersionUID = -6501269367559104957L;

        public UnintelligibleMessageException(String message, JSONException cause) {
            super(message, cause);
        }
    }

    private Result runDecideCheck(final String token, final String distinctId, final RemoteService poster)
        throws RemoteService.ServiceUnavailableException, UnintelligibleMessageException {
        final String responseString = getDecideResponseFromServer(token, distinctId, poster);
        if (MPConfig.DEBUG) {
            Log.v(LOGTAG, "Mixpanel decide server response was:\n" + responseString);
        }

        Result parsed = new Result();
        if (null != responseString) {
            parsed = parseDecideResponse(responseString);
        }

        final Iterator<InAppNotification> notificationIterator = parsed.notifications.iterator();
        while (notificationIterator.hasNext()) {
            final InAppNotification notification = notificationIterator.next();
            final Bitmap image = getNotificationImage(notification, mContext, poster);
            if (null == image) {
                Log.i(LOGTAG, "Could not retrieve image for notification " + notification.getId() +
                        ", will not show the notification.");
                notificationIterator.remove();
            } else {
                notification.setImage(image);
            }
        }

        return parsed;
    }// runDecideCheck

    /* package */ static Result parseDecideResponse(String responseString)
            throws UnintelligibleMessageException {
        JSONObject response;
        final Result ret = new Result();

        try {
            response = new JSONObject(responseString);
        } catch (final JSONException e) {
            final String message = "Mixpanel endpoint returned unparsable result:\n" + responseString;
            throw new UnintelligibleMessageException(message, e);
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
            for (int i = 0; i < surveys.length(); i++) {
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
            final int notificationsToRead = Math.min(notifications.length(), MPConfig.MAX_NOTIFICATION_CACHE_COUNT);
            for (int i = 0; i < notificationsToRead; i++) {
                try {
                    final JSONObject notificationJson = notifications.getJSONObject(i);
                    final InAppNotification notification = new InAppNotification(notificationJson);
                    ret.notifications.add(notification);
                } catch (final JSONException e) {
                    Log.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
                } catch (final BadDecideObjectException e) {
                    Log.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
                } catch (final OutOfMemoryError e) {
                    Log.e(LOGTAG, "Not enough memory to show load notification from package: " + notifications.toString(), e);
                }
            }
        }

        if (response.has("event_bindings")) {
            try {
                ret.eventBindings = response.getJSONArray("event_bindings");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for event bindings: " + response);
            }
        }

        if (response.has("variants")) {
            try {
                ret.variants = response.getJSONArray("variants");
            } catch (final JSONException e) {
                Log.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for variants: " + response);
            }
        }

        return ret;
    }

    private String getDecideResponseFromServer(String unescapedToken, String unescapedDistinctId, RemoteService poster)
            throws RemoteService.ServiceUnavailableException {
        final String escapedToken;
        final String escapedId;
        try {
            escapedToken = URLEncoder.encode(unescapedToken, "utf-8");
            if (null != unescapedDistinctId) {
                escapedId = URLEncoder.encode(unescapedDistinctId, "utf-8");
            } else {
                escapedId = null;
            }
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("Mixpanel library requires utf-8 string encoding to be available", e);
        }

        final StringBuilder queryBuilder = new StringBuilder()
                .append("?version=1&lib=android&token=")
                .append(escapedToken);

        if (null != escapedId) {
            queryBuilder.append("&distinct_id=").append(escapedId);
        }

        final String checkQuery = queryBuilder.toString();
        final String[] urls;
        if (mConfig.getDisableFallback()) {
            urls = new String[]{mConfig.getDecideEndpoint() + checkQuery};
        } else {
            urls = new String[]{mConfig.getDecideEndpoint() + checkQuery,
                    mConfig.getDecideFallbackEndpoint() + checkQuery};
        }

        if (MPConfig.DEBUG) {
            Log.v(LOGTAG, "Querying decide server, urls:");
            for (int i = 0; i < urls.length; i++) {
                Log.v(LOGTAG, "    >> " + urls[i]);
            }
        }

        final byte[] response = getUrls(poster, mContext, urls);
        if (null == response) {
            return null;
        }
        try {
            return new String(response, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("UTF not supported on this platform?", e);
        }
    }

    private Bitmap getNotificationImage(InAppNotification notification, Context context, RemoteService poster)
        throws RemoteService.ServiceUnavailableException {
        String[] urls = {notification.getImage2xUrl(), notification.getImageUrl()};

        final WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final int displayWidth = getDisplayWidth(display);

        if (notification.getType() == InAppNotification.Type.TAKEOVER && displayWidth >= 720) {
            urls = new String[]{notification.getImage4xUrl(), notification.getImage2xUrl(), notification.getImageUrl()};
        }

        for (String url : urls) {
            try {
                return mImageStore.getImage(url);
            } catch (ImageStore.CantGetImageException e) {
                Log.v(LOGTAG, "Can't load image " + url + " for a notification", e);
            }
        }

        return null;
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    private static int getDisplayWidth(final Display display) {
        if (Build.VERSION.SDK_INT < 13) {
            return display.getWidth();
        } else {
            final Point displaySize = new Point();
            display.getSize(displaySize);
            return displaySize.x;
        }
    }

    private static byte[] getUrls(RemoteService poster, Context context, String[] urls)
        throws RemoteService.ServiceUnavailableException {
        if (! poster.isOnline(context)) {
            return null;
        }

        byte[] response = null;
        for (String url : urls) {
            try {
                final MPConfig config = MPConfig.getInstance(context);
                final SSLSocketFactory socketFactory = config.getSSLSocketFactory();
                response = poster.performRequest(url, null, socketFactory);
                break;
            } catch (final MalformedURLException e) {
                Log.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
            } catch (final FileNotFoundException e) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Cannot get " + url + ", file not found.", e);
                }
            } catch (final IOException e) {
                if (MPConfig.DEBUG) {
                    Log.v(LOGTAG, "Cannot get " + url + ".", e);
                }
            } catch (final OutOfMemoryError e) {
                Log.e(LOGTAG, "Out of memory when getting to " + url + ".", e);
                break;
            }
        }

        return response;
    }

    private final MPConfig mConfig;
    private final Context mContext;
    private final List<DecideMessages> mChecks;
    private final ImageStore mImageStore;

    private static final JSONArray EMPTY_JSON_ARRAY = new JSONArray();

    private static final String LOGTAG = "MixpanelAPI.DChecker";
}
