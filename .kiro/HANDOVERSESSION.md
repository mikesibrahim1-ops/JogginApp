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
- **V1.46** — `versionCode 46`, `versionName "1.46"` (`app/build.gradle`). FULLY SHIPPED.
- Git: HEAD **`2e0f18d`** on `main`, pushed to `origin/main` (in sync). Tag **`V1.46`** pushed.
  Earlier tags V1.40–V1.45 also exist.
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`, Java/Kotlin 17, Compose compiler 1.5.10.
- Last build: `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL** (debug-signed; full
  unit suite green incl. ExerciseTargetsTest + BuddyInviteTest). NOTE: the Gradle daemon kept
  dying mid-build this session (accumulated dead daemons after shell wedges); the fix that
  worked was killing all `java.exe`, clearing `~/.gradle/daemon/*`, then building with
  **`--no-daemon`**. Use that if the daemon churns.
- **V1.46 is PUBLISHED on GitHub** (not draft) with `joggin.apk` asset; `/releases/latest/
  download/joggin.apk` returns 200; install page `https://joggin-a69a7.web.app/get` serves it.
  Release URL: https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.46
- Installed + verified on the user's device R5CR100AY2V (dumpsys: versionCode 46 / versionName
  1.46, apkSigningVersion 3).
- APKs in `builds/`: `joggin-v1.46.apk` (archived; keeps newest 4 → v1.43–v1.46; v1.42 pruned)
  and `joggin.apk` (lowercase release copy = V1.46, 12,326,145 bytes).

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
- V1.46 is fully shipped (built, tagged, published, installed). Nothing release-blocking pending.
- **On-device verification** still owed by the user: (a) real two-device Buddy Link pairing with
  the new accept/ignore dialog, and (b) eyeball the per-type Personal Targets colours across the
  4 themes. Both are code-complete and build-verified but not runtime-verified this session.
- **Cloud-function deploy is BLOCKED on Blaze** — see §7. The user wants to avoid upgrading, so
  the killed-app sender push stays undeployed. Pairing does not depend on it. If the push is
  wanted without Blaze later, send the FCM notification client-side from the accepting device
  instead of via the `onBuddyLinkWrite` Cloud Function trigger.

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
