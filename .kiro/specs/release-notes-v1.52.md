# Release notes — V1.52

Version: **1.52** (versionCode 52). Tag `V1.52`. Successor to V1.51.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.52 — Route sharing + security hardening`

```
🗺️ Share your live route, not just a ping
• New Buddy sharing mode: choose Ping (your location dot) or Route (your live trail)
• Route sharing is consent-gated — you explicitly turn it on before your trail is shared

🔒 Buddy Link security hardening
• Your account identifiers are now kept in an owner-only area a buddy can't read
• Stricter server-side rules on what each side can write

✨ Polish
• Clearer Copy / Share buttons on the Buddy code (pill-shaped, better contrast)
• Capitalized main-page swipe labels with clearer curved arrows
• Shared route map no longer clipped at the top/bottom
```

---

## How to publish

> ⚠️ **Ship the rules deploy and the APK TOGETHER this release.** The security hardening
> (merged in `963e9a5`) moved `authUid`/`fcmToken` into an owner-only subdoc
> (`users/{appId}/private/self`) and tightened the write whitelists. A NEW client against
> OLD rules — or an OLD client against NEW rules — can mismatch. Deploy
> `firestore:rules` and release the 1.52 APK in the same window.

```
# build
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain

# archive + make the lowercase release copy
.\archive-build.ps1
Copy-Item builds\joggin-v1.52.apk builds\joggin.apk -Force

# deploy the hardened Firestore rules (REQUIRED — inert until deployed)
& "C:\Users\INFINITI\AppData\Roaming\npm\firebase.cmd" deploy --only firestore:rules

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.52 builds\joggin.apk -t "Joggin V1.52 — Route sharing + security hardening" -F .kiro\specs\release-notes-v1.52.md
```

The install page (`https://joggin-a69a7.web.app/get`) and the in-app update check both
point at `releases/latest/download/joggin.apk`, so publishing `V1.52` with the
`joggin.apk` asset is what offers the update to V1.51 users.

> Hosting deploy is NOT required this release (no assetlinks/hosting change since V1.51).
> Only the Firestore rules deploy is required.

---

## Internal changelog (since V1.51)

### Buddy Ping/Route sharing mode (new feature)
- New `BuddyShareMode` enum (PING / ROUTE) with a mode toggle in `BuddyLinkSection`.
- Route mode is consent-gated: `enableRouteMode()` / `disableRouteMode()` route through
  `applyShareMode(BuddyShareMode)` in `BuddyLinkController` (renamed from `setShareMode`
  to avoid a JVM signature clash with the generated setter for `var shareMode`).
- Mode wired through `BuddyRepository` / `FirebaseBuddyRepository` / `NoOpBuddyRepository`
  and `BuddyLocationService`; shared trail rendered in `JogMap`. Adds `BuddyShareModeTest`.
- Commit `a88f6e2`.

### Buddy Link security hardening
Merged in `963e9a5` (design→review then implement→semantic-review loops, APPROVED).
- **F2+F9:** moved `authUid`/`fcmToken` out of the buddy-readable `users/{appId}` into an
  owner-only `users/{appId}/private/self`; `ownsUser` reads authUid from the subdoc;
  `deleteAllMyData` deletes the subdoc; Cloud Function `lookupUser` read-path updated.
- **F3+F5:** additive field whitelists + lat/lon range + type + size caps on
  locations/trail/users; monotonic `reqCount`/`reqWindowStart` on `links` update;
  `createdAt` immutable on users update.
- **F10:** new `locations/{appId}/locReq/{requesterAppId}` subcollection (reuses
  CONDITION-1) — fixes the previously-broken on-demand location request.
- **F7:** `backup_rules.xml` + `data_extraction_rules.xml` exclude `jog_prefs` + `app_log.txt`.
- **F8:** `usesCleartextTraffic=false` (both manifests).
- **F14:** deleted dead non-dotted assetlinks copy.
- All edits ADDITIVE — CONDITION-1 + V1.49 behavior intact. Both rules copies byte-identical.

### UI polish
- Pill-shaped Buddy code Copy/Share buttons with a contrasting border (`ff5b597`,
  preceded by `ff3aef5` making them visible against the pane background).
- Capitalized main-page swipe labels + clearer curved swipe arrows (`d98d07c`).
- Shared route bitmap projection inset so the map isn't clipped top/bottom (`f691e30`).

### Build
- `app/build.gradle` version bump 51 → 52.

### Verification
- `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL (on pwsh 7; the old 5.1
  terminal wedge was fixed this session by installing PowerShell 7.6.7).
- Route-mode detail: `.agents/tasks/buddy-route-mode/verification.md`.
- Security detail: `.agents/tasks/buddy-security-harden/verification.md`.

> ⚠️ assetlinks.json still carries the DEBUG signing cert's SHA-256 (builds are
> debug-signed). A release signing key (F6, deferred) would need its SHA-256 added too.
