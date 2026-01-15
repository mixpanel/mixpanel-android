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
  - **IMPORTANT**: Run tests from the main module (not :mixpaneldemo) using `:connectedAndroidTest`
  - Run all tests: `./gradlew :connectedAndroidTest`
  - Run specific test class: `./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName`
  - Run specific test method: `./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName#testMethodName`
  - Run multiple test methods: `./gradlew :connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.mixpanel.android.mpmetrics.TestClassName#testMethod1,testMethod2`
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

### Automated Release Process
The `release.sh` script handles the complete release workflow:
1. Updates version in gradle.properties and README.md
2. Builds and publishes artifacts to OSSRH staging
3. Automatically uploads to Maven Central Portal (requires env vars)
4. Creates git tag and updates documentation
5. Updates to next snapshot version

**Required Environment Variables**:
```bash
export CENTRAL_PORTAL_TOKEN=<your-portal-username>
export CENTRAL_PORTAL_PASSWORD=<your-portal-password>
```

### Maven Central Portal Setup

The SDK publishes via the new Maven Central Portal:

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
   - Artifacts are uploaded to OSSRH staging API: `https://ossrh-staging-api.central.sonatype.com/`
   - The Portal upload is triggered via the manual API endpoint
   - Deployments appear at https://central.sonatype.com/publishing/deployments
   - Manual release to Maven Central is required from the Portal UI (unless using automatic publishing)

3. **GitHub Actions**:
   - Use the `publish-maven.yml` workflow for automated publishing
   - Portal tokens should be stored as repository secrets:
     - `CENTRAL_PORTAL_TOKEN` (the token username)
     - `CENTRAL_PORTAL_PASSWORD` (the token password)
   - Publishing types available:
     - `user_managed`: Manual release from Portal UI (default)
     - `automatic`: Auto-release if validation passes

4. **Manual Portal Upload** (if automation fails):
   ```bash
   AUTH_TOKEN=$(echo -n "username:password" | base64)
   curl -X POST \
     -H "Authorization: Bearer $AUTH_TOKEN" \
     "https://ossrh-staging-api.central.sonatype.com/manual/upload/defaultRepository/com.mixpanel.android?publishing_type=user_managed"
   ```

## Project Configuration

- Min SDK: 21
- Target/Compile SDK: 34
- Android Gradle Plugin: 8.13.2
- Kotlin: 2.1.0

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

## Memories

- Ensured CLAUDE.md accurately reflects the most recent changes