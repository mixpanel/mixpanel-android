# Debug Issue Prompt Template

Use this template when debugging issues in the Mixpanel Android SDK:

## Prompt Structure

"I'm debugging an issue in the Mixpanel Android SDK where [SYMPTOM].

Context:
- SDK version: [VERSION]
- Android version: [ANDROID_VERSION]
- Device: [DEVICE_INFO]
- Frequency: [always/intermittent/specific conditions]

Observed behavior:
[WHAT_HAPPENS]

Expected behavior:
[WHAT_SHOULD_HAPPEN]

I've already checked:
- [ ] Debug logging enabled
- [ ] Token is correct
- [ ] Not opted out
- [ ] Network connectivity

Please help me:
1. Identify potential root causes
2. Add strategic logging to trace the issue
3. Suggest test scenarios to reproduce
4. Provide a fix that maintains SDK stability"

## Common Debug Scenarios

### Events Not Sending
"I'm debugging an issue in the Mixpanel Android SDK where events are not appearing in the dashboard.

Context:
- SDK version: 8.2.0
- Android version: 13
- Device: Pixel 6
- Frequency: Always

Observed behavior:
Events are tracked but never appear in Mixpanel dashboard. No errors in logs.

Expected behavior:
Events should appear within 60 seconds.

I've already checked:
- [x] Debug logging enabled - shows events queued
- [x] Token is correct
- [x] Not opted out
- [x] Network connectivity available

Please help me trace through:
1. AnalyticsMessages queue processing
2. MPDbAdapter storage
3. HttpService flush attempts
4. Response handling"

### Memory Leak
"I'm debugging a memory leak in the Mixpanel Android SDK.

Context:
- SDK version: 8.2.0
- LeakCanary shows Activity held by MixpanelAPI

Observed behavior:
Activities not garbage collected after rotation.

Expected behavior:
No Activity references should be retained.

Please help identify:
1. Where Activity context might be stored
2. Missing WeakReference usage
3. Listener registration without cleanup"

### Thread Deadlock
"I'm debugging a deadlock in the Mixpanel Android SDK.

Context:
- Occurs when calling getPeople().set() from multiple threads
- ANR after 5 seconds

Observed behavior:
App freezes when multiple threads access People API.

Expected behavior:
Thread-safe concurrent access.

Please analyze:
1. Lock ordering in synchronized blocks
2. Potential circular dependencies
3. Lock granularity improvements"