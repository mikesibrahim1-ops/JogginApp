# Release notes — V1.53

Version: **1.53** (versionCode 53). Tag `V1.53`. Successor to V1.52.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.53 — launch-crash hotfix`

```
🚑 Fixes a crash on launch in V1.52
• V1.52 crashed immediately on open for all devices (VerifyError)
• No feature or behavior changes — this is a pure stability hotfix
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
Copy-Item builds\joggin-v1.53.apk builds\joggin.apk -Force

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.53 builds\joggin.apk -t "Joggin V1.53 — launch-crash hotfix" -F .kiro\specs\release-notes-v1.53.md
```

The install page (`https://joggin-a69a7.web.app/get`) and the in-app update check both
point at `releases/latest/download/joggin.apk`, so publishing `V1.53` with the
`joggin.apk` asset is what offers the update to V1.52 users.

---

## Internal changelog (since V1.52)

### Launch-crash hotfix (VerifyError)
- V1.52 crashed on launch on every device with an ART `VerifyError`. Root cause: the
  `AppStrings` string-table constructor passed too many arguments in a single method
  invocation, exceeding the ART verifier's per-method register limit (a device's ART
  verifier caps a single method invocation at 16 argument registers; the 226-field
  table forced a mega-invoke well over that cap).
- Fixed by giving the fields defaults and splitting construction into chunked `.copy()`
  calls inside a function so no single generated method invocation passes all fields at
  once.
- No feature or behavior changes. No Firestore rules change.

### Build
- `app/build.gradle` version bump 52 → 53.

### Verification
- `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL.
- Verified clean launch on device (no VerifyError, process stayed alive).
