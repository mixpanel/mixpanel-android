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
   
   # Connect to Codespace with ADB tunnel
   gh cs ssh -c YOUR_CODESPACE_NAME -- -R 5037:localhost:5037
   ```

3. **In the Codespace terminal**:
   ```bash
   # Verify device connection
   adb devices
   
   # Build the SDK
   ./gradlew build
   
   # Run instrumented tests
   ./gradlew connectedAndroidTest
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
3. Check the SSH tunnel is active
4. Try `adb kill-server` and `adb start-server` in Codespace

### Build Issues
- Clear Gradle cache: `./gradlew clean`
- Check Java version: `java -version` (should be 17)
- Verify Android SDK: `echo $ANDROID_HOME`

## Development Workflow

1. **Make changes** in the Codespace editor
2. **Build locally**: `./gradlew assembleDebug`
3. **Run tests**: `./gradlew connectedAndroidTest`
4. **Install on device**: `./gradlew installDebug`

## Notes

- The Android emulator cannot run inside Codespaces due to lack of KVM support
- Use the hybrid model for the best development experience
- All builds and tests run in the Codespace, only the emulator/device is external