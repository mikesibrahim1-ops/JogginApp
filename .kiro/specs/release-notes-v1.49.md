# Release notes — V1.49

Version: **1.49** (versionCode 49). Tag `V1.49`. Successor to V1.48.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.49 — Buddy Link connect fixed`

```
🤝 Buddy Link connect by code now works
• Fixed the "permission denied" error when tapping Connect with a friend's code
• Your friend gets the "<name> wants to buddy up" invite and can Accept or Ignore

🔒 Invites must be accepted by the person invited
• Nobody can add you as a buddy without your OK
• Opening a friend's invite link now sends them a request (or connects you straight
  away if they already asked you)

🔄 Buddy Link reconnects by itself
• If Buddy Link couldn't sign in, it now retries automatically when you open the app
  instead of showing "temporarily unavailable" until you turn it off and on again
```

---

## How to publish (CLI, gh keyring auth)

Deploy the Firestore rules FIRST (they must go live together with this APK):

```
cd firebase-hosting
firebase deploy --only firestore:rules
```

Then publish the release:

```
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.49 builds\joggin.apk -t "Joggin V1.49 — Buddy Link connect fixed" -F .kiro\specs\release-notes-v1.49.md
```

The install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published. The in-app
update check also points at `releases/latest/download/joggin.apk`, so publishing the
release with that asset name is what makes "Check for updates" offer the new build.

> No Firebase Hosting change is required. Only the Firestore rules are deployed.

---

## Internal changelog (since V1.48)

### Fixed — Buddy Link PERMISSION_DENIED (two root causes)
1. `FirebaseBuddyRepository.requestLink` / `acceptInvite` read `users/{other}` as an
   existence precheck BEFORE any link existed. The `users` read rule only allows the
   owner or a party to an existing link (`linkedEitherState`), so the precheck was
   denied every time. Both prechecks are removed.
2. Hidden second blocker: both then ran a transaction whose `tx.get(links/{id})` read a
   link doc that did not exist yet. The `links` read rule dereferenced
   `resource.data.a/b`, which errors when `resource == null`, so the read was denied.
   The rule now allows a single-doc `get` of a missing link (reveals nothing).
- Target existence moved server-side: the links create rule requires
  `exists(users/{b})`. A denied create in `requestLink` maps to `NOT_FOUND`, so the UI
  shows "No one found with that code" instead of "temporarily unavailable".
- Refuted (not changed): link-id mismatch, stale local cache, eager listeners.

### Security — links can no longer be forced to accepted
- Create: caller must be side `a`, doc id must equal the sorted pair (no forged ids),
  no self-link, `state == 'pending'` only, and `users/{b}` must exist.
- Update: caller must be a party, `a`/`b` immutable; state may stay the same
  (re-request counter bump), go to `declined`/`removed` (either party), or go to
  `accepted` ONLY from `pending` and ONLY by side `b`.
- `links` read split into `get` (missing doc allowed) and `list` (party only).
  Doc-id wildcard renamed `linkId` → `lid` so it doesn't shadow the `linkId()` function.
- `acceptInvite` (deep-link Accept) no longer creates an accepted link. It accepts an
  existing pending link where I'm side `b` (→ CONNECTED), is a no-op if already
  accepted (→ CONNECTED), else sends a pending request back via `requestLink`
  (→ REQUEST_SENT, "Request sent — <name> needs to accept"). Returns
  `BuddyResult<InviteOutcome>`; `acceptInvite(fromAppId, fromName)`.
- `acceptLink` (in-app prompt + notification action) guards client-side in a
  transaction: only side `b` of a pending link may accept.

### Added — session auto-retry
- `BuddyLinkController.start()`: when enabled and `repo.needsSession()` (Firebase: no
  `auth.currentUser`), re-runs `ensureSession` off the main thread with bounded backoff
  (3 attempts, 2 s then 4 s) BEFORE attaching listeners, logged via `AppLogger`.
  `enableConfirmed()` uses the same retry and now observes after the session is up
  (listeners attached signed-out were denied and silently terminated).
- `observeAvailability()` adds a `FirebaseAuth.AuthStateListener`, so a late sign-in
  flips `backendAvailable` to true.
- `NoOpBuddyRepository.needsSession()` = false → NoOp behavior unchanged.

### Deploy note — rules MUST ship with this APK
- Old V1.48 clients: their `requestLink` precheck still fails, and their
  `acceptInvite` path (which created accepted links) is now rejected by the rules.
  Acceptable, since V1.48 linking never worked.

### Files
`FirebaseBuddyRepository.kt`, `BuddyRepository.kt`, `NoOpBuddyRepository.kt`,
`BuddyLinkController.kt`, `BuddyLinkDecisions.kt` (new), `MainActivity.kt`,
`Strings.kt` (EN+EL), `firestore.rules` + `firebase-hosting/firestore.rules`
(identical), `app/build.gradle` (version bump), `BuddyLinkDecisionsTest.kt` (new).
