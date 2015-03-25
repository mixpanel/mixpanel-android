package com.mixpanel.example.hello;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.EditText;

import com.mixpanel.android.mpmetrics.MixpanelAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * A little application that allows people to update their Mixpanel information,
 * and receive push notifications from a Mixpanel project.
 *
 * For more information about integrating Mixpanel with your Android application,
 * please check out:
 *
 *     https://mixpanel.com/docs/integration-libraries/android
 *
 * For instructions on enabling push notifications from Mixpanel, please see
 *
 *     https://mixpanel.com/docs/people-analytics/android-push
 *
 * @author mixpanel
 *
 */
public class MainActivity extends Activity {

    /*
     * You will use a Mixpanel API token to allow your app to send data to Mixpanel. To get your token
     * - Log in to Mixpanel, and select the project you want to use for this application
     * - Click the gear icon in the lower left corner of the screen to view the settings dialog
     * - In the settings dialog, you will see the label "Token", and a string that looks something like this:
     *
     *        2ef3c08e8466df98e67ea0cfa1512e9f
     *
     *   Paste it below (where you see "YOUR API TOKEN")
     */
    public static final String MIXPANEL_API_TOKEN = "NOT A REAL TOKEN";

    /*
     * In order for your app to receive push notifications, you will need to enable
     * the Google Cloud Messaging for Android service in your Google APIs console. To do this:
     *
     * - Navigate to https://code.google.com/apis/console
     * - Select "Services" from the menu on the left side of the screen
     * - Scroll down until you see the row labeled "Google Cloud Messaging for Android"
     * - Make sure the switch next to the service name says "On"
     *
     * To identify this application with your Google API account, you'll also need your sender id from Google.
     * You can get yours by logging in to the Google APIs Console at https://code.google.com/apis/console
     * Once you have logged in, your sender id will appear as part of the URL in your browser's address bar.
     * The URL will look something like this:
     *
     *     https://code.google.com/apis/console/b/0/#project:256660625236
     *                                                       ^^^^^^^^^^^^
     *
     * The twelve-digit number after 'project:' is your sender id. Paste it below (where you see "YOUR SENDER ID")
     *
     * There are also some changes you will need to make to your AndroidManifest.xml file to
     * declare the permissions and receiver capabilities you'll need to get your push notifications working.
     * You can take a look at this application's AndroidManifest.xml file for an example of what is needed.
     */
    public static final String ANDROID_PUSH_SENDER_ID = "YOUR SENDER ID";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final String trackingDistinctId = getTrackingDistinctId();

        // Initialize the Mixpanel library for tracking and push notifications.
        mMixpanel = MixpanelAPI.getInstance(this, MIXPANEL_API_TOKEN);


        // We also identify the current user with a distinct ID, and
        // register ourselves for push notifications from Mixpanel.

        mMixpanel.identify(trackingDistinctId); //this is the distinct_id value that
        // will be sent with events. If you choose not to set this,
        // the SDK will generate one for you

        mMixpanel.getPeople().identify(trackingDistinctId); //this is the distinct_id
        // that will be used for people analytics. You must set this explicitly in order
        // to dispatch people data.

        // People analytics must be identified separately from event analytics.
        // The data-sets are separate, and may have different unique keys (distinct_id).
        // We recommend using the same distinct_id value for a given user in both,
        // and identifying the user with that id as early as possible.

        mMixpanel.getPeople().initPushHandling(ANDROID_PUSH_SENDER_ID);

        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        final long nowInHours = hoursSinceEpoch();
        final int hourOfTheDay = hourOfTheDay();

        // For our simple test app, we're interested tracking
        // when the user views our application.

        // It will be interesting to segment our data by the date that they
        // first viewed our app. We use a
        // superProperty (so the value will always be sent with the
        // remainder of our events) and register it with
        // registerSuperPropertiesOnce (so no matter how many times
        // the code below is run, the events will always be sent
        // with the value of the first ever call for this user.)
        // all the change we make below are LOCAL. No API requests are made.
        try {
            final JSONObject properties = new JSONObject();
            properties.put("first viewed on", nowInHours);
            properties.put("user domain", "(unknown)"); // default value
            mMixpanel.registerSuperPropertiesOnce(properties);
        } catch (final JSONException e) {
            throw new RuntimeException("Could not encode hour first viewed as JSON");
        }

        // Now we send an event to Mixpanel. We want to send a new
        // "App Resumed" event every time we are resumed, and
        // we want to send a current value of "hour of the day" for every event.
        // As usual,all of the user's super properties will be appended onto this event.
        try {
            final JSONObject properties = new JSONObject();
            properties.put("hour of the day", hourOfTheDay);
            mMixpanel.track("App Resumed", properties);
        } catch(final JSONException e) {
            throw new RuntimeException("Could not encode hour of the day in JSON");
        }

        // If you have surveys or notifications, and you have set AutoShowMixpanelUpdates set to false,
        // the onResume function is a good place to call the functions to display surveys or
        // in app notifications. It is safe to call both these methods right after each other,
        // since they do nothing if a notification or survey is already showing.
        mMixpanel.getPeople().showNotificationIfAvailable(this);
        mMixpanel.getPeople().showSurveyIfAvailable(this);
    }

    // Associated with the "Send to Mixpanel" button in activity_main.xml
    // In this method, we update a Mixpanel people profile using MixpanelAPI.People.set()
    // and set some persistent properties that will be sent with
    // all future track() calls using MixpanelAPI.registerSuperProperties()
    public void sendToMixpanel(final View view) {

        final EditText firstNameEdit = (EditText) findViewById(R.id.edit_first_name);
        final EditText lastNameEdit = (EditText) findViewById(R.id.edit_last_name);
        final EditText emailEdit = (EditText) findViewById(R.id.edit_email_address);

        final String firstName = firstNameEdit.getText().toString();
        final String lastName = lastNameEdit.getText().toString();
        final String email = emailEdit.getText().toString();

        final MixpanelAPI.People people = mMixpanel.getPeople();

        // Update the basic data in the user's People Analytics record.
        // Unlike events, People Analytics always stores the most recent value
        // provided.
        people.set("$first_name", firstName);
        people.set("$last_name", lastName);
        people.set("$email", email);

        // We also want to keep track of how many times the user
        // has updated their info.
        people.increment("Update Count", 1L);

        // Mixpanel events are separate from Mixpanel people records,
        // but it might be valuable to be able to query events by
        // user domain (for example, if they represent customer organizations).
        //
        // We use the user domain as a superProperty here, but we call registerSuperProperties
        // instead of registerSuperPropertiesOnce so we can overwrite old values
        // as we get new information.
        try {
            final JSONObject domainProperty = new JSONObject();
            domainProperty.put("user domain", domainFromEmailAddress(email));
            mMixpanel.registerSuperProperties(domainProperty);
        } catch (final JSONException e) {
            throw new RuntimeException("Cannot write user email address domain as a super property");
        }

        // In addition to viewing the updated record in mixpanel's UI, it might
        // be interesting to see when and how many and what types of users
        // are updating their information, so we'll send an event as well.
        // You can call track with null if you don't have any properties to add
        // to an event (remember all the established superProperties will be added
        // before the event is dispatched to Mixpanel)
        mMixpanel.track("update info button clicked", null);
    }

    // This is an example of how you can use Mixpanel's revenue tracking features from Android.
    public void recordRevenue(final View view) {
        final MixpanelAPI.People people = mMixpanel.getPeople();
        // Call trackCharge() with a floating point amount
        // (for example, the amount of money the user has just spent on a purchase)
        // and an optional set of properties describing the purchase.
        people.trackCharge(1.50, null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // To preserve battery life, the Mixpanel library will store
        // events rather than send them immediately. This means it
        // is important to call flush() to send any unsent events
        // before your application is taken out of memory.
        mMixpanel.flush();
    }

    ////////////////////////////////////////////////////

    public void setBackgroundImage(final View view) {
        final Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, PHOTO_WAS_PICKED);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (PHOTO_WAS_PICKED == requestCode && null != data) {
            final Uri imageUri = data.getData();
            if (null != imageUri) {
                // AsyncTask, please...
                final ContentResolver contentResolver = getContentResolver();
                try {
                    final InputStream imageStream = contentResolver.openInputStream(imageUri);
                    System.out.println("DRAWING IMAGE FROM URI " + imageUri);
                    final Bitmap background = BitmapFactory.decodeStream(imageStream);
                    getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), background));
                } catch (final FileNotFoundException e) {
                    Log.e(LOGTAG, "Image apparently has gone away", e);
                }
            }
        }
    }

    private String getTrackingDistinctId() {
        final SharedPreferences prefs = getPreferences(MODE_PRIVATE);

        String ret = prefs.getString(MIXPANEL_DISTINCT_ID_NAME, null);
        if (ret == null) {
            ret = generateDistinctId();
            final SharedPreferences.Editor prefsEditor = prefs.edit();
            prefsEditor.putString(MIXPANEL_DISTINCT_ID_NAME, ret);
            prefsEditor.commit();
        }

        return ret;
    }

    // These disinct ids are here for the purposes of illustration.
    // In practice, there are great advantages to using distinct ids that
    // are easily associated with user identity, either from server-side
    // sources, or user logins. A common best practice is to maintain a field
    // in your users table to store mixpanel distinct_id, so it is easily
    // accesible for use in attributing cross platform or server side events.
    private String generateDistinctId() {
        final Random random = new Random();
        final byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        return Base64.encodeToString(randomBytes, Base64.NO_WRAP | Base64.NO_PADDING);
    }

    ///////////////////////////////////////////////////////
    // conveniences

    private int hourOfTheDay() {
        final Calendar calendar = Calendar.getInstance();
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private long hoursSinceEpoch() {
        final Date now = new Date();
        final long nowMillis = now.getTime();
        return nowMillis / 1000 * 60 * 60;
    }

    private String domainFromEmailAddress(String email) {
        String ret = "";
        final int atSymbolIndex = email.indexOf('@');
        if ((atSymbolIndex > -1) && (email.length() > atSymbolIndex)) {
            ret = email.substring(atSymbolIndex + 1);
        }

        return ret;
    }

    private MixpanelAPI mMixpanel;
    private static final String MIXPANEL_DISTINCT_ID_NAME = "Mixpanel Example $distinctid";
    private static final int PHOTO_WAS_PICKED = 2;
    private static final String LOGTAG = "Mixpanel Example Application";
}
