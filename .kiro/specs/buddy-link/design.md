# Buddy Link — Design (DRAFT v2)

> Status: **DRAFT for review**, updated to match the finalized `requirements.md`.
> Foundational decisions locked: Firebase backend (D1), live ~5-min + on-demand
> tracking (D2), **1:1 mutual** for v1 (D3), anonymous App ID on **first enable**
> (D4), **invite-link** linking — QR dropped (D5). Open sub-decisions are marked
> **[OPEN]**.

---

## 1. Overview

Buddy Link adds consensual, **mutual**, live location sharing between two JogginApp
users, plus a lightweight breadcrumb trail of where a buddy has been. It is the
app's first networked feature; everything else stays local and offline-first (if
Firebase is unavailable, the rest of the app is unaffected — Req 10).

Principles baked into the design:
- **Mutual-on gate (CONDITION-1):** no location flows unless *both* users have the
  toggle ON and GPS on. Enforced client-side AND in Firebase security rules.
- **Consent-first (CONDITION-2):** a link requires explicit acceptance; either side
  revokes instantly.
- **Transparency / anti-stalking (Req 11):** whoever is being tracked always sees
  the eye indicator; tapping it opens the Buddy Link options section.
- **Blocking (Req 12):** a blocked user is unlinked, purged, and can't re-link.
- **Bounded data (Req 8):** latest fix + sparse breadcrumb trail, 7-day max
  retention, auto-purged.
- **Swappable backend:** all Firebase access sits behind a `BuddyRepository`
  interface so the backend can change later without touching feature logic.

---

## 2. High-level architecture

```
┌──────────────────────── Android app (existing) ────────────────────────┐
│  MainActivity / Compose UI                                               │
│    ├─ Home map (JogMap) ─shows─► buddy avatar + trail + eye icon + badge  │
│    └─ Options pane ─hosts─► Buddy Link section (collapsible, last)        │
│                                                                          │
│  BuddyLinkViewModel (state holder)                                       │
│    ├─ observes link state, buddy location, presence, requests            │
│    └─ drives UI + enforces mutual-on gate client-side                    │
│                                                                          │
│  BuddyRepository (interface) → FirebaseBuddyRepository (impl)            │
│  BuddyIdentity (local prefs: App ID, link cache; part of Backup/Restore) │
│  BuddyLocationService (foreground) ─uploads my fix every ~5 min / on-demand│
│  Invite deep link (joggin://buddy/v1/<appId>) handled by MainActivity     │
└──────────────────────────────────────────────────────────────────────┬─┘
                                     TLS                                  ▼
                    ┌──────────────────── Firebase ───────────────────────┐
                    │  Anonymous Auth  (uid == App ID)                     │
                    │  Cloud Firestore:                                    │
                    │    users/{uid}              profile, toggle, presence│
                    │    users/{uid}/blocked/{x}  blocklist                │
                    │    links/{linkId}           pair + accepted state    │
                    │    locations/{uid}          latest fix (7-day TTL)   │
                    │    locations/{uid}/trail/{t} sparse breadcrumbs (TTL)│
                    │  Cloud Messaging (FCM)      request / accept pushes  │
                    │  Security Rules             mutual-consent + block   │
                    │  Scheduled cleanup          purge >7-day data        │
                    └──────────────────────────────────────────────────────┘
```

**Why a ViewModel:** Buddy Link state is async, long-lived, and comes from Firebase
listeners, so it outlives individual composables. A `BuddyLinkViewModel` keeps it
out of `MainActivity`'s `remember` soup. (New pattern for this codebase — flagged
for your approval.)

---

## 3. Backend within Firebase

- **Auth:** Firebase **Anonymous Authentication**, provisioned lazily **on first
  enable** of Buddy Link (D4, Req 2.1). The `uid` *is* the App ID; security rules
  key off `request.auth.uid`.
- **Database:** **Cloud Firestore** for everything. The ~5-min interval means write
  volume is tiny, so Firestore cost is negligible and one product/one rules
  language keeps it simple. **[OPEN B1]** resolved → Firestore-only.
- **Cleanup:** a scheduled **Cloud Function** (or Firestore TTL policy) purges
  `locations` docs and `trail` points older than 7 days (Req 8.1). **[OPEN B8]**
  Firestore native TTL vs. scheduled function — draft: **Firestore TTL policy** on
  an `expireAt` field (no code, built-in), with a function only if finer control is
  needed.

---

## 4. Data model (Firestore)

```
users/{uid}
  displayName:  string          # from existing profile name
  photoRef:     string|null     # buddy avatar source (see §10)
  buddyEnabled: bool            # master toggle
  gpsOn:        bool            # device location-enabled state
  sharing:      bool            # derived: buddyEnabled && gpsOn && recent fix
  fcmToken:     string|null
  updatedAt:    timestamp

users/{uid}/blocked/{blockedUid}     # presence of doc = blocked
  at: timestamp

links/{linkId}                # linkId = sorted("uidA_uidB")  → dedupe (Req 3.6)
  a:         uid              # requester
  b:         uid              # recipient
  state:     "pending"|"accepted"|"declined"|"removed"
  reqCount:  int             # requests in current 24h window (Req 3.8)
  reqWindowStart: timestamp
  createdAt: timestamp
  updatedAt: timestamp

locations/{uid}               # latest fix only, overwritten each broadcast
  lat: number
  lon: number
  at:  timestamp             # fix time → "updated N min ago"; >15 min = stale
  expireAt: timestamp        # now + 7 days → drives TTL purge

locations/{uid}/trail/{autoId}   # sparse breadcrumb points (Req 5b)
  lat: number
  lon: number
  at:  timestamp
  expireAt: timestamp        # now + 7 days → TTL purge
```

Notes:
- **Latest fix + sparse trail** (Req 5b): the trail stores the periodic ~5-min
  fixes (optionally thinned), NOT a dense track. Both carry `expireAt` for the
  7-day TTL (Req 8.1).
- **`linkId`** deterministic (sorted pair) prevents duplicate links (Req 3.6).
- **`blocked` subcollection** backs Req 12; rules consult it to refuse reads/links.
- **1:1 for v1**, but `links` already supports many links per user → groups later
  need only UI/query changes (D3).

---

## 5. Identity, lifecycle & Backup/Restore integration

- **First enable (Req 2.1):** flipping the master toggle ON the first time →
  anonymous sign-in → persist `uid` in prefs. App launch/name-entry untouched
  (offline-first preserved, Req D4/point 9).
- **Backup/Restore (Req 2.3, D4):** the existing JSON backup file gains a
  `buddyIdentity` block (the App ID + minimal link cache). Restoring on a new device
  re-adopts the same App ID so links survive a reinstall/phone change.
  - **[OPEN B9]** Firebase anonymous uid is normally device-bound; to truly restore
    the *same* uid we must either (a) use a **custom token / stored credential**
    approach, or (b) store our OWN app-generated UUID as the App ID and map it to
    the anonymous uid server-side. Draft: **(b)** — the App ID is our own UUID
    (stable, backup-restorable); the Firebase anon uid is just the auth session
    keyed to that App ID in the user doc. This is the clean way to make identity
    portable. **To confirm in tasks.**
- **Reset identity (Req 8.4):** deletes user doc, links, locations, trail; issues a
  fresh App ID.
- **Vanished-buddy rule (D4):** if a buddy's `updatedAt`/last fix is older than 7
  days, show "unavailable / link may be lost" rather than a stale dot.

---

## 6. Linking flow (invite link, mutual consent)

```
User A                              Firebase                        User B
──────                              ────────                        ──────
Buddy Link ▸ "Invite Buddy"
 → share sheet: joggin://buddy/v1/<A.appId>   (WhatsApp/any app)
                                                     B taps the link
                                          MainActivity intent-filter opens it
                                          → create/upsert links/{linkId}
                                            {a:B, b:A, state:"pending", reqCount++}
   ◄──FCM: "B wants to link"───────────────────
 A taps Accept → state:"accepted"
   ──listener──► both see each other in buddy list
 sharing still gated by CONDITION-1 (both toggle ON + GPS on)
```

- **Deep link:** `joggin://buddy/v1/<appId>` with an `https://jogginapp.github.io/
  buddy?id=<appId>` fallback page (so the link is tappable even if the app parses
  the intent). Handled by an `AndroidManifest` intent-filter → `MainActivity`.
- **Request frequency (Req 3.8):** `reqCount`/`reqWindowStart` on the link cap
  *alerts* to twice per 24h; beyond that the request sits in B's **pending list**
  (Add/Remove) with no new push.
- **Block gate (Req 3.9, 12):** if B has A in `blocked`, the request is silently
  refused (rules + client).
- Invalid/unknown/expired link → error, no link (Req 3.5).
- **[OPEN B2 dropped]** (no QR library needed). Instead we need deep-link handling +
  the static fallback page on GitHub Pages.

---

## 7. Mutual-on gate (CONDITION-1) — three-layer enforcement

1. **Broadcaster client:** `BuddyLocationService` only runs / writes `locations`
   while `buddyEnabled` AND device location enabled AND permission granted; if GPS
   off → stop writing, set `sharing=false` (Req 6.4, 6.7).
2. **Viewer client:** show a buddy avatar only when `users/{buddy}.sharing==true`
   AND a fix newer than **15 min** exists AND the local user is enabled (Req 5.5,
   5.8). No stale-as-live.
3. **Security rules:** deny reading `locations/{buddy}` (and `trail`) unless an
   **accepted** link exists AND both users' `sharing==true` AND neither has blocked
   the other. Tamper-resistant, not just UI (see §9).

---

## 8. On-demand "request location now" (Req 6.1b)

- Viewer taps "Request location" on a buddy → writes a `locReq` flag/timestamp
  (e.g. on the `links` doc or a `requests/{uid}` doc).
- Broadcaster's app/service observes it and, IF CONDITION-1 holds, pushes a fresh
  fix promptly (outside the 5-min cadence).
- **[OPEN B10]** delivery when broadcaster's app is backgrounded: the foreground
  service can react if running; if fully killed, an FCM "data" message can wake it.
  Draft: foreground-service reaction for v1; FCM-wake as enhancement.

---

## 9. Security rules (sketch)

```
match /users/{uid} {
  allow read:  if request.auth.uid == uid || isAcceptedBuddy(uid);
  allow write: if request.auth.uid == uid;
  match /blocked/{b} { allow read, write: if request.auth.uid == uid; }
}
match /links/{linkId} {
  allow read, update: if request.auth.uid in [resource.data.a, resource.data.b];
  allow create: if request.auth.uid == request.resource.data.a
                && !isBlocked(request.resource.data.b, request.auth.uid);
}
match /locations/{uid} {
  allow write: if request.auth.uid == uid;
  allow read:  if isAcceptedBuddy(uid)
               && get(users/$(uid)).data.sharing == true
               && get(users/$(request.auth.uid)).data.sharing == true
               && !isBlocked(uid, request.auth.uid)
               && !isBlocked(request.auth.uid, uid);
  match /trail/{t} { allow read: if <same condition as parent read>;
                     allow write: if request.auth.uid == uid; }
}
```
- **[OPEN B4]** `isAcceptedBuddy` / `isBlocked` implementation. Rules can't run
  arbitrary queries, so store a small `buddies` map and check `blocked/{uid}` via
  `exists()`. Finalize in tasks.

---

## 10. UI design

### 10.1 Options pane — Buddy Link section (collapsible, last, collapsed by default)
- **Master toggle** (OFF default). First enable → consent dialog (Req 1.5) +
  background-location permission request (Req 6.6, 6b).
- **Invite Buddy** → share sheet with the invite link (Req 2.4, 3.1).
- **Buddy list**: the linked buddy — person avatar + name + status ("sharing" /
  "unavailable") + **History** (trail), **Remove**, **Block** actions (Req 3b, 12).
- **Pending list**: incoming requests with Add / Remove (Req 3b.3, 3.8).
- **Blocked list**: view / unblock (Req 12.5).
- **Permission-denied note** if background location is denied/downgraded (Req 6c).
- Non-blocking **backend-unreachable** status (Req 10.2).
- Built on the shared collapsible-section pattern (matches the pane refactor).

### 10.2 Home-screen map — avatar, trail, indicators
- **Buddy avatar** (person marker; photo or placeholder) at latest fix, distinct
  from the user's marker (Req 5.1–5.2). Tap → name + "updated N min ago" (Req 5.3).
- **Buddy breadcrumb trail** drawn when the user opens history (Req 5b), visually
  distinct from own routes.
- **"Broadcast on" badge** while this device broadcasts (Req 6.2).
- **Eye icon** (bottom of map) whenever someone can see the user; **tap → navigates
  to the Buddy Link options section** (Req 11.2); non-dismissible while observed.
- **[OPEN B5]** placement: bottom-right = focus button, bottom-left = OSM
  attribution. Draft: **eye icon bottom-center**, broadcast badge top-center.

---

## 11. Location reporting service

- **New `BuddyLocationService`** (foreground), separate from
  `ForegroundLocationService` (Req 6.3) so it runs regardless of an active workout.
- Interval constant `BUDDY_BROADCAST_INTERVAL_MS ≈ 5 min` (Req 6.1); handles
  on-demand requests (§8).
- Requires background location; **feature unusable if denied** (Req 6b, 6c).
- Writes latest fix + appends a sparse trail point; sets `sharing`.
- Graceful no-network (skip/queue, resume), no crash (Req 6.5).
- Foreground notification carries the "broadcast on" disclosure (Req 6.2).
- Doze caveat documented (Req 6.8): timing is best-effort.
- **[OPEN B6]** one vs. two foreground services during a workout. Draft: two,
  separate, for isolation.

---

## 12. Notifications (FCM)

- `fcmToken` on user doc. Triggers: request received (7.1), accepted/declined
  (7.2), optional stop-sharing/unlink warning to the other party (7.3, Req 4.5).
- **[OPEN B7]** push requires a **Cloud Function** triggered by `links` writes
  (recommended) vs. in-app polling (simpler, less timely). Draft: **Cloud
  Function**; polling fallback acceptable for a first cut.

---

## 13. Blocking (Req 12)

- Block → write `users/{me}/blocked/{them}`, set link `state:"removed"`, delete my
  copy of their location/trail, and rely on rules so they can't read mine or re-link
  (§9). Unblock → delete the blocked doc. Silent to the blocked party (Req 12.4).

---

## 14. Privacy, data handling, compliance (Req 8)

- Latest fix + sparse trail only; **7-day TTL** auto-purge (8.1). Remove/Block →
  delete shared data (8.2, 12.3). Delete-my-data action (8.4). TLS via SDK (8.5).
- Consensual/mutual/revocable + visible eye indicator → keeps the feature on the
  right side of Play Store "location sharing / tracking another person" policy (8.3).

---

## 15. Error handling & offline (Req 10)

- All Firebase access wrapped; failures surface as non-blocking status, never
  crash. Core app never depends on Firebase. SDK auto-retries; UI shows "Buddy Link
  unavailable" offline.

---

## 16. Accessibility (Req 13)

- Content descriptions on toggle, eye indicator, avatar, invite/add/remove/block.
- Broadcast/eyes state conveyed to assistive tech, not colour/icon alone.

---

## 17. Dependencies & project changes (heads-up for tasks)

- Firebase: `google-services.json`, Google Services Gradle plugin, Firebase BoM +
  Auth + Firestore (+ Messaging, + Functions if B7=Cloud Function).
- Deep-link intent-filter in `AndroidManifest` + a static fallback page on
  `jogginapp.github.io`.
- Backup/Restore format bump to include `buddyIdentity` (§5).
- New source files (approx): `BuddyRepository`, `FirebaseBuddyRepository`,
  `BuddyLinkViewModel`, `BuddyIdentity`, `BuddyLocationService`, `BuddyModels`,
  Buddy Link Compose UI + map overlay; Strings (EN/EL); theme reuse.
- One-time user setup: create free Firebase project + download config; add TTL
  policy (guided in tasks).

---

## 18. Open decisions to resolve within tasks

- **B1** Firestore-only. *(resolved: yes)*
- **B4** exact `isAcceptedBuddy`/`isBlocked` rule helpers. *(TBD in tasks)*
- **B5** on-map indicator placement. *(draft: eye bottom-center)*
- **B6** one vs. two foreground services during workout. *(draft: two)*
- **B7** Cloud Function push vs. in-app polling. *(draft: Cloud Function)*
- **B8** Firestore native TTL vs. cleanup function. *(draft: TTL)*
- **B9** identity portability mechanism (own-UUID App ID vs. custom token). *(draft:
  own-UUID App ID mapped to anon uid)*
- **B10** on-demand delivery when broadcaster backgrounded/killed. *(draft:
  foreground-service reaction; FCM-wake later)*

---

## 19. Requirements coverage map

| Requirement | Covered by |
|---|---|
| 1 Toggle & discovery | §10.1 |
| 2 Identity + backup | §3, §5, §10.1 |
| 3 Linking (invite link) | §6, §10.1 |
| 3b Add/remove/pending/block | §10.1, §13 |
| 4 Consent to be located | §6, §7, §12, §14 |
| 5 Viewing buddy (avatar) | §7, §10.2 |
| 5b Breadcrumb trail history | §4, §10.2 |
| 6 Reporting + on-demand + bg | §8, §11 |
| 7 Notifications | §12 |
| 8 Privacy/retention/compliance | §14 |
| 9 Localization/theming | §10, §17 |
| 10 Graceful offline | §15 |
| 11 Eyes-on-you (tap→options) | §7, §10.2 |
| 12 Blocking | §9, §13 |
| 13 Accessibility | §16 |
| CONDITION-1 mutual-on | §7, §9 |
| CONDITION-2 consent-first | §6, §9 |
