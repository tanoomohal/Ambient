#!/usr/bin/env bash
# Install Ambient on the projector and make it the home screen.
# Usage: scripts/install.sh <projector-ip>
set -euo pipefail
IP="${1:?usage: $0 <projector-ip>}"
export ANDROID_SERIAL="$IP:5555"
APK="$(dirname "$0")/../app/build/outputs/apk/release/app-release.apk"
PKG=com.tanoo.ambient

adb connect "$ANDROID_SERIAL" >/dev/null
adb wait-for-device
adb install -r "$APK"

# Now Playing: Android TV has no UI for notification-listener access, so grant it here.
adb shell cmd notification allow_listener "$PKG/$PKG.NowPlayingService"
# Lets power-on open HDMI even when another app was in front before standby.
adb shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow

# Make Ambient the HOME app, then disable the stock launcher so HOME can't fall back to it.
adb shell cmd package set-home-activity "$PKG/.MainActivity"
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
echo "Done. Undo with scripts/restore.sh"
