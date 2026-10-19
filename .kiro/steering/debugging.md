# Debugging: read the error before theorizing

When a feature "doesn't work," "crashes," or "doesn't show," the FIRST step is to read
the app's actual error output — never theorize about the cause before you have it.

## Check these first, in order
1. **In-app log** — `app_log.txt` (via `AppLogger`): pull it with
   `adb -s <dev> shell "run-as com.example.joggingapp cat files/app_log.txt | tail -n 60"`.
   The app logs buddy/Firestore/service errors here (e.g. `PERMISSION_DENIED`).
2. **logcat** — relaunch the app and capture:
   `adb -s <dev> logcat -c; adb -s <dev> shell monkey -p com.example.joggingapp -c android.intent.category.LAUNCHER 1; Start-Sleep 8; adb -s <dev> logcat -d`
   Filter for the feature + `AndroidRuntime|FATAL|PERMISSION|Firestore` and EXCLUDE Samsung
   `Forest*` noise (`Select-String "Forest" -NotMatch` — it contains "FatalError" and false-positives).
3. Only AFTER you have the real error, form a hypothesis and read the relevant code.

## Why this rule exists (real miss, 2026-10-18)
A user reported "can't see my buddy." The actual cause was `PERMISSION_DENIED` on every
Firestore buddy op (a V1.52 rules/client mismatch) — plainly visible in `app_log.txt`. Instead,
a UI-reactivity theory was pursued and a whole release (V1.57 liveness tick) shipped against the
wrong problem before the log was ever read. The fix was real but not the blocker. Reading the
log first would have pointed straight at permissions and saved a release cycle.

## Corollary
- A green build / clean launch does NOT mean a feature works — runtime behavior (Firestore reads,
  permissions, notifications firing) must be confirmed against real logs/behavior, not assumed.
- When backend-gated (Firestore rules) features break right after a rules change, suspect a
  client/rules mismatch first.
