# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build the library
./gradlew build

# Run unit tests
./gradlew test

# Run instrumented tests (requires Android device/emulator)
./gradlew connectedAndroidTest

# Install to local Maven repository  
./gradlew install

# Clean build artifacts
./gradlew clean

# Generate Javadocs
./gradlew androidJavadocs

# Run tests with coverage
./gradlew createDebugCoverageReport

# Lint checks
./gradlew lint

# Build demo app
./gradlew :mixpaneldemo:build
```

## Testing

- **No unit tests**: The SDK uses instrumented tests only for real device validation
- **Instrumented tests**: Located in `/src/androidTest/` (require Android device/emulator)
  - Use AndroidJUnit4 runner
  - Test pattern: `./gradlew connectedAndroidTest --tests "*TestClassName*"`
  - BlockingQueue pattern for async testing
  - TestUtils provides mock implementations

## Architecture Overview

The Mixpanel Android SDK follows a producer-consumer pattern with persistent storage:

1. **MixpanelAPI** (Main Entry Point)
   - Singleton access via `getInstance()`
   - Handles event tracking, people properties, feature flags
   - Thread-safe, supports multiple instances

2. **AnalyticsMessages** (Message Queue)
   - Manages communication between user threads and background worker
   - Batches events before sending to servers
   - Implements retry logic and offline handling

3. **MPDbAdapter** (Persistence Layer)
   - SQLite-based storage for events and people updates
   - Handles offline queuing and data retrieval

4. **PersistentIdentity** (Identity Management)
   - Manages distinct IDs, anonymous IDs, super properties
   - Uses SharedPreferences for persistence

5. **HttpService** (Network Layer)
   - Handles all HTTP communication with Mixpanel servers
   - Supports GZIP compression
   - Configurable timeouts and retry logic

## Key Implementation Details

- Events are batched and sent every 60 seconds or on app background
- SQLite database stores events when offline
- Feature flags are cached and refreshed periodically
- Automatic events track app lifecycle (configurable)
- ProGuard rules are provided in `proguard.txt`

## Release Process

The project uses semantic versioning (X.Y.Z) and publishes to Maven Central:
- Version is defined in `gradle.properties` as `VERSION_NAME`
- Release script: `./release.sh [version]`
- Published as: `com.mixpanel.android:mixpanel-android:X.Y.Z`

### Maven Central Portal Setup

The SDK now publishes via the new Maven Central Portal (replacing OSSRH):

1. **Generate Portal Tokens**:
   - Log in to https://central.sonatype.com with your OSSRH credentials
   - Navigate to your account settings
   - Generate a user token (username and password pair)
   - Store these securely in `~/.gradle/gradle.properties`:
     ```
     centralPortalToken=<your-token-username>
     centralPortalPassword=<your-token-password>
     ```
   - **Security Note**: For enhanced security, consider using encrypted storage options instead of plain text:
     - Environment variables: `export CENTRAL_PORTAL_TOKEN=...`
     - gradle-credentials-plugin for encrypted storage
     - System keychain integration (e.g., macOS Keychain, Windows Credential Store)
     - CI/CD secret management systems

2. **Publishing Process**:
   - The release script uses the OSSRH Staging API for compatibility
   - After running `./release.sh`, deployments appear at https://central.sonatype.com/publishing/deployments
   - Manual release to Maven Central is required from the Portal UI

3. **GitHub Actions**:
   - Portal tokens should be stored as repository secrets:
     - `CENTRAL_PORTAL_TOKEN` (the token username)
     - `CENTRAL_PORTAL_PASSWORD` (the token password)

## Project Configuration

- Min SDK: 21
- Target/Compile SDK: 34
- Android Gradle Plugin: 8.10.0
- Kotlin: 1.9.0

## Key Patterns and Conventions

### Threading Model
- Single HandlerThread for background processing
- No Service components used
- Message-based communication between threads
- All public APIs are thread-safe

### Error Handling Philosophy
- **Never crash the host app** - catch all exceptions
- Silent failures with logging for non-critical errors
- Defensive null checking throughout
- Graceful degradation when features unavailable

### Code Style
- Package-private visibility by default for internal classes
- Member variables prefixed with 'm' (e.g., `mContext`)
- Final fields for immutability
- Static nested classes for data models
- Synchronized blocks over synchronized methods

### API Design
- Singleton with token-based instances
- Builder pattern for configuration (MixpanelOptions)
- Fluent interfaces for People/Group operations
- Progressive disclosure through method overloading

### Resource Management
- Always use application context to prevent leaks
- Cursor and database cleanup in finally blocks
- Lazy initialization for expensive objects
- WeakReference for activity/callback references

### Testing Approach
- Instrumented tests only (no unit tests)
- BlockingQueue pattern for async verification
- Real database testing (not mocked)
- Thread safety validation in tests

### Configuration Hierarchy
1. Runtime options (MixpanelOptions)
2. AndroidManifest meta-data
3. Compile-time defaults (MPConfig)

### Database Patterns
- Direct SQLite usage (no ORM)
- Enum-based table management
- Prepared statements for performance
- Automatic cleanup based on data age

For detailed patterns and examples, see:
- `.claude/context/discovered-patterns.md`
- `.claude/context/architecture/system-design.md`
- `.claude/context/workflows/`