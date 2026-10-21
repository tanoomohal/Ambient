# Ambient

A minimal home screen for the XGIMI projector (tested on the Horizon Pro, model XK03H, Android TV 11), replacing the Google TV launcher.

- **Home**: an ambient dashboard with an analog and digital clock, a weather card, air quality, PM2.5 and UV, and a Now Playing bar. Every widget can be toggled in Settings.
- **Apps** (press ↓ from Home): HDMI / Apple TV tile first, then the apps you choose. Hold OK on an app to rearrange it.
- **Settings**: Light / Dark / Automatic theme, an option to open HDMI on power-on when CEC sees a device, HDMI source, clock style, stats, location, units, and app picker.

The design follows Apple's tvOS guidelines: focus is the main interaction, the focused item lifts and gets a crisp edge, text is large enough to read from across the room, and nothing is shown that you didn't ask for. Inter stands in for SF Pro because SF Pro's license is limited to Apple platforms.

## Build

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

## Install / undo

On the projector, enable Developer options and network debugging first. Then:

```sh
scripts/install.sh <projector-ip>   # installs, grants media + overlay access, sets as HOME, disables stock launcher
scripts/restore.sh <projector-ip>   # re-enables the Google TV launcher
```

## How things work

| Feature | Mechanism |
|---|---|
| HDMI switch | XGIMI has no handler for the standard passthrough `ACTION_VIEW`. Instead it starts `com.xgimi.xhplayer/.InputActivity` with action `com.xgimi.action.hdmiPlayer`, data = the passthrough URI of the **hardware** input (`…/HW1`), and extra `sw=HDMI1` as a fallback. The standard intent is tried second |
| Apple TV detection | HDMI-CEC devices appear as child TV inputs (e.g. `com.xgimi.tvinput/.inputservice.Hdmi1TvInputService/HDMI120004`) labelled with the CEC name |
| Power-on → HDMI | `SCREEN_ON` receiver plus a cold-boot check, polling up to 15 s for CEC. Needs `appops SYSTEM_ALERT_WINDOW allow` to start an activity from the background |
| Now Playing | `MediaSessionManager.getActiveSessions`; needs `cmd notification allow_listener` because Android TV has no UI for it. Doesn't cover the Apple TV over HDMI, since HDMI carries no metadata |
| Weather / AQI | Open-Meteo forecast, air-quality and geocoding APIs (no key) |

## Credits

The Inter font by Rasmus Andersson is used under the SIL Open Font License 1.1 (see `FONT-LICENSE-Inter.txt`).
