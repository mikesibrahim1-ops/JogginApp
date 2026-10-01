# Buddy Link — Deployment & Verification Runbook

These are the remaining **operational** steps (Firebase console / CLI, Play Console,
physical devices) that finish the Buddy Link feature. The app code is complete and
compiles; these steps require account/device access and must be done by a human.

Firebase project: **`joggin-a69a7`** (from `firebase-hosting/.firebaserc`).
Package: `com.example.joggingapp`.

---

## 1. Deploy Firestore security rules (CONDITION-1, Req 8, 12)

The rules live at repo-root `firestore.rules` and are now wired into
`firebase-hosting/firebase.json` (`firestore.rules` block). Deploy them:

```powershell
# From the firebase-hosting directory (where firebase.json + .firebaserc live):
cd firebase-hosting
firebase deploy --only firestore:rules
```

Prereqs: `npm i -g firebase-tools` and `firebase login` once.
Verify afterward in Console → Firestore → Rules that the published rules match
`firestore.rules` (self-only writes, mutual-sharing + no-block location reads).

## 2. Deploy Storage rules

```powershell
cd firebase-hosting
firebase deploy --only storage
```

(`storage.rules` is now referenced in `firebase.json`.)

## 3. Configure the Firestore TTL policy (7-day purge, Req 8.1)

The app stamps an `expireAt` (Firestore Timestamp) on every write to:
- `locations/{appId}`            (field: `expireAt`)
- `locations/{appId}/trail/{autoId}` (field: `expireAt`)

TTL is NOT a rules-file setting — create a TTL policy per collection. Easiest via
gcloud (collection-group scope covers the subcollection):

```powershell
# Latest-fix docs:
gcloud firestore fields ttls update expireAt --collection-group=locations --project=joggin-a69a7 --enable-ttl

# Sparse trail points:
gcloud firestore fields ttls update expireAt --collection-group=trail --project=joggin-a69a7 --enable-ttl
```

Or Console → Firestore → **TTL** → Create policy: collection group `locations`,
field `expireAt`; repeat for collection group `trail`. Purging begins within ~24h of
the timestamp; it is best-effort, not instantaneous.

## 4. Enable Firebase Auth + services (if not already)

Console → Authentication → Sign-in method → enable **Anonymous**.
Console → Firestore Database → ensure it exists (Native mode).
`google-services.json` is already in `app/` so the client is wired.

## 5. Play Store data-safety disclosure (Req 8.3)

Ready-to-paste answers for the Data Safety form are in **`play-data-safety.md`**
(covers each field: precise location, retention, deletion, security, background-location
justification). Summary:

In Play Console → App content → Data safety, declare **precise location** collection
and sharing for the Buddy Link feature:
- Collected: precise location (shared with linked buddies while sharing is on).
- Purpose: app functionality (location sharing between mutually-linked users).
- Shared with other users; user can request deletion (in-app "Delete my Buddy Link
  data" action). Retention: auto-deleted after 7 days (TTL).
- Encrypted in transit; deletion mechanism provided.
In-app consent copy already exists (`buddyLinkConsentBody`).

## 6. On-device verification (Phase 9)

Needs **two physical devices** (buddy linking is 1:1 mutual). Walk through:

1. Enable Buddy Link on both (consent → background-location permission granted).
2. Device A shares an invite; Device B opens the `joggin://buddy/...` or
   `https://joggin-a69a7.web.app/buddy?...` link → pending request appears on A.
   - Also test the **expired** link path (a link older than 7 days → "invite expired").
3. Accept on A → both show each other as buddies; notifications fire (request received
   / accepted).
4. With both toggled ON + GPS on, confirm the **buddy avatar** appears on the home map
   and updates; let a fix go >15 min stale → avatar goes not-live.
5. Confirm the **eye "eyes-on-you"** indicator shows on the party being watched.
6. **On-demand:** tap "📍 Request location now" → B pushes a fresh fix; B gets the
   low-key "location shared" notification. Tap again immediately → button shows
   "⏳ …please wait" (2-min cooldown).
7. **History:** toggle "🧭 Show history" → buddy trail polyline draws; hide clears it.
8. **Block:** block from A → unlinked both directions; B can't re-link (rules).
9. **Unlink warning:** remove on one side → other side gets "stopped sharing" notice.
10. **Delete my data:** run it → user doc/links/location/trail removed, identity reset,
    toggle OFF.
11. **Backup/restore identity:** back up on A, reinstall, restore → App ID preserved
    (backup format v2); links still resolve. Also restore a legacy v1 (bare-array)
    backup to confirm backward compatibility.
12. **7-day purge:** confirm old `locations`/`trail` docs disappear after the TTL window.
13. **Core-app-unaffected:** temporarily rename `app/google-services.json`, rebuild —
    app still builds/runs using `NoOpBuddyRepository` (Req 10). Restore the file after.
14. **Battery:** confirm the ~5-min broadcast interval + foreground service isn't a drain
    over a longer session.
15. **A11y:** TalkBack pass over the Buddy Link section (toggle state, action labels).
16. **Localization:** switch to Greek, re-check the section + notifications.

## 7. Version bump + archive (Phase 9.5)

Current: `versionCode 40`, `versionName "1.40"` (`app/build.gradle`). Bump before
releasing the Buddy Link build; archive per `archive-build.ps1`.

---

## 8. Deploy the push Cloud Function (task 7.3)

Server-side push is now built: `firebase-hosting/functions/` (Cloud Function
`onBuddyLinkWrite` → data-only FCM) + the client `BuddyFirebaseMessagingService`.

```powershell
cd firebase-hosting/functions
npm install
cd ..
firebase deploy --only functions
```

Prereqs:
- **Cloud Functions requires the Blaze (pay-as-you-go) plan.** Upgrade the project in
  the Console first (there's a generous free tier; typical Buddy Link volume is minimal).
- The function region is `us-central1` (see `functions/index.js`).
- After deploy, verify in Console → Functions that `onBuddyLinkWrite` is listed and
  Cloud Messaging API is enabled.

On-device check: with the app **killed** on device B, have device A send/accept a link
→ B should still receive the notification (routed via `BuddyFirebaseMessagingService`).

Note: in-app vs. push notifications are de-duplicated — the push receiver
(`BuddyFirebaseMessagingService`) suppresses its notification while the app is in the
foreground (tracked by `AppForeground`), since the in-app controller diffing already
surfaces the event then. When the app is backgrounded/killed, only the push path runs.
So confirm the killed-app test on device B specifically.

## Deferred (optional)

- (none outstanding for notifications)
