# Release notes — V1.47

Version: **1.47** (versionCode 47). Tag `V1.47`. Successor to V1.46.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.47 — Buddy invites open the app, in-app update check`

```
🤝 Buddy Link invites that open the app
• Tapping a buddy invite now opens Joggin directly instead of just the website
• Invites carry both a one-tap "open the app" link and a web link for people who don't have Joggin yet
• The invite web page now uses a reliable Android open method with an install fallback

🔄 Check for updates — built in
• Joggin now checks for a newer version automatically on launch (about once a day)
• If an update is ready you'll see a simple "Update available" prompt with a one-tap Update button
• You can also check any time: Options → App → Check for updates, which shows your current version
• Update downloads the latest APK directly; no store account needed
```

---

## How to publish (CLI, gh keyring auth)

```
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.47 builds\joggin.apk -t "Joggin V1.47 — Buddy invites open the app, in-app update check" -F .kiro\specs\release-notes-v1.47.md
```

The install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published. The in-app
update check also points at `releases/latest/download/joggin.apk`, so publishing the
release with that asset name is what makes "Check for updates" offer the new build.

> After merge, the orchestrator runs `firebase deploy --only hosting` so the new
> `/.well-known/assetlinks.json` (served via a Hosting rewrite from
> `public/well-known/assetlinks.json`) goes live — this is what lets Android verify the
> https invite link as a real App Link.

---

## Internal changelog (since V1.46)

### Fixed — Buddy Link invites now open the app (belt-and-suspenders)
- Root cause (per `.agents/tasks/buddy-deeplink-investigation.md`): the share path sent only
  the https fallback link, whose auto-open depended on Android App Links verification, which
  was doubly broken — no `android:autoVerify="true"` on the https intent-filter and the live
  `/.well-known/assetlinks.json` returned `[]`. The `/buddy` page then relied on an
  un-gestured `joggin://` redirect that modern/in-app browsers block.
- `BuddyInvite.buildShareText` now includes BOTH links: the custom-scheme
  `joggin://buddy/v1/<id>?exp=<ms>` (reliable one-tap opener, no verification needed) AND the
  https link (kept as the tappable "don't have the app?" entry point). The message is
  localized via a new `buddyInviteShareBody` string (EN + EL).
- `firebase-hosting/public/buddy/index.html` now opens the app from an explicit button tap
  using an Android `intent://…;scheme=joggin;package=com.example.joggingapp;` URI with
  `S.browser_fallback_url` → `/get`, with the raw `joggin://` scheme as a secondary attempt.
- True App Links enabled: `android:autoVerify="true"` added to the https intent-filter in BOTH
  `app/src/main/AndroidManifest.xml` and `app/src/firebase/AndroidManifest.xml` (kept in sync),
  plus a real Digital Asset Links file for `com.example.joggingapp` with the debug cert SHA-256.
- Hosting gotcha handled: the file is stored at the NON-dotfile path
  `firebase-hosting/public/well-known/assetlinks.json` (so the `"**/.*"` ignore glob can't drop
  it) and served at `/.well-known/assetlinks.json` via a Hosting rewrite + an `application/json`
  Content-Type header.
  Files: `BuddyInvite.kt`, `BuddyLinkController.kt`, `BuddyLinkSection.kt`, `Strings.kt` (EN+EL),
  `AndroidManifest.xml` (main + firebase), `firebase.json`, `public/buddy/index.html`,
  `public/well-known/assetlinks.json`.

### Added — in-app update check (GitHub Releases based)
- New `UpdateChecker` object. Auto-checks on launch, throttled to ~once/day via
  `update_last_check_ms` in SharedPreferences `jog_prefs`; a newer version shows a dismissable
  "Update available" prompt (`UpdatePromptDialog`) with Update / Later actions.
- New collapsible **App** section in the Options pane (above Buddy Link): shows
  "Version 1.47" and a non-throttled "Check for updates" button with an inline
  "You're up to date" / "Version X.Y available" (+ Update) / "Couldn't check for updates" result.
- Queries `GET https://api.github.com/repos/mikesibrahim1-ops/JogginApp/releases/latest`
  (`Accept: application/vnd.github+json`) off the main thread via `HttpURLConnection` on
  `Dispatchers.IO` (no new dependency), short 6–7s timeouts. Parse/network/403-rate-limit
  errors fail silently for the auto-check and show a brief message for the manual check.
- Pure `UpdateChecker.isNewerVersion(latest, current)` does numeric dot-wise semver comparison
  (so `1.10 > 1.9`, `2.0 > 1.99`), tolerates a leading `V`/`v`, and returns false for malformed
  input. Covered by new `UpdateCheckerTest.kt` (JVM, no Android deps).
- The Update action opens `releases/latest/download/joggin.apk` via `ACTION_VIEW`, falling back
  to the `/get` install page. No silent/in-app install.
  Files: `UpdateChecker.kt`, `UpdateCheckerTest.kt`, `MainActivity.kt`, `Strings.kt` (EN+EL),
  `app/build.gradle` (version bump).

### Not included / unchanged
- No new runtime permission needed (INTERNET already present; opening a URL needs none).
- On-device App Links verification (`adb shell pm get-app-links com.example.joggingapp`) must be
  confirmed after `firebase deploy --only hosting` publishes the real assetlinks.json.
- No Firebase Functions / Blaze changes.
