# ReelTracker

A completely private, offline-first Android app that counts how many Instagram Reels and YouTube Shorts you watch each day.

## What it does

ReelTracker uses Android's AccessibilityService to watch the UI structure of Instagram and YouTube. Each time you enter a Reels/Shorts feed, or swipe to the next video, it records a timestamped count. Everything stays on your device — there is no network permission, no account, no analytics.

## Privacy

- **No INTERNET permission** — the app literally cannot send data anywhere.
- **Scoped monitoring** — the accessibility service only receives events from `com.instagram.android` and `com.google.android.youtube`. All other apps are invisible to it.
- **No content reading** — only view resource IDs and scroll events are observed. Video titles, captions, account names, messages, and all personal content are never read or stored.
- **On-device storage only** — a local SQLite database via Room. Deleted when you uninstall.

## Setup

1. Install the APK (`./gradlew assembleDebug` to build, or use Android Studio).
2. Open the app. If the accessibility service is not enabled, tap **Enable** to open system Accessibility settings.
3. Find **ReelTracker** in the list and toggle it on.
4. Grant battery optimisation exemption when prompted (keeps tracking alive in background).

> **Note:** On Android 13+ you may need to allow "restricted settings" for the accessibility service. Go to Settings → Apps → ReelTracker → three-dot menu → Allow restricted settings, then return to Accessibility settings to enable it.

## Usage

- **Home** — today's count per platform, service status.
- **Stats** — daily / weekly / monthly breakdown with simple bar charts.
- **Privacy** — full explanation of what is and isn't monitored.

## Known Limitations

| Limitation | Explanation |
|---|---|
| View ID instability | Instagram and YouTube may rename their internal view IDs in any update. When this happens, detection silently stops (no false positives). A warning appears on the Home screen if no events are detected in 48 h. |
| Approximate counts | A 500 ms debounce window coalesces events from a single swipe. Very fast scrolling may slightly under-count. |
| Banking apps | Some banking apps refuse to run when any accessibility service with "retrieve window content" is active. Toggle the service off for the duration of banking sessions. |
| OEM battery management | MIUI, One UI, ColorOS and similar OEM skins may kill background processes aggressively. Add ReelTracker to your phone's battery whitelist in device settings. |
| PiP not supported | Shorts played in picture-in-picture mode are not counted in v1. |

## Building from source

Requirements: JDK 17, Android SDK with API 35.

```bash
cd "reel counter"
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # release APK (unsigned)
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (requires device/emulator)
```

## Architecture

```
accessibility/
  ReelTrackerAccessibilityService  — event loop, dispatches to detectors + state machines
  NodeWrapper / RealNodeWrapper    — testable abstraction over AccessibilityNodeInfo
  detectors/
    InstagramReelsDetector         — view-ID-based Reels detection
    YouTubeShortsDetector          — view-ID-based Shorts detection (player vs. shelf)
  statemachine/
    FeedStateMachine               — per-platform session state (Idle→Entered→InFeed→Exited)
    DebounceFilter                 — per-platform 500 ms event coalescing

data/
  db/        — Room entity, DAO, database
  repository — ViewEventRepository interface + implementation

domain/
  model/     — Platform, ViewEvent, DailyCount, DateUtils
  usecase/   — RecordViewEvent, GetDailyStats, GetWeeklyStats, GetMonthlyStats

service/
  KeepAliveService  — foreground service to prevent OS from freezing the process
  BootReceiver      — restarts KeepAliveService after reboot if service was enabled

ui/           — MVVM: HomeViewModel, StatsViewModel, Compose screens, Material 3 theme
di/           — ServiceLocator (manual DI, no Hilt)
```

## License

MIT
