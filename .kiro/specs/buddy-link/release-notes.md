# Release notes — Buddy Link update

Target version: **1.41** (versionCode 41) — bump `app/build.gradle` before release
(currently 1.40 / 40). Adjust the number below if you pick a different one.

---

## Play Store "What's new" (user-facing, short)

Use ONE of these. Play caps this field at 500 characters.

### Option A — concise
```
Introducing Buddy Link 🛰️ — share your live location with a trusted running buddy.
• Invite a buddy with a link; both of you opt in
• See each other on the map while you jog, with a clear "eyes on you" indicator
• Ask a buddy for their location on demand
• Full control: remove, block, or delete your data anytime
Location sharing is off by default and auto-clears after 7 days.
```

### Option B — friendly
```
Jog safer with Buddy Link 🛰️
Share your live location with a running buddy you trust. Send an invite, both opt in,
and see each other on the map while you're out. A clear indicator shows when a buddy
can see you, and you can request a location any time. Turn it off, block, or delete
your data whenever you want — sharing is off by default and clears after 7 days.
```

Also update the theme note if shipping the theme fix in the same release:
```
• New default theme: Midnight Pulse, plus a polished Sunrise Energy options panel
```

---

## Internal changelog (full, for the team / commit / tag)

### Added — Buddy Link (opt-in live location sharing with a buddy)
- **Invite & link:** share a `joggin://buddy/...` invite (with an https fallback page);
  1:1 mutual linking with pending/accept/decline. Invites expire after 7 days.
- **Live map:** buddy avatar on the home map when mutually sharing and the fix is fresh
  (<15 min); tap for name + "updated N min ago"; buddy breadcrumb **History** toggle.
- **"Eyes on you" indicator:** shows when a buddy can currently see you.
- **On-demand:** "Request location now" (rate-limited to once / 2 min per buddy); the
  located party gets a low-key "location shared" notice.
- **Notifications:** link request received / accepted / unlinked. In-app while the app
  is open; **server push** (Cloud Function + FCM) when it's backgrounded/killed, with
  de-duplication so you never get both.
- **Privacy & control:** off by default; consent dialog + background-location permission
  on first enable; Remove, Block/Unblock, and "Delete my Buddy Link data"; 7-day TTL
  auto-purge of location + trail; Firestore security rules enforce mutual-sharing +
  block checks.
- **Identity portability:** the anonymous Buddy Link ID is folded into Backup/Restore
  (backup format v2) so it survives reinstall; legacy v1 backups still restore.
- **Localization & a11y:** full English + Greek strings and content descriptions on the
  Buddy Link controls.

### Changed — Theme (if bundled in this release)
- Default theme is now **Midnight Pulse** (was Forest Trail as the fallback).
- **Sunrise Energy** options/config panel now uses a warm orange-derived color instead
  of the clashing navy.

### Fixed / internal
- Repaired the unit-test module (removed an obsolete mini-map preview test; added
  `PermissionHelper` + tests). Added `BuddyTransitions` with 17 unit tests for the
  notification/cooldown logic. Full suite: 24 tests, green.

### Notes / requires ops before this is fully live
- Requires the Firebase deploy steps in `deployment-runbook.md`: deploy rules + storage
  + the push Cloud Function (Blaze plan), enable the Firestore TTL policy and Anonymous
  Auth, and complete the Play data-safety disclosure (`play-data-safety.md`).
- If the backend isn't configured, the app still runs normally with Buddy Link inert.

---

## Suggested git tag / commit message
```
Release 1.41 — Buddy Link (opt-in live location sharing) + theme default/panel fix

Adds the full Buddy Link feature: invite-link linking, live buddy map, eyes-on-you
indicator, on-demand requests, notifications (in-app + FCM server push), privacy
controls (remove/block/delete, 7-day TTL), and backup/restore identity. Also sets the
default theme to Midnight Pulse and fixes the Sunrise options-panel color.

Requires Firebase deploy + TTL/Auth config + Play data-safety (see
.kiro/specs/buddy-link/deployment-runbook.md).
```
