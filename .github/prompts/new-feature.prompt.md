# New Feature Prompt Template

Use this template when implementing a new feature in the Mixpanel Android SDK:

## Prompt Structure

"I need to add [FEATURE_NAME] to the Mixpanel Android SDK that [DESCRIPTION].

Requirements:
- Public API method: `mixpanel.[METHOD_NAME]([PARAMETERS])`
- Should store data in [events/people/groups/new table]
- Configuration option: `[CONFIG_NAME]` (default: [DEFAULT_VALUE])
- Must be thread-safe and handle offline mode

The feature should follow existing patterns:
- Package-private implementation class
- Message-based communication to worker thread
- Defensive input validation without exceptions
- Instrumented tests with BlockingQueue pattern

Please generate:
1. Public API method in MixpanelAPI.java
2. Message description class
3. Handler implementation
4. Database support (if needed)
5. Configuration in MPConfig/MixpanelOptions
6. Instrumented test class"

## Example Usage

"I need to add session recording to the Mixpanel Android SDK that captures user interactions.

Requirements:
- Public API method: `mixpanel.startSessionRecording(int maxDuration)`
- Should store data in new 'sessions' table
- Configuration option: `sessionRecordingEnabled` (default: false)
- Must be thread-safe and handle offline mode

The feature should follow existing patterns:
- Package-private SessionRecorder class
- Message-based communication to worker thread
- Defensive input validation without exceptions
- Instrumented tests with BlockingQueue pattern

Please generate:
1. Public API method in MixpanelAPI.java
2. SessionDescription message class
3. Handler implementation in AnalyticsMessages
4. Database table in MPDbAdapter
5. Configuration in MPConfig/MixpanelOptions
6. SessionRecorderTest instrumented test class"