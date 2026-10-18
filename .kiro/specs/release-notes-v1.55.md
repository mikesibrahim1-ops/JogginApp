# Release notes — V1.55

Version: **1.55** (versionCode 55). Tag `V1.55`. Successor to V1.54.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.55 — grouped Earned/Outstanding achievements`

```
🏅 A tidier medals wall
• The Achievements grid is now split into two clearly-headed sections — "Earned" and "Outstanding" — so the medals you've earned and the ones you're still working toward are no longer mixed together.
```

---

## How to publish

> No Firestore rules change this release — do NOT deploy `firestore:rules`.
> No hosting/assetlinks change either.

```
# build
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain

# archive + make the lowercase release copy
.\archive-build.ps1
Copy-Item builds\joggin-v1.55.apk builds\joggin.apk -Force

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.55 builds\joggin.apk -t "Joggin V1.55 — grouped Earned/Outstanding achievements" -F .kiro\specs\release-notes-v1.55.md
```

The install page (`https://joggin-a69a7.web.app/get`) and the in-app update check both
point at `releases/latest/download/joggin.apk`, so publishing `V1.55` with the
`joggin.apk` asset is what offers the update to V1.54 users.

---

## Internal changelog (since V1.54)

### Grouped Earned/Outstanding achievements grid
- The Achievements grid is now partitioned into two visually-headed sections:
  **"Earned"** (medals with an earned date) followed by **"Outstanding"** (still in
  progress). Previously earned and in-progress medals were intermixed in a single grid.
- Section headers span the full grid width; in-group sort order is preserved from the
  already-sorted list. A section is omitted when it has no items.
- No other changes this release (no changes to achievement evaluation, notifications,
  medals showcase, or Buddy Link).

### Build
- `app/build.gradle` version bump 54 → 55.

### Verification
- `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL.
- Verified clean launch on device (no VerifyError, process stayed alive — pidof returned a PID).
