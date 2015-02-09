Getting Started
---------------
You can find getting started guides for using Mixpanel at:

* https://mixpanel.com/help/reference/android#installing-eclipse for installing in Eclipse.
* https://mixpanel.com/help/reference/android#installing-as for installing in Android Studio.
* https://mixpanel.com/help/reference/android#initializing for library initialization.
* https://mixpanel.com/help/reference/android#sending_events for tracking events.
* https://mixpanel.com/help/reference/android#creating_profiles for updating people analytics.
* https://mixpanel.com/help/reference/android#push_configure for sending push notifications.
* https://mixpanel.com/help/reference/android#surveys for showing surveys.

See https://github.com/mixpanel/sample-android-mixpanel-integration for a full featured sample application.

License
-------

See LICENSE File for details. The Base64Coder,
ConfigurationChecker, and StackBlurManager classes, and the entirety of the
 com.mixpanel.android.java_websocket package used by this
software have been licensed from non-Mixpanel sources and modified
for use in the library. Please see the relevant source files, and the
LICENSE file in the com.mixpanel.android.java_websocket package for details.

The StackBlurManager class uses an algorithm by Mario Klingemann <mario@quansimondo.com>
You can learn more about the algorithm at
http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html.

Want to Contribute?
-------------------
The Mixpanel library for Android is an open source project, and we'd love to see your contributions!
We'd also love for you to come and work with us! Check out http://boards.greenhouse.io/mixpanel/jobs/25078#.U_4BBEhORKU for details.

Changelog
---------

#### v4.5.3

 * Disable $app_open event by default. Users can opt-in to sending automatic $app_open events by adding
 ```
  <meta-data android:name="com.mixpanel.android.MPConfig.DisableAppOpenEvent"
       android:value="false" />
 ```

 Enabling $app_open events will enable Notification open tracking for push notifications.

#### v4.5.2

 * Low level features to allow for more advanced push notifications

 * Bugfix, honor DisableFallback setting in checks for surveys and in-app notifications

#### v4.5.1

 * Update pom to allow users of gradle and maven to use the library without specifying packaging aar packaging.

 * Fix issue that prevented building from source in Eclipse

#### v4.5

 * Introducing dynamic event binding! Developers and stakeholders can now bind Mixpanel events to
 user interactions using the UI in the Mixpanel web application.

 * added timeEvent, for automatically adding duration to events with a begin and end.

 * New configuration directives

 The 4.5 version of the library allows for meta-data of the form

 ```
 <meta-data android:name="com.mixpanel.android.MPConfig.ResourcePackageName"
      android:value="YOUR_PACKAGE_NAME" />
 ```

 This tag will only be useful in specific circumstances, for users with certain kinds of exotic custom builds.
 Most users of the library will not require it.
 (You'll get messages in your logs if the library thinks that you need it)

```
 <meta-data android:name="com.mixpanel.android.MPConfig.DisableGestureBindingUI"
      android:value="true" />
```

When included in your Manifest with value="true", this tag disables the use of the connection
gesture to the mobile event binding UI in the Mixpanel web application. Events created and bound in the UI
will still be sent by the application, this directive just disables use the connection gesture
to pair with a Mixpanel to create and edit event bindings. If the application is run in an
emulator, it will still check for pairing with the editor.

```
 <meta-data android:name="com.mixpanel.android.MPConfig.DisableEmulatorBindingUI"
      android:value="true" />
```

When included in your Manifest with value="true", this tag disables pairing with the mobile
event binding UI in the Mixpanel web application. Events created and bound in the UI will
still be sent by the application, this directive just disables the emulator binding behavior. Use of
the connection gesture on a physical device will still work for pairing with the editor.

 * Easier use of Proguard with the library

 To use the Mixpanel library with Proguarded builds, add the following to your proguard.cfg file

 ```
-keep class com.mixpanel.android.abtesting.** { *; }
-keep class com.mixpanel.android.mpmetrics.** { *; }
-keep class com.mixpanel.android.surveys.** { *; }
-keep class com.mixpanel.android.util.** { *; }
-keep class com.mixpanel.android.java_websocket.** { *; }

-keepattributes InnerClasses

-keep class **.R
-keep class **.R$* {
    <fields>;
}
```

Mixpanel uses the R class of your package to facilitate easier dynamic tracking across builds of your application.

* The deprecated methods setFlushInterval and checkForSurvey are now no-ops

This method was deprecated in version 4.0, and now is a no-op. To change the flush
interval for your application, use the com.mixpanel.android.MPConfig.FlushInterval
meta-data tag in your manifest. To get available surveys, call getSurveyIfAvailable()

* The minimum Android OS version necessary for surveys, in app notifications, and dynamic event binding
  has been increased to JellyBean/API 16. The minimum OS version to use basic tracking features
  has been increased to Gingerbread/API 9.

#### v4.4.1

 * Improved support for Push notifications in Android Lollipop/API
   21. Users sending push notifications to Lollipop devices should
   include some version of Google Play Services in their build. In
   include Google Play Services, add the following to your
   build.gradle file:

```
   compile "com.google.android.gms:play-services:3.1+" // Any version above 3.1 will work
```

#### v4.3.1

 * This is a bugfix release only, to alter the handling of Surveys and In-App notifications when
   activities are removed or move to the background.

#### v4.3.0
 * Added support for App Links tracking

 * Added a way to get super properties

#### v4.2.2

 * Removed lint warnings from build

 * Fixed issue that could cause NullPointerExceptions to be thrown from the library
   if a user was identified as null

 * Handle attempts to load In-app notifications in low memory conditions

#### v4.2.1

 * Fixed a bug that would cause events to be dropped when the device thinks it has a valid network
   connection, but cannot actually send data over it.

#### v4.2.0

* `showSurveyById` and `showNotificationById` have been added for precise control over which
  survey or notification should be displayed.

* Added several default properties for Mixpanel People profiles. Each call to `set()` will now
  automatically include the application version name, Android version, and manufacturer, make, and
  model of the phone.

#### v4.1.0

This version adds support for Android in app notifications.

* There is now an additional theme parameter on the SurveyActivity declaration in AndroidManifest.xml
  that is used for full screen in app notifications.

  ```
  <activity android:name="com.mixpanel.android.surveys.SurveyActivity"
            android:theme="@style/com_mixpanel_android_SurveyActivityTheme"/>
  ```

* A new unified set of functions have been created to make it easier to fetch and display surveys
  and in app notifications.

  * `getSurveyIfAvailable()` and `getNotificationIfAvailable()` have been added to fetch Survey and
    InAppNotification objects when the library has successfully received them. You may use these objects
    to display your own custom surveys or in app notifications.

  * `showSurveyIfAvailable()` and `showNotificationIfAvailable()` have been added to display surveys and
    notifications when the library has successfully received them.

  * `addOnMixpanelUpdatesReceivedListener()` and `removeOnMixpanelUpdatesReceivedListener()` have been added
    so you may be notified when the library has successfully received a survey or in app notification in the
    background.

  * `showSurvey()` and `checkForSurvey()` functions have been deprecated.

* `com.mixpanel.android.MPConfig.AutoCheckForSurveys` has been deprecated. The option has been renamed
  to `com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates`. It is also now used for both surveys and in app
  notifications.

* `com.mixpanel.android.MPConfig.TestMode` has been added. This option, when set to true, will render
  your in app notifications and surveys but not track that they have been displayed. If you have multiple
  notifications/surveys, calls the respective show/get methods will simply rotate through them.

* `MixpanelAPI.logPosts()` has been deprecated. Set the `com.mixpanel.android.MPConfig.EnableDebugLogging`
  flag to true to now get extensive debugging output.

* The minimum Android version necessary for surveys and in app notifications has been increased to 14,
  Ice Cream Sandwich to improve stability.

* `MixpanelAPI.alias()` has been added.

* The default library manifest no longer merges in recommended tags by default, as this was breaking
  builds for some users. You'll need to follow the steps in https://mixpanel.com/help/reference/android
  to configure your manifest for automatic referrer tracking, push notifications, in-app messages,
  and surveys. The recommended `<application>` tag in your app is

  ```
  <application>
          <!-- This activity allows your application to show Mixpanel
               surveys and takeover in app notifications. -->
          <activity android:name="com.mixpanel.android.surveys.SurveyActivity"
                    android:theme="@style/com_mixpanel_android_SurveyActivityTheme" />

          <!-- This receiver will allow your application to register for
               and receive Mixpanel push notifications.
               Make sure to change YOUR_PACKAGE_NAME to your own applications package. -->
          <receiver android:name="com.mixpanel.android.mpmetrics.GCMReceiver"
              android:permission="com.google.android.c2dm.permission.SEND" >
              <intent-filter>
                  <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                  <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
                  <category android:name="YOUR_PACKAGE_NAME" />
              </intent-filter>
          </receiver>

          <!-- This receiver will allow your application to record referrer
               parameters as super properties automatically -->
          <receiver android:name="com.mixpanel.android.mpmetrics.InstallReferrerReceiver" android:exported="true">
              <intent-filter>
                  <action android:name="com.android.vending.INSTALL_REFERRER" />
              </intent-filter>
          </receiver>
  </application>
  ```

#### v4.0.1

* Default event storage is now 5 days.

#### v4.0.0

This is a major release, with significant changes to library behavior.

* Changes to the steps required to integrate the Mixpanel library in your project.

  In previous releases, the Mixpanel library was distributed as a jar file. As of 4.0.0,
  use of the library varies with the build tools chosen.

  ###### For Eclipse and Ant

  For building with Eclipse or ant, download the Mixpanel repository and follow the steps outlined
  here, for "Referencing a Library Project":
  http://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject

  ###### For Gradle and Android Studio

  For building with Gradle or Android Studio- add the following dependency to your build.gradle file

  ```
    dependencies {
        compile "com.mixpanel.android:alooma-mixpanel-android:4.0.0@aar"
    }
  ```

  A version of each release is hosted in Maven central, and will not require you to manually
  download or install any artifacts.

* Support for `getPeople().union()`, `getPeople().setOnce()`, and `getPeople().unset()` has been added.

* Fallback to HTTP from HTTPS is disabled by default

  In previous releases, the Mixpanel library would automatically fall
  back to communicating over HTTP if HTTPS communication failed. This
  was to facilitate use on Android 2.2 (Froyo) and older OS versions,
  which had poor support for modern SSL certificates.

  In the 4.0.0, HTTP fallback behavior is disabled by default, but can be
  reenabled for users who intend to ship to older devices by adding
  the following tags to the `<application>` tag in the Application's
  AndroidManifest.xml:

  ```
  <meta-data android:name="com.mixpanel.android.MPConfig.DisableFallback"
             android:value="false" />
  ```

* Support for Mixpanel surveys. Support takes the form of two new API calls
  and some new default automatic behavior

  - `MixpanelAPI.getPeople().checkForSurveys()` will query Mixpanel for surveys
    targeted to the current user, and pass a Survey object
    to a callback when a survey is found, or null if no Survey could be found

  - `MixpanelAPI.getPeople().showSurvey()` will launch a new Activity that shows
    the given survey to the user, and send the results of the survey to Mixpanel

  - Unless configured with `com.mixpanel.android.MPConfig.AutoCheckForSurveys` metadata,
    applications using the Mixpanel library will automatically query for and show
    an available survey on application startup.

* Passing a null token or null context to `MixpanelAPI.getInstance()` will result in
  a null return value.

* Automatic `$bluetooth_enabled` property will only be added automatically on devices with
  OS version greater than API 18/OS version 4.3/Jelly Bean MR2. This feature had a
  critical bug on older platforms, and was not in popular use.

* Users can now configure `MixpanelAPI` behavior by including `<meta-data>` tags in the `<application>`
  tag of their apps. The following meta-data keys are supported:

  * `com.mixpanel.android.MPConfig.FlushInterval` (value: a time in milliseconds)

    If provided, the library will use this interval instead of the default as a
    target maximum duration between attempts to flush data to Mixpanel's servers.

  * `com.mixpanel.android.MPConfig.DisableFallback` (value: a boolean)

    If provided and equal to "false", the library will attempt to send data over
    HTTP if HTTPS fails

  * `com.mixpanel.android.MPConfig.AutoCheckForSurveys` (value: a boolean)

    If provided and equal to "false", the Mixpanel library will not attempt to
    retrieve and show surveys automatically, users can still show surveys using
    `MixpanelAPI.getPeople().checkForSurvey()` and `MixpanelAPI.getPeople().showSurvey()`

* Automatic referrer tracking from the Google Play Store

  Adding the following to the main `<application>` tag in your AndroidManfest.xml will
  automatically set super properties associated with the referrer to your Google Play Store listing:

  ```
  <receiver android:name="com.mixpanel.android.mpmetrics.InstallReferrerReceiver"
            android:exported="true">
      <intent-filter>
          <action android:name="com.android.vending.INSTALL_REFERRER" />
      </intent-filter>
  </receiver>
  ```

* Previous version of the library allowed setting "distinct_id" as a
  superProperty, and would use this value as the distinct id for event
  tracking. This behavior has been removed, and super properties with
  the name "distinct_id" will be ignored. Callers can still provide
  their own value for "distinct_id" in the properties argument to track.

* A scary stack trace log in the common, not-scary case of fallback from HTTPS to HTTP has been
  removed.

* `MixpanelAPI.setFlushInterval()` has been deprecated.
  Use `<meta-data android:name="com.mixpanel.android.MPConfig.FlushInterval" android:value="XXX" />`
  instead, where "XXX" is the desired interval in milliseconds.

* `MixpanelAPI.enableFallbackServer()` has been deprecated.
  Use `<meta-data android:name="com.mixpanel.android.MPConfig.DisableFallback" android:value="true" />`
  to disable fallback to HTTP if HTTPS is unavailable.

#### v3.3.1

* Internal changes to improve startup performance.

#### v3.3.0

* Calls to `increment()` now accept more general types (doubles instead of longs)
  existing calls should work without changes, types are strictly more general.

* Payloads of `increment()` are treated as doubles rather than longs in the internal library


#### v3.2.0

* The library now falls back to HTTP communication if HTTPS communication is unavailable.
  In particular, this may occur in early versions of Android that only support a small
  set of certificate authorities.
  To disable this behavior, call

  ```
  MixpanelAPI.enableFallbackServer(yourMainActivity, false);
  ```

* More robust handling of internal threads and work queues.

  The Mixpanel library now owns one, continuous thread for handling
  messages (rather than spawning a thread on demand.)

* Improvements to internal library error handling.


#### v3.1.1

* Bugfix and test for providing metadata with revenue analytics messages

#### v3.1.0

* Support for Mixpanel Revenue analytics

  The Mixpanel library now supports revenue analytics. To track income, call

  ```
  Mixpanel.getPeople().trackCharge(amount, properties)
  ```

  where amount is a double representing the amount of the charge, and properties is
  a possibly null collection of properties associated with the charge.

#### v3.0.0

* Major change to configuration necessary for push notifications

  In version 3.0, you will need to add the following receiver to the <application> tag
  in your AndroidManifest.xml file to receive push notifications using initPushNotification.

  ```
  <receiver android:name="com.mixpanel.android.mpmetrics.GCMReceiver"
            android:permission="com.google.android.c2dm.permission.SEND" >
      <intent-filter>
          <action android:name="com.google.android.c2dm.intent.RECEIVE" />
          <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
          <category android:name="YOUR_PACKAGE_NAME" />
      </intent-filter>
  </receiver>
  ```

  Be sure to replace "YOUR_PACKAGE_NAME" above with the package name of your project
  (The value of the "package" attribute in your <manifest> tag.)

* Backward incompatible change: Major change to the handling of automatically assigned distinct ids

  In version 3.0, library-assigned distinct ids are randomly generated
  and stored as needed. If you are upgrading from an older version
  that used the automatically generated distinct id, and you want to
  maintain the same distinct id across upgrades, you can generate it
  with the following code:

  ```
  String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
  String oldStyleId = Build.PRODUCT + '_' + androidId;
  mMixpanel.identify(oldStyleId);
  ```

* Stop using version in artifact name

  New versions of the Mixpanel library are named "MixpanelAPI.jar",
  rather than "MixpanelAPI_vXXX.jar".


#### v2.2.3

* Use SSL api endpoint by default

#### v2.2.2

* Fix to of initPushHandling

#### v2.2.1

* Changes to handling of catastrophic database failures (for example, full disks)

  The Mixpanel library will now truncate its database in the face of full disks or corrupted
  databases.

#### v2.2

* Changes to default properties sent with every event

  "Built-in" properties and names have changed for richer and more
  accurate names, and to conform with future versions of the iOS
  library.

* Experimental Feature: Allow users to set default timing for sending server messages

  Users can use MixpanelAPI.setFlushInterval to increase or decrease
  the default frequency of server messages.

* More robust tests

* Changes to error handling

  Mixpanel will no longer retry sending messages when it encounters
  out of memory errors during send.

#### v2.1

* Support for Mixpanel push notifications

    The Mixpanel library now handles registering for and receiving
    Google Cloud Messaging notifications sent from the Mixpanel interface.
    See https://mixpanel.com/docs/people-analytics/android-push
    for instructions on getting started using Mixpanel to
    engage directly with users of your application.

* ant-based build system for core libraries

    To build the core library, type 'ANDROID_HOME=/path/to/android/sdk ant library'
    at your command line.

* API brought closer to Mixpanel Javascript and iPhone APIs

    Main class now named "MixpanelAPI", as in iPhone library

    Methods renamed to align with iPhone API method names

    People Analytics features are now accessed via the
    MixpanelAPI.People class, accessed via MixpanelAPI.getPeople()

    People.identify is now independent of MixpanelAPI.identify

    User identity and super properties now persist across
    application shutdown

* More explicit threading model

    Eliminated dependency on external Looper for timing
    of messages sent to Mixpanel

* Extensive documentation

    Public APIs are now more thoroughly documented

* Unit Tests provided as part of core code

   Unit tests are an Eclipse project under
   test/, are run against the demo application

#### v2.0

* Added support for Mixpanel People
* Added MPConfig object for easier configurability
* MPMetrics.VERSION now available
* Improved debugging information available
* Event queuing submission model revamped

#### v1.3

Due to an unexplained increase in Sqlite insertion times on ICS, we now use a background thread to queue up
events being sent.

#### v1.2

* Add country geolocation by default. Now all events tracked will have the property 'Country.'

#### v1.1

* Convert MPMetrics into a singleton, fixing a rare race caused by multiple instances.
* Reduce the number of flushes being called when events are backed up.
* Correctly check status of API call result
* Better support for using multiple tokens within a single Android application

#### v1.0 (original)

* Removed the funnel() methods. Funnels have been built from events for some time now, and this update to Android simply reflects that change we have made.
  The proper way to perform funnel analysis is to create a funnel based on events that you are sending. For more information, please see http://mixpanel.com/api/docs/guides/funnel-analysis

* Renamed the event method to track, to be more consistent with the existing APIs.
  Furthermore, the propeties object passed to the new track method is no longer a HashMap, but a JSONObject.
  This will cause types to be correctly preseved in Segmentation.
