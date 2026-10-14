# Release notes — V1.50

Version: **1.50** (versionCode 50). Tag `V1.50`. Successor to V1.49.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.50 — Buddy Link live map + online alerts`

```
🗺️ Buddy Link map now goes live the moment you turn it on
• Turn on Buddy Link and confirm "I understand" — your buddy now shows up on the map
  right away, no need to close and reopen the app

🔔 Get notified when a buddy comes online
• When your buddy starts sharing, you'll see "Your buddy, <name>, is online"
• Tap the notification to jump straight to the map, centered on them

🤝 Re-sending an invite after being declined now works
• If a buddy declined or removed you, re-entering their code sends a fresh request
  instead of silently doing nothing while saying "Request sent"
```

---

## How to publish (CLI, gh keyring auth)

No Firestore rules change in this version — the V1.49 rules already cover everything
here. (If the rules were somehow not deployed, deploy them; otherwise skip.)

Build + archive + publish:

```
# build (see .kiro/HANDOVERSESSION.md §3 for the flaky-terminal procedure)
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain

# archive + make the lowercase release copy
.\archive-build.ps1
Copy-Item builds\joggin-v1.50.apk builds\joggin.apk -Force

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.50 builds\joggin.apk -t "Joggin V1.50 — Buddy Link live map + online alerts" -F .kiro\specs\release-notes-v1.50.md
```

The install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published. The in-app
update check also points at `releases/latest/download/joggin.apk` and compares
`tag_name` to the installed `versionName`, so publishing `V1.50` with the `joggin.apk`
asset is what makes "Check for updates" offer the new build to V1.49 users.

> No Firebase Hosting change is required. No Firestore rules change required.

---

## Internal changelog (since V1.49)

### Fixed — Buddy Link live map did not start until an app restart (`292c501`)
- Enabling Buddy Link + completing the "I understand" consent did not begin the live
  session in-process; the map only populated after a full close + reopen.
- Root cause: `BuddyLinkController.start()` set `started = true` even while disabled,
  and `disable()` left listeners attached, so the session/listener lifecycle was stale;
  the Compose map also never re-evaluated time-based `isLiveNow()`.
- Fix: one idempotent `ensureSession()` (sign-in with bounded retry → attach listeners)
  shared by both `start()` (launch) and `enableConfirmed()` (consent). `disable()` now
  cancels the listener jobs and resets an `observing` flag so a later re-enable
  re-attaches a fresh set. A 15 s UI tick in `JogApp` re-evaluates liveness so the map
  repaints when a buddy appears/goes stale without a restart.

### Added — "buddy online" notification + tap-to-map (`292c501`)
- A local "Your buddy, <name>, is online" notification fires only on a genuine
  offline→online transition. Detection is pure and unit-tested
  (`BuddyTransitions.onlineEvents` / `onlineSnapshot`): the first emission is a silent
  baseline (no burst on launch) and a carried-forward snapshot dedupes, so one alert per
  transition. "Online" uses the same `isLiveNow` notion the map uses (mutual sharing +
  a fresh fix), so a buddy with sharing on but a stale fix is not announced early.
- Reuses the existing buddy-events notification channel.
- Tapping the notification opens the app and centers the map on that buddy:
  `BuddyNotifier` adds `EXTRA_FOCUS_BUDDY_ID` to the open-app PendingIntent;
  `MainActivity.onCreate/onNewIntent` reads it into a new `BuddyFocusBus` (mirrors the
  existing `BuddyInviteBus` deep-link pattern); `JogApp` polls it, jumps the pager to the
  map page, and `JogMap` animates/zooms to the buddy's fix (only when the live primary
  buddy matches the named id).

### Fixed — false "Request sent" when re-sending after a decline/removal (`e4797d3`)
- `requestLink`'s existing-doc branch bumped the re-request counter but kept a
  `declined`/`removed` dead-end state, so the rules-allowed `tx.update` returned Success
  and the UI showed a false "Request sent" while no fresh pending request was created.
- Fix (client-only, no rules change): a `declined`/`removed` link is deleted and a fresh
  `pending` request is recreated (both writes permitted by the deployed V1.49 rules; an
  in-place `declined→pending` reset is forbidden by the update rule). Decision extracted
  as pure `BuddyLinkDecisions.requestLinkAction` with unit tests.

### Rules / backend
- No Firestore rules change. `firestore.rules` + `firebase-hosting/firestore.rules`
  unchanged and still byte-identical. No Cloud Function change (still Spark-plan only).

### Files (since V1.49)
`BuddyLinkController.kt`, `BuddyNotifier.kt`, `BuddyTransitions.kt`, `JogMap.kt`,
`MainActivity.kt`, `Strings.kt` (EN+EL), `BuddyTransitionsTest.kt`,
`FirebaseBuddyRepository.kt`, `BuddyLinkDecisions.kt`, `BuddyLinkDecisionsTest.kt`,
`app/build.gradle` (version bump 49 → 50).
