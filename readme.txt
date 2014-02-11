This is the BETA version of this software, you can get the most recent stable version at:

    https://github.com/mixpanel/mixpanel-android/releases/tag/v3.3.2

Documentation:
    You can find getting started guides for using Mixpanel at
    https://mixpanel.com/docs/integration-libraries/android#installing for installation instructions
    https://mixpanel.com/docs/integration-libraries/android#sending_events for tracking events
    https://mixpanel.com/docs/integration-libraries/android#creating_profiles for updating people analytics
    https://mixpanel.com/docs/integration-libraries/android#push_quick_start for sending push notifications
    https://mixpanel.com/docs/integration-libraries/android#surveys for showing surveys

Demo:
    See https://github.com/mixpanel/sample-android-mixpanel-integration for a full featured sample application

License:

    See LICENSE File for details. The Base64Coder,
    ConfigurationChecker, and StackBlurManager classes used by this
    software have been licensed from non-Mixpanel sources and modified
    for use in the library.  Please see the relevant source files for
    details.

    The StackBlurManager class uses an algorithm by Mario Klingemann <mario@quansimondo.com>
    You can learn more about the algorithm at
    http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html

Changelog:

v4.0.0
---------------------
This is a major release, with significant changes to library behavior.

* Changes to the steps required to integrate the Mixpanel library in your project.

  In previous releases, the Mixpanel library was distributed as a jar file. As of 4.0.0,
  use of the library varies with the build tools chosen.

  For Eclipse and Ant
  -------------------
      For building with Eclipse or ant, download the Mixpanel repository and follow the steps outlined
      here, for "Referencing a Library Project":

      http://developer.android.com/tools/projects/projects-eclipse.html#ReferencingLibraryProject

  For Gradle and Android Studio
  -----------------------------
      For building with Gradle or Android Studio- add the following dependency to your build.gradle file

          dependencies {
              compile "com.mixpanel.android:mixpanel-android:4.0.0@aar"
          }

       A version of each release is hosted in Maven central, and will not require you to manually
       download or install any artifacts.

* Support for getPeople().union(), getPeople().setOnce() and getPeople().unset() has been added.

* Fallback to HTTP from HTTPS is disabled by default

  In previous releases, the Mixpanel library would automatically fall
  back to communicating over HTTP if HTTPS communication failed. This
  was to facilitate use on Android 2.2 (Froyo) and older OS versions,
  which had poor support for modern SSL certificates.

  In the 4.0.0, HTTP fallback behavior is disabled by default, but can be
  reenabled for users who intend to ship to older devices by adding
  the following tags to the <application> tag in the Application's
  AndroidManifest.xml:

    <meta-data android:name="com.mixpanel.android.MPConfig.DisableFallback"
               android:value="false" />

* Support for Mixpanel surveys. Support takes the form of two new API calls
  and some new default automatic behavior

  - MixpanelAPI.getPeople().checkForSurveys will query Mixpanel for surveys
    targeted to the current user, and pass a Survey object
    to a callback when a survey is found, or null if no Survey could be found

  - MixpanelAPI.getPeople().showSurvey will launch a new Activity that shows
    the given survey to the user, and send the results of the survey to Mixpanel

  - Unless configured with com.mixpanel.android.MPConfig.AutoCheckForSurveys metadata,
    applications using the Mixpanel library will automatically query for and show
    an available survey on application startup.

* Passing a null token or null context to MixpanelAPI.getInstance() will result in
  a null return value.

* Automatic $bluetooth_enabled property will only be added automatically on devices with
  OS version greater than API 18/OS version 4.3/Jelly Bean MR2. This feature had a
  critical bug on older platforms, and was not in popular use.

* Users can now configure MixpanelAPI behavior by including <meta-data> tags in the <application>
  tag of their apps. The following meta-data keys are supported:

  com.mixpanel.android.MPConfig.FlushInterval (value: a time in milliseconds)

      If provided, the library will use this interval instead of the default as a
      target maximum duration between attempts to flush data to Mixpanel's servers.

  com.mixpanel.android.MPConfig.DisableFallback (value: a boolean)

      If provided and equal to "false", the library will attempt to send data over
      HTTP if HTTPS fails

  com.mixpanel.android.MPConfig.AutoCheckForSurveys (value: a boolean)

      If provided and equal to "false", the Mixpanel library will not attempt to
      retrieve and show surveys automatically, users can still show surveys using
      MixpanelAPI.getPeople().checkForSurvey and MixpanelAPI.getPeople().showSurvey

* Previous version of the library allowed setting "distinct_id" as a
  superProperty, and would use this value as the distinct id for event
  tracking. This behavior has been removed, and super properties with
  the name "distinct_id" will be ignored. Callers can still provide
  their own value for "distinct_id" in the properties argument to track.

* A scary stack trace log in the common, not-scary case of fallback from HTTPS to HTTP has been
  removed.

* MixpanelAPI.setFlushInterval() has been deprecated.
  Use <meta-data android:name="com.mixpanel.android.MPConfig.FlushInterval" android:value="XXX" />
  instead, where "XXX" is the desired interval in Milliseconds.

* MixpanelAPI.enableFallbackServer() has been deprecated.
  Use <meta-data android:name="com.mixpanel.android.MPConfig.DisableFallback" android:value="true" />
  to disable fallback to HTTP if HTTPS is unavailable.

v3.3.1
---------------------

* Internal changes to improve startup performance.

v3.3.0
---------------------

* Calls to increment() now accept more general types (doubles instead of longs)
  existing calls should work without changes, types are strictly more general.

* Payloads of increment() are treated as doubles rather than longs in the internal library


v3.2.0
---------------------

* The library now falls back to HTTP communication if HTTPS communication is unavailable.
  In particular, this may occur in early versions of Android that only support a small
  set of certificate authorities.
  To disable this behavior, call

        MixpanelAPI.enableFallbackServer(yourMainActivity, false);

* More robust handling of internal threads and work queues.

  The Mixpanel library now owns one, continuous thread for handling
  messages (rather than spawning a thread on demand.)

* Improvements to internal library error handling.


v3.1.1
---------------------

* Bugfix and test for providing metadata with revenue analytics messages

v3.1.0
---------------------

* Support for Mixpanel Revenue analytics

  The Mixpanel library now supports revenue analytics. To track income, call

      Mixpanel.getPeople().trackCharge(amount, properties)

  where amount is a double representing the amount of the charge, and properties is
  a possibly null collection of properties associated with the charge.

v3.0.0
---------------------

* Major change to configuration necessary for push notifications

  In version 3.0, you will need to add the following receiver to the <application> tag
  in your AndroidManifest.xml file to receive push notifications using initPushNotification.

  <receiver android:name="com.mixpanel.android.mpmetrics.GCMReceiver"
            android:permission="com.google.android.c2dm.permission.SEND" >
      <intent-filter>
          <action android:name="com.google.android.c2dm.intent.RECEIVE" />
          <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
          <category android:name="YOUR_PACKAGE_NAME" />
      </intent-filter>
  </receiver>

  Be sure to replace "YOUR_PACKAGE_NAME" above with the package name of your project
  (The value of the "package" attribute in your <manifest> tag.)

* Backward incompatible change: Major change to the handling of automatically assigned distinct ids

  In version 3.0, library-assigned distinct ids are randomly generated
  and stored as needed. If you are upgrading from an older version
  that used the automatically generated distinct id, and you want to
  maintain the same distinct id across upgrades, you can generate it
  with the following code:

      String androidId = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
      String oldStyleId = Build.PRODUCT + '_' + androidId;
      mMixpanel.identify(oldStyleId);

* Stop using version in artifact name

  New versions of the Mixpanel library are named "MixpanelAPI.jar",
  rather than "MixpanelAPI_vXXX.jar".


v2.2.3
---------------------
* Use SSL api endpoint by default

v2.2.2
---------------------
* Fix to of initPushHandling

v2.2.1
---------------------
* Changes to handling of catastrophic database failures (for example, full disks)

  The Mixpanel library will now truncate its database in the face of full disks or corrupted
  databases.

v2.2
---------------------

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

v2.1
---------------------

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

v2.0
-------------------
* Added support for Mixpanel People
* Added MPConfig object for easier configurability
* MPMetrics.VERSION now available
* Improved debugging information available
* Event queuing submission model revamped

v1.3
-------------------
Due to an unexplained increase in Sqlite insertion times on ICS, we now use a background thread to queue up
events being sent.

v1.2
-------------------
* Add country geolocation by default. Now all events tracked will have the property 'Country.'

v1.1
-------------------
* Convert MPMetrics into a singleton, fixing a rare race caused by multiple instances.
* Reduce the number of flushes being called when events are backed up.
* Correctly check status of API call result
* Better support for using multiple tokens within a single Android application

v1.0 (original)
------------------
* Removed the funnel() methods. Funnels have been built from events for some time now, and this update to Android simply reflects that change we have made.
  The proper way to perform funnel analysis is to create a funnel based on events that you are sending. For more information, please see http://mixpanel.com/api/docs/guides/funnel-analysis

* Renamed the event method to track, to be more consistent with the existing APIs.
  Furthermore, the propeties object passed to the new track method is no longer a HashMap, but a JSONObject.
  This will cause types to be correctly preseved in Segmentation.

