<p align="center">
  <img src="https://github.com/mixpanel/mixpanel-android/blob/assets/mixpanel.png?raw=true" alt="Mixpanel Android Library" height="150"/>
</p>

# Latest Version [![Build Status](https://travis-ci.org/mixpanel/mixpanel-android.svg)](https://travis-ci.org/mixpanel/mixpanel-android)

##### _June 15, 2018_ - [v5.4.1](https://github.com/mixpanel/mixpanel-android/releases/tag/v5.4.1)

# Table of Contents

<!-- MarkdownTOC -->

- [Quick Start Guide](#quick-start-guide)
    - [Installation](#installation)
    - [Integration](#integration)
- [I want to know more!](#i-want-to-know-more)
- [Want to Contribute?](#want-to-contribute)
- [Changelog](#changelog)
- [License](#license)

<!-- /MarkdownTOC -->

<a name="quick-start-guide"></a>
# Quick Start Guide

Check out our **[official documentation](https://mixpanel.com/help/reference/android)** for more in depth information on installing and using Mixpanel on Android.

<a name="installation"></a>
## Installation

### Dependencies in *app/build.gradle*

Add Mixpanel and Google Play Services to the `dependencies` section in *app/build.gradle*

```gradle
compile "com.mixpanel.android:mixpanel-android:5.+"
compile "com.google.android.gms:play-services-gcm:7.5.0+"
```

### Permissions in *app/src/main/AndroidManifest.xml*

```xml
<!-- This permission is required to allow the application to send events and properties to Mixpanel -->
<uses-permission android:name="android.permission.INTERNET" />

<!-- This permission is optional but recommended so we can be smart about when to send data  -->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- This permission is optional but recommended so events will contain information about bluetooth state -->
<uses-permission android:name="android.permission.BLUETOOTH" />
```

<a name="integration"></a>
## Integration

### Initialization

Initialize Mixpanel in your main activity *app/src/main/java/com/mixpanel/example/myapplication/MainActivity.java*. Usually this should be done in [onCreate](https://developer.android.com/reference/android/app/Activity.html#onCreate(android.os.Bundle)).

```java
String projectToken = YOUR_PROJECT_TOKEN; // e.g.: "1ef7e30d2a58d27f4b90c42e31d6d7ad" 
MixpanelAPI mixpanel = MixpanelAPI.getInstance(this, projectToken);
```
Remember to replace `YOUR_PROJECT_TOKEN` with the token provided to you on mixpanel.com.

### Tracking

After installing the library into your Android app, Mixpanel will <a href="https://mixpanel.com/help/questions/articles/which-common-mobile-events-can-mixpanel-collect-on-my-behalf-automatically" target="_blank">automatically collect common mobile events</a>. You can enable/ disable automatic collection through your <a href="https://mixpanel.com/help/questions/articles/how-do-i-enable-common-mobile-events-if-i-have-already-implemented-mixpanel" target="_blank">project settings</a>.

With the `mixpanel` object created in [the last step](#integration) a call to `track` is all you need to send additional events to Mixpanel.

```java
mixpanel.track("Event name no props")

JSONObject props = new JSONObject();
props.put("Prop name", "Prop value");
props.put("Prop 2", "Value 2");
mixpanel.track("Event name", props);
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
