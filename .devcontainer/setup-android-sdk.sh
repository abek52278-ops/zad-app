#!/bin/bash
set -e

ANDROID_HOME=/opt/android-sdk

# Create SDK directory
mkdir -p $ANDROID_HOME
cd $ANDROID_HOME

# Download command-line tools
echo "Downloading Android SDK command-line tools..."
wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q commandlinetools-linux-11076708_latest.zip
rm commandlinetools-linux-11076708_latest.zip
mkdir -p cmdline-tools/latest
mv cmdline-tools/* cmdline-tools/latest/ || true

# Accept licenses
echo "Accepting Android SDK licenses..."
yes | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null 2>&1 || true

# Install required SDKs and tools
echo "Installing Android SDK packages..."
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
  "platforms;android-36" \
  "platforms;android-35" \
  "build-tools;36.0.0" \
  "platform-tools" \
  "emulator" \
  "system-images;android-35;google_apis;x86_64" \
  > /dev/null 2>&1

echo "Android SDK setup complete!"
echo "ANDROID_HOME=$ANDROID_HOME"
which sdkmanager
