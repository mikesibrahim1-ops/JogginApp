# Buddy Link — Requirements

## Overview

Buddy Link is a safety feature that lets a JogginApp user link with one or more
trusted people ("buddies") and, with explicit consent on both sides, see a
buddy's location so they can keep track of a friend or family member who may be
in a rough area or out on a solo activity.

The feature lives in a new collapsible section at the bottom of the Options pane.
It is **off by default** and only becomes active when the user turns on a master
toggle. Linking is done by sharing an **invite link** (via WhatsApp or any other
platform) that encodes the user's unique App ID; the recipient opens the link to
initiate the connection. (QR-code linking has been dropped.)

This document defines **what** Buddy Link must do. The **how** (architecture,
data model, screens) is defined in `design.md` after these requirements are agreed.

---

## Foundational decisions (MUST be resolved before design)

Buddy Link is architecturally different from every other feature in JogginApp,
which today is 100% local (SharedPreferences + Gson, a foreground GPS service, no
backend, no accounts). Sharing one person's live location with another person's
device is **impossible without a shared server / cloud service**. The following
decisions gate the rest of the spec. Each has a recommended default.

### D1 — Backend / where location data lives
- **Options:** (a) Firebase (Auth + Firestore/Realtime DB + Cloud Messaging),
  (b) Supabase or another BaaS, (c) self-hosted server, (d) serverless
  "share-on-demand link" only (no continuous tracking).
- **DECIDED:** (a) **Firebase**. Google-managed backend on the free tier. Provides:
  Anonymous Auth (backs the unique App ID), a real-time database (Firestore or
  Realtime Database) to relay buddy locations between phones, and Cloud Messaging
  (FCM) for link-request / accept notifications. The design SHALL isolate all
  Firebase access behind a repository/service interface so the backend could be
  swapped (e.g. self-hosted) in future without rewriting feature logic.
- **Note:** GitHub Pages (static) and a bare web domain cannot host live location
  relay; hence Firebase. The user owns a free Firebase project.
- **Status:** ☑ DECIDED

### D2 — Tracking scope for v1
- **Options:** (a) live continuous location, (b) on-demand snapshot ("where are
  you now?"), (c) only while the buddy is on an active workout.
- **Recommended default:** (a) live continuous location while the buddy has Buddy
  Link enabled, with a battery-conscious update interval.
- **DECIDED:** (a) live continuous location, broadcast on a periodic interval of
  **~5 minutes** (configurable, subject to change), gated by the mutual-on
  condition (see CONDITION-1).
- **Status:** ☑ DECIDED

### D3 — Direction & group size for v1
- **Options:** (a) 1:1 mutual, (b) 1:1 one-directional, (c) many-to-many groups.
- **DECIDED:** **1:1 mutual** for v1 (both linked users see each other; no one-way
  links). Data model and UI to be written so that many-to-many **group** support
  and one-way links can be added in a future version without redesign.
- **Status:** ☑ DECIDED

### D4 — Identity model
- **Options:** (a) real accounts with sign-in (email/phone verified),
  (b) anonymous device-generated unique App ID (no login), (c) hybrid.
- **DECIDED (revised):** (b) anonymous device-generated unique App ID, no login.
  The unique ID is generated **the first time the user enables Buddy Link** (not on
  first app launch), so the app stays offline-first and the existing first-launch
  name-entry flow is untouched. The ID persists for the life of the install.
- **Identity portability:** the App ID (and link state) SHALL be included in the
  app's existing **Backup/Restore** file, so a user can recover their identity and
  buddies on a new device. (Option B — reuses existing backup machinery.)
- **Vanished-buddy rule:** if a buddy has not been seen for longer than the data
  retention window (7 days, see Req 8), the other party SHALL show them as
  "unavailable / link may be lost" rather than a permanently stale position.
- **Status:** ☑ DECIDED

### D5 — Link methods included in v1
- **Options:** QR code, App ID, phone number, email, shareable invite link.
- **DECIDED (revised):** **Invite link only for v1.** The user generates/shares an
  invite link encoding their App ID (e.g. `joggin://buddy/v1/<appId>` behind an
  https fallback), sent via WhatsApp or any platform. The recipient opens it to
  initiate the link. **QR code is dropped.** No phone/email lookup in v1.
- **Rationale:** buddies often are NOT physically together when first linking
  (the use case is tracking someone who is out), so screen-to-screen QR scanning
  doesn't fit; a shareable link works remotely.
- **Status:** ☑ DECIDED

> These defaults are assumed throughout the requirements below and are marked
> `[assumes Dx]` where relevant. If a decision changes, affected requirements are
> revisited.

---

## Fundamental conditions (non-negotiable)

### CONDITION-1 — Mutual-on requirement
No location tracking of any kind SHALL occur unless **BOTH** parties in a link:
- have the Buddy Link master toggle **ON**, AND
- have device **GPS / location enabled** and location permission granted.

If either party has Buddy Link OFF, or either party's GPS is off/unavailable, then
**neither** party's location is shared and neither can locate the other. This is a
hard gate that overrides all viewing/reporting behaviour in Requirements 4, 5, 6.

### CONDITION-2 — Consent-first
A link never shares location until it has been mutually accepted, and either party
can revoke instantly at any time (see Requirement 4).

---

## Requirements

### Requirement 1 — Feature discovery & master toggle

**User story:** As a user, I want a clearly labelled Buddy Link section in the
Options pane that is off until I choose to enable it, so that I stay in control
and the feature never runs without my knowledge.

#### Acceptance Criteria
1. WHEN the user opens the Options pane THEN the system SHALL display a "Buddy
   Link" section as the last section of the pane.
2. The Buddy Link section SHALL be collapsible, collapsed by default, and SHALL
   expand/collapse when its header is tapped.
3. WHEN the section is expanded THEN the system SHALL display a master toggle that
   is OFF by default.
4. WHILE the master toggle is OFF the system SHALL NOT collect, upload, or display
   any location data for Buddy Link, and SHALL NOT run any Buddy Link background
   process.
5. WHEN the user turns the master toggle ON for the first time THEN the system
   SHALL run this ordered first-enable flow: [G3]
   a. Present a **consent explanation** (what is shared, with whom, how to stop) and
      require explicit confirmation. IF the user declines THEN the toggle stays OFF
      and nothing is provisioned.
   b. AFTER consent, provision the anonymous App ID (Req 2.1) and request the
      required **background location permission** (Req 6.6/6b).
   c. IF the user GRANTS the permission THEN Buddy Link activates (toggle ON).
   d. IF the user DENIES it (or grants only foreground/"while using") THEN the
      toggle SHALL return to OFF and the section SHALL show the permission-required
      note (Req 6c). Buddy Link SHALL NOT partially activate. [G3]
6. WHEN the user turns the master toggle OFF THEN the system SHALL immediately stop
   sharing this user's location and stop any Buddy Link background process.

### Requirement 2 — Unique identity

**User story:** As a user, I want a unique ID that others can use to link with me,
so that people can find and connect to me reliably.

#### Acceptance Criteria
1. WHEN the user enables Buddy Link for the first time THEN the system SHALL
   generate a unique App ID for that install (provisioned lazily/silently, not on
   first app launch). [D4]
2. The App ID SHALL persist across app restarts and SHALL NOT change unless the
   user explicitly resets their identity.
3. The App ID and link state SHALL be included in the app's existing Backup/Restore
   file so the user can recover their identity and buddies on a new device. [D4]
4. WHEN the user views the Buddy Link section THEN the system SHALL let the user
   generate and **share an invite link** (encoding their App ID) via the device
   share sheet (WhatsApp or any platform). [D5]
5. IF a future version adds phone/email THEN those SHALL be optional and separately
   consented. [out of scope for v1]

### Requirement 3 — Linking with a buddy (via invite link)

**User story:** As a user, I want to link with a buddy by sending them an invite
link through WhatsApp or any app, so that we can become connected safety buddies
even when we are not physically together.

#### Acceptance Criteria
1. WHEN the user chooses "Link Buddy" / "Invite Buddy" THEN the system SHALL let
   them share an invite link (encoding their App ID) via the device share sheet. [D5]
2. WHEN a recipient opens a valid invite link THEN the system SHALL initiate a link
   request to the App ID encoded in that link.
3. A link SHALL require the other party to accept before any location is shared
   (mutual consent, CONDITION-2). [D3]
4. WHEN a buddy accepts a link request THEN both users SHALL appear in each other's
   buddy list.
5. IF an invite link is invalid, malformed, or the encoded App ID does not exist
   THEN the system SHALL show a clear error and SHALL NOT create a link. [G1]
5a. IF the invited/target buddy is simply **offline or currently unreachable**
   (their app not running, no network, Buddy Link off) THEN this is NOT an error:
   the link request SHALL be created in the **pending** state and delivered when
   they next come online. Linking must work when the two people are not together or
   not simultaneously online (that is the core use case). [G1/C1]
5b. Invite links SHALL carry an **expiry of 7 days** from generation. IF a link is
   opened after it has expired THEN the system SHALL show a "link expired — ask for
   a new invite" message and SHALL NOT create a link. [G2]
6. The system SHALL prevent duplicate links to the same buddy (1:1 for v1: single
   active buddy, re-linkable after removal).
7. WHEN a user receives a link request THEN the system SHALL notify them and allow
   Accept or Decline.
8. A link request SHALL be shown to the recipient once. IF the requester cancels
   and re-requests THEN the request MAY be shown again, but SHALL NOT be shown as a
   new alert more than **twice within any 24-hour period**. After that, the request
   SHALL simply sit in the recipient's **pending list** with Add/Remove controls
   rather than generating further alerts. [point 12]
9. The system SHALL NOT allow linking to a user who has **blocked** the requester
   (see Requirement 12). Blocked requests SHALL be silently refused.

### Requirement 3b — Managing buddies (add / remove)

**User story:** As a user, I want to add and remove buddies from the Buddy Link
section, so that I control who I am linked with at all times.

#### Acceptance Criteria
1. WHEN the Buddy Link section is expanded THEN the system SHALL provide an
   "Invite Buddy" action (shares the invite link per Requirement 3) and SHALL list
   the current buddy (or buddies, when groups are supported).
2. Each listed buddy SHALL show at minimum their name (and person avatar/photo if
   available) and the current sharing status (e.g. "sharing" / "unavailable").
3. The system SHALL show a **pending list** of incoming link requests, each with
   Add (accept) and Remove (decline) controls. [point 12]
4. WHEN the user chooses to remove/unlink a buddy THEN the system SHALL ask for
   confirmation and, on confirm, SHALL remove the link immediately for both
   parties (see Requirement 4.5).
5. WHEN a buddy is removed THEN the buddy's avatar SHALL disappear from the
   home-screen map and no further location SHALL be exchanged in either direction.
6. The system SHALL provide a **Block** action for a buddy or requester (see
   Requirement 12).
7. The invite/add/remove controls SHALL only be available while the master toggle
   is ON.

### Requirement 4 — Consent to be located

**User story:** As a buddy, I want to control whether my location is visible and
to whom, so that I am never tracked without my active, revocable consent.

#### Acceptance Criteria
1. A user's location SHALL only be shared WHILE their master toggle is ON, their
   GPS is on, AND the specific buddy relationship is active AND the buddy also
   satisfies these conditions (CONDITION-1, mutual-on).
2. WHEN a user is sharing location THEN the system SHALL make it clearly visible to
   that user that sharing is active (see Requirement 11, the "eyes on you"
   indicator, and Requirement 6.2, the broadcast indicator).
3. The user SHALL be able to see the list of buddies who can currently locate them.
4. The user SHALL be able to stop being located at any time, taking effect
   immediately, by either (a) turning the **master toggle OFF** (stops sharing with
   ALL buddies), or (b) **unlinking/blocking** a specific buddy. There is no
   separate "pause" control — the master toggle IS the on/off, which keeps the
   mutual-on gate (CONDITION-1) unambiguous. [G4]
5. WHEN a user unlinks a buddy OR turns off the master toggle THEN the other party
   SHALL no longer receive that user's location AND SHALL be shown an on-screen
   warning message that the buddy has unlinked / sharing has stopped. [point 11]

### Requirement 5 — Viewing a buddy's location (home-screen map avatar)

**User story:** As a user, I want to see my linked buddy as an avatar on the
home-screen map, so that I can check at a glance that they are safe without leaving
my main view.

#### Acceptance Criteria
1. WHILE a buddy is actively sharing (CONDITION-1 satisfied for both parties) THE
   system SHALL display that buddy as an avatar marker on the **home-screen map**,
   at the buddy's most recent known location.
2. The buddy avatar SHALL be a **person marker** using the buddy's profile picture
   if available, otherwise a clear person placeholder, and SHALL be visually
   distinct from the user's own route/live-location marker.
3. WHEN the user taps a buddy's avatar on the map THEN the system SHALL show that
   buddy's user name and the age/timestamp of the location ("updated N minutes
   ago"). Tapping again or elsewhere SHALL dismiss the label.
4. WHILE a buddy is sharing THE system SHALL update the avatar's position as new
   location updates arrive (per the ~5-minute broadcast interval, D2).
5. IF a buddy is not currently sharing (their toggle off, their GPS off, unlinked,
   or no recent fix — i.e. CONDITION-1 not satisfied) THEN the system SHALL NOT show
   a live avatar and SHALL, if applicable, indicate the buddy is unavailable rather
   than showing a stale position as if live.
6. IF the user has multiple linked buddies sharing THEN the system SHALL show an
   avatar for each. [1:1 for v1 per D3; requirement written to allow more later]
7. The system SHALL always display the **last-location date/time** for the buddy.
8. A location fix older than **15 minutes** SHALL be treated as **stale**: the
   buddy SHALL be shown as not-currently-live (per 5.5), with the last-known time
   still displayed. [point 5]

### Requirement 5b — Buddy location history / trail

**User story:** As a user, I want to see where my buddy has been recently, not just
their current dot, so I can understand their route like an activity trail.

#### Acceptance Criteria
1. WHILE a buddy is/was sharing THE system SHALL retain a **lightweight breadcrumb
   trail** of the buddy's recent locations within the data-retention window (7
   days, Req 8), and SHALL offer a **history option** to view it.
2. The trail SHALL be **sparse, not a dense track** — the system SHALL store only a
   small number of breadcrumb points (e.g. the periodic ~5-minute fixes, optionally
   thinned) to minimise data. It is NOT a high-resolution route like an activity
   recording.
3. WHEN the user opens a buddy's history THEN the system SHALL draw the buddy's
   breadcrumb trail on a map, showing where they were and where they are now.
4. The trail SHALL be limited to the retention window; points older than 7 days
   SHALL NOT be shown or stored. [point 14]
5. WHEN a buddy is removed OR blocked THEN their stored trail SHALL be deleted.
6. The buddy trail SHALL be visually distinct from the user's own activity routes.

> NOTE: This is a deliberate, bounded exception to "latest fix only." The trail is a
> lightweight breadcrumb kept ONLY within the 7-day retention window and ONLY for
> actively-linked buddies, then purged. Kept sparse to reduce data footprint.

### Requirement 6 — Location reporting (the sharing device)

**User story:** As a sharing user, I want my location to be broadcast periodically
but without ruining my battery, and I want a clear indicator that broadcasting is
on, so the feature is useful, sustainable, and transparent.

#### Acceptance Criteria
1. WHILE Buddy Link sharing is active (master toggle ON AND GPS on — CONDITION-1)
   THE system SHALL broadcast (upload) the user's location approximately **once
   every 5 minutes**. This interval SHALL be a single configurable constant so it
   can be tuned later without redesign.
1b. The system SHALL support an **on-demand "request location now"** action so a
   linked buddy can request a fresh fix between the periodic broadcasts; WHEN a
   request is received AND CONDITION-1 holds THEN the system SHALL broadcast a fresh
   fix promptly. [point 5]
2. WHILE the user is broadcasting THE system SHALL display a clearly visible marker
   / indicator stating that Buddy Link is active and broadcasting is currently ON
   (e.g. an on-screen badge and/or a persistent notification reading "Buddy Link
   active — broadcast on").
3. The Buddy Link reporting process SHALL be separate from the workout GPS
   foreground service, so buddy sharing works independently of doing an activity
   (it must continue whether or not an activity is in progress).
4. IF the device GPS/location is turned off or permission is lost THEN the system
   SHALL stop broadcasting and SHALL reflect that broadcasting is not active
   (CONDITION-1 fails), rather than broadcasting a stale or empty location.
5. WHEN the device has no network THEN the system SHALL skip or queue updates
   gracefully and resume when connectivity returns, without crashing.
6. The system SHALL request and require appropriate location permissions
   (including background location) before background broadcasting can run.
6b. WHEN live Buddy Link is turned ON THE system SHALL require **background
   location usage = enabled**, and background broadcasting SHALL remain active
   until either party turns Buddy Link off. [point 7]
6c. IF background location permission is **denied** (or downgraded to
   foreground-only) THEN Buddy Link SHALL NOT be usable, and the system SHALL show
   an explanatory note in the Buddy Link section (and prompt the user if they had
   it on and then revoke the permission). [point 8]
7. WHEN sharing is stopped (toggle OFF, all buddies unlinked, or GPS off) THEN the
   reporting process SHALL terminate, the "broadcast on" indicator SHALL be
   removed, and no further uploads SHALL occur.
8. NOTE (platform reality): Android Doze/App-Standby may stretch the actual
   background interval when the device is idle; the foreground service is used to
   keep broadcasting as reliable as the OS allows, but exact 5-minute timing is
   best-effort, not guaranteed. [point 7 caveat]

### Requirement 7 — Notifications

**User story:** As a user, I want to be notified about important Buddy Link events,
so I stay aware of link requests and sharing state.

#### Acceptance Criteria
1. WHEN a link request is received THEN the system SHALL notify the user.
2. WHEN a buddy accepts or declines the user's request THEN the system SHALL notify
   the user.
3. WHEN a buddy stops sharing or unlinks THEN the system MAY notify the user.
4. Notifications SHALL respect the device notification permission and SHALL degrade
   gracefully if it is denied.

### Requirement 8 — Privacy, data handling & store compliance

**User story:** As a user, I want my location data handled responsibly, so I can
trust the feature and so the app remains publishable.

#### Acceptance Criteria
1. The system SHALL store the minimum location data needed. Buddy location data
   (latest fix and trail history) SHALL be retained for **no longer than 7 days**
   and older data SHALL be automatically purged. [point 14]
2. WHEN a link is removed THEN the associated shared location data SHALL be deleted
   or rendered inaccessible.
3. The system SHALL present a clear disclosure of location sharing consistent with
   app store (Google Play) location and "tracking another person" policies.
4. The system SHALL provide a way for the user to delete their Buddy Link identity
   and all associated links/data.
5. All location data in transit SHALL be sent over secure (TLS) connections.

### Requirement 9 — Localization & theming

**User story:** As an existing JogginApp user, I want Buddy Link to match the rest
of the app, so it feels native.

#### Acceptance Criteria
1. All Buddy Link user-facing text SHALL be provided in both English and Greek via
   the existing Strings i18n mechanism.
2. Buddy Link UI SHALL use the existing theme tokens (colors, shapes) so it matches
   the current Options pane styling.

### Requirement 10 — Graceful behaviour without backend availability

**User story:** As a user, I want the app to behave sensibly if Buddy Link's
service is unreachable, so the rest of the app is never harmed.

#### Acceptance Criteria
1. IF the Buddy Link backend is unreachable THEN the core app (tracking, history,
   achievements, options) SHALL continue to work normally.
2. WHEN the backend is unreachable THEN Buddy Link SHALL show a clear, non-blocking
   status and SHALL retry appropriately rather than crash.

### Requirement 11 — "Eyes on you" anti-stalking indicator

**User story:** As a user, I want the app to always make it obvious when someone
can see my location, so that Buddy Link can never be used to watch me covertly and
I always know I am being observed.

#### Acceptance Criteria
1. WHILE Buddy Link is ON AND at least one buddy can currently see the user's
   location (CONDITION-1 satisfied) THE system SHALL display a persistent,
   clearly-visible indicator that "someone has eyes on you" — implemented as an
   **eye icon at the bottom of the home-screen map**. This applies whether the link
   is mutual or (in a future version) one-way: whoever is being tracked MUST see
   the indicator. [point 10]
2. WHEN the user taps the eye indicator THEN the system SHALL **navigate to the
   Buddy Link section of the Options pane** (where the user can see who has
   visibility and manage/remove/block them). [point 13]
3. The eye indicator SHALL be distinct from the "broadcast on" indicator
   (Requirement 6.2): broadcasting = "I am sending my location"; eyes = "someone is
   receiving/able to see my location". Both MAY be shown together.
4. WHILE Buddy Link is OFF, or no buddy can currently see the user, THE system SHALL
   NOT show the eye indicator (nothing to disclose).
5. The indicator SHALL NOT be dismissible in a way that hides active observation;
   it SHALL remain visible for as long as someone can see the user.
6. This indicator is a hard anti-stalking safeguard and SHALL NOT be removed or
   suppressed by any setting other than actually stopping the sharing/link.

### Requirement 12 — Blocking a user

**User story:** As a user, I want to block someone so they can never track me or
request to link with me again, so that I am protected from unwanted contact.

#### Acceptance Criteria
1. The system SHALL provide a **Block** action for any current buddy or incoming
   requester. [point 6]
2. WHEN a user blocks someone THEN the system SHALL immediately unlink them, stop
   all location exchange in both directions, and remove that person's avatar/trail
   from the map.
3. WHEN a user blocks someone THEN the system SHALL **delete the blocked person's
   past locations and live location** held for this user, and SHALL prevent the
   blocked person from seeing any of the user's location. [point 6]
4. WHILE a user is blocked THE system SHALL silently refuse any further link
   requests from them (no alert to either party beyond the block confirmation).
5. The system SHALL allow the user to view and **unblock** previously blocked users.

### Requirement 13 — Accessibility

**User story:** As a user relying on assistive technology, I want Buddy Link
controls to be accessible, consistent with the rest of the app.

#### Acceptance Criteria
1. Interactive Buddy Link elements (master toggle, eye indicator, avatar,
   invite/add/remove/block actions) SHALL have meaningful content descriptions.
2. Status indicators (broadcast on, eyes-on-you) SHALL convey their state to
   assistive technology, not by colour/icon alone.

---

## Explicitly out of scope for v1 (candidates for later)

- Many-to-many buddy groups. [see D3]
- One-way (asymmetric) links. [v1 is mutual; see open question 10]
- QR-code linking. [dropped in favour of invite links, D5]
- Phone-number / email lookup and verification. [see D5]
- Geofencing / "arrived home" / "left area" alerts.
- SOS / panic button.
- iOS or web clients (JogginApp is Android-only).

---

## Open questions to confirm

Resolved this round:
- ~~Link method~~ → **invite link** (QR dropped). [D5]
- ~~Buddy display~~ → **person avatar on home map + history trail**. [Req 5, 5b]
- ~~Update interval / staleness~~ → **5 min or on-demand; stale after 15 min; show
  last-location datetime**. [Req 5, 6]
- ~~Unlink silent vs. notify~~ → **on-screen warning to the other party**. [Req 4.5]
- ~~Block~~ → **added, purges past + live location**. [Req 12]
- ~~Background permission~~ → **required when on; denied = feature unusable + note**.
  [Req 6]
- ~~Request spam~~ → **max twice / 24h then sits in pending list**. [Req 3.8]
- ~~Eye-icon tap~~ → **navigates to Buddy Link options section**. [Req 11.2]
- ~~Retention~~ → **7 days max**. [Req 8.1]
- ~~Identity portability~~ → **folded into existing Backup/Restore**. [D4]

Confirmed this round:
1. **Mutual vs. one-way** → **mutual-only for v1** ☑ (one-way deferred to later).
2. **Identity portability** → **fold Buddy ID into the existing Backup/Restore
   file** ☑ (Option B).
3. **SOS/panic** → **out of scope for v1**, kept as a candidate for later ☑.
4. **Buddy trail** → **lightweight breadcrumb** ☑ — store a small number of sparse
   points (not a dense track) to minimise data (see Req 5b).

Gap-check fixes applied:
- **G1/C1 (offline linking)** → an offline/unreachable buddy now creates a
  **pending** link, not an error; only invalid/malformed/nonexistent IDs error.
  [Req 3.5, 3.5a]
- **G2 (invite expiry)** → invite links **expire after 7 days**. [Req 3.5b]
- **G3 (first-enable flow)** → ordered flow defined: **consent → provision ID →
  request background permission → activate only if granted; revert to OFF if
  denied**. [Req 1.5]
- **G4 (pause vs. toggle)** → **removed "pause" as a separate control**; the master
  toggle is the sole on/off, keeping the mutual-on gate unambiguous. [Req 4.4]

No open questions remain. Requirements are ready for tasks.
