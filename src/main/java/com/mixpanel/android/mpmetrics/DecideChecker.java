package com.mixpanel.android.mpmetrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

import com.mixpanel.android.util.ImageStore;
import com.mixpanel.android.util.MPLog;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLSocketFactory;

/* package */ class DecideChecker {
    private static final String LOGTAG = "MixpanelAPI.DChecker";

    private final MPConfig mConfig;
    private final Context mContext;
    private final Map<String, DecideMessages> mChecks;
    private final ImageStore mImageStore;
    private final SystemInformation mSystemInformation;

    private static final JSONArray EMPTY_JSON_ARRAY = new JSONArray();

    private static final String NOTIFICATIONS = "notifications";
    private static final String EVENT_BINDINGS = "event_bindings";
    private static final String VARIANTS = "variants";
    private static final String AUTOMATIC_EVENTS = "automatic_events";

    /* package */ static class Result {
        public Result() {
            notifications = new ArrayList<>();
            eventBindings = EMPTY_JSON_ARRAY;
            variants = EMPTY_JSON_ARRAY;
            automaticEvents = false;
        }

        public final List<InAppNotification> notifications;
        public JSONArray eventBindings;
        public JSONArray variants;
        public boolean automaticEvents;
    }

    public DecideChecker(final Context context, final MPConfig config, final SystemInformation systemInformation) {
        mContext = context;
        mConfig = config;
        mChecks = new HashMap<String, DecideMessages>();
        mImageStore = createImageStore(context);
        mSystemInformation = systemInformation;
    }

    protected ImageStore createImageStore(final Context context) {
        return new ImageStore(context, "DecideChecker");
    }

    public void addDecideCheck(final DecideMessages check) {
        mChecks.put(check.getToken(), check);
    }

    public void runDecideCheck(final String token, final RemoteService poster) throws RemoteService.ServiceUnavailableException {
        DecideMessages updates = mChecks.get(token);
        if (updates != null) {
            final String distinctId = updates.getDistinctId();
            try {
                final Result result = runDecideCheck(updates.getToken(), distinctId, poster);
                updates.reportResults(result.notifications, result.eventBindings, result.variants, result.automaticEvents);
            } catch (final UnintelligibleMessageException e) {
                MPLog.e(LOGTAG, e.getMessage(), e);
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

        MPLog.v(LOGTAG, "Mixpanel decide server response was:\n" + responseString);

        Result parsed = new Result();
        if (null != responseString) {
            parsed = parseDecideResponse(responseString);
        }

        final Iterator<InAppNotification> notificationIterator = parsed.notifications.iterator();
        while (notificationIterator.hasNext()) {
            final InAppNotification notification = notificationIterator.next();
            final Bitmap image = getNotificationImage(notification, mContext);
            if (null == image) {
                MPLog.i(LOGTAG, "Could not retrieve image for notification " + notification.getId() +
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

        JSONArray notifications = null;
        if (response.has(NOTIFICATIONS)) {
            try {
                notifications = response.getJSONArray(NOTIFICATIONS);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for notifications: " + response);
            }
        }

        if (null != notifications) {
            final int notificationsToRead = Math.min(notifications.length(), MPConfig.MAX_NOTIFICATION_CACHE_COUNT);
            for (int i = 0; i < notificationsToRead; i++) {
                try {
                    final JSONObject notificationJson = notifications.getJSONObject(i);
                    final String notificationType = notificationJson.getString("type");

                    if (notificationType.equalsIgnoreCase("takeover")) {
                        final TakeoverInAppNotification notification = new TakeoverInAppNotification(notificationJson);
                        ret.notifications.add(notification);
                    } else if (notificationType.equalsIgnoreCase("mini")) {
                        final MiniInAppNotification notification = new MiniInAppNotification(notificationJson);
                        ret.notifications.add(notification);
                    }
                } catch (final JSONException e) {
                    MPLog.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
                } catch (final BadDecideObjectException e) {
                    MPLog.e(LOGTAG, "Received a strange response from notifications service: " + notifications.toString(), e);
                } catch (final OutOfMemoryError e) {
                    MPLog.e(LOGTAG, "Not enough memory to show load notification from package: " + notifications.toString(), e);
                }
            }
        }

        if (response.has(EVENT_BINDINGS)) {
            try {
                ret.eventBindings = response.getJSONArray(EVENT_BINDINGS);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for event bindings: " + response);
            }
        }

        if (response.has(VARIANTS)) {
            try {
                ret.variants = response.getJSONArray(VARIANTS);
            } catch (final JSONException e) {
                MPLog.e(LOGTAG, "Mixpanel endpoint returned non-array JSON for variants: " + response);
            }
        }

        if (response.has(AUTOMATIC_EVENTS)) {
            try {
                ret.automaticEvents = response.getBoolean(AUTOMATIC_EVENTS);
            } catch (JSONException e) {
                MPLog.e(LOGTAG, "Mixpanel endpoint returned a non boolean value for automatic events: " + response);
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
        
        queryBuilder.append("&properties=");

        JSONObject properties = new JSONObject();
        try {
            properties.putOpt("$android_lib_version", MPConfig.VERSION);
            properties.putOpt("$android_app_version", mSystemInformation.getAppVersionName());
            properties.putOpt("$android_version", Build.VERSION.RELEASE);
            properties.putOpt("$android_app_release", mSystemInformation.getAppVersionCode());
            properties.putOpt("$android_device_model", Build.MODEL);
            queryBuilder.append(URLEncoder.encode(properties.toString(), "utf-8"));
        } catch (Exception e) {
            MPLog.e(LOGTAG, "Exception constructing properties JSON", e.getCause());
        }

        final String checkQuery = queryBuilder.toString();
        final String url = mConfig.getDecideEndpoint() + checkQuery;

        MPLog.v(LOGTAG, "Querying decide server, url: " + url);

        final byte[] response = checkDecide(poster, mContext, url);
        if (null == response) {
            return null;
        }
        try {
            return new String(response, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            throw new RuntimeException("UTF not supported on this platform?", e);
        }
    }

    private Bitmap getNotificationImage(InAppNotification notification, Context context)
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
                MPLog.v(LOGTAG, "Can't load image " + url + " for a notification", e);
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

    private static byte[] checkDecide(RemoteService poster, Context context, String url)
        throws RemoteService.ServiceUnavailableException {
        final MPConfig config = MPConfig.getInstance(context);

        if (!poster.isOnline(context, config.getOfflineMode())) {
            return null;
        }

        byte[] response = null;
        try {
            final SSLSocketFactory socketFactory = config.getSSLSocketFactory();
            response = poster.performRequest(url, null, socketFactory);
        } catch (final MalformedURLException e) {
            MPLog.e(LOGTAG, "Cannot interpret " + url + " as a URL.", e);
        } catch (final FileNotFoundException e) {
            MPLog.v(LOGTAG, "Cannot get " + url + ", file not found.", e);
        } catch (final IOException e) {
            MPLog.v(LOGTAG, "Cannot get " + url + ".", e);
        } catch (final OutOfMemoryError e) {
            MPLog.e(LOGTAG, "Out of memory when getting to " + url + ".", e);
        }

        return response;
    }

    public DecideMessages getDecideMessages(String token) {
        return mChecks.get(token);
    }
}
