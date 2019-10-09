package com.mixpanel.android.mpmetrics;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.mixpanel.android.util.MPLog;

import java.util.Random;


/**
 * Service for handling Firebase Cloud Messaging callbacks.
 *
 * <p>You can use FirebaseMessagingService to report Firebase Cloud Messaging registration identifiers
 * to Mixpanel, and to display incoming notifications from Mixpanel to
 * the device status bar. This is the simplest way to get up and running with notifications from Mixpanel.
 *
 * <p>To enable FCM in your application, place your google-services.json file in your Android project
 * root directory, add firebase messaging as a dependency in your gradle file:
 *
 * <pre>
 * {@code
 * buildscript {
 *      ...
 *      dependencies {
 *          classpath 'com.google.gms:google-services:4.1.0'
 *          ...
 *      }
 * }
 *
 * dependencies {
 *     implementation 'com.google.firebase:firebase-messaging:17.3.4'
 *     ...
 * }
 *
 * apply plugin: 'com.google.gms.google-services'
 * }
 * </pre>
 *
 * And finally add a clause like the following
 * to the &lt;application&gt; tag of your AndroidManifest.xml.
 *
 *<pre>
 *{@code
 *
 * <service
 *  android:name="com.mixpanel.android.mpmetrics.MixpanelFCMMessagingService"
 *  android:enabled="true"
 *  android:exported="false">
 *      <intent-filter>
 *          <action android:name="com.google.firebase.MESSAGING_EVENT"/>
 *      </intent-filter>
 * </service>
 *}
 *</pre>
 *
 * <p>Once the FirebaseMessagingService is configured, the only thing you have to do to
 * get set up Mixpanel messages is call {@link MixpanelAPI.People#identify(String) }
 * with a distinct id for your user.
 *
 * <pre>
 * {@code
 *
 * MixpanelAPI.People people = mMixpanelAPI.getPeople();
 * people.identify("A USER DISTINCT ID");
 *
 * }
 * </pre>
 *
 * @see MixpanelAPI#getPeople()
 * @see <a href="https://mixpanel.com/docs/people-analytics/android-push">Getting Started with Android Push Notifications</a>
 */
public class MixpanelFCMMessagingService extends FirebaseMessagingService {
    private static final String LOGTAG = "MixpanelAPI.MixpanelFCMMessagingService";

    /* package */ static void init() {
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(Task<InstanceIdResult> task) {
                        if (task.isSuccessful()) {
                            String registrationId = task.getResult().getToken();
                            addToken(registrationId);
                        }
                    }
                });
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        MPLog.d(LOGTAG, "MP FCM on new message received");
        onMessageReceived(getApplicationContext(), remoteMessage.toIntent());
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        MPLog.d(LOGTAG, "MP FCM on new push token: " + token);
        addToken(token);
    }

    /**
     * Util method to let subclasses customize the payload through the push notification intent.
     *
     * @param context The application context
     * @param intent Push payload intent. Could be modified before calling super() from a sub-class.
     *
     */
    protected void onMessageReceived(Context context, Intent intent) {
        showPushNotification(context, intent);
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This
     * is useful when you use multiple push providers.
     * This method should be called from a onNewToken callback. It adds a new FCM token to a Mixpanel
     * people profile.
     *
     * @param token Firebase Cloud Messaging token to be added to the people profile.
     */
    public static void addToken(final String token) {
        MixpanelAPI.allInstances(new MixpanelAPI.InstanceProcessor() {
            @Override
            public void process(MixpanelAPI api) {
                api.getPeople().setPushRegistrationId(token);
            }
        });
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This
     * is useful when you use multiple push providers.
     * Displays a Mixpanel push notification on the device.
     *
     * @param context The application context you are tracking
     * @param messageIntent Intent that bundles the data used to build a notification. If the intent
     *                      is not valid, the notification will not be shown.
     *                      See {@link #showPushNotification(Context, Intent)}
     */
    public static void showPushNotification(Context context, Intent messageIntent) {
        MixpanelPushNotification mixpanelPushNotification = new MixpanelPushNotification(context.getApplicationContext());
        showPushNotification(context, messageIntent, mixpanelPushNotification);
    }

    /**
     * Only use this method if you have implemented your own custom FirebaseMessagingService. This is
     * useful if you need to override {@link MixpanelPushNotification} to further customize your
     * Mixpanel push notification.
     * Displays a Mixpanel push notification on the device.
     *
     * @param context The application context you are tracking
     * @param messageIntent Intent that bundles the data used to build a notification. If the intent
     *                      is not valid, the notification will not be shown.
     *                      See {@link #showPushNotification(Context, Intent)}
     * @param mixpanelPushNotification A customized MixpanelPushNotification object.
     */
    public static void showPushNotification(Context context, Intent messageIntent, MixpanelPushNotification mixpanelPushNotification) {
        Notification notification = mixpanelPushNotification.createNotification(messageIntent);

        MixpanelNotificationData data = mixpanelPushNotification.getData();
        String message = data == null ? "null" : data.getMessage();
        MPLog.d(LOGTAG, "MP FCM notification received: " + message);

        if (null != notification) {
            final NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            final int id = new Random().nextInt(Integer.MAX_VALUE);
            if (mixpanelPushNotification.getData().getTag() != null) {
                // Use 0 as id so that we can reference notification solely by tag
                notificationManager.notify(mixpanelPushNotification.getData().getTag(), 0, notification);
            } else {
                notificationManager.notify(id, notification);
            }
        }
    }
}
