# Release notes — V1.51

Version: **1.51** (versionCode 51). Tag `V1.51`. Successor to V1.50.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.51 — Buddy invite opens the app`

```
🔗 Tap a buddy invite and the app opens straight to Accept/Decline
• When a friend shares their Buddy Link invite, tapping it now opens Joggin
  directly to the "wants to be your buddy — Accept / Decline" prompt
• Works from chat apps (WhatsApp, SMS, etc.) — no copy/paste, no browser detour

📨 Friendlier share message
• Sharing your code now sends a short invite with a one-tap link, not a bare code
```

---

## How to publish

Requires BOTH a hosting deploy (for the https link to auto-open the app) AND the release.

```
# build (see .kiro/HANDOVERSESSION.md §3 for the flaky-terminal procedure)
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain

# archive + make the lowercase release copy
.\archive-build.ps1
Copy-Item builds\joggin-v1.51.apk builds\joggin.apk -Force

# deploy hosting so /.well-known/assetlinks.json serves the real statement (not [])
cd firebase-hosting
& "C:\Users\INFINITI\AppData\Roaming\npm\firebase.cmd" deploy --only hosting
cd ..

# publish
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.51 builds\joggin.apk -t "Joggin V1.51 — Buddy invite opens the app" -F .kiro\specs\release-notes-v1.51.md
```

The install page (`https://joggin-a69a7.web.app/get`) and the in-app update check both
point at `releases/latest/download/joggin.apk`, so publishing `V1.51` with the
`joggin.apk` asset is what offers the update to V1.50 users.

> ⚠️ The hosting deploy is REQUIRED this release. Without it, the Android App Links
> verification (`autoVerify`) fails because the server serves an empty `[]` at
> `/.well-known/assetlinks.json`, and the https invite link opens the browser instead of
> the app. The `joggin://` custom-scheme link and the `intent://` web-page fallback open
> the app without the deploy, but true https auto-open needs it.

---

## Internal changelog (since V1.50)

### Buddy Link invite now reliably opens the app → Accept/Decline (routing/delivery fix)
Tapping a shared invite now opens Joggin to the existing Accept/Decline prompt instead of
landing in a browser. No change to the accept/pairing flow; the recipient still taps
Accept (consent preserved — Buddy Link shares live location, so no silent auto-add).

- **Share message leads with the custom-scheme link** `joggin://buddy/v1/<id>?exp=<ms>`
  (opens the installed app directly, no App Links verification needed), with the https
  link kept as the "don't have the app" fallback. EN + EL. Plus a localized share subject
  (`buddyShareSubject`).
- **`android:autoVerify="true"`** added to the https `/buddy` VIEW intent-filter in BOTH
  `app/src/main/AndroidManifest.xml` and `app/src/firebase/AndroidManifest.xml`.
- **Digital Asset Links** published at `firebase-hosting/public/well-known/assetlinks.json`
  (package `com.example.joggingapp`, debug cert SHA-256), served at
  `/.well-known/assetlinks.json` via a `firebase.json` rewrite + `application/json` header
  (dodges the `**/.*` ignore glob). REQUIRES `firebase deploy --only hosting`.
- **`firebase-hosting/public/buddy/index.html`** hardened: opens via an Android `intent://`
  URI with `browser_fallback_url` → the `/get` install page, replacing the unreliable raw
  `joggin://` auto-redirect.

### Rules / backend
- No Firestore rules change, no Cloud Functions change, no new Android permission.

### Files (since V1.50)
`app/src/main/AndroidManifest.xml`, `app/src/firebase/AndroidManifest.xml`,
`firebase-hosting/firebase.json`, `firebase-hosting/public/well-known/assetlinks.json`,
`firebase-hosting/public/buddy/index.html`, `BuddyLinkSection.kt`, `Strings.kt` (EN+EL),
`app/build.gradle` (version bump 50 → 51).

> ⚠️ assetlinks.json carries the DEBUG signing cert's SHA-256 (builds are debug-signed).
> If a release signing key is ever introduced, add its SHA-256 too or App Links break.
