#!/usr/bin/env bash
# Put the stock Google TV launcher back. Ambient stays installed but is no longer home.
set -euo pipefail
IP="${1:?usage: $0 <projector-ip>}"
export ANDROID_SERIAL="$IP:5555"
adb connect "$ANDROID_SERIAL" >/dev/null
adb wait-for-device
adb shell pm enable com.google.android.tvlauncher
adb shell cmd package set-home-activity com.google.android.tvlauncher/.MainActivity
adb shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
echo "Stock launcher restored. Remove Ambient with: adb uninstall com.tanoo.ambient"
