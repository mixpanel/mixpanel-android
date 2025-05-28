# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is the Mixpanel Android SDK - a library for integrating Mixpanel analytics into Android applications. The SDK provides event tracking, user profile management, and feature flag functionality.

## Key Architecture

### Core Components

1. **MixpanelAPI** (src/main/java/com/mixpanel/android/mpmetrics/MixpanelAPI.java)
   - Main entry point for SDK usage
   - Singleton pattern with `getInstance()` methods
   - Provides APIs for tracking events, managing user profiles, and accessing feature flags
   - Key methods: `track()`, `identify()`, `alias()`, `getPeople()`, `getGroup()`, `getFlags()`

2. **AnalyticsMessages** 
   - Handles asynchronous message processing and network communication
   - Manages event queue and batch sending to Mixpanel servers

3. **MPDbAdapter**
   - SQLite-based persistence for events and user data
   - Handles offline storage and retry logic

4. **PersistentIdentity**
   - Manages user identity and super properties across app sessions
   - Handles SharedPreferences storage

5. **Feature Flag System** (Recently added)
   - **FeatureFlagManager**: Core flag management with async/sync APIs
   - **FeatureFlagDelegate**: Interface for SDK integration
   - **MixpanelFlagVariant**: Data model for flag variants
   - Supports both synchronous and asynchronous flag retrieval

### Threading Model

- Main SDK operations are thread-safe
- FeatureFlagManager uses dedicated HandlerThread for state management
- Network operations run on separate executor threads
- Callbacks are dispatched to main thread

## Build Commands

```bash
# Build the library
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests (requires device/emulator)
./gradlew connectedAndroidTest

# Install to local Maven repository
./gradlew install

# Generate Javadoc
./gradlew androidJavadocsJar

# Lint checks
./gradlew lint

# Type checking (if configured)
# Check build.gradle for specific lint/type check tasks
```

## Development Workflow

### Testing Changes
1. Make changes to the SDK code
2. Test in the demo app: `mixpaneldemo/`
3. Run unit tests: `./gradlew test`
4. Run instrumented tests: `./gradlew connectedAndroidTest`

### Single Test Execution
```bash
# Run specific test class
./gradlew test --tests "com.mixpanel.android.mpmetrics.MixpanelBasicTest"

# Run specific test method
./gradlew test --tests "com.mixpanel.android.mpmetrics.MixpanelBasicTest.testTrackEvent"
```

### Release Process
The release process is automated via `release.sh`:
1. Updates version in `gradle.properties`
2. Updates README.md with release date
3. Publishes to Maven Central
4. Creates git tag
5. Updates documentation

## Important Patterns

### Feature Flag Usage
```java
// Async API
mixpanel.getFlags().isFlagEnabled("flag_name", false, isEnabled -> {
    // Handle result on main thread
});

// Sync API (avoid on main thread)
boolean isEnabled = mixpanel.getFlags().isFlagEnabledSync("flag_name", false);
```

### Event Tracking
- Events are queued locally and sent in batches
- Automatic retry on network failure
- 60-second flush interval by default

### Error Handling
- Network errors are logged but don't crash the app
- Failed events are persisted for retry
- Extensive debug logging available via MPConfig.EnableDebugLogging

## Current Branch Status
Currently on feature branch: `jared-feature-flag`
- Recent work on feature flag functionality
- Main branch for PRs: `master`