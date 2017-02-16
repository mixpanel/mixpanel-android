<p align="center">
  <img src="https://github.com/mixpanel/mixpanel-android/blob/assets/mixpanel.png?raw=true" alt="Mixpanel Android Library" height="150"/>
</p>

Latest Version [![Build Status](https://travis-ci.org/mixpanel/mixpanel-android.svg)](https://travis-ci.org/mixpanel/mixpanel-android)
--------------------------
##### _February 14, 2017_ - [v4.9.4](https://github.com/mixpanel/mixpanel-android/releases/tag/v4.9.4)

Quick Installation
------------------
Check out our **[official documentation](https://mixpanel.com/help/reference/android)** to learn how to install the library on Android Studio. It takes less than 2 minutes!

**TLDR;**

`
compile "com.mixpanel.android:mixpanel-android:4.+" 
`

```java
String projectToken = YOUR_PROJECT_TOKEN; // e.g.: "1ef7e30d2a58d27f4b90c42e31d6d7ad" 
MixpanelAPI mixpanel = MixpanelAPI.getInstance(this, projectToken);
mixpanel.track("My first event!");
```
I want to know more!
--------------------
No worries, here are some links that you will find useful:
* **[Sample app](https://github.com/mixpanel/sample-android-mixpanel-integration)**
* **[Android integration video tutorial](https://www.youtube.com/watch?v=KcpOa93eSVs)**
* **[Full API Reference](http://mixpanel.github.io/mixpanel-android/index.html)**

Have any questions? Reach out to [support@mixpanel.com](mailto:support@mixpanel.com) to speak to someone smart, quickly.

Want to Contribute?
-------------------
The Mixpanel library for Android is an open source project, and we'd love to see your contributions!
We'd also love for you to come and work with us! Check out our **[opening positions](http://boards.greenhouse.io/mixpanel/)** for details.

Changelog
---------
See [wiki page](https://github.com/mixpanel/mixpanel-android/wiki/Changelog).

License
-------

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
