# AGENTS.md - Core SDK Components

This file provides focused instructions for AI agents working on the core Mixpanel SDK components.

## Component Overview

This directory contains the heart of the Mixpanel Android SDK:
- **MixpanelAPI.java** - Public API facade
- **AnalyticsMessages.java** - Message queue and background processing
- **MPDbAdapter.java** - SQLite persistence layer
- **PersistentIdentity.java** - User identity management
- **HttpService.java** - Network communication

## Critical Rules for This Directory

1. **MixpanelAPI Changes**
   - This is the ONLY public class - maintain backwards compatibility
   - Every public method must be thread-safe
   - Add JavaDoc with examples for any new methods
   - Never throw exceptions - catch and log

2. **AnalyticsMessages Pattern**
   ```java
   // Always add new message types following this pattern
   private static final int NEW_MESSAGE_TYPE = X;
   
   public static final class NewDescription {
       private final String data;
       private final String token;
       // Immutable - only constructor and getters
   }
   ```

3. **Database Operations**
   ```java
   // Always follow this pattern in MPDbAdapter
   Cursor cursor = null;
   try {
       cursor = db.query(...);
       // Process cursor
   } finally {
       if (cursor != null) cursor.close();
   }
   ```

4. **Thread Boundaries**
   - Public API methods: Main thread
   - Message processing: Worker thread (HandlerThread)
   - Database operations: Worker thread only
   - Network requests: Spawned from worker thread

## Common Tasks

### Adding a New Public API Method

1. Add to MixpanelAPI.java with overloads:
   ```java
   public void newMethod(String param) {
       newMethod(param, null);
   }
   
   public void newMethod(String param, JSONObject properties) {
       if (!hasOptedOut()) {
           try {
               // Validate
               if (param == null) {
                   MPLog.e(LOGTAG, "Invalid param");
                   return;
               }
               // Queue to worker
               Message msg = Message.obtain();
               msg.what = NEW_METHOD_MESSAGE;
               msg.obj = new MethodDescription(param, properties, mToken);
               mMessages.enqueueMessage(msg);
           } catch (Exception e) {
               MPLog.e(LOGTAG, "Failed", e);
           }
       }
   }
   ```

2. Add handler in AnalyticsMessages
3. Add test in androidTest/

### Modifying Database Schema

1. Increment DATABASE_VERSION in MPDbAdapter
2. Add migration in onUpgrade:
   ```java
   if (oldVersion < NEW_VERSION) {
       // Add column or create table
       // NEVER drop existing tables/data
   }
   ```
3. Update Table enum if new table
4. Test upgrade path from previous version

### Adding Configuration Option

1. Add to MPConfig.java:
   ```java
   private final boolean mNewOption;
   
   // In constructor
   mNewOption = metaData.getBoolean(
       "com.mixpanel.android.MPConfig.NewOption", 
       DEFAULT_VALUE
   );
   ```

2. Add to MixpanelOptions.Builder
3. Document in AndroidManifest example

## Testing Requirements

For ANY change in this directory:

```bash
# Minimum test run - all tests
./gradlew connectedAndroidTest

# Specific component tests (use full class names)
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MPDbAdapterTest
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.AnalyticsMessagesTest

# Run specific test method
./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.MixpanelBasicTest#testTrackCharge
```

## Do NOT Modify Without Approval

- DATABASE_VERSION (requires migration testing)
- Public API method signatures (breaks compatibility)
- Message type constants (affects message processing)
- Table names or schemas (requires migration)

Remember: This is the core of a widely-used SDK. Every change here affects thousands of apps.