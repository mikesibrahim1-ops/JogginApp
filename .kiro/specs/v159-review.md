# V1.59 Review — startup-crash fix + logging

Scope: uncommitted diff (5 files) implementing the ForegroundServiceDidNotStartInTimeException fix in `BuddyLocationService.onStartCommand`, API-34 foregroundServiceType handling, crash-to-app_log wiring, lifecycle breadcrumbs, version bump, and release notes.

## Code assessment (focus points a–g)

**(a) startForeground unconditional at top — PASS.**
`onStartCommand` now calls `enterForeground()` immediately after the entry breadcrumb, before BOTH early-return branches (`ACTION_STOP` and `!hasBackgroundLocation()`). The old `startForeground(NOTIF_ID, buildNotification())` lower down was removed. Any start path now satisfies the contract within the ~5s window. Contract no longer violated.

**(b) API-34 foregroundServiceType handling — PASS.**
Both `app/src/main/AndroidManifest.xml` and `app/src/firebase/AndroidManifest.xml` declare `android:foregroundServiceType="location"` on `.BuddyLocationService`, and both declare `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION` permissions. `enterForeground()` passes `ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION` on API >= Q and the 2-arg overload below. It uses the platform `startForeground(id, notif, type)` overload rather than `ServiceCompat.startForeground`; this is functionally equivalent and correct. The whole call is wrapped in try/catch(Exception), so a `SecurityException` / `ForegroundServiceStartNotAllowedException` (e.g. location permission absent on API 34) is logged and returns false — no new crash introduced on the no-permission path. `ServiceInfo` import present; `ServiceCompat` appears only in a KDoc reference, not compiled code, so no dangling symbol.

**(c) stopEverything balance — PASS.**
`stopEverything()` calls `stopForeground(STOP_FOREGROUND_REMOVE)`, `nm.cancel(NOTIF_ID)`, and `stopSelf()`. Since `enterForeground()` always runs first now, every early-return path (`enterForeground` false, STOP, no-perm) routes through `stopEverything()`, so the always-created foreground notification is always removed. No lingering notification, no double-foreground.

**(d) crash-to-app_log + crash_log.txt preserved — PASS.**
`CrashLogger.install` now calls `logToAppLog(...)` in addition to the existing `log(...)` (crash_log.txt) and `writeReportFile(...)`. `logToAppLog` writes a concise one-liner (class + message + top frame) at `LogCategory.ERROR`, wrapped in its own try/catch so it never throws from the handler. crash_log.txt behavior unchanged.

**(e) CrashLogger.install early enough — PASS.**
Moved to the first statement after `super.onCreate()` in `MainActivity.onCreate`, before `handleBuddyInviteIntent` / `handleBuddyFocusIntent` / Compose bring-up. Captures startup crashes.

**(f) breadcrumbs concise — PASS.**
One line each: onStartCommand entry (action + bgPerm), enterForeground success, startForeground failure, onDestroy reason, and controller applySharingState on/off. Existing AppLogger style, not spammy.

**(g) version bump + release notes — PASS (minor doc nit).**
`app/build.gradle` 58→59 / "1.58"→"1.59". `.kiro/specs/release-notes-v1.59.md` present and accurate on the root cause and fix. Nit (non-blocking): the notes say the foreground entry "uses `ServiceCompat.startForeground(...)`" whereas the code uses the raw platform overload — equivalent behavior, documentation-only discrepancy.

## Blocking finding

**Required build/test verification evidence is missing.**
The task states verification is the coder's job and the recorded `assembleDebug` / `testDebugUnitTest` result must be read from the commit message or final report; if that evidence is missing entirely, reject rather than run the suites. The working tree is uncommitted and HEAD is the prior V1.58 handover commit (`6a29d27`) with no V1.59 build/test result. No coder final report with a recorded pass is available in context. A fresh `app/build/outputs/apk/debug/app-debug.apk` exists (dated 22/10/2026) which suggests a build ran, but an APK artifact is not a recorded statement that `assembleDebug` AND `testDebugUnitTest` passed. Per the step contract this missing required evidence blocks approval.

User-visible consequence: without a confirmed clean build + unit-test run, we cannot certify the V1.59 fix compiles and the existing unit tests still pass before release.

Correction: record the `./gradlew assembleDebug testDebugUnitTest` result (pass/fail summary) in the commit message or final report. Once that evidence is present and shows success, this is approvable — the code changes themselves meet all seven focus requirements with no correctness/security/data blocker.
