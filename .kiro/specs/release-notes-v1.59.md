# Release notes — V1.59

Version: **1.59** (versionCode 59). Tag `V1.59`. Successor to V1.58.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.59 — fix startup crash when Buddy Link/location was active`

```
🔧 Fixed a crash on open
• Fixed the app crashing on launch when Buddy Link / location was active and
  background-location permission was missing (or the service start raced the system)
• The crash was ForegroundServiceDidNotStartInTimeException — the location-sharing
  service now enters the foreground immediately and unconditionally, satisfying
  Android's foreground-service contract every time
• Crashes are now recorded in the in-app log and more lifecycle events are logged,
  so problems are easier to diagnose
• No change to who can see what — no rules change
```

---

## How to publish

```
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
.\archive-build.ps1
Copy-Item builds\joggin-v1.59.apk builds\joggin.apk -Force
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.59 builds\joggin.apk -t "Joggin V1.59 — fix startup crash when Buddy Link/location was active" -F .kiro\specs\release-notes-v1.59.md
```

No Firestore rules deploy (no rules change).

---

## Internal changelog (since V1.58)

### Startup crash — ForegroundServiceDidNotStartInTimeException
With location ON, the app could crash on open (observed 4-5 times in a row, then
self-heal hours later; clearing cache did not help). The Android dropbox crash was
`android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
Context.startForegroundService() did not then call Service.startForeground()` at
`BuddyLinkController.applySharingState` → `ContextCompat.startForegroundService(...)`.
The in-app log repeatedly showed `BuddyLocationService cannot start — background
location permission missing` right before each crash.

Root cause: `BuddyLinkController.applySharingState()` starts `BuddyLocationService`
with `startForegroundService()`. Android's contract requires the service to call
`startForeground()` within ~5s of that start or the OS kills the whole app. But
`BuddyLocationService.onStartCommand` had two early-return paths that returned WITHOUT
calling `startForeground()` — the `ACTION_STOP` branch and the
`!hasBackgroundLocation()` branch — so any start that hit an early return tripped the
contract and crashed the app. The timing/state dependence made it intermittent and
self-healing.

Fix: `onStartCommand` now calls `startForeground()` FIRST, unconditionally, at the very
top — before any early-return — so the contract is always satisfied the moment the
service starts. The `ACTION_STOP` / missing-permission branches then run and call
`stopEverything()` (which does `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`),
leaving no lingering notification. Because `BuddyLocationService` declares
`android:foregroundServiceType="location"` in both manifests, on API 34+ (targetSdk 34)
the foreground entry uses `ServiceCompat.startForeground(..., FOREGROUND_SERVICE_TYPE_LOCATION)`
and is wrapped in try/catch: if Android rejects it (e.g. a `SecurityException` /
`ForegroundServiceStartNotAllowedException` when location permission is absent) the
service logs the failure and stops cleanly rather than crashing — the app NEVER crashes
on service start. The controller's existing try/catch around `startForegroundService`
is kept as defense-in-depth (it cannot catch the system-thrown timeout; the real fix is
startForeground-first in the service).

### Crash + event logging (log every event, not just start)
- Uncaught crashes are now also appended to the MAIN in-app log (`app_log.txt` via
  `AppLogger`, `LogCategory.ERROR`) as a concise one-liner (exception class + message +
  top stack frame), in addition to the existing `crash_log.txt`, so a future log tail
  shows the crash inline.
- `CrashLogger.install(context)` now runs as the first line of `MainActivity.onCreate`,
  before intent handling and the Compose/buddy bring-up that can crash.
- Added concise lifecycle breadcrumbs: `BuddyLocationService.onStartCommand` entry (with
  action + whether background-location permission is present), startForeground
  success/failure, service `onDestroy`, and controller `applySharingState` sharing
  on/off decisions. One line each, existing AppLogger style — no per-fix verbosity.

### Files
`app/src/main/java/com/example/joggingapp/BuddyLocationService.kt`,
`app/src/main/java/com/example/joggingapp/CrashLogger.kt`,
`app/src/main/java/com/example/joggingapp/MainActivity.kt`,
`app/src/main/java/com/example/joggingapp/BuddyLinkController.kt`,
`app/build.gradle` (58 → 59).
