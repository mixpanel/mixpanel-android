#!/bin/bash

# Helper script to connect local Android device/emulator to GitHub Codespace
# This script should be run on your LOCAL machine, not in the Codespace

set -e

echo "🔌 GitHub Codespace ADB Connection Helper"
echo "========================================="
echo ""

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ GitHub CLI (gh) is not installed."
    echo "   Please install it from: https://cli.github.com/"
    exit 1
fi

# Check if user is authenticated
if ! gh auth status &> /dev/null; then
    echo "❌ You are not authenticated with GitHub CLI."
    echo "   Please run: gh auth login"
    exit 1
fi

# Check if adb is installed
if ! command -v adb &> /dev/null; then
    echo "⚠️  ADB is not installed or not in PATH."
    echo "   Please install Android SDK or Android Studio."
    echo ""
fi

# List available codespaces
echo "📋 Your active Codespaces:"
echo ""
gh cs list

echo ""
echo "🔧 Prerequisites:"
echo "   1. Android emulator running OR physical device connected"
echo "   2. ADB server running (we'll start it for you)"
echo ""

# Start ADB server
if command -v adb &> /dev/null; then
    echo "🚀 Starting ADB server..."
    adb start-server
    echo "✅ ADB server started"
    echo ""
    echo "📱 Local devices:"
    adb devices
else
    echo "⚠️  Skipping ADB server start (ADB not found)"
fi

echo ""
echo "📝 To connect to a Codespace, run:"
echo ""
echo "   gh cs ssh -c CODESPACE_NAME -- -R 5037:localhost:5037"
echo ""
echo "Replace CODESPACE_NAME with one from the list above."
echo ""
echo "Once connected, you can use 'adb devices' in the Codespace terminal."
echo ""
echo "💡 Tip: Keep this SSH connection open while developing!"