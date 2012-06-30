Documentation: http://mixpanel.com/api/docs/guides/android

Demo: See the demo/ folder for a working demo application

Changelog:

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
