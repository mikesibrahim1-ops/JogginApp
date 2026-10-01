# HANDOVERSESSION — JogginApp

> Auto-generated hand-off summary. A new session should read this FIRST and align to
> this state before doing work. Regenerated on demand when the user sends `#HookStart`
> (HANDOVERSESSION hook, trigger: UserPromptSubmit).

## 1. Project
Android GPS fitness tracker (walk/run/cycle) at `c:\Users\INFINITI\Documents\Kiro\JogginApp`.
100% Kotlin, Jetpack Compose UI, osmdroid maps, foreground GPS service, SharedPreferences +
Gson storage, English/Greek i18n via `Strings.kt`. Has a **Firebase backend** (live) powering
the in-progress **Buddy Link** safety-buddy location-sharing feature. Distributed as a
sideloaded APK (not on Play Store).

## 2. Current version / build state
- **V1.40** — `versionCode 40`, `versionName "1.40"`, footer label "V1.40" in MainActivity.
- `minSdk 24`, `targetSdk 34`, `compileSdk 34`.
- Last build: **BUILD SUCCESSFUL** (assembleDebug, debug-signed). Archived at
  `builds/joggin-v1.40.apk` and staged as `builds/joggin.apk` (lowercase, for GitHub upload).

## 3. Environment notes (IMPORTANT — avoids wasted time)
- **Terminal is very flaky.** PowerShell echoes char-by-char; foreground shells wedge often;
  background terminals sometimes swallow commands or cross-feed stdin (a `Start-Sleep` from one
  call leaks into another terminal). Interactive `[Y/N]` prompts can wedge the whole session.
- **Reliable BUILD pattern:** fresh background terminal running
  `cmd /c ".\gradlew.bat assembleDebug --console=plain > FILE.txt 2>&1 & echo DONE_%errorlevel% >> FILE.txt"`,
  then poll `FILE.txt` with the read tool. Success = `BUILD SUCCESSFUL` + `DONE_0`.
- **Reliable COPY/archive pattern:** direct `Copy-Item` with **absolute paths** (relative paths
  fail — shell cwd drifts after `cd`). Verify with list_directory/file_search, NOT stdout (garbled).
- **`firebase deploy` works from here** now that the user has run `firebase login` (stored on
  machine). Run via a fresh background terminal → file; confirm "Deploy complete!" in the file.
- Cannot reach the phone or run `adb` from this environment — **device testing is user-side.**
- After tasks: stop stray background terminals and delete temp `.txt` logs.

## 4. What was accomplished (across recent sessions)
### Buddy Link (spec in `.kiro/specs/buddy-link/`) — Phases 3–6 built + Firebase live
- **Phase 3–4:** `BuddyLinkSection.kt` UI (master toggle, G3 consent→ensureId→bg-permission→
  activate/revert, invite share, buddy/pending/blocked lists). Controller in `JogApp`; invite
  deep-link intent-filter + `BuddyInviteBus` in `MainActivity`; GPS-on → `updateGpsState`.
- **Phase 5:** `BuddyLocationService.kt` (foreground, ~5-min broadcast + on-demand, broadcast-on
  notification); controller starts/stops it on CONDITION-1 (`enabled && gpsOn`).
- **Phase 6:** `makeBuddyAvatarDrawable` (amber marker), buddy avatar + dashed trail in `JogMap`
  (only when `isLiveNow()`), eye "eyes-on-you" indicator (bottom-center, tap→options),
  broadcast-on badge; controller exposes `primaryBuddyView` + `buddyTrail`.
- **Firebase prep:** gated so app builds WITHOUT `google-services.json` — `firebaseEnabled =
  file('google-services.json').exists()` in `app/build.gradle` controls plugin, deps
  (BoM 33.1.2 auth/firestore/messaging + coroutines-play-services), `src/firebase/java` sourceSet,
  and `BuildConfig.FIREBASE_ENABLED`. `BuddyRepositoryProvider` loads `FirebaseBuddyRepository`
  via **reflection** (main source never hard-refs Firebase). `.gitignore` ignores the JSON.
- `FirebaseBuddyRepository.kt` (in `app/src/firebase/java/...`): App-ID-keyed docs (own UUID),
  anon uid stored as `authUid` + `authMap/{uid}→appId` resolver; full interface via callbackFlow.
- `firestore.rules`: owner-only writes + CONDITION-1 (accepted link + both sharing + not blocked)
  for location reads. Hardened with `mappedToMe()` (anti App-ID squat); `ensureSession` writes
  `authMap` first.

### Firebase configured & working (user did console steps)
- Project `joggin-a69a7`, package `com.example.joggingapp`; Anonymous Auth + Firestore (prod mode).
- `google-services.json` in `app/`. Security rules **published**.
- **Verified:** enabling Buddy Link created `authMap/{uid}` + `users/{appId}` docs in Firestore.
- TTL **skipped** (needs Blaze; free Spark can't) — harmless at this scale.

### App distribution — SOLVED
- Original problem: APK shared via WhatsApp → fresh phone "App not installed" after passing scan.
- Root causes: WhatsApp corrupts attached APKs; old `minSdk 26` rejected older phones.
- Fixes: `minSdk`→24; `shareApp()` now shares a **download link** (not the APK file).
- **Firebase Hosting** (`firebase-hosting/`) serves pages: `/` landing, `/get` install page,
  `/buddy` invite fallback (fixes old 404). Deployed & live at `https://joggin-a69a7.web.app`.
- APK **cannot** go on Firebase Hosting (Spark bans executables) and Firebase Storage needs Blaze
  → APK hosted on **GitHub Releases**: `github.com/mikesibrahim1-ops/JogginApp` (public). Download
  page links to `.../releases/latest/download/joggin.apk` (lowercase, redeployed).
- **CONFIRMED:** user installed on a fresh phone from `…/get` — installed fine. Distribution works.
- Firebase CLI v15.32.0 installed, user logged in. Node v24.19.0 / npm 11.17.0.

### HANDOVERSESSION hook
- `.kiro/hooks/handoversession.json` — trigger `UserPromptSubmit`, matcher `#HookStart`, agent
  action. Only regenerates this file when the user's message contains `#HookStart` (the real gate
  is a condition inside the prompt, since matchers aren't reliably applied to UserPromptSubmit).

### Housekeeping
- Bumped to V1.40, built, archived (`builds/` = joggin-v1.40/1.39/1.38/1.36 + staged joggin.apk).
- Standardized on lowercase `joggin.apk` everywhere. Deleted stray temp `.txt` files + unused
  `docs/` folder. Background terminals stopped.

## 5. Current working state (verified vs. untested)
- **Verified working:** app builds V1.40; Firebase enable creates Firestore docs on one device;
  distribution chain (`/get` → GitHub APK → install on fresh phone).
- **Compiled but NOT exercised:** `FirebaseBuddyRepository` + `firestore.rules` for the two-party
  flows (linking, mutual sharing, map avatar, eye indicator, on-demand, unlink/block). Expect
  iteration once a 2nd device is available. Buddy Link map/eye overlays never rendered live yet
  (no live buddy data until 2 devices link).

## 6. Immediate next step(s)
- **PENDING USER ACTION:** update the GitHub **v1.40** release to serve the real V1.40 — the
  published release currently has a relabeled V1.39 APK named `Joggin.apk` (capital J). User must:
  edit v1.40 release → remove old asset → upload `builds/joggin.apk` (lowercase, as-is) → save.
  Then `…/get` serves real V1.40 (footer reads V1.40).
- After that: to continue Buddy Link, need a **2nd test device** for two-party verification
  (Phase 9), then Phase 7 (FCM notifications) and Phase 8 (privacy/compliance).

## 7. Known caveats / open items / blockers
- APK is **debug-signed** (debug key). Fine for sideloading, but switching to release signing
  later forces users to uninstall first (signature mismatch → "App not installed"). Release
  keystore `joggin-release.jks` exists but signing env vars (`JOGGIN_KEYSTORE_PASSWORD` etc.)
  are NOT set.
- `com.example.joggingapp` cannot be published to Play Store (reserved namespace) — irrelevant
  for link distribution.
- Firebase Storage / TTL blocked on Blaze (paid) — deferred.
- Buddy Link Phases 7–8 not started; two-device testing blocked on hardware.

## 8. Release recipe (going forward)
1. Bump version + build → produces `builds/joggin.apk`.
2. User creates a new GitHub release (tag `vX.Y`), uploads `joggin.apk` (lowercase, SAME filename
   every time). Repo now has a `main` branch/README, so no "invalid target_commitish" error.
3. Done — `/get` auto-serves newest via the `/latest/` URL. No page edit, no redeploy.

## 9. Key files
- `app/build.gradle` — version, `minSdk 24`, `firebaseEnabled` gating, buildConfig flag.
- `app/src/main/java/com/example/joggingapp/` — `MainActivity.kt` (JogApp, shareApp, options pane,
  deep-link), `BuddyLinkController.kt`, `BuddyLinkSection.kt`, `BuddyLocationService.kt`,
  `Buddy*.kt` (models/constants/identity/invite/repository/provider/noop), `JogMap.kt`,
  `MarkerBitmaps.kt`, `ForegroundLocationService.kt`, `Strings.kt`.
- `app/src/firebase/java/com/example/joggingapp/FirebaseBuddyRepository.kt` — gated; compiles only
  when `app/google-services.json` present.
- `firestore.rules` — Firestore security rules (paste into console when changed).
- `firebase-hosting/` — `firebase.json`, `.firebaserc` (→joggin-a69a7), `public/{index,get,buddy}`.
- `.kiro/specs/buddy-link/` — requirements.md, design.md, tasks.md, firebase-setup.md.
- `.kiro/hooks/handoversession.json` — hook that regenerates this file when user sends `#HookStart`.
- `archive-build.ps1` — archives debug APK to `builds/` (kept newest 4); direct Copy-Item is the
  more reliable path given shell flakiness.
