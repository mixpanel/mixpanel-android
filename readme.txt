Documentation:
    You can find getting started guides for using Mixpanel at

    https://mixpanel.com/docs/integration-libraries/android for tracking events
    https://mixpanel.com/docs/people-analytics/android for updating people analytics
    https://mixpanel.com/docs/people-analytics/android-push for sending push notifications

Demo:
    See https://github.com/mixpanel/mixpanel-android for a full featured sample application

License:
    See LICENSE File for details. The Base64Coder class and ConfigurationChecker class used by
    this software have been licensed from non-Mixpanel sources and modified for use in the library.
    Please see the relevant source files for details.

Changelog:

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
 
