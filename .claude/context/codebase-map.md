# Mixpanel Android SDK Codebase Map

## Project Structure Overview

```
mixpanel-android/
├── src/main/java/com/mixpanel/android/
│   ├── mpmetrics/                    # Core SDK implementation
│   │   ├── MixpanelAPI.java         # Main entry point & public API
│   │   ├── AnalyticsMessages.java   # Message queue & background processing
│   │   ├── MPDbAdapter.java         # SQLite persistence layer
│   │   ├── PersistentIdentity.java  # Identity & properties management
│   │   ├── HttpService.java         # Network communication
│   │   ├── MPConfig.java            # Configuration management
│   │   ├── FeatureFlagManager.java  # Feature flags implementation
│   │   ├── ResourceIds.java         # R.id resource handling
│   │   ├── ResourceReader.java      # Resource reading utilities
│   │   ├── DecideChecker.java       # Remote configuration fetcher
│   │   ├── SessionMetadata.java    # Session tracking
│   │   ├── ConnectivityReceiver.java # Network state monitoring
│   │   ├── ExceptionHandler.java    # Crash reporting
│   │   ├── MixpanelActivityLifecycleCallbacks.java # Lifecycle integration
│   │   └── [Various data models & utilities]
│   └── util/                         # Utility classes
│       ├── MPLog.java               # Logging utility
│       ├── HttpService.java         # HTTP utilities
│       ├── ImageStore.java          # Image caching
│       ├── OfflineMode.java         # Offline mode management
│       └── [Other utilities]
├── src/androidTest/                  # Instrumented tests only
│   └── java/com/mixpanel/android/
│       ├── mpmetrics/               # Core SDK tests
│       └── util/                    # Utility tests
├── mixpaneldemo/                     # Demo application (Kotlin/Compose)
│   └── src/main/
│       ├── java/                    # Demo app code
│       └── res/                     # Demo resources
├── acceptance/test-application/      # Acceptance test app
├── build.gradle                      # Main build configuration
├── maven.gradle                      # Maven publishing configuration
├── proguard.txt                      # Consumer ProGuard rules
└── gradle.properties                 # Version & properties

```

## Key Components

### Core SDK (`src/main/java/com/mixpanel/android/mpmetrics/`)

**Entry Points:**
- `MixpanelAPI` - Main SDK interface, singleton per token
- `MixpanelOptions` - Runtime configuration

**Data Flow Components:**
- `AnalyticsMessages` - Manages event queue and background processing
- `MPDbAdapter` - SQLite database for offline storage
- `HttpService` - Network communication layer

**Identity & State:**
- `PersistentIdentity` - Manages user identity and super properties
- `SessionMetadata` - Session tracking and timing

**Feature Components:**
- `FeatureFlagManager` - Feature flag loading and caching
- `DecideChecker` - Remote configuration updates
- `ExceptionHandler` - Automatic crash reporting

### Testing Structure (`src/androidTest/`)

**Test Organization:**
- All tests are instrumented (require Android device/emulator)
- No unit tests (src/test directory absent)
- Tests use AndroidJUnit4 runner
- Mock implementations in TestUtils

### Demo Application (`mixpaneldemo/`)

**Technology Stack:**
- Kotlin language
- Jetpack Compose UI
- Demonstrates SDK integration patterns
- Shows all major SDK features

## Module Dependencies

```
mixpanel-android (library)
    ├── androidx.annotation:annotation
    ├── androidx.core:core
    └── Android SDK (min 21, target 34)

mixpaneldemo (app)
    ├── :mixpanel-android (project dependency)
    ├── Jetpack Compose dependencies
    └── Kotlin standard library
```

## Build Variants

- **debug** - Development build with debugging enabled
- **release** - Production build with ProGuard optimization

## Data Flow Architecture

```
User Code
    ↓
MixpanelAPI (Public Interface)
    ↓
AnalyticsMessages (Queue Management)
    ↓
MPDbAdapter (Persistence)
    ↓
HttpService (Network)
    ↓
Mixpanel Servers
```

## Key Design Decisions

1. **Single Module Library** - All code in one module for simplicity
2. **Minimal Dependencies** - Only essential AndroidX libraries
3. **Custom HTTP** - No external networking libraries
4. **SQLite Direct** - No ORM, direct database access
5. **HandlerThread** - Background processing without Service
6. **Instrumented Tests Only** - Focus on real device testing

This map provides navigation context for understanding code organization and relationships between components.