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
- **V1.49** — `versionCode 49`, `versionName "1.49"` (`app/build.gradle`). PUBLISHED; **on-device
  install PENDING** (device disconnected at release time). Install when plugged in:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk` (the app-debug.apk currently in the
  build output IS V1.49), or from the GitHub release / install page. **Both phones need V1.49** —
  V1.48 can no longer connect (its requestLink precheck is denied by the new rules).
- Git: HEAD **`9c2c51b`** on `main`, pushed to `origin/main` (in sync). Tag **`V1.49`** pushed.
  Earlier tags V1.40–V1.48 also exist.
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`, Java/Kotlin 17, Compose compiler 1.5.10.
- Last build: `assembleDebug` + `testDebugUnitTest` → **BUILD SUCCESSFUL** + `DONE_0` (debug-
  signed; 65/65 unit tests incl. new BuddyLinkDecisionsTest). Built with **`--no-daemon`** (see §3).
- **V1.49 is PUBLISHED on GitHub** (not draft) with `joggin.apk` asset.
  Release URL: https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.49
- **Firestore rules for V1.49 were DEPLOYED this session** (`firebase deploy --only
  firestore:rules` → "released", DONE_0). The rules and the V1.49 APK MUST go together.
- Firebase Hosting was deployed in the V1.47 session; not touched since. (Functions still NOT
  deployed — Blaze-blocked.) **Firebase Anonymous Authentication is now ENABLED** in the console
  (user did this — it was the first Buddy "temporarily unavailable" root cause; see §7).
- **Device R5CR100AY2V** (Samsung SM-G991B, Android 15) connects intermittently; last confirmed
  on-device version was V1.48. Install V1.49 when plugged in.
- APKs in `builds/`: `joggin-v1.49.apk` (archived; keeps newest 4 → v1.46–v1.49; v1.45 pruned)
  and `joggin.apk` (lowercase release copy = V1.49).

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
- `9c2c51b` — **V1.49: fix Buddy Link PERMISSION_DENIED + secure links rules + session auto-retry**
  (versionCode 49, bump + release notes in this commit). THE BIG FIX for "connect by code never
  worked / Firebase permission denied." Root cause (see
  `.agents/tasks/buddy-permission-loop-investigation.md`): a chicken-and-egg in the app —
  `requestLink`/`acceptInvite` read `users/{other}` as an existence precheck BEFORE any link
  existed, which the rules deny (users readable only by owner or an existing link party); and the
  follow-up transaction `tx.get(links/{id})` on a not-yet-existing link doc was also denied
  (`resource` null). The repeated "permission denied" spam in the in-app debugger was these
  one-shot `get()`s. NOT a link-id mismatch, NOT a stale cache. Fixes:
  (1) **App** (`FirebaseBuddyRepository.kt`): removed both `users/{other}` prechecks; a
  PERMISSION_DENIED on link create now maps to `Reason.NOT_FOUND` so the UI shows "unknown code"
  (not "temporarily unavailable"); `acceptInvite` no longer creates an already-`accepted` link —
  it either accepts an existing pending-to-me link or sends a pending request back (two-branch
  logic extracted to pure `BuddyLinkDecisions.kt`, unit-tested in `BuddyLinkDecisionsTest.kt`).
  (2) **Firestore rules** (BOTH `firestore.rules` + `firebase-hosting/firestore.rules`, identical):
  `links` read allows `resource == null` (so the creator's tx.get works); `links` create requires
  `a==me`, correct `linkId`, `b != me`, `state=='pending'` (SECURITY: can't be born accepted), and
  `exists(users/{b})` (server-side replacement for the removed client precheck); `links` update
  forbids changing `a`/`b` and only lets side `b` move a pending link to `accepted`. This also
  CLOSED a hole where anyone holding your 32-hex code could force an accepted link and read your
  location if sharing. (3) **Session auto-retry** (`BuddyLinkController.kt`): `start()` now
  re-runs `ensureSession` with bounded backoff if enabled-but-not-signed-in, so a failed first
  sign-in self-heals instead of showing "temporarily unavailable" until an off/on toggle.
  EN+EL strings. Build green (65/65). **Rules deployed. Not verified: real two-device connect**
  (needs 2 phones both on V1.49). Files: FirebaseBuddyRepository.kt, BuddyLinkController.kt,
  BuddyLinkDecisions.kt (new), BuddyRepository.kt, NoOpBuddyRepository.kt, MainActivity.kt,
  Strings.kt, both firestore.rules, release-notes-v1.49.md, BuddyLinkDecisionsTest.kt (new).
  ⚠ **KNOWN NON-BLOCKER (review-flagged, not yet fixed):** if a buddy declines/removes your invite
  and you re-send, the app can show "Request sent" when the request didn't actually go through
  (declined/removed dead-end). Candidate for a follow-up.
- `e3fd0d6` — **V1.48: code-based Buddy Link invites + Accept/Ignore notification** (versionCode
  48, bump + release notes in this commit). Replaces the broken share-a-link invite with a
  CODE-BASED flow, all on the FREE Spark plan (no Cloud Functions, no paid push). Design (user-
  chosen): the CODE = the existing `appId` (per-install UUID in jog_prefs `buddy_app_id`); no
  username registry. Each user sees THEIR OWN code (formatted + Copy/Share) in the Buddy section;
  to connect, the SENDER enters the RECIPIENT's code → `requestLink(targetCode)` writes a pending
  `links` doc (`a=me, b=target, state=pending`) now carrying a denormalized **`fromName`** (sender
  displayName) so the prompt can name the sender before acceptance. The recipient's existing
  `observePendingRequests` Firestore listener raises a LOCAL notification "<name> wants to buddy
  up" with **Accept / Ignore** action buttons wired to a new `goAsync` `BroadcastReceiver`
  (`BuddyInviteActionReceiver`, registered `exported=false` in BOTH manifests) that calls
  acceptLink/declineLink off-thread. Accept → link `accepted` → `observeBuddies` shows both sides
  by `displayName`. Added **`createdAt`** (create-if-absent) on `users/{appId}` + a "Member since
  <date>" display. New `BuddyCodeTest` (code normalize/validate, 6/6). EN+EL strings. **Firestore
  rules unchanged** (no field whitelist → new fields already allowed; no deploy). Deep-link path
  (joggin:// + https /buddy) kept for back-compat but the code flow is the primary invite UI.
  ⚠ **FREE-TIER LIMITATION (unchanged, by design):** the invite notification fires via the
  recipient's live Firestore listener — reliable when their app is foreground/backgrounded/running
  the location foreground service; a FULLY KILLED app sees the pending invite on next open. True
  push to a killed app needs a server (Blaze), which the user has ruled out. Client-side FCM is not
  possible (no server key; legacy API dead; HTTP v1 needs server-minted OAuth).
- `8ec3b35` — Handover update (V1.47).
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
- **Verified:** everything compiles; 65/65 unit suite green; V1.49 published + rules deployed +
  anonymous auth enabled in console.
- **NOT verified on-device:** V1.49 is not installed on the phone yet; the real two-phone
  code-connect flow (enter code → Connect → Accept → both appear) has never completed
  successfully end-to-end. V1.48 testing hit PERMISSION_DENIED, which V1.49 is intended to fix.
  The whole point of V1.49 is unproven until a two-phone test on V1.49 succeeds.
- **Buddy history so far:** V1.48 code-connect failed on-device with "temporarily unavailable"
  (anon auth was off — user enabled it) then "Firebase permission denied" (the precheck bug V1.49
  fixes). Each prior Buddy fix compiled + reviewed but none has been confirmed working on two
  real devices.

## 6. Immediate next step(s) — START HERE
1. **Install V1.49 on BOTH phones.** Plug in R5CR100AY2V →
   `adb install -r app\build\outputs\apk\debug\app-debug.apk` (adb at
   `C:\Users\INFINITI\Downloads\platform-tools-latest-windows\platform-tools\adb.exe`; the current
   app-debug.apk IS V1.49). Second phone: install from the GitHub release / install page, or plug
   it in too. V1.48 cannot connect — both MUST be on V1.49.
2. **Two-phone test:** enable Buddy Link on both; phone A pastes phone B's code → Connect →
   expect "Invite sent" (NOT a permission error); phone B gets "<name> wants to buddy up" → Accept;
   both should list each other under Buddies. If it fails, pull the error: the IN-APP debugger
   (version triple-tap → Debug console, or `AppLogger` file at
   `files/app_log.txt` via `adb exec-out run-as com.example.joggingapp cat files/app_log.txt`)
   shows "Firebase buddy op failed: <reason>"; the exact denied Firestore path is only in logcat
   tag `Firestore` (`adb logcat -d | Select-String Firestore`). Match timestamps to taps.
3. Follow-up candidate (user-approved area): fix the declined/removed re-send "Request sent"
   false-positive (§4 known non-blocker).
- Still owed from earlier (lower priority): eyeball per-type target colours across the 4 themes.
- **Optional (free):** enable true https App-Link auto-open by registering the app + debug SHA-256
  in the Firebase console (§4 V1.47 limitation). Not needed for invites to work.
- **Cloud-function deploy still BLOCKED on Blaze** (§7); user avoids all cost. Everything shipped
  works without it.

## 7. Known caveats / open items / blockers
- **History "Run" vs "Walk" heuristic LEFT AS-IS by user decision:** in the history card
  (`MainActivity.kt` ~line 852), a route stored as walk (`activityType == 0`) with
  `avgSpeed*3.6 > 5 km/h` is displayed as "Run". This misfires on brisk walks and on
  runs-that-end-on-a-walk. A one-line gate (`route.activityTypes == null && …`) would limit it to
  genuinely legacy routes if the user ever wants it fixed.
- Exercise Targets not folded into backup/restore (`BackupData` v2); no "undo last rep log" beyond
  the new deduction entry; PLANK_SECONDS reuses the integer rep machinery (value = seconds).
- The old auto-handover hook is removed; keep this file updated manually.
- **Firebase Anonymous Auth** must stay ENABLED (console → Authentication → Sign-in method →
  Anonymous). It was OFF originally, which made every Buddy session fail with "temporarily
  unavailable". The user enabled it this session; do NOT enable "Auto clean-up" (it deletes
  anonymous accounts >30 days, which would drop buddy identities). Firestore DB is provisioned.
- **This session was almost entirely Buddy Link debugging** (V1.49). The terminal was EXTREMELY
  flaky — many captures wrote nothing. The reliable diagnostic that worked: `adb exec-out run-as
  com.example.joggingapp cat files/app_log.txt` for the in-app log, and `adb logcat -d |
  Select-String Firestore` for the exact denied path. Two long-running workflow runs had to be
  aborted after hanging for hours on the wedged shell.
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
