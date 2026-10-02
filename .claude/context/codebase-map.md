# Mixpanel Android SDK Codebase Map

## Project Structure Overview

```
mixpanel-android/
├── analytics/                        # Main analytics SDK module (:analytics)
│   ├── src/main/java/com/mixpanel/android/
│   │   ├── mpmetrics/                # Core SDK implementation
│   │   │   ├── MixpanelAPI.java      # Main entry point & public API
│   │   │   ├── MixpanelOptions.java  # Runtime configuration (builder)
│   │   │   ├── AnalyticsMessages.java   # Message queue & background processing
│   │   │   ├── MPDbAdapter.java      # SQLite persistence layer
│   │   │   ├── PersistentIdentity.java  # Identity & properties management
│   │   │   ├── MPConfig.java         # Configuration management
│   │   │   ├── FeatureFlagManager.java  # Feature flags implementation
│   │   │   ├── FeatureFlagOptions.java / FlagsConfig.java / MixpanelFlagVariant.java # Feature-flag models
│   │   │   ├── AutomaticEvents.java  # Automatic lifecycle events
│   │   │   ├── DeviceIdProvider.java # Device/anonymous ID generation
│   │   │   ├── SessionMetadata.java  # Session tracking
│   │   │   ├── SessionReplayBroadcastReceiver.java # Session Replay integration hook
│   │   │   ├── ResourceIds.java / ResourceReader.java # Resource handling
│   │   │   ├── ExceptionHandler.java # Crash reporting
│   │   │   ├── MixpanelActivityLifecycleCallbacks.java # Lifecycle integration
│   │   │   └── [Various data models & utilities]
│   │   └── util/                     # Utility classes
│   │       ├── MPLog.java            # Logging utility
│   │       ├── HttpService.java      # HTTP / network communication
│   │       ├── RemoteService.java    # HTTP service interface
│   │       ├── OfflineMode.java      # Offline mode management
│   │       ├── ProxyServerInteractor.java / W3CTraceContext.java # Proxy & trace context
│   │       └── [Other utilities]
│   ├── src/test/                     # Unit tests (JVM — JUnit/Robolectric)
│   │   └── java/com/mixpanel/android/mpmetrics/
│   ├── src/androidTest/              # Instrumented tests (device/emulator)
│   │   └── java/com/mixpanel/android/
│   │       ├── mpmetrics/            # Core SDK tests
│   │       └── util/                 # Utility tests
│   ├── mixpaneldemo/                 # Demo application (:analytics:mixpaneldemo)
│   │   └── src/main/
│   │       ├── java/                 # Demo app code
│   │       └── res/                  # Demo resources
│   ├── build.gradle                  # Analytics module build
│   ├── proguard.txt                  # Consumer ProGuard rules
│   └── gradle.properties             # Analytics version & POM properties
├── common/                           # Shared utilities (:common — MixpanelEventBridge, JsonLogic)
├── openfeature-provider/             # OpenFeature provider (:openfeature-provider)
├── session-replay/                   # Session Replay SDK (:session-replay, published; own CI + CHANGELOG)
│   └── sessionreplaydemo/            # Session Replay demo app (:session-replay:sessionreplaydemo)
├── build-logic/                      # Included build: mixpanel.maven-publish / mixpanel.ktlint convention plugins
├── gradle/libs.versions.toml         # Version catalog (used by session-replay)
├── scripts/                          # Dev scripts (codespace helper)
├── build.gradle                      # Root build (cross-cutting config)
├── settings.gradle                   # Subproject includes (6 projects)
└── gradle.properties                 # Shared org.gradle.* / android.* settings

```

## Key Components

### Core SDK (`analytics/src/main/java/com/mixpanel/android/mpmetrics/`)

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
- `FeatureFlagManager` - Feature flag loading and caching (with `FlagsConfig` / `MixpanelFlagVariant`)
- `ExceptionHandler` - Automatic crash reporting

### Testing Structure (`analytics/src/androidTest/`)

**Test Organization:**
- **Unit tests** — `analytics/src/test/` (JUnit/Robolectric, run on the JVM via `:analytics:test`)
- **Instrumented tests** — `analytics/src/androidTest/` (AndroidJUnit4, require a device/emulator, via `:analytics:connectedAndroidTest`)
- Async verification uses the BlockingQueue pattern; mock implementations in TestUtils

### Demo Application (`analytics/mixpaneldemo/`)

**Technology Stack:**
- Kotlin language
- Jetpack Compose UI
- Demonstrates SDK integration patterns
- Shows all major SDK features

## Module Dependencies

```
mixpanel-android (library)
    ├── com.mixpanel.android:mixpanel-android-common (Maven coordinate, not project dep)
    ├── androidx.annotation:annotation
    ├── androidx.core:core
    ├── androidx.lifecycle:lifecycle-process
    ├── io.github.jamsesso:json-logic-java (+ gson pin for its transitive JSON dep)
    └── Android SDK (min 21, target 34)

:analytics:mixpaneldemo (app)
    ├── :analytics (project dependency)
    ├── Jetpack Compose dependencies
    └── Kotlin standard library
```

## Build Variants

- **debug** - Development build with coverage enabled
- **release** - Production build; `minifyEnabled false` — the library ships a minimal
  consumer ProGuard rule (`analytics/proguard.txt`) but does not minify itself

## Data Flow Architecture

```
User Code
    ↓
MixpanelAPI (Public Interface)
    ├─ tracking → AnalyticsMessages (Queue Management)
    │                 ├─→ MPDbAdapter (SQLite persistence/offline queue)
    │                 └─→ HttpService (Network) → Mixpanel Servers
    └─ feature flags → FeatureFlagManager → HttpService (separate path, no SQLite)
```

Note: AnalyticsMessages owns both the DB adapter and the HTTP poster —
MPDbAdapter never talks to the network.

## Key Design Decisions

1. **Multi-Module Repo** - Independently versioned/published modules (:analytics, :common, :openfeature-provider, :session-replay) consuming each other via Maven coordinates
2. **Minimal Dependencies** - Small fixed runtime set (see Module Dependencies above); no new deps
3. **Custom HTTP** - No external networking libraries
4. **SQLite Direct** - No ORM, direct database access (rawQuery-based)
5. **HandlerThread Workers** - Background processing on dedicated HandlerThreads (AnalyticsMessages, FeatureFlagManager) without Service components
6. **Two Test Layers** - JVM unit tests (`src/test/`) plus instrumented tests (`src/androidTest/`) for real-device coverage

This map provides navigation context for understanding code organization and relationships between components.