# HANDOVERSESSION — JogginApp

> Hand-off summary. A new session should read this FIRST and align to this state before
> doing work. (The old auto-regeneration hook `.kiro/hooks/handoversession.json` was removed
> by the user — this file is now maintained manually.)

## 1. Project
Android GPS fitness tracker (walk/run/cycle) at `c:\Users\INFINITI\Documents\Kiro\JogginApp`.
100% Kotlin, Jetpack Compose UI, osmdroid maps, foreground GPS service, SharedPreferences
("jog_prefs") + Gson storage, English/Greek i18n via `Strings.kt`. Live Firebase backend
(project `joggin-a69a7`) powers **Buddy Link** (opt-in 1:1 live-location sharing). Distributed
as a sideloaded APK: GitHub Releases hosts the APK; a Firebase Hosting install page links to it.

## 2. Current version / build state
- **V1.47** — `versionCode 47`, `versionName "1.47"` (`app/build.gradle`). PUBLISHED; on-device
  install PENDING (device was disconnected at release time — reinstall when the phone is plugged
  in: `adb install -r app\build\outputs\apk\debug\app-debug.apk`, or it will show up via the new
  in-app "Check for updates").
- Git: HEAD **`d9fcd1c`** on `main`, pushed to `origin/main` (in sync). Tag **`V1.47`** pushed.
  Earlier tags V1.40–V1.46 also exist.
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`, Java/Kotlin 17, Compose compiler 1.5.10.
- Last build: `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL** (debug-signed; full
  unit suite green incl. ExerciseTargetsTest + BuddyInviteTest). NOTE: the Gradle daemon kept
  dying mid-build this session (accumulated dead daemons after shell wedges); the fix that
  worked was killing all `java.exe`, clearing `~/.gradle/daemon/*`, then building with
  **`--no-daemon`**. Use that if the daemon churns.
- **V1.47 is PUBLISHED on GitHub** (not draft) with `joggin.apk` asset; `/releases/latest/
  download/joggin.apk` returns 200; install page `https://joggin-a69a7.web.app/get` serves it.
  Release URL: https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.47
- Firebase **Hosting deployed** this session (`firebase deploy --only hosting`) — serves the
  hardened `/buddy` page + `assetlinks.json`. (Functions NOT deployed — still Blaze-blocked.)
- **Device R5CR100AY2V was DISCONNECTED** at release time, so V1.47 is NOT yet installed on it
  (last confirmed on-device version is V1.46). Install when plugged in.
- APKs in `builds/`: `joggin-v1.47.apk` (archived; keeps newest 4 → v1.44–v1.47; v1.43 pruned)
  and `joggin.apk` (lowercase release copy = V1.47, 12,345,943 bytes).

## 3. Environment notes (IMPORTANT — avoids wasted time)
- **Terminal is very flaky.** PowerShell echoes commands char-by-char and reports **exit -1
  even on success**. Do NOT trust stdout/exit code — redirect to a file (`*> file.txt 2>&1`)
  and read it with the file tools. The shell also WEDGES entirely at times (no output, no file
  written even for `Out-File`); when that happens, wait and retry, or use a fresh background
  process. `list_directory`/`file_search`/`read_file` still work when the shell is wedged.
- **Build pattern:** background process running
  `.\gradlew.bat assembleDebug [testDebugUnitTest] --console=plain *> LOG.txt; "DONE_$LASTEXITCODE" | Out-File -Append LOG.txt`,
  then poll LOG.txt. Success = `BUILD SUCCESSFUL` + `DONE_0`. The log is UTF-16-ish; the file
  reader shows it fine.
- **Gradle stalls** at `processDebugResources` for 1–4 min before breaking through — be patient.
  Cold daemon starts add ~1 min. Incremental builds after a warm daemon are ~7–30s.
- **⚠ Stop the build terminal before copying/installing the APK** (file lock on
  `app/build/outputs/apk/debug/app-debug.apk`). Run ONE gradle build at a time.
- **adb IS available** (`adb.exe` in `C:\Users\INFINITI\Downloads\platform-tools...`). A real
  device (`R5CR100AY2V`, Samsung SM-G991B, Android 15) connects intermittently — the user plugs
  it in on request; verify with `adb devices` (empty list = disconnected). Install:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk` → look for `Success`.
- **gh (GitHub CLI) IS installed** (v2.102.0 at `"$env:ProgramFiles\GitHub CLI\gh.exe"`, not on
  PATH — call by full path) and **authenticated via the OS keyring** as `mikesibrahim1-ops`
  (scopes incl. `repo`, `workflow`). **No token file needed anymore** — releases publish directly.
  Earlier `.ghtoken` file approaches are obsolete; a prior token hit 401 (expired). gh stores
  creds in the keyring, so `Test-Path "$APPDATA\GitHub CLI\hosts.yml"` is False even when logged in.
- `firebase deploy` works (user logged in) but is NOT needed for releases — the install page
  points at GitHub `releases/latest`.
- **⚠ Two manifests stay in sync:** `app/src/main/AndroidManifest.xml` + `app/src/firebase/AndroidManifest.xml`.
- **⚠ Do NOT use `return@Column`/early-return inside a Compose content lambda** — it corrupts the
  composer group stack → `IndexOutOfBoundsException` in `Stack.pop`. Use if/else. (Caused a real
  crash earlier this project; now fixed. See AchievementsScreen.kt comment.)

## 4. What was accomplished across recent sessions (all committed + pushed)
Newest first on `main`:
- `d9fcd1c` — **V1.47: reliable Buddy Link invite deep-link + in-app update check** (versionCode
  47, bump + release notes included in this commit). Two features:
  (1) **Buddy Link invite now opens the APP, not just the website.** Root cause (see
  `.agents/tasks/buddy-deeplink-investigation.md`): the share link was https-only and relied on
  Android App Links verification that was doubly broken (no `autoVerify`; empty `assetlinks.json`),
  so taps fell back to the browser. Fix (belt-and-suspenders): `BuddyInvite.buildShareText` now
  shares BOTH the `joggin://buddy/v1/<id>?exp=` custom-scheme link (reliable opener, no
  verification needed) and the https link; `firebase-hosting/public/buddy/index.html` fires an
  Android `intent://...package=com.example.joggingapp;S.browser_fallback_url=<get page>;end` from
  a user tap; `autoVerify="true"` added to the https `/buddy` intent-filter in BOTH manifests;
  `assetlinks.json` added at `firebase-hosting/public/well-known/assetlinks.json` with a
  firebase.json **rewrite** `/.well-known/assetlinks.json` → `/well-known/...` (dodges the
  `**/.*` ignore glob). The app-side parse/accept flow was already correct and unchanged.
  (2) **In-app update check** (sideload-friendly, GitHub Releases based). New
  `UpdateChecker.kt` queries `api.github.com/repos/mikesibrahim1-ops/JogginApp/releases/latest`
  off-thread, compares `tag_name` vs `BuildConfig.VERSION_NAME` via a pure `isNewerVersion`
  (unit-tested in `UpdateCheckerTest.kt`), auto-checks ~once/day on launch (throttle persisted in
  jog_prefs) and shows an update prompt; a new collapsible **"App"** section in Settings
  (MainActivity.kt) shows the version + a "Check for updates" button; the Update action opens the
  latest APK URL (install-page fallback). EN+EL strings added.
  ⚠ **KNOWN LIMITATION — https auto-verify still won't work:** Firebase Hosting intercepts
  `/.well-known/assetlinks.json` with its own handler and returns `[]`, overriding the rewrite
  (the file DOES serve correctly at `/well-known/assetlinks.json`). To enable true https App-Link
  auto-open, add the Android app + the DEBUG SHA-256
  (`24:0D:EE:CC:…99:0E:B0`) under Firebase Console → Project Settings → Your apps. NOT required
  for the invite to open the app — the custom-scheme + `intent://` path already does that.
- `295e112` — Handover update (V1.46).
- `2e0f18d` — **Bump to V1.46** (versionCode 46) + release notes
  (`.kiro/specs/release-notes-v1.46.md`). Built, archived, tagged V1.46, pushed, PUBLISHED to
  GitHub with `joggin.apk`, installed + verified on device. V1.46 rolls up `1d029a8` + `0ebf67d`.
- `1d029a8` — **Per-type colours in Personal Targets** (Achievements screen). The completed-
  exercise boxes now colour by `ExerciseType` instead of a single fixed `c.success`, so e.g.
  push-ups (accent) vs sit-ups (primary) are distinguishable. `targetColor` moved to
  `ExerciseTargets.kt` as one shared `internal` function (single source of truth; both the
  Achievements and Exercise Targets screens call it), exhaustive over all 12 types, no `else`.
  The 7 manual types got distinct hues (orange/teal/purple/pink/blue/green/brown). Files:
  `ExerciseTargets.kt`, `ExerciseTargetsScreen.kt`, `AchievementsScreen.kt`. UI appearance across
  the 4 themes not yet eyeballed on-device (boxes use alpha 0.2f tint behind an emoji).
- `a396835` — Add `.kiro/steering/brevity.md` (always-on steering rule for concise responses).
- `37870a8` — Handover update (prior).
- `0ebf67d` — **Buddy Link invite-pairing fix.** Fixes the role-inversion bug where the
  invite embedded the SENDER's appId, so opening the link made the recipient the requester and
  the Accept prompt landed on the sender → pairing never completed. Now: the recipient sees an
  Accept/Ignore **dialog** on opening a valid invite; **Accept** has the recipient create
  `links/{sorted(me,sender)}` directly in `state="accepted"` (`a=recipient, b=sender`) — allowed
  by the deployed firestore.rules (create requires `a==myAppId()`, no state constraint) so **NO
  rules change/deploy**; **Ignore** discards cleanly; received invites are listed in the Buddy
  Link section and **removable** anytime; the enable-gating drop is fixed (invite is held via
  BuddyInviteBus and processed after the enable flow instead of being discarded). New
  `acceptInvite(fromAppId)` on the `BuddyRepository` interface + both impls (Firebase + NoOp).
  New `BuddyInviteTest.kt`. `firebase-hosting/functions/index.js` got an additive accepted-on-
  create push — **requires `firebase deploy --only functions`** (pairing does NOT depend on it;
  not yet deployed). Build verified green (`assembleDebug` + `testDebugUnitTest` → BUILD
  SUCCESSFUL + DONE_0). **Not verified: real two-device pairing** (needs 2 devices + Firebase).
  Files: FirebaseBuddyRepository.kt, BuddyRepository.kt, NoOpBuddyRepository.kt,
  BuddyLinkController.kt, BuddyLinkSection.kt, MainActivity.kt, Strings.kt (EN+EL),
  functions/index.js, + BuddyInviteTest.kt.
- `8421dd5` — **Bump to V1.45** (versionCode 45) + release notes. Published to GitHub, tag V1.45.
- `c61acb3` — **New exercise types** (BICEP_CURLS, TRICEP_DIPS, LUNGES, CRUNCHES, BURPEES,
  JUMPING_JACKS, PLANK_SECONDS — manual/rep-counted); **Activity row in the end-of-activity
  summary** (emoji + localized label from the route, handles blends); **"Past Runs" → "Activity
  History"** rename.
- `178e539` — Bump to V1.44 (versionCode 44). NOTE: V1.44 was tagged but **never published** to
  GitHub; its changes are rolled into the published V1.45.
- `bac7252` — **Rep-log deduction**: custom in-app number pad (0–9, minus, backspace; no system
  keyboard) in `LogRepsDialog`; minus toggles negative (button turns red / "Deduct"); `logReps`
  accepts negatives; `evaluateTargets` clamps progress to ≥0.
- `706a5f2` — Bump to V1.43 (published earlier).
- `07a3cb4` — Personal Targets crash fix (removed `return@Column`), dim-header-text colour fix
  (→ `onSecondary`), instant-completion fix (`evaluateTargets` only counts activity at/after a
  target's `createdAt`), and **Day/Week/Month grouping** of completed targets.

This session specifically: added the 7 exercise types, the summary Activity row, the history
rename; bumped/built/archived/committed/tagged/pushed **V1.45**; **published V1.45 to GitHub via
gh (keyring auth)**; installed V1.45 on the device; and (pending in this final step) committing
the removal of the handover hook.

## 5. Current working state (verified vs. untested)
- **Verified:** everything compiles; unit suite green; V1.45 published + live (asset present,
  /latest returns 200); V1.45 installed on-device (version confirmed).
- **Compiled, lightly/you-tested on device:** the 7 new exercise types, number-pad deduction,
  the summary Activity row, the history rename. User has been smoke-testing on-device each round.
- **Buddy Link** two-party flows + push still need 2 devices + Firebase config (unchanged).

## 6. Immediate next step(s)
- **Install V1.47 on the device** (it was disconnected at release): plug in R5CR100AY2V, then
  `adb install -r app\build\outputs\apk\debug\app-debug.apk` (adb at
  `C:\Users\INFINITI\Downloads\platform-tools-latest-windows\platform-tools\adb.exe`). The
  app-debug.apk from this session's build IS V1.47.
- **Verify on-device:** tapping a Buddy Link invite now opens the app → accept/ignore dialog
  (the actual bug the user reported); and the new "Check for updates" (Settings → App section +
  auto-on-launch prompt) works against GitHub Releases.
- Still owed from V1.46: real two-device Buddy pairing; eyeball per-type target colours across
  the 4 themes.
- **Optional:** enable true https App-Link auto-open by registering the app + debug SHA-256 in
  the Firebase console (see §4 known limitation). Not needed for invites to open the app.
- **Cloud-function deploy still BLOCKED on Blaze** (see §7); user avoids upgrading; pairing works
  without it.

## 7. Known caveats / open items / blockers
- **History "Run" vs "Walk" heuristic LEFT AS-IS by user decision:** in the history card
  (`MainActivity.kt` ~line 852), a route stored as walk (`activityType == 0`) with
  `avgSpeed*3.6 > 5 km/h` is displayed as "Run". This misfires on brisk walks and on
  runs-that-end-on-a-walk. A one-line gate (`route.activityTypes == null && …`) would limit it to
  genuinely legacy routes if the user ever wants it fixed.
- Exercise Targets not folded into backup/restore (`BackupData` v2); no "undo last rep log" beyond
  the new deduction entry; PLANK_SECONDS reuses the integer rep machinery (value = seconds).
- The old auto-handover hook is removed; keep this file updated manually.
- **Buddy Link Cloud Function deploy blocked on Blaze.** `firebase deploy --only functions` fails:
  project `joggin-a69a7` must be on the Blaze (pay-as-you-go) plan to enable
  `cloudbuild.googleapis.com`. User has chosen to AVOID upgrading. The `functions/index.js`
  accepted-on-create sender push (added in `0ebf67d`) is therefore NOT deployed; invite pairing
  works entirely client-side (Firestore) without it. Firestore reads/writes + app-sent FCM do not
  need Blaze — only deploying Cloud Functions does. Note: `firebase` CLI is at
  `C:\Users\INFINITI\AppData\Roaming\npm\firebase.cmd` (call the `.cmd` directly — the `.ps1`
  wrapper is blocked by PowerShell execution policy). APK debug-signed.

## 8. Key files
- `app/build.gradle` — version (45 / 1.45), minSdk 24, Firebase gating, signing.
- `app/src/main/java/com/example/joggingapp/`
  - `MainActivity.kt` — JogApp, tracking, screen booleans, `SummaryScreen` (now has Activity
    row), route save (`activityType`/`activityTypes`), `labelForMode`/`emojiForMode`/`blend*`.
  - `ExerciseTargets.kt` — `ExerciseType` enum (now 12 values), models/storage, `evaluateTargets`
    (clamps ≥0, honours `createdAt`), `logReps` (accepts negatives), `groupCompleted`.
  - `ExerciseTargetsScreen.kt` — targets screen, batch add, `LogRepsDialog` (custom number pad),
    `targetColor` (exhaustive when — update when adding ExerciseType values).
  - `AchievementsScreen.kt` — achievements + Personal Targets (Day/Week/Month grouping); has the
    "no return@Column" warning comment.
  - `Strings.kt` — EN/EL strings; `exerciseTypeLabel` + `periodLabel` (exhaustive whens — update
    when adding enum values); `activity`, `exType*`, `exDeductReps`, `exGroup*` keys.
  - `ui/theme/Theme.kt` — tokens + 4 themes.
- `app/src/test/java/.../ExerciseTargetsTest.kt` — pure-JVM tests (period boundaries, rep/distance
  aggregation, createdAt gating, deduction).
- `.kiro/specs/release-notes-v1.4X.md` — per-version GitHub release bodies (latest: v1.45).
- `firebase-hosting/` — `firebase.json`, `.firebaserc` (joggin-a69a7), rules, `functions/`,
  `public/{index,get,buddy}` install pages.
- `builds/joggin.apk` (+ `joggin-v1.45.apk`); `archive-build.ps1` archives + prunes to newest 4
  (does NOT make the lowercase `joggin.apk` — copy that separately after archiving).
- Publish command (gh keyring auth, no token):
  `& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.XX builds\joggin.apk -t "<title>" -F .kiro\specs\release-notes-v1.XX.md`
