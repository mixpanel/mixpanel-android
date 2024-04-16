#

## [v7.5.2](https://github.com/mixpanel/mixpanel-android/tree/v7.5.2) (2024-04-16)

### Enhancements

- Remove Mixpanel internal tracking [\#835](https://github.com/mixpanel/mixpanel-android/pull/835)

#

## [v7.5.0](https://github.com/mixpanel/mixpanel-android/tree/v7.5.0) (2024-04-09)

### Enhancements

- feat: add headers support for mixpanel proxy api calls [\#833](https://github.com/mixpanel/mixpanel-android/pull/833)

#

## [v7.4.2](https://github.com/mixpanel/mixpanel-android/tree/v7.4.2) (2024-03-15)

### Enhancements

- feat: Enable data separation for each MixPanel API instance [\#830](https://github.com/mixpanel/mixpanel-android/pull/830)

#

## [v7.4.1](https://github.com/mixpanel/mixpanel-android/tree/v7.4.1) (2024-02-02)

### Fixes

- MPConfig instances created through MixpanelAPI are not being utilized when making API calls from AnalyticsMessages. [\#827](https://github.com/mixpanel/mixpanel-android/pull/827)

#

## [v7.4.0](https://github.com/mixpanel/mixpanel-android/tree/v7.4.0) (2024-01-22)


### Non-Singleton MPConfig Class
In this release, we've introduced a significant change to the MPConfig class. Previously implemented as a singleton, MPConfig is now designed to provide instance-specific configurations. This change allows the creation of multiple MPConfig instances, each with potentially different configuration settings.  check the [docs](https://mixpanel.github.io/mixpanel-android/com/mixpanel/android/mpmetrics/MPConfig.html#getInstance(android.content.Context)) for more details

### Enhancements

- bump target API level to 33 [\#826](https://github.com/mixpanel/mixpanel-android/pull/826)
- make 'MPConfig' not a Singleton in favor of supporting multiple configurations for multiple Mixpanel instances [\#825](https://github.com/mixpanel/mixpanel-android/pull/825)

#

## [v7.3.3](https://github.com/mixpanel/mixpanel-android/tree/v7.3.3) (2024-01-12)

### Fixes

- Fix the potential `NullPointException` crash from `getOptOutTracking` [\#824](https://github.com/mixpanel/mixpanel-android/pull/824)

#

## [v7.3.2](https://github.com/mixpanel/mixpanel-android/tree/v7.3.2) (2023-09-14)

### Fixes

- Fix integration event when opted out [\#819](https://github.com/mixpanel/mixpanel-android/pull/819)

#

## [v7.3.0](https://github.com/mixpanel/mixpanel-android/tree/v7.3.0) (2023-03-02)

### Enhancements

- prefix device-specific distinct IDs with '$device:' [\#810](https://github.com/mixpanel/mixpanel-android/pull/810)

#

## [v7.2.2](https://github.com/mixpanel/mixpanel-android/tree/v7.2.2) (2023-01-30)

### Enhancements

- replace original with distinct\_id in alias [\#808](https://github.com/mixpanel/mixpanel-android/pull/808)

#

## [v7.2.1](https://github.com/mixpanel/mixpanel-android/tree/v7.2.1) (2022-11-16)

### Fixes

- Fix ANRs issue when opt in and remove residual image files left from the legacy SDK versions [\#807](https://github.com/mixpanel/mixpanel-android/pull/807)

#

## [v7.2.0](https://github.com/mixpanel/mixpanel-android/tree/v7.2.0) (2022-10-20)

### Enhancements

- Add the ability to remove residual image files left from the legacy SDK versions [\#804](https://github.com/mixpanel/mixpanel-android/pull/804)

To opt in, add this to `AndroidManifest.xml`. 
```        
<meta-data android:name="com.mixpanel.android.MPConfig.RemoveLegacyResidualFiles"
            android:value="true" />
```

- Remove Mixpanel DevX internal tracking [\#802](https://github.com/mixpanel/mixpanel-android/pull/802)
- Upgrade SDK version to 31 [\#801](https://github.com/mixpanel/mixpanel-android/pull/801)

#

## [v7.0.1](https://github.com/mixpanel/mixpanel-android/tree/v7.0.1) (2022-09-08)

### Fixes

- Fix the event being dropped if its properties contain custom class object [\#798](https://github.com/mixpanel/mixpanel-android/pull/798)

#

## [v7.0.0](https://github.com/mixpanel/mixpanel-android/tree/v7.0.0) (2022-08-16)

### Enhancements

- Add 'trackAutomaticEvents' as a required param in getInstance' and remove Mixpanel server api call for `Autotrack` setting [\#794](https://github.com/mixpanel/mixpanel-android/pull/794)

#

## [v6.5.2](https://github.com/mixpanel/mixpanel-android/tree/v6.5.2) (2022-08-05)

### Fixes

- Fix the crash in DevX tracking in debugging mode [\#797](https://github.com/mixpanel/mixpanel-android/pull/797)

#

## [v6.5.1](https://github.com/mixpanel/mixpanel-android/tree/v6.5.1) (2022-07-26)

### Enhancements

- Add the following new configs for you to optimize the Mixpanel tracking ( [\#795](https://github.com/mixpanel/mixpanel-android/pull/795)): 

```   /**
     * Set maximum number of events/updates to send in a single network request
     *
     * @param flushBatchSize  int, the number of events to be flushed at a time, defaults to 50
     */
    public void setFlushBatchSize(int flushBatchSize);

    /**
     * Set an integer number of bytes, the maximum size limit to the Mixpanel database.
     *
     * @param maximumDatabaseLimit an integer number of bytes, the maximum size limit to the Mixpanel database.
     */
    public void setMaximumDatabaseLimit(int maximumDatabaseLimit);
```

You can also set them in `AndroidManifest.xml`, i.e.
```
        <meta-data android:name="com.mixpanel.android.MPConfig.FlushBatchSize"
            android:value="5" />

        <meta-data android:name="com.mixpanel.android.MPConfig.MaximumDatabaseLimit"
            android:value="100000000" />
```


#

## [v6.4.0](https://github.com/mixpanel/mixpanel-android/tree/v6.4.0) (2022-06-30)

### Enhancements

- Support defining multiple instances by specifying `instanceName` in `getInstance` [\#792](https://github.com/mixpanel/mixpanel-android/pull/792)

This release adds the following APIs to MixpanelAPI:
```
/**
...
 * @param instanceName The name you want to uniquely identify the Mixpanel Instance.
   It is useful when you want more than one Mixpanel instance under the same project token.
...
**/
```
`getInstance(Context context, String token, String instanceName)`
`getInstance(Context context, String token, boolean optOutTrackingDefault, String instanceName)`
`getInstance(Context context, String token, JSONObject superProperties, String instanceName)`
`getInstance(Context context, String token, boolean optOutTrackingDefault, JSONObject superProperties, String instanceName)`

Please note: If you are going to add `instanceName` to `getInstance` on your existing implementation. `getInstance` will start using `instanceName` as the instance identifier rather than `token`, so you might lose some of the stored properties including the distinct Id under the `token`. We'd recommend using it when you need to create more than one instance under the same project token. You won't lose any events and user profile updates.

#

## [v6.3.0](https://github.com/mixpanel/mixpanel-android/tree/v6.3.0) (2022-06-24)

### Enhancements

- use millisecond precision for event time property [\#791](https://github.com/mixpanel/mixpanel-android/pull/791)

#

## [v6.2.2](https://github.com/mixpanel/mixpanel-android/tree/v6.2.2) (2022-05-20)

### Enhancements

- remove survey [\#787](https://github.com/mixpanel/mixpanel-android/pull/787)

#

## [v6.2.1](https://github.com/mixpanel/mixpanel-android/tree/v6.2.1) (2022-05-07)

### Fixes

- Fix Mixpanel DevX internal tracking [\#786](https://github.com/mixpanel/mixpanel-android/pull/786)

#

## [v6.2.0](https://github.com/mixpanel/mixpanel-android/tree/v6.2.0) (2022-05-05)

### Enhancements

- Make `getAnonymousId` public. [\#784](https://github.com/mixpanel/mixpanel-android/pull/784)
- Deprecating 'People.identify' and merge it into 'MixpanelAPI.identify' [\#781](https://github.com/mixpanel/mixpanel-android/pull/781)

#

## [v6.1.1](https://github.com/mixpanel/mixpanel-android/tree/v6.1.1) (2022-04-11)

### Fixes

- Deprecate  automatic property for "Radio\($radio\)" [\#782](https://github.com/mixpanel/mixpanel-android/pull/782)

#

## [v6.1.0](https://github.com/mixpanel/mixpanel-android/tree/v6.1.0) (2022-03-08)

### Enhancements

- Add clearTimedEvent\(\) and clearTimedEvents\(\) [\#779](https://github.com/mixpanel/mixpanel-android/pull/779)

### Fixes

- Fix `MPConfig.UseIpAddressForGeolocation` gets ignored  [\#780](https://github.com/mixpanel/mixpanel-android/pull/780)

#

## [v6.0.0](https://github.com/mixpanel/mixpanel-android/tree/v6.0.0) (2022-01-02)
- Remove Messages & Experiments feature, for more detail, please check this [post](https://mixpanel.com/blog/why-were-sunsetting-messaging-and-experiments/#:~:text=A%20year%20from%20now%2C%20on,offering%20discounts%20for%20getting%20started):

#

## [v6.0.0-beta2](https://github.com/mixpanel/mixpanel-android/tree/v6.0.0-beta2) (2021-12-18)

- Deprecate both the Mixpanel default property `$google_play_services`  and tracking Install Referrer in favor of not being marked as containing Ads. 

On Jan 1, 2022, we’ll remove the [Messages & Experiments](https://mixpanel.com/blog/why-were-sunsetting-messaging-and-experiments/#:~:text=A%20year%20from%20now%2C%20on,offering%20discounts%20for%20getting%20started) feature from Mixpanel. For now, you can choose to opt into our beta version without the Messages & Experiments feature support.

The beta version v6.0.0-beta2 is in parity with v5.9.6 but without the Messages & Experiments feature support. For more details, see our [changelog](https://github.com/mixpanel/mixpanel-android/wiki/Changelog)

To install this SDK, see our [integration guide](https://developer.mixpanel.com/docs/android-quickstart)

#

## [v5.9.6](https://github.com/mixpanel/mixpanel-android/tree/v5.9.6) (2021-12-14)

**Closed issues:**

- java.lang.ClassNotFoundException when calling MixpanelAPI.getInstance in 5.9.3 [\#766](https://github.com/mixpanel/mixpanel-android/issues/766)
- Please add trailing slashes to request endpoints in the Android SDK. [\#764](https://github.com/mixpanel/mixpanel-android/issues/764)

**Merged pull requests:**

- Remove integrations code for braze and airship [\#770](https://github.com/mixpanel/mixpanel-android/pull/770)
- Readme change: rebrand for live view report to events report [\#767](https://github.com/mixpanel/mixpanel-android/pull/767)

#

## [v5.9.5](https://github.com/mixpanel/mixpanel-android/tree/v5.9.5) (2021-10-19)

### Enhancements

- Add trailing slashes to request endpoint to in parity with iOS [\#765](https://github.com/mixpanel/mixpanel-android/pull/765)

**Closed issues:**

- Android 4 NullPointerException [\#763](https://github.com/mixpanel/mixpanel-android/issues/763)
- Mixpanel 5.9.3 org/jacoco/agent/rt/internal\_8ff85ea/Offline [\#761](https://github.com/mixpanel/mixpanel-android/issues/761)

#

## [v5.9.4](https://github.com/mixpanel/mixpanel-android/tree/v5.9.4) (2021-10-08)

### Fixes

- Fix java.lang.NoClassDefFoundError: Failed resolution of: Lorg/jacoco/agent/rt/internal\_8ff85ea/Offline [\#762](https://github.com/mixpanel/mixpanel-android/pull/762)

#
####  [v5.9.3](https://github.com/mixpanel/mixpanel-android/tree/v5.9.3) (2021-10-07)

####  Fixes

- Remove typo in the class name `LocalBroadcastManager` for reflection [\#760](https://github.com/mixpanel/mixpanel-android/pull/760)

#### Closed issues:**

- Google Play policy to update targetSdkVersion to 30 [\#756](https://github.com/mixpanel/mixpanel-android/issues/756)
- Failed resolution of: Lcom/google/firebase/iid/FirebaseInstanceId [\#744](https://github.com/mixpanel/mixpanel-android/issues/744)
- Mixpanel using legacy support libs in reflection calls - can't drop jetifier due to this [\#717](https://github.com/mixpanel/mixpanel-android/issues/717)


#### [v5.9.2](https://github.com/mixpanel/mixpanel-android/tree/v5.9.2) (2021-10-07)

####  Enhancements

- remove legacy support libs in reflection calls [\#759](https://github.com/mixpanel/mixpanel-android/pull/759)
- Upgrade targetSdkVersion to 30 [\#758](https://github.com/mixpanel/mixpanel-android/pull/758)
- Add explicit exported flag to activities [\#755](https://github.com/mixpanel/mixpanel-android/pull/755)

#### Closed issues:**

- Crash in v5.9.1,  java.lang.AssertionError @ WebSocketImpl.java:675 [\#757](https://github.com/mixpanel/mixpanel-android/issues/757)
- In-app messages cause: IllegalArgumentException: Software rendering doesn't support hardware bitmaps [\#753](https://github.com/mixpanel/mixpanel-android/issues/753)
- Mixpanel.addPushDeviceToken ? [\#752](https://github.com/mixpanel/mixpanel-android/issues/752)
- User profile are not showing on mixpanel dashborad [\#751](https://github.com/mixpanel/mixpanel-android/issues/751)
- SHA-1 usaged flagged as a security risk [\#750](https://github.com/mixpanel/mixpanel-android/issues/750)
- Rejecting re-init on previously-failed class java.lang.Class\<com.mixpanel.android.mpmetrics.InstallReferrerPlay\>: java.lang.NoClassDefFoundError: Failed resolution of: Lcom/android/installreferrer/api/InstallReferrerStateListener; [\#746](https://github.com/mixpanel/mixpanel-android/issues/746)
- App crashing with ConcurrentModificationException in Mixpanel SDK code [\#720](https://github.com/mixpanel/mixpanel-android/issues/720)
- In-app messages cause android app crash "Software rendering doesn't support hardware bitmaps" [\#711](https://github.com/mixpanel/mixpanel-android/issues/711)

####  Merged pull requests:**

- Improve README for the quick start guide [\#749](https://github.com/mixpanel/mixpanel-android/pull/749)
- Add a CHANGELOG placeholder [\#747](https://github.com/mixpanel/mixpanel-android/pull/747)
- Add github workflow for auto release [\#745](https://github.com/mixpanel/mixpanel-android/pull/745)
- Migrate to Github Actions for CI [\#743](https://github.com/mixpanel/mixpanel-android/pull/743)


#### [v5.9.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.9.1)
_May 18th - 2021_

#### Fixes
- Migrate to Airship 12.x for the integration
https://github.com/mixpanel/mixpanel-android/pull/742

#### [v5.9.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.9.0)
_April 29th - 2021_
#### Features
- add `setUseIpAddressForGeolocation`, `setEnableLogging` and `setServerURL`.
Now you can disable/enagle geo location tracking or the server URL in the run time
https://github.com/mixpanel/mixpanel-android/pull/739

#### Fixes
- Prevent the out of memory crash when adding events to the SQLiteDatabase
https://github.com/mixpanel/mixpanel-android/pull/737
- Enable tracking null property value
https://github.com/mixpanel/mixpanel-android/pull/736
- Fix string compare
https://github.com/mixpanel/mixpanel-android/pull/735

#### [v5.8.8](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.8)
_Mar 25th - 2021_
#### Fixes
- Crash prevention on getScaledScreenshot when rootView is not measured yet
https://github.com/mixpanel/mixpanel-android/pull/729

#### [v5.8.7](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.7)
_Mar 9th - 2021_
#### Fixes
- Replace deprecated view drawing cache API 
https://github.com/mixpanel/mixpanel-android/pull/724

- Change Android mixpanel default properties ($app_release, $app_build_number) from number to string
https://github.com/mixpanel/mixpanel-android/pull/723

#### [v5.8.6](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.6)
_Jan 15th - 2021_
#### Fixes
- Remove the legacy gesture tracker that causes crashes
https://github.com/mixpanel/mixpanel-android/pull/721

- Use SecureRandom instead of Random
https://github.com/mixpanel/mixpanel-android/pull/697

#### [v5.8.5](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.5)
_Sept 24th - 2020_
#### Fixes
- Fix crash MixpanelNotificationRouteActivity.handleRouteIntent 
https://github.com/mixpanel/mixpanel-android/pull/707

- Fix the crash for android 5.x if the rich notifiction with a button
https://github.com/mixpanel/mixpanel-android/pull/706

#### [v5.8.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.4)
_June 29th - 2020_

#### Fixes
- Fix `SecurityException` crash on `InstallReferrerPlay` class. More info: https://github.com/mixpanel/mixpanel-android/issues/700


#### [v5.8.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.3)
_April 21st - 2020_

#### Fixes
- Remove unused intent filter from activity
- Fix routing activity bug that made a push action to be repeated when the app was open again

#### Features
- Add option to disable automatic flushing when the app goes into the background. Add the following to you `<application>` tag on your `AndroidManifest.xml` if you don't want the SDK to automatically flush its queues when the app goes into the background:

```
<meta-data android:name="com.mixpanel.android.MPConfig.FlushOnBackground"
               android:value="false" /
```

#### [v5.8.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.2)
_March 25th - 2020_

#### Fixes
- Added `$radio` property (iOS parity) as a super property. You'll need to request permission for `READ_PHONE_STATE` if you want to have access to that property.
- Ensure web links are always open in a browser
- Fixed tracking `Message Received` under certain cases.
- Added compatibility with installreferrer 1.0 (`IllegalArgumentException` was thrown - fixes https://github.com/mixpanel/mixpanel-android/issues/678)

#### [v5.8.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.1)
_March 10th - 2020_

#### Fixes
- Catch all exceptions when reading referrer details from Google Play to avoid potential crash

#### [v5.8.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.8.0)
_March 4th - 2020_

#### New features
- Referrer details are now fetched from Google Play since `INSTALL_REFERRER` message is no longer supported by Google. If you have the following lines, please remove them from your `AndroidManifest.xml`:

**_Remove:_**
```
<receiver
  android:name="com.mixpanel.android.mpmetrics.InstallReferrerReceiver"
  android:exported="true">
  <intent-filter>
    <action android:name="com.android.vending.INSTALL_REFERRER" />
  </intent-filter>
</receiver>
```

You now need to use a new Google dependency to be able to track your referrer details. Update your `build.gradle` file and add the following dependency:

**_Add:_**
```
dependencies {
        implementation 'com.android.installreferrer:installreferrer:1.1'
        ...
}
```

As before, Mixpanel referrer track will inspect the referrer and not only set a new property `referrer` but will also look for the following keys and set them as event properties separately (if available): `utm_source`, `utm_medium`, `utm_term`, `utm_content` and `utm_campaign`.

#### Fixes
- `ConcurrentModificationException` using super properties (https://github.com/mixpanel/mixpanel-android/issues/658)
- Track session lengths as a numbers and not strings.
- In-app notification `NullPointerException`.
- Capture exception when writing on SQLite and restore state.
- Do not allow `null` values as `distinct_id`.

#### [v5.7.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.7.0)
_February 13th - 2020_

#### Features
- Additional support for rich push notifications: you can now include images, buttons, and more
- You can now track when a push notification was dismissed. Replacing existing events (backwards compatible) and adding new ones: `$push_notification_received`, `$push_notification_tap`, `$push_notification_dismissed` for notifications sent from Mixpanel.
- Add geolocation flag to people updates (https://github.com/mixpanel/mixpanel-android/pull/656)

#### Fixes
- Always union an existing device token. Useful if the device token was removed previously but still valid.

#### [v5.6.8](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.6.8)
_November 27th - 2019_

#### Fixes
- Added back `MixpanelAPI.getInstance(this, MY_TOKEN, optOutStatusDefaultFlag)`
- Check if the tweak name is null to avoid potential crash when declaring it.

#### [v5.6.7](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.6.7)
_November 22nd - 2019_

#### Fixes
- Crash when formatting timezones due to OEM bug https://github.com/mixpanel/mixpanel-android/issues/567
- Fix `ArrayOutOfBoundsException` due to OEM bug https://github.com/mixpanel/mixpanel-android/issues/241
- Use current loop handler when waiting for UA integration
- Better FCM initialization to avoid warnings and fix crash when using other FCM providers https://github.com/mixpanel/mixpanel-android/issues/608

#### New features
- Added `$ae_total_app_sessions` and `$ae_total_app_session_length`. Parity with iOS (thanks @Ivansap!)
- Allow passing super properties when initializing the library. Addresses https://github.com/mixpanel/mixpanel-android/issues/597
 

#### [v5.6.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.6.2)
_May 29th - 2019_
### Bug fixes
#### Fixes
- Fix fading on takeover in-app notifications not being present for some Android devices

#### [v5.6.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.6.1)
_May 10th - 2019_
### Bug fixes
#### Fixes
- Avoid `OutOfMemoryError` when loading in-app notifications in low-end devices
- Do not send `$carrier` property if empty. Parity with other SDKs
- Preserve user opt-out status when initializing the SDK

#### [v5.6.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.6.0)
_March 21st - 2019_
### Support for account-level analytics
#### New Features
- Set, add, and remove groups the user belongs to.
- Track events with specific groups
- Support for group-level profiles—set, update, and remove properties on account/group objects in Mixpanel

#### [v5.5.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.5.2)
_March 14th - 2019_
### Event triggered in-app notifications support
Currently users have no control over when an in-app notification shows up. With this release users can now control when an in-app gets displayed based on an event that they track within their app. This "trigger" event is defined during message creation at www.mixpanel.com.

You can additionally filter the event based on properties that are tracked along with the event for even finer controls.

#### [v5.5.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.5.1)
_March 7th - 2019_
### FCM improvements
#### Fixes
- Use `LruCache` from `android.util` and remove any dependencies with supportv4 library
- Push token was not set-up correctly if a user was not identified

#### Improvements
- ##### Allow sub-classes to override push notifications payload
```java
public class CustomMixpanelFirebaseMessaging extends MixpanelFCMMessagingService {
    @Override
    protected void onMessageReceived(Context context, Intent intent) {
        if (intent.getExtras().containsKey("mp_message")) {
            intent.putExtra("mp_message", "CUSTOM MESSAGE");
        }
        super.onMessageReceived(context, intent);
    }
}
```
- ##### Support when more than one push provider is used
Google doesn't allow an app to have more than one handler for `com.google.firebase.MESSAGING_EVENT` intent filter. Only the first registered service will handle a firebase message. If you need to use multiple push providers, you can now easily access Mixpanel FCM handler from your custom class. Register your custom FirebaseMessagingService service and:
```java
public class PushProvidersHandler extends FirebaseMessagingService {
    @Override
    public void onNewToken(String s) {
        super.onNewToken(s);
        MixpanelFCMMessagingService.addToken(s);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        if (remoteMessage.getData().containsKey("mp_message")) {
            MixpanelFCMMessagingService.showPushNotification(getApplicationContext(), remoteMessage.toIntent());
        }
    }
}
```

#### [v5.5.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.5.0)
_February 20th - 2019_
### FCM Support
GCM has been officially removed from the SDK. The following information is useful for existing and new Mixpanel customers:

**How to remove GCM from an existing app**
1. Open your `AndroidManifest.xml` file an remove the following permissions since they are included in FCM already:

```xml
<uses-permission android:name="com.google.android.c2dm.permission.RECEIVE" />
<uses-permission android:name="android.permission.GET_ACCOUNTS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<permission android:name="YOUR_PACKAGE.permission.C2D_MESSAGE" android:protectionLevel="signature" />
<uses-permission android:name="YOUR_PACKAGE.permission.C2D_MESSAGE" />
```

2. Remove the following tag from your `AndroidManifest.xml` file:

```xml
<receiver android:name="com.mixpanel.android.mpmetrics.GCMReceiver"
        android:permission="com.google.android.c2dm.permission.SEND" >
        <intent-filter>
                <action android:name="com.google.android.c2dm.intent.RECEIVE" />
                <action android:name="com.google.android.c2dm.intent.REGISTRATION" />
                <category android:name="YOUR_PACKAGE" />
         </intent-filter>
</receiver>
```

3. Remove GCM from your gradle dependencies:

```gradle
dependencies {
      compile 'com.google.android.gms:play-services-gcm:10.0.1'   // Remove this line
      ....
}
```

4. Remove any reference to `People.initPushHandling(senderId)` since it's no longer used:

```java
MixpanelAPI.getInstance(this).getPeople().initPushHandling(SENDER_ID); // Remove this line
```


**How to add FCM to your app**
1. Add the following tag inside your `application` tag in your `AndroidManifest.xml` file:

```xml
<application>
     ...
     <service
            android:name="com.mixpanel.android.mpmetrics.MixpanelFCMMessagingService"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                 <action android:name="com.google.firebase.MESSAGING_EVENT"/>
            </intent-filter>
      </service>
     ...
</application>
```

2. Add FCM to your gradle dependencies. You should already have Google Services added, but double check that's the case:

```gradle
buildscript {
     ...
     dependencies {
           classpath 'com.google.gms:google-services:4.1.0'
           ...
     }
}

dependencies {
     implementation 'com.google.firebase:firebase-messaging:17.3.4' // It must be higher than 16.2.0
     ...
}

apply plugin: 'com.google.gms.google-services'
```

3. Place your `google-services.json` file to your Android project. You can grab that file from your [Firebase Console center](https://support.google.com/firebase/answer/7015592?hl=en). 

4. We suggest to update your GCM API Key on mixpanel.com and use your FCM Server Key instead. Notice that your existing GCM Key it's now called Legacy token and it is still supported.

5. Enjoy!

#### [v5.4.5](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.4.5)
_January 31st - 2019_
- Pass flag to backend indicating when $distinct_id might have been set to a pre-existing $distinct_id value instead of a generated UUID (used when resolving aliases)

#### [v5.3.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.3.1)
_April 10th - 2018_
### Fixes
- Prevent double tracking for some experiments
- Prevent tweaks declaration before they have been explicitly declared in code. Fixes https://github.com/mixpanel/mixpanel-android/issues/531
- Fix in-app notification crash https://github.com/mixpanel/mixpanel-android/issues/516

### Improvements:
- Tweaks are cleared after calling `reset()` now.
- Added logic to prevent dup events at ingestion time
- Apply all editor changes at once when connected to Mixpanel UI builder
- Increase data expiration max time value


#### [v5.3.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.3.0)
_February 20th - 2018_
You can now change Mixpanel endpoints at runtime by using the following APIs:

```
MPConfig.getInstance(this).setEventsEndpoint("https://myapp.company.com/trackendpoint/");
```

```
MPConfig.getInstance(this).setPeopleEndpoint("https://myapp.company.com/peopleendpoint/");
```

```
MPConfig.getInstance(this).setDecideEndpoint("https://myapp.company.com/decidepoint/");
```

If you wish to restore Mixpanel values you can use the following APIs:
```
MPConfig.getInstance(this).setMixpanelEventsEndpoint();
```

```
MPConfig.getInstance(this).setMixpanelPeopleEndpoint();
```

```
MPConfig.getInstance(this).setMixpanelDecideEndpoint();
```

#### [v5.2.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.2.4)
_January 23rd - 2018_
- Fixes `IllegalStateException` when removing a mini in-app notification (https://github.com/mixpanel/mixpanel-android/issues/516)

#### [v5.2.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.2.3)
_January 04th - 2018_
- Remove unused code
- Fix https://github.com/mixpanel/mixpanel-android/issues/505

#### [v5.2.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.2.2)
_December 11th - 2017_
### New features
- Added support for Urban Airship integration. Learn more here: https://mixpanel.com/platform

### Fixes
- Fix `NullPointerException` on `Pair.hashCode()`: https://github.com/mixpanel/mixpanel-android/issues/507 and https://github.com/mixpanel/mixpanel-android/issues/515

#### [v5.2.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.2.1)
_October 9th - 2017_
### Fixes
- When running an experiment with multiple tweaks, only one was applied.
### Improvements
- We now persist variants as soon as the SDK receives them. In our previous release we relied on the call of `joinExperimentsIfAvailable()` method.
- We now apply persisted tweaks even if they are not declared yet.

#### [v5.2.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.2.0)
_September 23rd - 2017_
### New features
- We now persist Tweaks and AB test experiments!
- Android Oreo support. If you want to customize your notifications channel for devices running 26 or above you can use the following new keys:
```xml
<meta-data android:name="com.mixpanel.android.MPConfig.NotificationChannelId"
                   android:value="mixpanel_id" />

<meta-data android:name="com.mixpanel.android.MPConfig.NotificationChannelName"
                   android:value="mixpanel" />

<meta-data android:name="com.mixpanel.android.MPConfig.NotificationChannelImportance"
                   android:value="4" /> <!-- IMPORTANCE_HIGH -->
```
- Add option to specify whether or not Mixpanel will determine geolocation information using the client IP (see `ip=1` in https://mixpanel.com/help/reference/http for more information). Example of use:
```xml
<meta-data android:name="com.mixpanel.android.MPConfig.UseIpAddressForGeolocation"
                   android:value="false" />
```
- You can now specify maximum and minimum values for your tweaks with `longTweak` `intTweak` `doubleTweak` and `floatTweak`.
### Fixes
- Fix `SecurityException` crash on some Samsung devices running 7.0 due to missing `BLUETOOTH` permission (fixes https://github.com/mixpanel/mixpanel-android/issues/424)
- Call `onMixpanelTweakUpdated` only after tweaks are updated (fixes https://github.com/mixpanel/mixpanel-android/issues/472)
- Fix random crash on emulators (https://github.com/mixpanel/mixpanel-android/issues/417)
- Fix crash using mini in-app notifications when trying to remove a notification while the activity was being destroyed (fixes https://github.com/mixpanel/mixpanel-android/issues/400)
- Fix crash if we tried to show an in-app before `people.identify()` is called (fixes https://github.com/mixpanel/mixpanel-android/issues/449)
- Fix `BadParceableException` for activities with unparceable intents (fixes https://github.com/mixpanel/mixpanel-android/issues/251)
- Specify locale to calculate session length so session lengths are always in seconds.

### Improvements
- Various AB tests and Codeless events improvements (see PR for more details https://github.com/mixpanel/mixpanel-android/pull/492)
- Remove fallback urls support. The following meta-tags won't have any effect: 
```
com.mixpanel.android.MPConfig.DisableFallback
com.mixpanel.android.MPConfig.EventsFallbackEndpoint 
com.mixpanel.android.MPConfig.DecideFallbackEndpoint 
com.mixpanel.android.MPConfig.PeopleFallbackEndpoint
```
- Change thread priority from `THREAD_PRIORITY_LESS_FAVORABLE` to `THREAD_PRIORITY_BACKGROUND`
- Track whether the user tapped on the primary or secondary button in an in-app notification. We also now track `$campaign_open` even if there is no cta url
- Cast `campaign_id` and `message_id` to integers

#### [v5.1.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.1.4)
_June 23rd - 2017_
- Prevent in-app notifications from showing more than once (https://github.com/mixpanel/mixpanel-android/pull/469)

#### [v5.1.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.1.3)
_June 19th - 2017_
- We added support for tracking Mixpanel smart notifications (https://github.com/mixpanel/mixpanel-android/pull/467)

#### [v5.1.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.1.2)
_June 6th - 2017_

- Fix memory leak https://github.com/mixpanel/mixpanel-android/issues/463
- Add `eventElapsedTime()` API to retrieve the time elapsed for the named event since `timeEvent()` was called.

#### [v5.1.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.1.1)
_May 26th - 2017_

Fixes [#464](https://github.com/mixpanel/mixpanel-android/issues/464)


#### [v5.1.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.1.0)
_May 22nd - 2017_

With this release we are adding automatic tracking of common mobile events. 
These events include tracking app sessions, first app opens, app updated, and app crashed.

The feature will be rolled out slowly to all our users, and can be turned on in Project settings under Autotrack.

To configure the tracking of app sessions, we now expose two new configurations to provide lower and upper bounds on the session lengths that your app will track. For example, to only track app sessions with a minimum duration of 3 seconds and maximum duration of 5 minutes:

```
<meta-data android:name="com.mixpanel.android.MPConfig.MinimumSessionDuration"
            android:value="3000" />
```

```
<meta-data android:name="com.mixpanel.android.MPConfig.SessionTimeoutDuration"
            android:value="300000" />
```

#### [v5.0.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.0.2)
_April 24th - 2017_

- Fix crash when takeover activity is not defined in the AndroidManifest.xml https://github.com/mixpanel/mixpanel-android/pull/451
#### [v5.0.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.0.1)
_April 14th - 2017_

Fixes:
- Fix ConcurrentModificationException in Tweaks https://github.com/mixpanel/mixpanel-android/issues/414
- Fix ConcurrentModificationException on `OnMixpanelUpdatesReceivedListener` https://github.com/mixpanel/mixpanel-android/issues/395
- Update log output to be consistent with property names
- `getActiveNetworkInfo` proper handling: it could return `null`. Fix https://github.com/mixpanel/mixpanel-android/pull/445

New features:
- Thanks to https://github.com/mixpanel/mixpanel-android/pull/283 you can now specify your notifications behavior (sound, vibration, etc..) by using a meta-tag:
```
<meta-data
            android:name="com.mixpanel.android.MPConfig.NotificationDefaults"
            android:value="1"/>
```
You can use any of the following values: https://developer.android.com/reference/android/app/Notification.html#DEFAULT_ALL


#### [v5.0.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.0.0)
_April 3rd - 2017_

We have fully removed surveys after three months of being deprecated.

#### [v4.9.8](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.8)
_March 8th - 2017_

Remove `BLUETOOTH` permission and make it optional again.

#### [v4.9.7](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.7)
_March 1st - 2017_

Fix crash for API below 21 ([#434](https://github.com/mixpanel/mixpanel-android/issues/434))

#### [v4.9.6](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.6)
_February 24th - 2017_

Fix `NoSuchMethodError` for API below 23 when showing a mini in-app notification ([#431](https://github.com/mixpanel/mixpanel-android/issues/431))

#### [v4.9.5](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.5)
_February 20th - 2017_

We now can track open push notifications (for API 14 and above). Events that will be automatically sent (like iOS):
- `$app_open` will be tracked if the user taps on the push notification. campaign_id and message_id are sent as well as message_type=push.
- `$campaign_received` will be tracked if the app receives a push while the app is running in the foreground. Same properties as before are sent.

#### [v4.9.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.4)
_February 14th - 2017_

In addition to the standard dark or light message templates, you can now customize the format of your in-app messages to ensure they are on-brand:
* Customize text, background, and button colors
* Remove image fades
* Select large or standard image footprints by hiding text
* Add a secondary call-to-action button for multiple deep-linking paths

Improvements:
  - Custom logger. Use `MPLog` and [customize your minimum log level](https://github.com/mixpanel/mixpanel-android/blob/v4.9.4/src/main/java/com/mixpanel/android/util/MPLog.java#L17).

#### [v4.9.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.3)
_December 21st - 2016_
### We are deprecating surveys as in 4.9.3. Surveys will be completely removed from the SDK soon.
- Crash fixes:
  - `ConcurrentModificationException` when using `addOnMixpanelUpdatesReceivedListener`.
  - `ConcurrentModificationException` when using `Tweaks.getDefaultValues()`
  - `NullPointerException` when destroying a survey.
- Improvements:
  - No network attempts to visual editor if it's not connected (this will remove all the error logs you guys are seeing in debug mode). 
  -  New meta-tag flag: `com.mixpanel.android.MPConfig.IgnoreInvisibleViewsVisualEditor`. Set this boolean to true if you don't want invisible views to be sent to our visual editors (AB Test or Codeless).

#### [v4.9.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.2)
_September 2nd - 2016_
- Crash fixes:
  - `NoClassDefFoundError` on some Samsung devices when using `BluetoothAdapter`
- Bug Fixes:
  - Tweak-related bugs with some of our improvements (below).
- Improvements:
  - New API: `getPushRegistrationId` to retrieve the device push token through a mixpanel instance.
  - Add proguard rules.
  - Ability to remove a single push token through `clearPushRegistrationId(token)`.
  - Ability to remove a people property through `remove(name, value)`
  - Ability to disable the `ViewCrawler` (AB Test/Codeless) for specific project tokens (handy if you have multiple mixpanel instances). To use this feature:
    
    ```
    <meta-data android:name="com.mixpanel.android.MPConfig.DisableViewCrawlerForProjects"
        android:resource="@array/my_project_list" />
    ```
    
    And define the following on your `array.xml`:
    
    ```
    <resources>
    <string-array name="my_project_list">
    <item>project token 1</item>
    <item>project token 2</item>
    </string-array>
    </resources>
    ```
  -  Ability to add listeners to know when tweaks are updated:
    
    ```
    MixpanelAPI.getInstance(context, API_TOKEN). addOnMixpanelTweakUpdatedListener(<your listener>)
    ```
  -  Push notification enhancements: From mixpanel.com you can now use the following keys as part of a push notification payload:
    
    `mp_icnm_l` Large icon to be used on a push notification.
    `mp_icnm_w` White icon to be used on Lollipop and above.
    `mp_color` Color to be used on Lollipop and above (#argb).
    Example of a payload:
    
    ```
    {"mp_icnm":"an_icon", "mp_icnm_l":"big_icon", "mp_icnm_w":"white_icon","mp_color":"#FFAA0000"}
    ```
  -  Time events are persisted across user sessions.
  -  Upgrade GCM and avoid using deprecated GCM APIs. **Your Google Play Services library must be 7.5.0 or above**

#### [v4.9.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.0)
_July 1st - 2016_
- Crash fixes:
  - Activity life cycle callbacks in old Android APIs.
  - `OutOfMemoryError` for in-app and ab test.
  - `NullPointerException` when accessing people profiles after resetting a Mixpanel instance.
- Bug Fixes:
  - Resize survey texts to fit any screen.
  - AB Test working in emulators running new Android APIs.
  - Proper data resetting after calling `reset()`.
- Improvements:
  - Hide next/previous arrows on surveys when there are no next/previous questions.
  - Added an LRU Cache to re-use & quickly access images.

#### [v4.8.7](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.8.7)
_May 11st - 2016_
- Bug fixes:
  - Get real available memory to avoid displaying small images when there's enough space.
  - In-app notifications: improved layout for certain devices (no overlap with text and correct image alignment).

#### [v4.8.6](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.8.6)
_April 22nd - 2016_
- New features:
  - New in-app notification styles: dark & light!
  - Added `OfflineMode`
- Bug fixes:
  - Clear pre-filled answers on surveys
  - Exit A/B test experiments when they are turned off

#### [v4.8.5](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.8.5)
_March 25th - 2016_
- New features:
  - Surveys are now translated to spanish.
  - Automatically flush events when the app goes to the background. Available for Android API 14 and above.
  - Flush all events in the queue regardless of its size.
  - New HTTP test cases
- Crash fixes:
  - NPE if the context is a `BridgeContext`.
  - Handle all exceptions on `HttpService` to avoid crash.
  - Strict Mode: Leak when registering a sensor listener.
  - `Survey` parceable: wrong `CREATOR` object.
- Bug fixes:
  - Remove `GET_ACCOUNTS` permission.
  - Remove unused play services. Only `gcm` and `base` are needed.

#### [v4.8.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.8.0)
_February 9th - 2016_
 * New features:
   * Let user disable any `decide` API (i.e. no surveys, no notifications and no AB testing) by configuring the meta-data. Example:
   ```
    <meta-data android:name="com.mixpanel.android.MPConfig.DisableDecideChecker"
            android:value="true" />
   ```
   * Let user disable any the `ViewCrawler` by configuring the meta-data. Example:
   ```
 <meta-data android:name="com.mixpanel.android.MPConfig.DisableViewCrawler"
            android:value="true" />
   ```
   * Removed debug flush interval. If you are setting `com.mixpanel.android.MPConfig.DebugFlushInterval` on your `AndroidManifest.xml` you should remove it since it won't have any effect.
   * Implemented a back-off mechanism when server is busy to avoid repeated and consecutive requests.
 * Crash fixes:
   * Add tracking to match iOS SDK: survey received, survey shown and correct app build number/app version.
   * Wrong `ViewGroup` casting.
   * Displaying notifications when backgrounding the app.
   * Out of memory when displaying big images in notifications.
 * Bug fixes:
   * Memory leak when using `SensorManager`.
   * Clear user-related notifications/surveys/ab testing after setting a new `disctinctId`.
   * Remove `tools:ignore` properties (`AllowsBackUp` and `GradleOverrides`).
   * No AB test tracking for original variant under certain circumstances.
   * RTL layouts were wrongly rendered.
   * Repeated notifications/surveys when using ad blocker apps.

#### [v4.7.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.7.0)
 * Add the support for API 23
 * Enhance the inapp notification rendering

#### [v4.6.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.6.4)
 * Add a feature where the images of in-app notifications will be
 * cached locally after the first successful download

#### [v4.6.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.6.3)
 * Fix a bug where the user defined listener (by calling
   mMixpanel.getPeople().addOnMixpanelUpdatesReceivedListener())
   is not called on new variants receiving

#### [v4.6.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.6.1)
 * The Mixpanel library no longer uses the default SSLSocketFactory
   from the system schema registry, instead preferring the system
   defaults. Most users will not need to make any changes to their
   integrations. To change the SSL settings that the Mixpanel library
   uses, call MPConfig.getInstance(context).setSSLSocketFactory(socketFactory)


#### [v4.6](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.6.0)
 * Mixpanel A/B testing is now supported on Android. Users can
   register and recieve A/B testing tweaks and alter application look
   and feel using the Mixpanel A/B testing tool.

 * Addition of
       trackMap(String, Map<String, Object>)
       registerSuperPropertiesMap(String, Map<String, Object>)
       registerSuperPropertiesOnceMap(String, Map<String, Object>)
       setMap(Map<String, Object>)
       setOnceMap(Map<String, Object)

   which allow updates to properties and user profiles without
   requiring the construction of a JSONObject.

 * updateSuperProperties() which allows users to update super
   properties in place, in a thread-safe manner.

 * addition of merge() to the People API

 * Many Mixpanel logtags have changed to conform to the expectation
   that all logtags should be under 23 characters long.

 * Added a new configuration for flush interval when the app is in debug mode, defaults to 1 second

#### [v4.5.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.5.3)

 * Disable $app_open event by default. Users can opt-in to sending automatic $app_open events by adding

 ```
  <meta-data android:name="com.mixpanel.android.MPConfig.DisableAppOpenEvent"
       android:value="false" />
 ```

#### [v4.5.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.5.2)

 * Low level features to allow for more advanced push notifications

 * Bugfix, honor DisableFallback setting in checks for surveys and in-app notifications

#### [v4.5.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.5.1)

 * Update pom to allow users of gradle and maven to use the library without specifying packaging aar packaging.

 * Fix issue that prevented building from source in Eclipse

#### [v4.5](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.5.0)

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

* The minimum Android OS version necessary for surveys, in-app notifications, and dynamic event binding
  has been increased to JellyBean/API 16. The minimum OS version to use basic tracking features
  has been increased to Gingerbread/API 9.

#### [v4.4.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.4.1)

 * Improved support for Push notifications in Android Lollipop/API
   21. Users sending push notifications to Lollipop devices should
   include some version of Google Play Services in their build. In
   include Google Play Services, add the following to your
   build.gradle file:

```
   compile "com.google.android.gms:play-services:3.1+" // Any version above 3.1 will work
```

#### [v4.3.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.3.1)

 * This is a bugfix release only, to alter the handling of Surveys and In-App notifications when
   activities are removed or move to the background.

#### [v4.3.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.3.0)
 * Added support for App Links tracking

 * Added a way to get super properties

#### [v4.2.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.2.2)

 * Removed lint warnings from build

 * Fixed issue that could cause NullPointerExceptions to be thrown from the library
   if a user was identified as null

 * Handle attempts to load In-app notifications in low memory conditions

#### v4.2.1

 * Fixed a bug that would cause events to be dropped when the device thinks it has a valid network
   connection, but cannot actually send data over it.

#### [v4.2.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.2.0)

* `showSurveyById` and `showNotificationById` have been added for precise control over which
  survey or notification should be displayed.

* Added several default properties for Mixpanel People profiles. Each call to `set()` will now
  automatically include the application version name, Android version, and manufacturer, make, and
  model of the phone.

#### [v4.1.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.1.0)

This version adds support for Android in-app notifications.

* There is now an additional theme parameter on the SurveyActivity declaration in AndroidManifest.xml
  that is used for full screen in-app notifications.

  ```
  <activity android:name="com.mixpanel.android.surveys.SurveyActivity"
            android:theme="@style/com_mixpanel_android_SurveyActivityTheme"/>
  ```

* A new unified set of functions have been created to make it easier to fetch and display surveys
  and in-app notifications.

  * `getSurveyIfAvailable()` and `getNotificationIfAvailable()` have been added to fetch Survey and
    InAppNotification objects when the library has successfully received them. You may use these objects
    to display your own custom surveys or in-app notifications.

  * `showSurveyIfAvailable()` and `showNotificationIfAvailable()` have been added to display surveys and
    notifications when the library has successfully received them.

  * `addOnMixpanelUpdatesReceivedListener()` and `removeOnMixpanelUpdatesReceivedListener()` have been added
    so you may be notified when the library has successfully received a survey or in-app notification in the
    background.

  * `showSurvey()` and `checkForSurvey()` functions have been deprecated.

* `com.mixpanel.android.MPConfig.AutoCheckForSurveys` has been deprecated. The option has been renamed
  to `com.mixpanel.android.MPConfig.AutoShowMixpanelUpdates`. It is also now used for both surveys and in-app
  notifications.

* `com.mixpanel.android.MPConfig.TestMode` has been added. This option, when set to true, will render
  your in-app notifications and surveys but not track that they have been displayed. If you have multiple
  notifications/surveys, calls the respective show/get methods will simply rotate through them.

* `MixpanelAPI.logPosts()` has been deprecated. Set the `com.mixpanel.android.MPConfig.EnableDebugLogging`
  flag to true to now get extensive debugging output.

* The minimum Android version necessary for surveys and in-app notifications has been increased to 14,
  Ice Cream Sandwich to improve stability.

* `MixpanelAPI.alias()` has been added.

* The default library manifest no longer merges in recommended tags by default, as this was breaking
  builds for some users. You'll need to follow the steps in https://mixpanel.com/help/reference/android
  to configure your manifest for automatic referrer tracking, push notifications, in-app messages,
  and surveys. The recommended `<application>` tag in your app is

  ```
  <application>
          <!-- This activity allows your application to show Mixpanel
               surveys and takeover in-app notifications. -->
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

#### [v4.0.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.0.1)

* Default event storage is now 5 days.

#### [v4.0.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.0.0)

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
        compile "com.mixpanel.android:mixpanel-android:4.0.0@aar"
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

#### [v3.3.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.3.1)

* Internal changes to improve startup performance.

#### [v3.3.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.3.0)

* Calls to `increment()` now accept more general types (doubles instead of longs)
  existing calls should work without changes, types are strictly more general.

* Payloads of `increment()` are treated as doubles rather than longs in the internal library


#### [v3.2.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.2.0)

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


#### [v3.1.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.1.1)

* Bugfix and test for providing metadata with revenue analytics messages

#### [v3.1.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.1.0)

* Support for Mixpanel Revenue analytics

  The Mixpanel library now supports revenue analytics. To track income, call

  ```
  Mixpanel.getPeople().trackCharge(amount, properties)
  ```

  where amount is a double representing the amount of the charge, and properties is
  a possibly null collection of properties associated with the charge.

#### [v3.0.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v3.0.0)

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


#### [v2.2.3](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.2.3)

* Use SSL api endpoint by default

#### [v2.2.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.2.2)

* Fix to of initPushHandling

#### [v2.2.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.2.1)

* Changes to handling of catastrophic database failures (for example, full disks)

  The Mixpanel library will now truncate its database in the face of full disks or corrupted
  databases.

#### [v2.2](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.2.0)

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

#### [v2.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.1.0)

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

#### [v2.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v2.0)

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


















































