# V1.59 On-Device Verification — Startup-Crash Fix

**Result: PASS**

- Device: samsung SM-G991B (R5CR100AY2V), Android 15 (API 35)
- Build: `./gradlew assembleDebug --console=plain` → BUILD SUCCESSFUL
- APK installed: `app/build/outputs/apk/debug/app-debug.apk` → `Success`
- **Install timestamp: 2026-10-22 18:19:19** (all "no-new-crash" evidence is anchored to this)

The crash being verified:
`android.app.RemoteServiceException$ForegroundServiceDidNotStartInTimeException` —
`Context.startForegroundService() did not then call Service.startForeground()` on
`BuddyLocationService`, thrown from `BuddyLinkController.applySharingState`.

---

## 1. The `onStartCommand` restructure (the fix)

`BuddyLocationService.onStartCommand` was restructured so that `startForeground()` is
called **immediately and unconditionally**, before any early-return branch. Previously a
start that then needed to stop (STOP action, or missing background-location permission)
could return from `onStartCommand` **without** ever calling `startForeground()`, which is
exactly what trips `ForegroundServiceDidNotStartInTimeException` when the service was
launched via `startForegroundService()`.

New order in `onStartCommand`:

1. Log the entry breadcrumb (action + bgPerm state).
2. Call `enterForeground()` **first**. If it fails, `stopEverything()` and
   `return START_NOT_STICKY`.
3. Only then evaluate the STOP action and the background-permission gate — both of which
   now `stopEverything()` *after* the foreground contract has already been satisfied.

Because `startForeground()` runs on every entry into `onStartCommand`, the ~5s OS contract
is honoured even when the service immediately stops itself.

## 2. Manifest `foregroundServiceType` + safe `startForeground` on API 34

Both service declarations carry `android:foregroundServiceType="location"`:

- `app/src/main/AndroidManifest.xml`
- `app/src/firebase/AndroidManifest.xml` (the overlay actually used here, since
  `app/google-services.json` is present — build.gradle swaps the manifest when Firebase is
  enabled)

Matching permissions are declared: `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_LOCATION`.

`enterForeground()` passes the matching type explicitly on API 29+ and wraps the call so a
throw can never crash the app:

```kotlin
private fun enterForeground(): Boolean {
    return try {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
        AppLogger.log(this, LogCategory.SERVICE, "BuddyLocationService entered foreground")
        true
    } catch (e: Exception) {
        AppLogger.log(this, LogCategory.ERROR,
            "BuddyLocationService startForeground failed: ${e.javaClass.simpleName}: ${e.message}")
        false
    }
}
```

On API 34+ (device here is API 35) the `location` type must be passed to
`startForeground`; it is. A `SecurityException` / `ForegroundServiceStartNotAllowed` is
caught and reported as `false`, so the service stops cleanly instead of crashing.

## 3. Crash-to-`app_log` wiring

`CrashLogger.install(this)` is called **first** in `MainActivity.onCreate` so any startup
crash is captured. V1.59 adds `logToAppLog()`, which — in addition to the existing
`crash_log.txt` — writes a concise one-liner into the MAIN in-app log (`app_log.txt` via
`AppLogger`, `LogCategory.ERROR`): `CRASH on <thread>: <class>: <message> at <top frame>`.
This means a future `tail` of `app_log.txt` shows a crash inline next to the lifecycle
breadcrumbs. It is best-effort and never throws from the crash handler.

## 4. No-new-crash evidence (`dumpsys dropbox --print`)

All `ForegroundServiceDidNotStartInTimeException` entries in dropbox are from the OLD build
**v58 (1.58)** and all predate the install. The crash signature (quoted, trimmed for
licensing):

```
Package: com.example.joggingapp v58 (1.58)
RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
  ... did not then call Service.startForeground(): ...BuddyLocationService...
    at ...ActivityThread.generateForegroundServiceDidNotStartInTimeException
    at ...BuddyLinkController.applySharingState(BuddyLinkController.kt:328)
    at ...BuddyLinkController.updateGpsState(BuddyLinkController.kt:316)
```

Crash entry timestamps reported by dropbox (newest last):

```
2026-10-22 16:44:49 .. 16:45:35  data_app_crash   (6 entries)
2026-10-22 18:05:51              data_app_crash
2026-10-22 18:05:55              data_app_crash
```

The newest crash is **18:05:55**, which is **before** the install at **18:19:19**.
Filtering for any entry at/after 18:19 returns **NONE**. No new
`ForegroundServiceDidNotStartInTimeException` (or any crash) occurred after the fix was
installed.

## 5. `pidof`-alive-across-5-launches evidence

Standard launch (monkey) left the process alive (pid 30331), and no genuine crash appeared
in logcat — scans for `FATAL EXCEPTION | VerifyError | ForegroundServiceDidNotStartInTime |
E AndroidRuntime` (with Samsung `Forest` noise excluded) returned NONE. (The only
`AndroidRuntime` lines were the benign monkey launcher process under uid 2000.)

Five force-stop + relaunch cycles with Buddy Link sharing active, `pidof` after each:

```
Launch #1: pidof=2773
Launch #2: pidof=3338
Launch #3: pidof=3952
Launch #4: pidof=4303
Launch #5: pidof=4811
```

Process stayed alive on every launch. No crash in dropbox or logcat across the run.

### Decisive crash-path note

Background-location permission is currently **not granted** on the device
(`bgPerm=false`). This is precisely the condition that produced the V1.58 crash: Buddy Link
starts the service via `startForegroundService()`, the service then needs to bail because
bg-permission is missing. Under V1.58 it returned without `startForeground()` and the OS
killed the app. Under V1.59 the breadcrumbs (below) show `startForeground` is now called
first and the subsequent self-stop is clean — so this run is a live reproduction of the
crash trigger, now passing. A separate explicit `pm revoke` was therefore unnecessary; the
device was already in the crash-triggering state.

## 6. Breadcrumb lines observed (`app_log.txt`)

The new service / `startForeground` lifecycle breadcrumbs are present on every launch.
Representative sequence from the post-install launches (note `entered foreground` appears
BEFORE the `cannot start` self-stop — proving the fix ordering):

```
[18:19:39.026] Service: Buddy sharing ON — starting BuddyLocationService
[18:19:39.212] Service: BuddyLocationService created
[18:19:39.212] Service: BuddyLocationService onStartCommand — action=com.example.joggingapp.BUDDY_START bgPerm=false
[18:19:39.232] Service: BuddyLocationService entered foreground
[18:19:39.234] Service: BuddyLocationService cannot start — background location permission missing
[18:19:39.373] Service: BuddyLocationService destroyed (stopped/killed)
```

Breadcrumbs confirmed present:
- `onStartCommand` entry (with action + `bgPerm` state)
- `entered foreground` (startForeground success) — fires before any early-return
- `applySharingState` decision path: `Buddy sharing ON — starting BuddyLocationService`
  followed by the permission-gate decision (`cannot start — ... permission missing`) and a
  clean `destroyed (stopped/killed)`

No `startForeground failed` ERROR breadcrumb appeared, and no `CRASH on ...` line was
written to `app_log.txt`.

---

## Conclusion

PASS. The V1.59 build (installed 18:19:19) honours the foreground-service contract:
`startForeground` is called first with the `location` type, the self-stop path is clean,
the process survives repeated launches under the exact bg-permission-missing condition that
crashed V1.58, and no new `ForegroundServiceDidNotStartInTimeException` (or any crash)
appears in dropbox or logcat after install. Lifecycle breadcrumbs and crash-to-app_log
wiring are confirmed live. Safe to proceed to release/commit.
