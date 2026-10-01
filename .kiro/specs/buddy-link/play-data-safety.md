# Play Store Data Safety — ready-to-enter answers (Buddy Link)

Fill this into Play Console → **App content → Data safety**. Answers reflect the actual
Buddy Link implementation in this repo (Firestore-backed location sharing between
mutually-linked users, 7-day TTL, in-app delete). Review against the whole app before
submitting — this covers the Buddy Link data flows; add anything else the app collects.

---

## Does your app collect or share any of the required user data types?
**Yes.**

## Is all of the user data encrypted in transit?
**Yes** — Firebase SDKs use TLS/HTTPS for all Firestore/Auth/FCM traffic.

## Do you provide a way for users to request that their data be deleted?
**Yes** — in-app "Delete my Buddy Link data" (removes user doc, links, location, trail,
and resets the local identity). Data also auto-expires (see retention below).

---

## Data types

### Location — Precise location
- **Collected:** Yes
- **Shared:** Yes (with the user's mutually-linked "buddies" while sharing is on)
- **Processed ephemerally:** No (stored, but auto-deleted after 7 days via Firestore TTL)
- **Required or optional:** Optional (Buddy Link is off by default; explicit opt-in with
  a consent dialog + background-location permission)
- **Purposes:**
  - App functionality (share live location with a linked buddy for safety while jogging)
- **Collected/shared because of a user action:** Yes (only while the user enables Buddy
  Link and is actively sharing)

### App activity / other (only if applicable)
- The app also stores a **buddy display name** and an **app-generated anonymous ID**
  (a random UUID, NOT tied to a device identifier or account) plus an FCM token for
  push. These are not "personal identifiers" in the Play taxonomy (no name/email/phone
  is required), but if the console asks:
  - **User IDs:** the app uses an app-generated anonymous UUID and Firebase Anonymous
    Auth (no account, no email). Shared with linked buddies only (the display name).
    Purpose: app functionality. Optional.

---

## Retention & deletion (free-text where the console allows)
- Live location + breadcrumb trail auto-delete after **7 days** (Firestore TTL on the
  `expireAt` field of `locations` and `locations/*/trail`).
- Users can delete all Buddy Link data immediately in-app.
- Removing/blocking a buddy stops sharing both directions.

## Security practices
- Encrypted in transit (TLS).
- Firestore security rules restrict location reads to accepted, mutually-sharing,
  non-blocked buddies (see `firestore.rules`).
- No data sold. Sharing is limited to the user's own chosen buddies.

---

## Notes for the reviewer / self-check
- Location sharing is **opt-in**, off by default, gated behind an in-app consent dialog
  (`buddyLinkConsentBody`) and OS background-location permission.
- The app requests `ACCESS_FINE_LOCATION`, `ACCESS_BACKGROUND_LOCATION`,
  `POST_NOTIFICATIONS` (see `AndroidManifest.xml`). Background location must be justified
  in the Play "Location permissions" declaration form — reason: sharing live location
  with a linked buddy while the app is backgrounded during a jog.
- If you have NOT yet enabled the Firestore TTL policy, the "auto-delete after 7 days"
  claim is not yet true in production — enable it first (deployment-runbook.md §3).
