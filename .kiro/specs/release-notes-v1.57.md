# Release notes — V1.57

Version: **1.57** (versionCode 57). Tag `V1.57`. Successor to V1.56.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.57 — buddies appear without needing a refresh`

```
🛰️ Buddies now show up on the map and in the list on their own
• Fixed buddies not appearing until you reopened the app or swiped to Achievements and back
• The live-status check is time-based and Compose wasn't re-evaluating it on its own
• Added a 3s liveness tick so the map and buddy list refresh reactively when a buddy comes online
```

---

## How to publish

```
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
.\archive-build.ps1
Copy-Item builds\joggin-v1.57.apk builds\joggin.apk -Force
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.57 builds\joggin.apk -t "Joggin V1.57 — buddies appear without needing a refresh" -F .kiro\specs\release-notes-v1.57.md
```

No Firestore rules deploy (no rules change).

---

## Internal changelog (since V1.56)

### Buddies not appearing until a forced recompose — reactive liveness tick
With Buddy Link on across two devices (both online), the other buddy did not appear on
the live map or in the buddy list until the user forced a recomposition — closing and
reopening the app, or swiping to the Achievements page and back.

Root cause: `BuddyLinkController.buddies` is a proper Compose `mutableStateOf`, but the
UI's "show this buddy?" decision is a **time-based derivation** —
`BuddyView.isLiveNow()` = `user.sharing && (now - location.at) <= STALE_MS`
(`BuddyModels.kt`). Compose can't observe wall-clock time passing, and the `sharing`
flag (user doc) and the `location` (location doc) arrive on separate Firestore
emissions. So the first recomposition after a buddy connected often evaluated
`isLiveNow() == false`, and nothing scheduled a re-check — except a coarse 15s tick
that covered only the map, and nothing for the buddy list.

Fix (the investigation report's Option 2): a controller-hoisted liveness tick.
`BuddyLinkController` now holds `var livenessTick by mutableStateOf(...)` (a
current-millis snapshot) bumped every 3s by a coroutine started alongside the Firestore
listeners in `beginObserving()` and cancelled in `disable()`/`dispose()` (same
lifecycle — no leak). `primaryBuddyView` reads the tick (so the map re-evaluates), and
a new `isBuddyLive(view)` helper reads it for the buddy-list rows (status label +
secondary action row). The old 15s `buddyTick` hack in `MainActivity.kt` was removed —
no duplicate tickers. The map and list now refresh within ~3s of a buddy going live,
with no swipe or restart.

No change to Firestore rules, the repository listener structure, buddy enable/consent
logic, or anything else — minimal reactive fix only.

### Files
`app/src/main/java/com/example/joggingapp/BuddyLinkController.kt`,
`app/src/main/java/com/example/joggingapp/MainActivity.kt`,
`app/src/main/java/com/example/joggingapp/BuddyLinkSection.kt`,
`app/build.gradle` (56 → 57).
