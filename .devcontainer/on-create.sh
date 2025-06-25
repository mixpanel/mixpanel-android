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

# Skip Gradle operations during onCreate phase
# These will be handled in postCreate when the workspace is properly mounted
echo "📦 Gradle setup will be performed in post-create phase..."

echo "✅ Codespace creation complete!"