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

# Setup Java environment
echo "☕ Setting up Java environment..."
export JAVA_HOME="/usr/lib/jvm/msopenjdk-21-amd64"
export PATH="$JAVA_HOME/bin:$PATH"

# Setup Gradle
echo "📦 Setting up Gradle..."
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew --version
    # Pre-download dependencies
    ./gradlew dependencies --no-daemon || true
fi

# Add Java configuration to shell profile
if ! grep -q "JAVA_HOME" ~/.zshrc; then
    echo "export JAVA_HOME=/usr/lib/jvm/msopenjdk-21-amd64" >> ~/.zshrc
    echo "export PATH=\$JAVA_HOME/bin:\$PATH" >> ~/.zshrc
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
echo "  Java Home: $JAVA_HOME"
echo "  Gradle wrapper: $(./gradlew --version 2>/dev/null | grep 'Gradle' | head -n 1 || echo 'Not found in current directory')"
echo "  Android SDK: $ANDROID_HOME"
echo "  ADB: $(which adb 2>/dev/null || echo 'Not found')"
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