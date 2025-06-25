#!/bin/bash
set -e

echo "===================================="
echo "🚀 Codespace Creation Script"
echo "===================================="

# This script runs when the container is created (only once)
# It handles one-time setup that should be cached in prebuilds

# Accept Android SDK licenses if needed
if [ -d "$ANDROID_HOME" ]; then
    echo "📱 Accepting Android SDK licenses..."
    yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true
fi

# Pre-download Gradle wrapper and dependencies
echo "📦 Pre-downloading Gradle dependencies..."
cd /workspace
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew --version
    # Download dependencies without building
    ./gradlew dependencies --no-daemon || true
fi

echo "✅ Codespace creation complete!"