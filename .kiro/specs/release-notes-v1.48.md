# Release notes — V1.48

Version: **1.48** (versionCode 48). Tag `V1.48`. Successor to V1.47.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.48 — Buddy Link by code, Accept/Ignore invites`

```
🤝 Buddy Link now works by code
• Every account has its own Buddy code — find it in Options → Buddy Link
• Tap Copy to grab your code, or Share to send it to a friend
• To add a friend, paste their code and tap Connect — no website, no download redirect

🔔 Invites you can act on
• When someone connects with you, you get a "<name> wants to buddy up" notification
• Accept or Ignore right from the notification — no need to open the app first
• Accepting pairs you both up instantly

👥 Buddies by name, with "Member since"
• Connected buddies show by their name instead of a cryptic id
• See your own "Member since" date next to your code, and your buddies' where available
```

---

## How to publish (CLI, gh keyring auth)

```
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.48 builds\joggin.apk -t "Joggin V1.48 — Buddy Link by code, Accept/Ignore invites" -F .kiro\specs\release-notes-v1.48.md
```

The install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published. The in-app
update check also points at `releases/latest/download/joggin.apk`, so publishing the
release with that asset name is what makes "Check for updates" offer the new build.

> No Firebase Hosting change is required for this release. The deep-link invite path
> and its `/.well-known/assetlinks.json` are unchanged and still work.

---

## Internal changelog (since V1.47)

### Added — code-based Buddy Link invite flow
- The "code" IS the existing `BuddyIdentity.appId` (a per-install UUID with dashes
  removed → 32 lowercase hex chars). There is NO separate code and NO `usernames/{name}`
  registry collection — the investigation's username-registry idea was superseded.
- Buddy section now shows the LOCAL user's own code formatted in 4-char chunks with
  Copy (copies the raw appId) and Share (optional, `ACTION_SEND` plain text) buttons.
- A text field + Connect button lets the sender paste a friend's code. The entry is
  normalized (strip spaces/dashes, lowercase) and format-validated (32 hex) client-side,
  self-invite is blocked, then `requestLink(targetCode, fromName = myDisplayName)` runs.
  Results surface inline: invite sent / invalid code / unknown code / already connected /
  own code. The live repo does a best-effort existence check (NOT_FOUND for unknown).
- `requestLink` extended to write `fromName` (sender displayName) on the `links/{linkId}`
  doc, denormalized so the recipient can show the name before the users doc is readable.

### Added — actionable recipient notification (no server, no Blaze)
- `BuddyNotifier.notifyRequestReceived(context, linkId, fromName)` now adds Accept /
  Ignore `NotificationCompat` action buttons (explicit-intent broadcast PendingIntents,
  `FLAG_IMMUTABLE`). Title uses `fromName` ("<name> wants to buddy up"), short-code
  fallback when blank.
- New `BuddyInviteActionReceiver` (`BroadcastReceiver`) handles the two actions off the
  main thread via `goAsync()` + a `Dispatchers.IO` coroutine, calling
  `acceptLink` / `declineLink`, cancelling the notification, and always finishing the
  pending result. Registered `android:exported="false"` in BOTH manifests (main +
  firebase overlay), kept in sync.
- The in-app `BuddyInviteDialog` path and the `AppForeground.isForeground` de-dupe are
  preserved, so an invite arriving in the foreground does not double-fire.

### Added — "Member since"
- `createdAt` written to `users/{appId}` create-if-absent (read first; never clobbered)
  and mapped onto `BuddyUser.createdAt`. The local user's own "member since" is stored
  in `jog_prefs` key `buddy_created_at` (create-if-absent) for a stable, read-free date;
  buddies show it where their user doc is readable.

### Backward compatibility
- Deep-link handling (`joggin://buddy` + https `/buddy`) and `BuddyInvite.parse` are
  untouched; the code flow is simply the prominent default. The share-sheet became the
  optional "share my code" button.

### Firestore rules — NO change
- Verified `firebase-hosting/firestore.rules`: the `links` create/update and `users`
  create/update rules do NOT whitelist a fixed field set, so the new `fromName` and
  `createdAt` fields are already permitted. No `usernames` collection added. No
  `firebase deploy --only firestore:rules` required.

### Not included / unchanged
- No Cloud Function / Blaze involvement (the undeployed `onBuddyLinkWrite` is unused).
- No new dependency (firebase-messaging, androidx.core NotificationCompat, coroutines
  already present).
- New pure logic (`formatBuddyCode` / `normalizeBuddyCode` / `isValidBuddyCode`) covered
  by `BuddyCodeTest.kt` (JVM, no Android deps).
  Files: `BuddyModels.kt`, `BuddyRepository.kt`, `NoOpBuddyRepository.kt`,
  `FirebaseBuddyRepository.kt`, `BuddyTransitions.kt`, `BuddyLinkController.kt`,
  `BuddyNotifier.kt`, `BuddyInviteActionReceiver.kt` (new), `BuddyLinkSection.kt`,
  `Strings.kt` (EN+EL), `AndroidManifest.xml` (main + firebase), `app/build.gradle`
  (version bump), `BuddyCodeTest.kt` (new).
