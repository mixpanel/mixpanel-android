# GitHub Codespaces Development Environment

This directory contains the configuration for developing the Mixpanel Android SDK in GitHub Codespaces.

## Overview

The development environment uses a **hybrid model**:
- **GitHub Codespace**: For coding, building, and running unit tests
- **Local Machine**: For running Android emulator or connecting physical devices

This approach provides the best performance since Codespaces don't support hardware acceleration (KVM) needed for Android emulators.

## Features

- **Java 17** with Microsoft OpenJDK
- **Android SDK 34** with build tools 34.0.0
- **Gradle wrapper** support (8.11.1)
- **Modern development tools**: ripgrep, fd, bat, sd
- **Starship prompt** for better terminal experience
- **Pre-configured VS Code** with Java and Android extensions
- **ADB bridge support** for connecting to local devices

## Quick Start

1. **Create a Codespace** from this repository
2. **Connect your local device/emulator**:
   
   On your LOCAL machine:
   ```bash
   # Start Android emulator or connect device
   adb start-server
   
   # List your codespaces to get the full name
   gh cs list
   
   # Connect to Codespace with ADB tunnel (use full codespace name)
   gh cs ssh -c FULL_CODESPACE_NAME -- -R 5037:localhost:5037
   # Example: gh cs ssh -c verbose-winner-gg9r5vqp7gc9pw4 -- -R 5037:localhost:5037
   ```

3. **In the Codespace terminal**:
   ```bash
   # Verify device connection
   adb devices
   
   # Build the SDK
   ./gradlew build
   
   # Run instrumented tests
   ./gradlew connectedAndroidTest
   
   # Run specific test class
   ./gradlew connectedAndroidTest --tests "*MixpanelBasicTest"
   ```

## Configuration Details

### Machine Requirements
- **CPU**: 8 cores
- **Memory**: 16GB
- **Storage**: 64GB

These resources ensure smooth Gradle builds and Java development.

### Port Forwarding
- **Port 5037**: ADB server connection (auto-forwarded)

### Environment Variables
- `ANDROID_HOME`: Points to Android SDK location
- `ANDROID_SDK_ROOT`: Same as ANDROID_HOME
- `PATH`: Includes Android SDK tools
- `GRADLE_USER_HOME`: Set to `/workspace/.gradle` for caching

## Prebuilds

For faster Codespace creation, configure prebuilds in your repository settings:
1. Go to Settings → Codespaces → Prebuild configurations
2. Add configuration for the `main` branch
3. This will pre-install all SDK components and dependencies

## Troubleshooting

### ADB Connection Issues
If `adb devices` shows no devices:
1. Ensure emulator/device is running locally
2. Run `adb start-server` locally  
3. Make sure you're using the full codespace name (use `gh cs list` to find it)
4. Check the SSH tunnel is active
5. Try `adb kill-server` and `adb start-server` in Codespace

Note: The error "Failed to start Emulator console for 5554" is harmless and can be ignored.

### Build Issues
- Clear Gradle cache: `./gradlew clean`
- Check Java version: `java -version` (should be 17)
- Verify Android SDK: `echo $ANDROID_HOME`

## Development Workflow

1. **Make changes** in the Codespace editor
2. **Build locally**: `./gradlew assembleDebug`
3. **Run tests**: `./gradlew connectedAndroidTest`
4. **Install on device**: `./gradlew installDebug`

### Running the Demo App

```bash
# Build and install the demo app
./gradlew :mixpaneldemo:installDebug

# Launch the demo app
adb shell am start -n com.mixpanel.mixpaneldemo/.MainActivity

# Or do both in one command
./gradlew :mixpaneldemo:installDebug && adb shell am start -n com.mixpanel.mixpaneldemo/.MainActivity
```

### Viewing Logs

```bash
# View Mixpanel SDK logs
adb logcat -s MixpanelAPI:*

# View all demo app logs
adb logcat | grep com.mixpanel.mixpaneldemo
```

### Opening Codespace in Different IDEs

```bash
# Open in VS Code (default)
gh cs code -c FULL_CODESPACE_NAME

# Open in JetBrains IDE (requires JetBrains Gateway)
gh cs code -c FULL_CODESPACE_NAME --ide jetbrains
```

## Notes

- The Android emulator cannot run inside Codespaces due to lack of KVM support
- Use the hybrid model for the best development experience
- All builds and tests run in the Codespace, only the emulator/device is external