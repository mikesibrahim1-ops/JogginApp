# Release notes — V1.54

Version: **1.54** (versionCode 54). Tag `V1.54`. Successor to V1.53.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.54 — achievement alerts, earned-only medals, tidier Buddy section`

```
🏅 Achievement alerts, a cleaner medals wall, and a tidier Buddy Link section
• You now get a notification the moment you earn an achievement (fires once per achievement)
• The earned/medals section shows only achievements you've actually earned — no more "almost there" clutter
• Buddy Link section tidied up: your buddy code is hidden behind a "Show Buddy Code" pill, add-by-code is tucked away, and the actions are now "Send Buddy Invite" and "Add Buddy"
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
Copy-Item builds\joggin-v1.54.apk builds\joggin.apk -Force

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.54 builds\joggin.apk -t "Joggin V1.54 — achievement alerts, earned-only medals, tidier Buddy section" -F .kiro\specs\release-notes-v1.54.md
```

The install page (`https://joggin-a69a7.web.app/get`) and the in-app update check both
point at `releases/latest/download/joggin.apk`, so publishing `V1.54` with the
`joggin.apk` asset is what offers the update to V1.53 users.

---

## Internal changelog (since V1.53)

### Achievement-earned notifications
- The user previously got no notification when completing an achievement. Now a
  notification fires the moment an achievement is earned.
- The alert fires exactly **once**, on the not-earned → earned transition, so a given
  achievement never re-notifies on subsequent recomputes or app restarts.

### Earned-only medals section
- The earned/medals section now shows **only** achievements that have actually been
  earned. Previously it also surfaced close-but-not-yet-earned medals; those are no
  longer shown in the earned section.

### Buddy Link section tidy
- The buddy code is hidden by default and only revealed when the user double-taps the
  **"Show Buddy Code"** pill (a small pill box makes it obvious it's tappable; double-
  tapping again hides it).
- The "enter a friend's code" (add-by-code) UI is tucked away to save space.
- "Keep Buddy Link" toggle acts as the section header, followed by the share-my-router
  part.
- Button renames: **"Share" → "Send Buddy Invite"** and **"Connect" → "Add Buddy"**.

### Build
- `app/build.gradle` version bump 53 → 54.

### Verification
- `assembleDebug` + `testDebugUnitTest` → BUILD SUCCESSFUL.
- Verified clean launch on device (no VerifyError, process stayed alive).
