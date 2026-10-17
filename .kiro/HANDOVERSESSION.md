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

## 0. LATEST SESSION SUMMARY (read this first — 2026-10-17, V1.52 ship session)
This session SHIPPED **V1.52** and fixed the long-standing flaky-terminal problem. All work is on
`main`, **pushed**, **tagged**, **released**, and the **rules are DEPLOYED**.
- **✅ Terminal wedge ROOT-CAUSED and FIXED.** The char-by-char echo + `exit -1` wedge was Windows
  PowerShell **5.1** running **PSReadLine 2.0.0** (broken shell-integration markers). Fix: updated
  PSReadLine to 2.4.5 (helped short cmds), then installed **PowerShell 7.6.7** via winget and set it
  as Kiro's default terminal (`terminal.integrated.defaultProfile.windows` = "PowerShell 7",
  profile path `C:\Users\INFINITI\AppData\Local\Microsoft\WindowsApps\pwsh.exe` — it installed as a
  per-user App Execution Alias, NOT in `C:\Program Files\PowerShell\7`). Verified: full streaming
  Gradle builds now run with correct exit codes, no echo. **The §3 file-redirect workaround is no
  longer needed** — the shell is reliable now.
- **✅ Merged the Buddy Route-mode feature** (`a88f6e2`): new `BuddyShareMode` enum (PING/ROUTE),
  mode toggle in `BuddyLinkSection`, consent-gated `enableRouteMode`/`disableRouteMode` via
  `applyShareMode` (renamed from `setShareMode` to avoid a JVM setter clash), wired through the
  repos + `BuddyLocationService`, trail rendered in `JogMap`, `BuddyShareModeTest` added. Rebased
  onto `main` over the pill-button commit (`ff5b597`) — the expected `BuddyLinkSection.kt` overlap
  auto-merged (kept both). Compile + unit tests green. Detail:
  `.agents/tasks/buddy-route-mode/verification.md`.
- **✅ Shipped V1.52** (`197c29e`, tag `V1.52`): bumped versionCode 51→52 / versionName "1.52",
  wrote `.kiro/specs/release-notes-v1.52.md`, built (`assembleDebug` + `testDebugUnitTest` → BUILD
  SUCCESSFUL), archived `builds/joggin-v1.52.apk`, copied to `builds/joggin.apk`. **Pushed `main`,
  pushed tag `V1.52`, PUBLISHED GitHub Release** (`releases/latest`, asset `joggin.apk` 12,383,998
  bytes): https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.52 . **DEPLOYED Firestore
  rules** (`firebase deploy --only firestore:rules` from `firebase-hosting/` → "released", compiled
  OK). V1.52 rolls up: route-mode + the security hardening (`963e9a5`) + UI polish (pill
  Copy/Share buttons `ff5b597`, swipe labels/arrows `d98d07c`, shared-route map inset `f691e30`).
  Ship order used: published the APK FIRST, then deployed rules (minimizes the old-client/new-rules
  mismatch window the security session warned about).
- **✅ Cleaned up ALL stale worktrees** — `.worktrees/buddy-route-mode` and `.worktrees/v149-buddy-fix`
  removed (dirs + branches `feat/buddy-route-mode`, `fix/v149-buddy-permission` deleted, pruned). No
  worktrees remain except the main checkout. All scratch probe files cleaned.
- **OPEN / NEXT STEPS for a new session:**
  1. **On-device test V1.52 on both phones** — install (`adb install -r app\build\outputs\apk\debug\app-debug.apk`,
     current app-debug.apk IS V1.52), then verify Buddy flows WORK AGAINST THE NEWLY-DEPLOYED RULES:
     route-mode PING↔ROUTE toggle + consent, connect-by-code, Accept/Decline, live map. We did NOT
     run emulator rules tests (no harness exists in the repo), so on-device is the real validation.
     ⚠ If a buddy feature breaks against the new rules, a rules rollback is quick (redeploy a prior
     `firestore.rules` from git history).
  2. **ROTATE the GitHub token** (F1 follow-up) — `.ghtoken` sat in the working tree (now gitignored,
     never committed). gh CLI uses the OS keyring, so the file isn't needed anyway.
  3. **F6 (deferred):** release signing key + its SHA-256 in assetlinks (currently debug-signed only).
- --- below this line is the PRIOR (2026-10-15 security) session summary, kept for history ---

## 0a. PRIOR SESSION SUMMARY (2026-10-15, security session)
This session acted on the security gap analysis. Landed on `main` (then local; **now shipped in
V1.52 this session** — pushed + rules deployed):
- **`faffec8` — F1:** `.ghtoken` now gitignored (`*token*` etc.) + scratch patterns. Verified it was
  NEVER committed to history (`git log --all -- .ghtoken` empty). ⚠ **USER STILL MUST ROTATE the
  token** on github.com (it sat in the working tree) and move it outside the repo.
- **`0b022d1` + `92fb987` + `9909152` + `963e9a5` — Buddy Link security hardening** (HEAD now
  **`963e9a5`**). Done via a design→review loop (converged) then an implement→semantic-review loop
  (APPROVED, no HIGH/MED). Covers: **F2+F9** (moved `authUid`/`fcmToken` out of the buddy-readable
  `users/{appId}` into owner-only `users/{appId}/private/self`; `ownsUser` redefined to read authUid
  from the subdoc; `deleteAllMyData` now deletes the subdoc; Cloud Function `lookupUser` read-path
  updated); **F3+F5** (additive field whitelists + lat/lon range + type + size caps on
  locations/trail/users; monotonic `reqCount`/`reqWindowStart` on `links` update; `createdAt`
  immutable on users update); **F10** (new `locations/{appId}/locReq/{requesterAppId}` subcollection
  reusing CONDITION-1 — fixes the previously-broken on-demand request); **F7** (backup_rules.xml +
  data_extraction_rules.xml excluding jog_prefs + app_log.txt, both manifests); **F8**
  (`usesCleartextTraffic=false`, both manifests); **F14** (deleted dead non-dotted assetlinks copy).
  Verified: `testDebugUnitTest` + Firebase `compileDebugKotlin` both BUILD SUCCESSFUL; both rules
  copies byte-identical (SHA `39AD48…65F93`); both manifests in sync. Full per-finding note at
  `.agents/tasks/buddy-security-harden/verification.md`; approved design at
  `.agents/tasks/buddy-security-harden/design.md`. All edits ADDITIVE — CONDITION-1 + V1.49 intact.
- **OPEN / NEXT STEPS for a new session:**
  1. **PUSH `main`** (origin/main is still at `a279002`; HEAD `963e9a5` is 5 commits ahead, unpushed).
  2. **DEPLOY the new rules**: `firebase deploy --only firestore:rules` — the hardening is INERT until
     deployed. ⚠ Deploy the rules and ship an APK built from `963e9a5` TOGETHER: the client now writes
     `authUid`/`fcmToken` to `users/{appId}/private/self` and the new rules whitelist the public doc,
     so an OLD client against NEW rules (or vice-versa) can mismatch. Bump version + build + archive +
     tag + publish when ready (no version bump was made this session).
  3. **Emulator rules tests** (design §11) recommended before/after deploy — assertions listed in
     verification.md (buddy can't read private/self; owner writes succeed; createdAt preserved; etc.).
  4. **ROTATE the GitHub token** (F1 follow-up).
  5. Still from the prior session: **on-device verify V1.51** (App Links open-the-app) on both phones.
  - OUT OF SCOPE this session (flagged, not done): **F6** release signing / assetlinks cert.
- ⚠ Cleaned up ALL stale worktrees this session except `.worktrees/v149-buddy-fix` (left per prior
  note). `feat/buddy-security-harden` worktree+branch removed after the ff-merge.

## 0b. PRIOR SESSION SUMMARY (2026-10-15)
This session: shipped two follow-ups + V1.50 + V1.51, and ran a security gap analysis.
- **Shipped V1.50** (live-map-on-enable fix + "buddy online" notification with tap-to-map + the
  re-send-after-decline false "Request sent" fix). Published to GitHub, installed on the new phone.
- **Shipped V1.51** (Buddy invite link reliably OPENS THE APP → existing Accept/Decline prompt +
  friendlier share message). Pushed `main`, tag `V1.51`, GitHub release published, **and Firebase
  Hosting deployed**. Fixed a real gotcha: Firebase intercepts `/.well-known/*` and served `[]`,
  overriding the rewrite → moved the real statement to the actual dotted path
  `firebase-hosting/public/.well-known/assetlinks.json` and removed the `"**/.*"` ignore glob.
  VERIFIED LIVE: `https://joggin-a69a7.web.app/.well-known/assetlinks.json` now serves the real
  Digital Asset Links statement (not `[]`).
- **HEAD = `a279002` on `main`, pushed, in sync with origin.** Current app = **V1.51** (versionCode
  51). `builds/joggin.apk` and `builds/joggin-v1.50.apk`/`joggin-v1.49.apk`/`joggin-v1.48.apk` +
  `joggin-v1.51.apk` present.
- **OPEN / NEXT STEPS for a new session:**
  1. **On-device verify V1.51** (user was installing while we worked): install V1.51 on BOTH phones
     (`adb install -r app\build\outputs\apk\debug\app-debug.apk` — current app-debug.apk IS V1.51),
     then `adb shell pm get-app-links com.example.joggingapp` should show `verified` for
     `joggin-a69a7.web.app`; real test = share code A→B via a chat app, tap link on B → app opens to
     Accept/Decline. (A reinstall is what makes Android re-run App Links verification against the
     now-fixed server.)
  2. **Security gap analysis is DONE** — full report at `.agents/tasks/security-gap-analysis.md`
     (14 findings, severity-ranked, file/line cited). NOT YET ACTED ON. Top items the user may want
     next:
     - **F1 (High, quick):** `.ghtoken` is a live-looking GitHub token in the repo root and is NOT
       in `.gitignore` (currently untracked, so not committed — but one `git add .` would commit it).
       Fix = gitignore it + ROTATE the token + move it out of the tree. (User decision needed to
       rotate.)
     - **F2+F9 (High/Med):** buddy `users/{appId}` docs leak `authUid` + `fcmToken` + full profile to
       any linked (even pending) party because Firestore reads are whole-document → split sensitive
       fields into an owner-only subdoc (data-model + rules change, needs redeploy).
     - **F3+F5 (High/Med):** no server-side rate/size/field limits → a malicious anonymous client can
       amplify Firestore cost on the free Spark plan; add field whitelists + size/type caps +
       `reqCount` monotonicity to the rules.
     - **F6 (Med):** verified App Link is pinned to a DEBUG signing cert; move to release signing for
       anything beyond personal testing. **F7/F8:** `allowBackup=true` + `usesCleartextTraffic=true`.
     - **F10:** the "request location now" feature is BROKEN (writes a doc the rules forbid, fails
       closed) — worth fixing if that feature is wanted. **F14:** delete the dead non-dot
       `public/well-known/assetlinks.json` copy (kept as a harmless fallback this session).
  3. I recommended batching F2/F3/F5/F9 as one "Buddy Link rules + data-model hardening" change
     (all touch `firestore.rules` + the user data model; needs a rules redeploy). Not started.
- ⚠ The old `.agents/tasks/buddy-deeplink-investigation.md` is STALE (describes pre-fix state) —
  ignore it; the App Links fix is live. The security report is the current authoritative doc.

## 2. Current version / build state
- **V1.52 (CURRENT — SHIPPED 2026-10-17)** — `versionCode 52`, `versionName "1.52"`
  (`app/build.gradle`). HEAD on `main` = **`197c29e`**, tag **`V1.52`**, pushed + in sync with
  origin. **PUBLISHED to GitHub Releases** (not draft, `releases/latest`, asset `joggin.apk`
  12,383,998 bytes): https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.52 .
  **Firestore rules DEPLOYED** (`firebase deploy --only firestore:rules`). Hosting NOT redeployed
  (no assetlinks/hosting change since V1.51). V1.52 = Buddy Ping/Route sharing mode (`a88f6e2`) +
  Buddy Link security hardening (`963e9a5`, F2/F3/F5/F7/F8/F9/F10/F14) + UI polish (pill Copy/Share
  `ff5b597`, swipe labels/arrows `d98d07c`, shared-route map inset `f691e30`). Build green
  (`assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL, run on pwsh 7). `builds/`:
  `joggin-v1.52.apk` archived (newest 4 kept: v1.49–v1.52; v1.48 pruned) + `joggin.apk` (= V1.52).
  Release notes: `.kiro/specs/release-notes-v1.52.md`. **On-device PENDING** — install on both
  phones and verify buddy flows against the newly-deployed rules (see §0 open steps).
  ⚠ assetlinks still carries the DEBUG cert SHA-256 — a release-signing key (F6) would need its
  SHA-256 added too.
- **V1.51** — `versionCode 51`, `versionName "1.51"` (`app/build.gradle`). Bump committed `9391032`;
  App Links serving fix committed `a279002`. **PUSHED to origin/main**, tag `V1.51` pushed, and
  **PUBLISHED to GitHub Releases** (not draft, `joggin.apk` asset ~12.4 MB, release id 414305662).
  Release URL: https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.51 — it is
  `releases/latest`. **Firebase Hosting DEPLOYED** (`firebase deploy --only hosting`). V1.51 =
  Buddy invite reliably opens the app → existing Accept/Decline prompt (routing/delivery fix) +
  friendlier share message. HEAD on `main` = **`a279002`**.
  ⚠ **App Links serving GOTCHA (now fixed — don't regress):** Firebase Hosting intercepts the
  dotted `/.well-known/*` path with its OWN handler returning `[]`, which OVERRODE the
  `firebase.json` rewrite from `/.well-known/assetlinks.json` → `/well-known/assetlinks.json` (that
  rewrite served `[]` at the dotted path for two deploys). FIX: the real statement now lives at the
  ACTUAL dotted path `firebase-hosting/public/.well-known/assetlinks.json`, the `firebase.json`
  `ignore` glob `"**/.*"` was REMOVED (it was excluding the `.well-known` dir — that's why deploys
  reported "found 4 files"; now "found 5"), and the rewrite was dropped. VERIFIED live:
  `https://joggin-a69a7.web.app/.well-known/assetlinks.json` serves the real statement (package
  `com.example.joggingapp` + debug SHA-256), NOT `[]`. The non-dotted `public/well-known/assetlinks.json`
  is still present as a harmless fallback. ⚠ assetlinks carries the DEBUG cert SHA-256
  (`24:0D:EE:CC:…99:0E:B0`) — a release-signing key would need its SHA-256 added too.
  **On-device PENDING:** install V1.51 on both phones (`adb install -r app\build\outputs\apk\debug\app-debug.apk`
  — current app-debug.apk IS V1.51), then `adb shell pm get-app-links com.example.joggingapp` should
  show `verified` for `joggin-a69a7.web.app`; real test = share code A→B via a chat app, tap link on
  B → Joggin opens to Accept/Decline. (ps1 scripts in repo root are blocked by execution policy —
  run with `powershell -ExecutionPolicy Bypass -File .\x.ps1`, or copy APKs directly.)
- **V1.50** — `versionCode 50`, `versionName "1.50"` (`app/build.gradle`). Committed `69117c1`,
  pushed to `origin/main`, tag `V1.50` pushed, and **PUBLISHED to GitHub Releases** (not draft,
  `joggin.apk` asset 12.4 MB, release id 413229474, published 2026-10-14). Release URL:
  https://github.com/mikesibrahim1-ops/JogginApp/releases/tag/V1.50 — it is `releases/latest`, so
  the install page and the in-app update check resolve to it. The user built the APK and ran the
  publish (local shell was wedged on my side; the user ran `publish_v150.ps1`'s steps). V1.50 rolls
  up `e4797d3` (re-send fix) + `292c501` (live-session-on-enable + buddy-online notification +
  tap-to-map). **No Firestore rules change** (V1.49 rules already cover it) — no `firebase deploy`
  needed. **on-device install PENDING** (install V1.50 on both phones to test the live-start +
  online notification + tap-to-map). `builds/`: archived `joggin-v1.50.apk` + lowercase `joggin.apk`
  (= V1.50). Build was green at the bump (assembleDebug + testDebugUnitTest → BUILD SUCCESSFUL).
- **V1.49** — `versionCode 49`, `versionName "1.49"`. PUBLISHED (GitHub + Firestore rules deployed +
  anon auth enabled). Superseded by V1.50 on `main`. Install when plugged in:
  `adb install -r app\build\outputs\apk\debug\app-debug.apk` (the app-debug.apk currently in the
  build output IS V1.49), or from the GitHub release / install page. **Both phones need V1.49** —
  V1.48 can no longer connect (its requestLink precheck is denied by the new rules).
- Git: HEAD **`9cf48bc`** on `main`, **NOT yet pushed** (origin/main is at `69117c1` = V1.50, tag
  `V1.50` pushed + published). `main` sits one+ commit ahead of the published V1.50; everything
  through `9cf48bc` is code-complete, unpushed, and NOT in any shipped APK. Contents on top of
  V1.50 (`69117c1`):
  - **`9cf48bc` — Buddy "Share" sends the full localized (EN/EL) invite message** (not the bare
    code): via `controller.buildInviteShareText` → `BuddyInvite.buildShareText` into `EXTRA_TEXT`
    + localized `EXTRA_SUBJECT` (`buddyShareSubject`). `text/plain` + `createChooser` kept (system
    share sheet). Copy/Connect/deep-link untouched. Build green, 77/77, review APPROVED.
  - **Buddy Link invite "open the app" / App Links fix is ALSO already present in `main`** (at/below
    `9cf48bc`). ⚠ The earlier `.agents/tasks/buddy-deeplink-investigation.md` is STALE — it
    describes the pre-fix state (empty assetlinks, no autoVerify). Reality now in the main
    workspace: `app/src/{main,firebase}/AndroidManifest.xml` have `android:autoVerify="true"` on
    the https `/buddy` filter; `firebase-hosting/public/well-known/assetlinks.json` exists (package
    `com.example.joggingapp`, DEBUG SHA-256 `24:0D:EE:CC:…99:0E:B0`); `firebase.json` rewrites
    `/.well-known/assetlinks.json` → `/well-known/assetlinks.json` with `application/json` headers
    (dodges the `**/.*` ignore glob); `firebase-hosting/public/buddy/index.html` uses an
    `intent://…package=com.example.joggingapp;S.browser_fallback_url=…/get;end` with a raw
    `joggin://` secondary. The shared message already leads with the `joggin://buddy/v1/<id>` link.
    So "tap invite → app opens → Accept/Decline" is fully implemented in code; it just needs to be
    DEPLOYED/SHIPPED (see TO SHIP below). A redundant workflow (`feature-buddy-invite-open-app`)
    re-did this and FAILED — its coder correctly found "already implemented, byte-identical to
    main"; nothing new to merge from it.
  History (newest first): `a279002` (App Links serving fix) → `9391032` (V1.51 bump+notes) →
  `9cf48bc` (full share message) → `69117c1` (V1.50 bump+notes) → `292c501` (live-start + online
  notif + tap-to-map) → `e4797d3` (re-send fix) → `7ef93d3`/`9c2c51b` (V1.49, tag `V1.49`). Tags
  V1.40–V1.51. ✅ V1.51 is SHIPPED (pushed, released, hosting deployed) — the V1.51 "TO SHIP" steps
  are DONE; see the V1.51 entry at the top of §2.
- ✅ **WORKTREES CLEANED UP (2026-10-17).** `git worktree list` now shows ONLY the main checkout.
  `.worktrees/buddy-route-mode` and `.worktrees/v149-buddy-fix` were removed (dirs + branches
  `feat/buddy-route-mode`, `fix/v149-buddy-permission` deleted + pruned). All earlier stale
  worktrees from prior sessions were already gone. Scratch probe files cleaned from repo root.
- **Working tree has untracked scratch files** (gitignored or junk): `LOG.txt`, `apks.txt`,
  `apks2.txt`, `fs_hits.txt`, `fscap.txt`, `.ghtoken`, `.agents/`, `.worktrees/`, plus a modified
  `firebase-hosting/.firebase/hosting.*.cache`. None are part of the app; safe to leave or clean.
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
- **✅ TERMINAL IS FIXED (2026-10-17).** Kiro now runs **PowerShell 7.6.7** (set as default profile
  in `%APPDATA%\Kiro\User\settings.json`). Streaming commands run normally with correct exit codes.
  The old char-by-char echo + `exit -1` wedge was Windows PowerShell 5.1 + PSReadLine 2.0.0.
  **You no longer need the file-redirect workaround** described below — run gradle/git directly and
  trust stdout + exit codes.
  - If the wedge EVER returns, confirm the terminal is pwsh 7 (`$PSVersionTable.PSVersion` should be
    7.x, Edition Core). If it reverted to 5.1, re-select the "PowerShell 7" default profile. The
    pwsh 7 binary is at `C:\Users\INFINITI\AppData\Local\Microsoft\WindowsApps\pwsh.exe`.
  - LEGACY WORKAROUND (only if pwsh 7 is somehow unavailable): PowerShell 5.1 wedges — redirect
    output to a file (`*> file.txt 2>&1`) and read it with the file tools; `list_directory` /
    `file_search` / `read_file` work even when the shell is wedged.
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

## 4. What was accomplished across recent sessions (all committed; see §2 for push state)
Newest first on `main`:
- `292c501` — **Buddy Link: start live session on enable (no app restart) + "buddy online"
  notification (tap → map)** (code change only — NO version bump, NOT yet pushed; not in any
  published APK). Three user-requested items: (1) **Restart bug** — enabling Buddy Link + completing
  the "I understand" consent did NOT start the live map until the app was closed+reopened. Root
  cause (planner-traced, not assumed): `enableConfirmed()` already called `beginObserving()`, but
  `BuddyLinkController.start()` set `started=true` even when disabled and `disable()` left listeners
  attached, so the session/listener lifecycle was stale; plus the Compose map never re-evaluated
  time-based `isLiveNow()`. Fix: a single idempotent `ensureSession()` (sign-in w/ bounded retry →
  attach listeners) shared by BOTH `start()` (launch) and `enableConfirmed()` (consent); `disable()`
  now cancels the listener Jobs + resets an `observing` flag so re-enable re-attaches cleanly; a
  15s UI tick in `JogApp` re-evaluates liveness so the map repaints when a buddy appears — no
  restart. (2) **"Your buddy, X, is online" local notification** fired ONLY on a genuine
  offline→online transition — pure `BuddyTransitions.onlineEvents()/onlineSnapshot()` using the same
  `isLiveNow` notion as the map, with initial-snapshot suppression (`prev==null` → silent baseline)
  and carried-forward dedupe (one alert per transition); wired into the existing `observeBuddies`
  diff + reused buddy-events notification channel. (3) **Tap the notification → open app on the MAP
  centered on that buddy**: `BuddyNotifier` puts an `EXTRA_FOCUS_BUDDY_ID` on the open-app
  PendingIntent; `MainActivity.onCreate/onNewIntent` reads it into a new `BuddyFocusBus` (mirrors the
  existing `BuddyInviteBus` deep-link pattern); `JogApp` polls it, jumps the pager to the map page,
  and `JogMap` animates/zooms to the buddy's fix (only when the live primary buddy matches the named
  id). EN+EL strings added (`buddyNotifOnlineTitle`/`buddyNotifOnlineBody`). NO rules/manifest/Cloud
  Function changes. Build verified green (`assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL +
  DONE_0; **BuddyTransitionsTest 24/24**, total **77/77**, 0 failures — the coder's full run on the
  exact committed content). Files: BuddyLinkController.kt, BuddyNotifier.kt, BuddyTransitions.kt,
  JogMap.kt, MainActivity.kt, Strings.kt, BuddyTransitionsTest.kt (+295/−14). ⚠ Workflow note: the
  implement-and-review workflow's code step HUNG for ~4h on the wedged shell AFTER its build
  succeeded; I aborted it and finished manually (reviewed the diff myself, committed `292c501`,
  ff-merged). The semantic-review gate did NOT run — I reviewed the four pieces by hand instead.
  **Not verified: on-device** (needs the two phones; the map-focus + notification are runtime UI).
- `e4797d3` — **Fix false "Request sent" when re-sending a Buddy Link invite after decline/removal**
  (code change only — NO version bump, NOT yet pushed). Resolves the §4 known non-blocker flagged in
  the V1.49 review. Root cause: `FirebaseBuddyRepository.requestLink`'s existing-doc branch bumped the
  24h re-request counter but KEPT the existing `state`, so when the prior link was in a dead-end
  `declined`/`removed` state the rules-allowed `tx.update` returned `Success` and the UI showed a
  false "Request sent" — but no fresh pending request was ever created, so the recipient
  (`observePendingRequests` filters `state=='pending'`) never saw it. Fix (client-only, NO rules
  change): when the existing link is `declined`/`removed`, `ref.delete().await()` the dead-end doc,
  then run the normal create transaction to make a genuinely fresh `pending` request. Both the delete
  (either party may delete) and the create (side `a`, pending, `exists(users/{b})`) are permitted by
  the ALREADY-DEPLOYED V1.49 rules; an in-place `declined→pending` reset is forbidden by the update
  rule, which is why delete-then-create is used. The re-send decision is extracted as pure
  `BuddyLinkDecisions.requestLinkAction(state) → RequestLinkAction {CREATE_NEW, BUMP_EXISTING,
  RECREATE_PENDING}` and unit-tested (5 new cases). `pending`/`accepted` links keep the old
  BUMP_EXISTING counter behavior (re-send to an existing buddy is still a no-op bump by design).
  NO interface/NoOp/UI/Strings changes; `firestore.rules` + `firebase-hosting/firestore.rules`
  untouched and still byte-identical → **no `firebase deploy` needed**. Build verified green
  (`assembleDebug` + `testDebugUnitTest --rerun-tasks` → BUILD SUCCESSFUL; BuddyLinkDecisionsTest
  **16/16**, fresh timestamp, 0 failures). **Not verified: real two-device re-send after a decline**
  (needs 2 phones on V1.49). Files: `FirebaseBuddyRepository.kt`, `BuddyLinkDecisions.kt`,
  `BuddyLinkDecisionsTest.kt` (+82/−1). ⚠ This fix is NOT in any published APK yet — it ships with
  the next version bump.
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
  ✅ **KNOWN NON-BLOCKER — NOW FIXED in `e4797d3`** (see top of §4): the declined/removed re-send
  false "Request sent" is resolved (delete-then-recreate a fresh pending request).
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
3. ✅ DONE — declined/removed re-send false-positive fixed in `e4797d3` (not pushed, not yet in a
   published APK; ships with the next version bump). Verify on two devices when convenient: decline
   an invite on phone B, then re-send from phone A → B should now actually receive a fresh pending
   invite (previously silently nothing).
4. **Push + release when ready:** `git push` `main`, then bump version + build + archive + tag +
   publish if you want `e4797d3` in a shipped APK.
- Still owed from earlier (lower priority): eyeball per-type target colours across the 4 themes
  (visual, on-device — best done by the user).
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
