#!/bin/bash
set -e

echo "===================================="
echo "🎯 Mixpanel Android SDK Development Environment"
echo "===================================="

# Install modern development tools as specified in CLAUDE.md
echo "🔧 Installing development tools..."

# Install ripgrep (rg) - preferred over grep
if ! command -v rg &> /dev/null; then
    echo "  📦 Installing ripgrep..."
    curl -LO https://github.com/BurntSushi/ripgrep/releases/download/14.1.0/ripgrep_14.1.0-1_amd64.deb
    sudo dpkg -i ripgrep_14.1.0-1_amd64.deb > /dev/null 2>&1
    rm ripgrep_14.1.0-1_amd64.deb
fi

# Install fd - preferred over find
if ! command -v fd &> /dev/null; then
    echo "  📦 Installing fd..."
    curl -LO https://github.com/sharkdp/fd/releases/download/v9.0.0/fd_9.0.0_amd64.deb
    sudo dpkg -i fd_9.0.0_amd64.deb > /dev/null 2>&1
    rm fd_9.0.0_amd64.deb
fi

# Install bat - preferred over cat
if ! command -v bat &> /dev/null; then
    echo "  📦 Installing bat..."
    curl -LO https://github.com/sharkdp/bat/releases/download/v0.24.0/bat_0.24.0_amd64.deb
    sudo dpkg -i bat_0.24.0_amd64.deb > /dev/null 2>&1
    rm bat_0.24.0_amd64.deb
fi

# Install sd - preferred over sed
if ! command -v sd &> /dev/null; then
    echo "  📦 Installing sd..."
    curl -LO https://github.com/chmln/sd/releases/download/v1.0.0/sd-v1.0.0-x86_64-unknown-linux-gnu.tar.gz
    tar -xzf sd-v1.0.0-x86_64-unknown-linux-gnu.tar.gz
    sudo mv sd-v1.0.0-x86_64-unknown-linux-gnu/sd /usr/local/bin/
    rm -rf sd-v1.0.0-x86_64-unknown-linux-gnu*
fi

# Install Starship prompt
if ! command -v starship &> /dev/null; then
    echo "  🌟 Installing Starship prompt..."
    curl -sS https://starship.rs/install.sh | sh -s -- --yes > /dev/null 2>&1
fi

# Configure shell
echo "🐚 Configuring shell..."
if [ ! -f ~/.zshrc ] || ! grep -q "starship init" ~/.zshrc; then
    echo 'eval "$(starship init zsh)"' >> ~/.zshrc
fi

# Android SDK setup
echo "📦 Setting up Android SDK..."
# Find the Android SDK location
if [ -n "$ANDROID_SDK_ROOT" ]; then
    ANDROID_HOME="$ANDROID_SDK_ROOT"
elif [ -d "/home/vscode/android-sdk" ]; then
    ANDROID_HOME="/home/vscode/android-sdk"
elif [ -d "/opt/android" ]; then
    ANDROID_HOME="/opt/android"
fi

if [ -n "$ANDROID_HOME" ]; then
    echo "  Android SDK found at: $ANDROID_HOME"
    export ANDROID_HOME
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
    
    # Accept licenses and install required components
    if command -v sdkmanager &> /dev/null; then
        echo "  Accepting Android licenses..."
        yes | sdkmanager --licenses > /dev/null 2>&1 || true
        echo "  Installing Android SDK components..."
        sdkmanager "platforms;android-34" "build-tools;34.0.0" "platform-tools" || true
    fi
    
    # Add to shell profile
    if ! grep -q "ANDROID_HOME" ~/.zshrc; then
        echo "export ANDROID_HOME=$ANDROID_HOME" >> ~/.zshrc
        echo "export ANDROID_SDK_ROOT=$ANDROID_HOME" >> ~/.zshrc
        echo "export PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools" >> ~/.zshrc
    fi
else
    echo "⚠️  Android SDK not found"
fi

# Setup Gradle
echo "📦 Setting up Gradle..."
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    # Test Java is available
    echo "  Java version: $(java -version 2>&1 | head -n 1)"
    echo "  JAVA_HOME: $JAVA_HOME"
    ./gradlew --version
    # Pre-download dependencies
    ./gradlew dependencies --no-daemon || true
fi

# Create helper scripts
echo "📝 Creating helper scripts..."

# Create ADB connection script
cat > ~/connect-adb.sh << 'EOF'
#!/bin/bash
# Helper script to connect to local ADB server via SSH tunnel

echo "🔌 ADB Remote Connection Helper"
echo "================================"
echo ""
echo "This script helps you connect your local Android emulator/device to this Codespace."
echo ""
echo "Prerequisites on your local machine:"
echo "1. Android emulator running OR physical device connected"
echo "2. ADB server running (run 'adb start-server' locally)"
echo ""
echo "To connect, run this command on your LOCAL machine:"
echo ""
echo "  gh cs ssh -c $CODESPACE_NAME -- -R 5037:localhost:5037"
echo ""
echo "Once connected, you can use 'adb devices' here to see your device."
echo ""
echo "Current ADB status:"
adb devices 2>/dev/null || echo "ADB not available. Please connect using the command above."
EOF
chmod +x ~/connect-adb.sh

# Verify setup
echo ""
echo "🔍 Environment verification:"
echo "  Java version: $(java -version 2>&1 | head -n 1)"
echo "  Java Home: ${JAVA_HOME:-'Auto-detected by system'}"
echo "  Gradle wrapper: $(./gradlew --version 2>/dev/null | grep 'Gradle' | head -n 1 || echo 'Not found in current directory')"
echo "  Android SDK: ${ANDROID_HOME:-'Not configured'}"
echo "  ADB: $(which adb 2>/dev/null || echo 'Not found')"
echo "  SDKManager: $(which sdkmanager 2>/dev/null || echo 'Not found')"
echo ""
echo "===================================="
echo "✅ Development environment ready!"
echo ""
echo "🚀 Quick Start:"
echo "  1. Run '~/connect-adb.sh' for instructions on connecting to your local emulator/device"
echo "  2. Use './gradlew build' to build the SDK"
echo "  3. Use './gradlew connectedAndroidTest' to run instrumented tests"
echo ""
echo "📚 For more info, see README.md and CLAUDE.md"
echo "===================================="