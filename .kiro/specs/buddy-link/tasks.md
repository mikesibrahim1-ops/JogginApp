# Buddy Link — Implementation Tasks

> Plan derived from `requirements.md` (incl. gap-fixes G1–G4) and `design.md` (v2).
> Decisions locked: Firebase (D1), live ~5-min + on-demand (D2), 1:1 mutual (D3),
> anonymous App ID on first-enable (D4), invite-link linking (D5).
>
> Tasks are ordered so the app stays buildable and offline-first at every step.
> Firebase-dependent work is isolated behind interfaces so early tasks don't break
> the existing app. Each task lists the requirements it satisfies.
>
> **STATUS LEGEND:** `[x]` done · `[~]` partial (see note) · `[ ]` not started.
> **Audit (2026-09-29):** checkboxes below reflect the ACTUAL code state, not the
> original plan. Firebase is LIVE (`app/google-services.json` present → plugin, deps,
> `src/firebase/java` source set, and `BuildConfig.FIREBASE_ENABLED=true` all active),
> so `BuddyRepositoryProvider` loads `FirebaseBuddyRepository`, not the NoOp. Phases
> 0–6 are essentially built end-to-end; the real remaining work is a handful of missing
> UI hooks, backup/restore identity folding, and all of Phase 7 (notifications/FCM).

---

## Phase 0 — Groundwork & safety (no Firebase yet)

- [ ] 0.1 Feature flag / constant `BUDDY_LINK_ENABLED`. _(Req 10)_
  **PARTIAL / done-differently:** no named `BUDDY_LINK_ENABLED` constant. Gating is
  done via build-time `BuildConfig.FIREBASE_ENABLED` (selects backend) + a per-user
  runtime toggle `BuddyLinkController.enabled` (prefs key `buddy_enabled`). Core-app
  safety is satisfied through the `NoOpBuddyRepository` fallback.
- [x] 0.2 `BuddyModels.kt` data classes. _(Req 2, 3, 5, 5b)_
  `BuddyLinkState` (PENDING/ACCEPTED/DECLINED/REMOVED), `BuddyUser`, `BuddyLink`,
  `BuddyLocation`, `BuddyTrailPoint`, plus `BuddyView` with `isLiveNow()`.
- [x] 0.3 `BuddyConstants`. _(Req 5.8, 6.1, 3.5b, 3.8, 8.1)_
  `BROADCAST_INTERVAL_MS` (5 min), `STALE_MS` (15 min), `RETENTION_DAYS=7`,
  `INVITE_EXPIRY_DAYS=7`, `REQUEST_ALERT_MAX_PER_24H=2` (+ scheme/host/window consts).
- [x] 0.4 Buddy Link strings (EN + EL) in `Strings.kt`. _(Req 9)_
  Full block present in both locales (title, toggle, consent, invite, pending,
  block/unblock, statuses, eye indicator, broadcast-on, permission note, errors,
  history). Note: a few strings (`buddyHistory`) are defined but not yet used in UI.

## Phase 1 — Identity (local first, Firebase-agnostic)

- [x] 1.1 `BuddyIdentity`: app-owned UUID on first enable. _(Req 2.1, 2.2, D4)_
  `ensureId()`/`getId()`/`hasId()`/`reset()` (+ `adoptId()`), dashless UUID in
  `jog_prefs` key `buddy_app_id`, created from `enableConfirmed`.
- [x] 1.2 Fold App ID into Backup/Restore JSON. _(Req 2.3, D4)_
  Backup format bumped to **v2**: `RouteStorage.kt` now has a `BackupData` envelope
  (`version`, `buddyAppId`, `routes`) with `toJson()` + `parse()`. `backupRuns()` writes
  the envelope incl. `BuddyIdentity.getId()`; the restore launcher parses it and calls
  `BuddyIdentity.adoptId()` **only if the install has no identity yet** (never clobbers
  a provisioned one). `parse()` is backward-compatible with legacy v1 bare-array
  backups. Unit tests added in `RouteStorageTest.kt` (see caveat below). _(2026-09-29)_
- [x] 1.3 Invite link format + generate/parse/validate. _(Req 2.4, 3.1, 3.5, 3.5b)_
  `BuddyInvite.kt`: `joggin://buddy/v1/<appId>?exp=` + https fallback
  `https://joggin-a69a7.web.app/buddy?id=&exp=`, `buildShareText`, `parse()` →
  Valid/Malformed/Expired incl. 7-day expiry check.

## Phase 2 — Backend abstraction & Firebase setup

- [x] 2.1 `BuddyRepository` interface (suspend/Flow API). _(Req 10)_
  `BuddyRepository.kt` full API + `BuddyResult` sealed type w/ reason enum. All
  feature code depends on this, not Firebase.
- [x] 2.2 User Firebase setup (project, app, google-services.json, Auth, Firestore).
  `app/google-services.json` present (project `joggin-a69a7`, package
  `com.example.joggingapp`).
- [x] 2.3 Firebase Gradle wiring; app builds with feature OFF. _(Req 10)_
  Conditional `com.google.gms.google-services` + firebase-bom 33.1.2, auth, firestore,
  messaging, coroutines-play-services; `src/firebase/java` added to source set;
  `FIREBASE_ENABLED` BuildConfig field — all gated on the JSON.
- [x] 2.4 `FirebaseBuddyRepository`. _(Req 2, 3, 4, 5, 12)_
  Implements every interface method: anon sign-in mapped via `authMap/{uid}→appId`,
  App-ID-keyed `users`/`locations`/`links`, sorted deterministic `linkId`, 24h
  request-count logic, `expireAt` TTL stamps, on-demand `locReq`, `deleteAllMyData`.
  Note: accepts `fcmToken` but no app code fetches/stores it (see 7.1).
- [ ] 2.5 Firestore security rules + TTL policy. _(CONDITION-1, Req 8, 12)_
  **Rules DONE:** `firestore.rules` (repo root) enforces only-self writes,
  accepted-link + mutual `sharing==true` + no-block for location/trail reads, link
  constraints. **TTL PARTIAL:** `expireAt` is written, but the Firestore TTL policy is
  a console setting with no evidence it's configured. Rules must be manually published.

## Phase 3 — State holder & toggle

- [x] 3.1 State holder (`BuddyLinkController`). _(Req 1, 3b, 10)_
  Plain Compose-state class (not AndroidX VM); exposes toggle, appId, gpsOn,
  backendAvailable, buddies, pending, blocked, watchers, buddyTrail.
- [x] 3.2 Master toggle + first-enable flow (G3). _(Req 1.3–1.6, 6.6, 6c, G3)_
  `BuddyLinkSection`: consent dialog → bg-location permission → `enableConfirmed`;
  denied permission never sticks ON.
- [x] 3.3 CONDITION-1 client-side. _(CONDITION-1, Req 4.1)_
  `sharing = enabled && gpsOn`; `updateGpsState` pushes to backend + drives
  `applySharingState()` to start/stop the service.

## Phase 4 — Buddy Link options UI

- [x] 4.1 Collapsible section as LAST options section. _(Req 1.1–1.3, 9.2)_
  `BuddyLinkSection` rendered last in the options pane via `CollapsibleSection`.
- [x] 4.2 Invite Buddy → share sheet. _(Req 2.4, 3.1)_
  Builds share text, fires `ACTION_SEND` chooser.
- [x] 4.3 Incoming invite deep link. _(Req 3.2, 3.5, 3.5a, 3.5b)_
  Manifest intent-filters (`joggin://buddy`, `https://joggin-a69a7.web.app/buddy`);
  `handleBuddyInviteIntent` → `BuddyInviteBus` → parse LaunchedEffect →
  requestLink / expired / invalid. Static fallback page in firebase-hosting/public.
- [x] 4.4 Buddy / Pending / Blocked lists. _(Req 3b, 12)_
  Pending (accept/decline), buddy rows (status + Remove-with-confirm + **Block**-with-
  confirm), and Blocked list (with unblock) all render. Block button added to buddy
  rows wiring `controller.block()` via a `BuddyDialog` confirmation. _(2026-09-29)_
- [x] 4.5 Request-frequency rule (≤2 alerts / 24h). _(Req 3.8)_
  Backend tracks `reqCount`/`reqWindowStart`; client now enforces the alert cap.
  `BuddyLinkController.detectPendingTransitions` tracks `linkId → reqCount` and fires the
  request notification on a new OR bumped `reqCount`, but only while
  `reqCount <= REQUEST_ALERT_MAX_PER_24H` (2). Beyond that the request stays visible in
  the pending list, silently. _(2026-09-30)_
- [x] 4.6 Permission-required note when bg location denied/revoked. _(Req 6c)_
  `BuddyLinkSection` shows `buddyPermissionNeeded` in-section whenever enabled but
  `ACCESS_BACKGROUND_LOCATION` is not granted; re-checked on `ON_RESUME`
  (LifecycleEventObserver) so a grant/revoke in settings updates on return. _(2026-09-30)_
- [x] 4.7 Localize + content descriptions. _(Req 9, 13)_
  Full EN/EL. contentDescription added to the master toggle (on/off state via
  `buddyToggleDesc`), invite, accept/decline, remove/block, unblock, delete-data
  (`buddyActionDesc(action, name)`), plus the permission note. _(2026-09-30)_

## Phase 5 — Location reporting service

- [x] 5.1 `BuddyLocationService` (separate foreground service). _(Req 6.1, 6.3, 5b, D2)_
  ~5-min broadcast loop → `writeMyLocation` (latest fix + sparse trail append).
- [x] 5.2 Broadcast-on indicator; stop conditions. _(Req 6.2, 6.4, 6.7)_
  Foreground notification (channel `joggin_buddy`); started/stopped as CONDITION-1
  changes.
- [x] 5.3 Bg-location required; graceful no-network. _(Req 6.5, 6.6, 6b, 6.8)_
  Checks `ACCESS_BACKGROUND_LOCATION` and self-stops if absent; backend failures
  non-fatal.
- [x] 5.4 On-demand "request location now". _(Req 6.1b; G6)_
  Backend + service + UI button all done. **Rate-limit:** per-buddy cooldown
  (`BuddyConstants.ON_DEMAND_REQUEST_COOLDOWN_MS` = 2 min); `controller.requestBuddyLocation`
  returns false within cooldown, and the button shows "⏳ …please wait" (disabled) via a
  5s UI tick until it elapses. **G6 decision = NOTIFY (not silent):** when the service
  reacts to an on-demand request it fires `BuddyNotifier.notifyLocationShared()` on a
  low-importance channel (`joggin_buddy_events_low`) so the located party is informed
  without nuisance. _(2026-09-30)_

## Phase 6 — Map display (home screen)

- [x] 6.1 Buddy avatar overlay. _(Req 5.1–5.6, 5.8)_
  `JogMap` draws a buddy marker at the latest fix, gated on `isLiveNow()` (mutual +
  <15 min); `primaryBuddyView` selects the live buddy.
- [x] 6.2 Tap avatar → name + "updated N min ago". _(Req 5.3, 5.7)_
  Marker title = name, snippet = `buddyUpdatedLabel` (JustNow / N min ago).
- [x] 6.3 Eye "eyes-on-you" indicator. _(Req 11)_
  Bottom-center 👁 badge when `hasWatchers`; taps open options pane; has
  contentDescription; distinct from top-center 📡 broadcast badge.
- [x] 6.4 Buddy breadcrumb trail / history. _(Req 5b)_
  Trail drawn (dashed polyline in `JogMap`) and now user-invoked: controller has
  `showHistory` + `toggleHistory()` and the map reads `visibleBuddyTrail` (empty unless
  history is on and a buddy is live). A "🧭 Show/Hide history" toggle added to each live
  buddy row. History resets on disable/delete. _(2026-09-29)_

## Phase 7 — Notifications

- [x] 7.1 Store FCM token on the user doc. _(Req 7)_
  `BuddyRepository.refreshFcmToken()` added; `FirebaseBuddyRepository` fetches
  `FirebaseMessaging.getInstance().token` and merges `fcmToken` onto the user doc
  (messaging API stays in the gated `src/firebase`). Called from
  `BuddyLinkController.beginObserving()` (best-effort, non-blocking). NoOp = unavailable.
  _(2026-09-30)_
- [x] 7.2 Notify on link request / accepted / unlink warning. _(Req 7.1–7.4, 4.5)_
  **v1 in-app delivery.** New `BuddyNotifier` posts one-shot notifications on a
  dedicated `joggin_buddy_events` channel (IMPORTANCE_DEFAULT; gated on
  POST_NOTIFICATIONS). `BuddyLinkController` diffs successive Firestore emissions
  (baselines null on first load so no initial-load spam; reset on disable) to fire:
  request-received (new pending), request-accepted (new buddy), unlinked (buddy
  vanished). EN/EL strings added. _(2026-09-30)_
  Note: "declined" isn't separately notified (a declined request just disappears);
  revisit if desired.
- [x] 7.3 Push mechanism — Cloud Function + FirebaseMessagingService. _(design §12)_
  **Built (server-side push).** `firebase-hosting/functions/` holds a Cloud Function
  `onBuddyLinkWrite` (Firestore `onWrite` on `links/{linkId}`, region us-central1) that
  sends **data-only** FCM (`type` = request/accepted/unlinked, `actorName`) to the
  affected party's `fcmToken`, honouring the ≤2/24h `reqCount` cap. Wired into
  `firebase.json` (`functions` block, nodejs20). Client: gated
  `BuddyFirebaseMessagingService` (`src/firebase`) maps the data message to
  `BuddyNotifier` and refreshes the token on rotation. Manifest gating:
  `src/firebase/AndroidManifest.xml` (full copy of main + the FCM `<service>`) is swapped
  in by `build.gradle` only when `firebaseEnabled`, so non-Firebase builds are unaffected.
  Verified via `assembleDebug` (manifest merge OK) + `testDebugUnitTest`. **Pending user
  action:** `firebase deploy --only functions` (requires the Blaze plan). _(2026-09-30)_

## Phase 8 — Privacy, retention, compliance

- [ ] 8.1 Confirm 7-day TTL purge. _(Req 8.1)_
  Code writes `expireAt` on `locations/{appId}` and `.../trail/{autoId}`. TTL policy is
  a console/gcloud setting (not a file) — exact enable commands for both collection
  groups are in `deployment-runbook.md §3`. **Pending user action** (needs project
  access); then confirm purge on-device (§6.12).
- [ ] 8.2 Remove/Block deletes both directions; block prevents re-link. _(Req 8.2, 12.2, 12.3)_
  **PARTIAL:** `removeLink` sets state=removed; `blockUser` records block + removes
  link; rules block reads on removed/blocked and link-create when blocked. Shared-data
  purge relies on TTL rather than explicit deletion on remove/block.
- [x] 8.3 "Delete my Buddy Link data" action. _(Req 8.4)_
  A red "🗑 Delete my Buddy Link data" button added at the bottom of the enabled
  section, confirmed via `BuddyDialog`, wiring `controller.deleteAllData()` (deletes
  backend data + resets identity + disables). _(2026-09-29)_
- [ ] 8.4 Data-safety disclosure + in-app consent copy. _(Req 8.3)_
  In-app consent copy exists (`buddyLinkConsentBody`). Play Store data-safety
  disclosure is external — the exact declaration to enter is drafted in
  `deployment-runbook.md §5`. **Pending user action** (Play Console).

## Phase 9 — Verification

- [ ] 9.1 Two-device manual test (link/offline→pending, mutual gate, avatar stale at
  15 min, eye indicator, on-demand, unlink warning, block, trail, 7-day purge). _(all)_
- [ ] 9.2 Confirm core app unaffected with Firebase misconfigured/offline. _(Req 10)_
  (Architecturally supported via NoOp fallback; not explicitly re-verified.)
- [ ] 9.3 Battery check (~5-min interval; foreground service stable).
- [ ] 9.4 Localization + accessibility pass. _(Req 9, 13)_
- [ ] 9.5 Bump version + archive build. (Currently versionCode 40 / versionName 1.40.)

---

## Remaining work (prioritized, from this audit)
1. ~~Missing UI hooks (Block, Request-now, Delete-my-data, History)~~ — **DONE 2026-09-29**
   (tasks 4.4, 5.4-UI, 6.4, 8.3). Compiles clean (`compileDebugKotlin` BUILD SUCCESSFUL).
   Not yet manually tested on-device.
2. ~~Task 1.2 — fold App ID into Backup/Restore~~ — **DONE 2026-09-29** (backup v2
   envelope). Compiles clean. See test caveat below.
3. ~~Phase 7 — FCM token, in-app notifications, AND server-side push~~ — **DONE
   2026-09-30** (7.1, 7.2, 7.3). Cloud Function + gated `BuddyFirebaseMessagingService`
   built; verified via `assembleDebug` (manifest merge) + `testDebugUnitTest`. Deploy of
   the function is pending user action (Blaze plan) — runbook §8.
4. ~~Task 5.4 tuning — on-demand request rate-limit + notify-vs-silent (G6)~~ — **DONE
   2026-09-30** (2-min per-buddy cooldown; located party notified low-key).
5. ~~Task 4.5 / 4.6 / 4.7~~ — **DONE 2026-09-30** (alert cap, permission-revoked note,
   content descriptions).
6. **ALL Buddy Link code is now complete.** Only **ops / verification (USER action)**
   remains — see `deployment-runbook.md` for turnkey steps. `firebase-hosting/firebase.json`
   wires `firestore.rules` + `storage.rules` + `functions` for `firebase deploy` (valid
   JSON, confirmed). Remaining human steps: upgrade to Blaze, deploy rules + functions,
   enable Firestore TTL on `locations`/`trail` (`expireAt`), enable Anonymous Auth, Play
   data-safety disclosure, two-device Phase 9 testing, version bump.

## Open items to settle during implementation (from design)
- **B4** exact `isAcceptedBuddy`/`isBlocked` security-rule helpers — addressed in `firestore.rules`.
- **B5** on-map eye/broadcast indicator placement — done (top-center 📡 vs bottom-center 👁).
- **B7** Cloud Function push vs. in-app polling — RESOLVED: Cloud Function `onBuddyLinkWrite` (7.3 done).
- **B9** identity portability mechanism — app-owned UUID chosen; backup folding (1.2) DONE (v2).
- **B10** on-demand delivery when broadcaster backgrounded/killed (Phase 5.4) — partially addressed:
  the located party's device runs the foreground `BuddyLocationService` while sharing, so
  it can respond; delivery when that device's app is fully killed is a known limitation.
- **G6** on-demand request: notify the located party or silent — RESOLVED: notify (low-key), Phase 5.4 done.

## Build / test environment caveats (2026-09-29)
- **Production code builds clean.** Verified with `gradlew.bat --rerun-tasks
  compileDebugKotlin` (Firebase source set included) → BUILD SUCCESSFUL.
- **Unit test module fixed & green.** `gradlew.bat testDebugUnitTest` → BUILD SUCCESSFUL,
  7 tests / 0 failures. The previously broken tests were resolved:
  - `RoutePreviewTest.kt` DELETED — it tested a removed mini-map preview feature
    (`buildPreviewPoints`/`MiniMapRenderState` don't exist in production anywhere).
  - `PermissionHelperTest.kt` now passes — added `PermissionHelper` (main source) with
    pure `shouldRequestNotificationPermission` (API≥33) and
    `shouldRequestBackgroundLocationPermission` (API≥29) decisions.
  - `RouteStorageTest.kt` — original + 4 new `BackupData` tests (v2 round-trip, null
    buddyId, legacy v1 array, garbage→null) all pass.
- Terminal quirk: the PowerShell tool echoes commands oddly and reports exit -1 even on
  success; a reused Gradle daemon reports `compileDebugKotlin UP-TO-DATE` after edits, so
  use `--rerun-tasks` (or redirect to a log file) to force/observe a real recompile.
  Running 3+ concurrent gradle processes stalls the daemon — run one build at a time.
- **7.3 push verified via `assembleDebug`** (full APK, so the Firebase manifest overlay
  merge is exercised) + `testDebugUnitTest` → BUILD SUCCESSFUL.
- **Notification/cooldown logic now unit-tested.** The transition + rate-limit decisions
  were extracted from `BuddyLinkController` into a pure `BuddyTransitions` object;
  `BuddyTransitionsTest` covers new/re-request/cap, buddy appeared/vanished, first-
  emission baseline, and cooldown (17 tests). Full suite = 24 tests, 0 failures.
- **⚠ Two manifests must stay in sync.** `app/src/main/AndroidManifest.xml` (base) and
  `app/src/firebase/AndroidManifest.xml` (overlay = base + FCM `<service>`; swapped in by
  build.gradle when `firebaseEnabled`). AGP can't merge a second manifest into `main`
  without product flavors, so the overlay is a full copy by necessity. A PostFileSave
  hook (`.kiro/hooks/manifest-sync-reminder.json`) reminds on edits. If you ever add a
  product flavor, prefer moving the FCM service into a `firebase` flavor manifest to kill
  the duplication.
