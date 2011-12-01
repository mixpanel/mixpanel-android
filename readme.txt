Documentation: http://mixpanel.com/api/docs/guides/android

Demo: See the demo/ folder for a working demo application

Note: In the latest version of the Android library, we have removed the funnel() methods. Funnels have been built from events for some time now, and this update to Android simply reflects that change we have made.

The proper way to perform funnel analysis is to create a funnel based on events that you are sending. For more information, please see http://mixpanel.com/api/docs/guides/funnel-analysis

We have also renamed the event method to track, to be more consistent with the existing APIs. Furthermore, the propeties object passed to the new track method is no longer a HashMap, but a JSONObject. This will cause types to be correctlypreseved in Segmentation.
