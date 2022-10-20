<p align="center">
  <img src="https://github.com/mixpanel/mixpanel-android/blob/assets/mixpanel.png?raw=true" alt="Mixpanel Android Library" height="150"/>
</p>

# Latest Version

##### _October 19, 2022_ - [v7.2.0](https://github.com/mixpanel/mixpanel-android/releases/tag/v7.2.0)

# Table of Contents

<!-- MarkdownTOC -->

- [Quick Start Guide](#quick-start-guide)
    - [Install Mixpanel](#1-install-mixpanel)
    - [Initialize Mixpanel](#2-initialize-mixpanel)
    - [Send Data](#3-send-data)
    - [Check for Success](#4-check-for-success)
- [FAQ](#i-want-to-know-more)
- [I want to know more!](#i-want-to-know-more)
- [Want to Contribute?](#want-to-contribute)
- [Changelog](#changelog)
- [License](#license)

<!-- /MarkdownTOC -->

<a name="quick-start-guide"></a>
# Quick Start Guide

Check out our **[official documentation](https://mixpanel.com/help/reference/android)** for more in depth information on installing and using Mixpanel on Android.

## 1. Install Mixpanel
You will need your project token for initializing your library. You can get your project token from [project settings](https://mixpanel.com/settings/project).

**Step 1 - Add the mixpanel-android library as a gradle dependency:**
We publish builds of our library to the Maven central repository as an .aar file. This file contains all of the classes, resources, and configurations that you'll need to use the library. To install the library inside Android Studio, you can simply declare it as dependency in your build.gradle file.

Add the following lines to the `dependencies` section in *app/build.gradle*

```gradle
implementation "com.mixpanel.android:mixpanel-android:6.+"
```
 
Once you've updated your build.gradle file, you can force Android Studio to sync with your new configuration by clicking the Sync Project with Gradle Files icon at the top of the window.

![Sync Android With Gradle](https://storage.googleapis.com/cdn-mxpnl-com/static/readme/android-sync-gradle.png)

This should download the .aar dependency at which point you'll have access to the Mixpanel library API calls. If it cannot find the dependency, you should make sure you've specified `mavenCentral()` as a repository in your `build.gradle`.

**Step 2 - Add permissions to your AndroidManifest.xml:**
In order for the library to work you'll need to ensure that you're requesting the following permissions in your AndroidManifest.xml:

```java
<!--
This permission is required to allow the application to send
events and properties to Mixpanel.
-->
<uses-permission
  android:name="android.permission.INTERNET" />

<!--
  This permission is optional but recommended so we can be smart
  about when to send data.
 -->
<uses-permission
  android:name="android.permission.ACCESS_NETWORK_STATE" />

<!--
  This permission is optional but recommended so events will
  contain information about bluetooth state
-->
<uses-permission
  android:name="android.permission.BLUETOOTH" />
```
At this point, you're ready to use the Mixpanel Android library inside Android Studio.

## 2. Initialize Mixpanel
Once you've set up your build system or IDE to use the Mixpanel library, you can initialize it in your code by calling MixpanelAPI.getInstance with your application context, your Mixpanel project token and automatic events setting. You can find your token in [project settings](https://mixpanel.com/settings/project).

```java
import com.mixpanel.android.mpmetrics.MixpanelAPI;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        trackAutomaticEvents = true;
        MixpanelAPI mixpanel = MixpanelAPI.getInstance(this, "YOUR_TOKEN", trackAutomaticEvents);
    }
}
```
[See all configuration options](http://mixpanel.github.io/mixpanel-android/index.html)

## 3. Send Data
Let's get started by sending event data. You can send an event from anywhere in your application. Better understand user behavior by storing details that are specific to the event (properties). After initializing the library, Mixpanel will [automatically collect common mobile events](https://mixpanel.com/help/questions/articles/which-common-mobile-events-can-mixpanel-collect-on-my-behalf-automatically). You can enable/disable automatic collection through your [project settings](https://help.mixpanel.com/hc/en-us/articles/115004596186#enable-or-disable-common-mobile-events). Also, Mixpanel automatically tracks some properties by default. [learn more](https://help.mixpanel.com/hc/en-us/articles/115004613766-Default-Properties-Collected-by-Mixpanel)

```java
JSONObject props = new JSONObject();
props.put("source", "Pat's affiliate site");
props.put("Opted out of email", true);

mixpanel.track("Sign Up", props);
```
In addition to event data, you can also send [user profile data](https://developer.mixpanel.com/docs/android#storing-user-profiles). We recommend this after completing the quickstart guide.

## 4. Check for Success
[Open up the Events report (formerly Live View) in Mixpanel](http://mixpanel.com/report/events) to view incoming events. 

Once data hits our API, it generally takes ~60 seconds for it to be processed, stored, and queryable in your project.

👋 👋  Tell us about the Mixpanel developer experience! [https://www.mixpanel.com/devnps](https://www.mixpanel.com/devnps) 👍  👎

# FAQ
**I want to stop tracking an event/event property in Mixpanel. Is that possible?**

Yes, in Lexicon, you can intercept and drop incoming events or properties. Mixpanel won’t store any new data for the event or property you select to drop. [See this article for more information](https://help.mixpanel.com/hc/en-us/articles/360001307806#dropping-events-and-properties).

**I have a test user I would like to opt out of tracking. How do I do that?**

Mixpanel’s client-side tracking library contains the [optOutTracking()](http://mixpanel.github.io/mixpanel-android/com/mixpanel/android/mpmetrics/MixpanelAPI.html#optOutTracking--) method, which will set the user’s local opt-out state to “true” and will prevent data from being sent from a user’s device. More detailed instructions can be found in the section, [Opting users out of tracking](android#opting-users-out-of-tracking).

**Why aren't my events showing up?**

To preserve battery life and customer bandwidth, the Mixpanel library doesn't send the events you record immediately. Instead, it sends batches to the Mixpanel servers every 60 seconds while your application is running, as well as when the application transitions to the background. You can call [flush()](http://mixpanel.github.io/mixpanel-android/com/mixpanel/android/mpmetrics/MixpanelAPI.html#flush--) manually if you want to force a flush at a particular moment for example before your application is completely shutdown.

If your events are still not showing up after 60 seconds, check if you have opted out of tracking. You can also enable Mixpanel debugging and logging, it allows you to see the debug output from the Mixpanel Android library. To enable it, you will want to add the following permission within your AndroidManifest.xml inside the `<application>` tag:

```java
...
<application>
    <meta-data
      android:name="com.mixpanel.android.MPConfig.EnableDebugLogging"
      android:value="true" />
    ...
</application>
...
```

<a name="i-want-to-know-more"></a>
# I want to know more!

No worries, here are some links that you will find useful:
* **[Sample app](https://github.com/mixpanel/sample-android-mixpanel-integration)**
* **[Android integration video tutorial](https://www.youtube.com/watch?v=KcpOa93eSVs)**
* **[Full API Reference](http://mixpanel.github.io/mixpanel-android/index.html)**

Have any questions? Reach out to [support@mixpanel.com](mailto:support@mixpanel.com) to speak to someone smart, quickly.

<a name="want-to-contribute"></a>
# Want to Contribute?

The Mixpanel library for Android is an open source project, and we'd love to see your contributions!
We'd also love for you to come and work with us! Check out our **[opening positions](https://mixpanel.com/jobs/#openings)** for details.

<a name="changelog"></a>
# Changelog

See [wiki page](https://github.com/mixpanel/mixpanel-android/wiki/Changelog).

<a name="license"></a>
# License

```
See LICENSE File for details. The Base64Coder,
ConfigurationChecker, and StackBlurManager classes, and the entirety of the
 com.mixpanel.android.java_websocket package used by this
software have been licensed from non-Mixpanel sources and modified
for use in the library. Please see the relevant source files, and the
LICENSE file in the com.mixpanel.android.java_websocket package for details.

The StackBlurManager class uses an algorithm by Mario Klingemann <mario@quansimondo.com>
You can learn more about the algorithm at
http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html.
```
