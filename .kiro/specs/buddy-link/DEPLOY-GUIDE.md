# Buddy Link — Step-by-Step Deployment Guide (V1.41)

A follow-along guide for the **user-side** ops that finish Buddy Link. Everything in the
app code / repo is already done (see status box below). These steps need YOUR Firebase
account, console, gcloud, and two physical devices — they can't be done from the IDE.

Work top to bottom. After each `firebase deploy`, paste the output back into chat and
I'll confirm it landed before you move on.

---

## Already done for you (no action needed)
- ✅ **Code complete** — Buddy Link Phases 0–7 built; V1.41 (`versionCode 41`).
- ✅ **Deploy wiring committed + pushed** (`98a5289` on `origin/main`): `firebase.json`
  references `firestore.rules`, `storage.rules`, and `functions/`.
- ✅ **Fresh V1.41 debug APK built** → `builds\joggin.apk` (ready to sideload).
- ✅ **Function deps installed** — `firebase-hosting/functions/node_modules` populated
  (`firebase-admin` + `firebase-functions`). So `firebase deploy --only functions` is a
  single step; you do NOT need to run `npm install` again.

## Facts you'll need
- Firebase project: **`joggin-a69a7`**
- Android package: `com.example.joggingapp`
- Cloud Function: **`onBuddyLinkWrite`**, region **`us-central1`**, trigger = Firestore
  `onWrite` on `links/{linkId}`, sends **data-only** FCM (`type`, `actorName`).
- All `firebase` commands below run from the **`firebase-hosting`** directory.

> **Open a terminal there first:**
> ```powershell
> cd "c:\Users\INFINITI\Documents\Kiro\JogginApp\firebase-hosting"
> firebase projects:list   # sanity check you're logged in; joggin-a69a7 should appear
> ```
> If not logged in: `firebase login`.

---

## STEP 1 — Upgrade the project to the Blaze plan  ⚠ REQUIRED FIRST
Cloud Functions (Step 3) and Firestore TTL (Step 4) will **not** deploy on the free
Spark plan. Blaze is pay-as-you-go with a generous free tier; Buddy Link's volume is tiny.

1. Open: https://console.firebase.google.com/project/joggin-a69a7/usage/details
2. Click **Modify plan** → choose **Blaze** → attach/confirm a billing account.
3. (Optional but smart) set a **budget alert** (e.g. $1–$5) so you're warned of any usage.

**Verify:** the Usage page header shows **Blaze** (not Spark).

> You can do Steps 2 and 5 on the free plan, but 3 and 4 are hard-blocked until Blaze is on.

---

## STEP 2 — Deploy Firestore + Storage security rules
```powershell
firebase deploy --only firestore:rules
firebase deploy --only storage
```
**Expect:** each ends with `✔  Deploy complete!`.

**Verify in console:**
- Firestore → Rules → published rules match `firebase-hosting/firestore.rules`
  (owner-only writes; location/trail reads require accepted link + both `sharing==true` + not blocked).
- Storage → Rules → `/public/**` read-only public, everything else locked.

**If it errors:** a `Permission denied` / `API not enabled` usually means Firestore
isn't created yet — do Step 5 first, then retry.

---

## STEP 3 — Deploy the push Cloud Function  (needs Blaze)
```powershell
firebase deploy --only functions
```
**Expect:** build/upload logs, then `✔  Deploy complete!` and a line listing
`onBuddyLinkWrite`. First deploy can take 1–3 min and may ask to enable required APIs
(Cloud Functions, Cloud Build, Artifact Registry, Cloud Messaging) — say **yes**.

**Verify in console:**
- https://console.firebase.google.com/project/joggin-a69a7/functions → `onBuddyLinkWrite`
  is listed, region `us-central1`, trigger Firestore `links/{linkId}` (onWrite).
- Cloud Messaging API shows **enabled**.

**Common first-deploy hiccup:** if it complains about APIs not enabled, approve the
prompt (or enable them in the Google Cloud console) and re-run the same command.

---

## STEP 4 — Enable the 7-day TTL purge  (needs Blaze)
The app stamps `expireAt` on `locations/{appId}` and `locations/{appId}/trail/{autoId}`.
Create a TTL policy per collection group.

**Easiest (gcloud):**
```powershell
gcloud firestore fields ttls update expireAt --collection-group=locations --project=joggin-a69a7 --enable-ttl
gcloud firestore fields ttls update expireAt --collection-group=trail --project=joggin-a69a7 --enable-ttl
```
(Needs the gcloud CLI + `gcloud auth login`. If you don't have gcloud, use the console:
Firestore → **TTL** → Create policy → collection group `locations`, field `expireAt`;
repeat for `trail`.)

**Verify:** Firestore → TTL lists two **Active** policies (`locations`, `trail`).
Purging is best-effort within ~24h of `expireAt`, not instant.

---

## STEP 5 — Confirm Auth + Firestore are on
- Authentication → Sign-in method → **Anonymous** = **Enabled**.
- Firestore Database → exists in **Native** mode (create it if prompted; nearest region).
- `app/google-services.json` is already in the repo, so the client is wired.

**Verify:** enabling Buddy Link in the app (next step) creates `authMap/{uid}` and
`users/{appId}` docs in Firestore.

---

## STEP 6 — Install V1.41 on two devices
Buddy linking is 1:1 mutual, so you need **two** phones.

- Sideload `builds\joggin.apk` to each (transfer via cable/Drive — **not** WhatsApp, it
  corrupts APKs), **or** upload `joggin.apk` to the GitHub release and install both from
  the `…/get` page.
- The APK is **debug-signed** — fine for sideloading. (If a device already has an older
  release-signed build, uninstall it first to avoid a signature-mismatch "App not installed".)

---

## STEP 7 — Two-device verification walkthrough
Full checklist is in `deployment-runbook.md §6`. The essentials, in order:

1. Enable Buddy Link on both (consent → grant background-location).
2. Device A → **Invite Buddy** → send link; B opens `joggin://buddy/...` or
   `https://joggin-a69a7.web.app/buddy?...` → request appears as **pending** on A.
   Also try an **expired** link (>7 days) → "invite expired".
3. **Accept** on A → both show each other as buddies; request/accepted notifications fire.
4. Both toggled ON + GPS on → **buddy avatar** appears on the home map and updates; let a
   fix go >15 min stale → avatar goes not-live.
5. **👁 eyes-on-you** indicator shows on the watched party (bottom-center).
6. **📍 Request location now** → B pushes a fresh fix + gets a low-key "location shared"
   note. Tap again immediately → button shows **⏳ …please wait** (2-min cooldown).
7. **🧭 Show history** → buddy trail polyline draws; hide clears it.
8. **Block** from A → unlinked both directions; B can't re-link.
9. **Unlink warning** → remove on one side → other side gets "stopped sharing".
10. **🗑 Delete my data** → user/links/location/trail removed, identity reset, toggle OFF.
11. **Killed-app push (Step 3 payoff):** kill the app on B, have A send/accept a link →
    B should STILL get the notification (via the Cloud Function → FCM path).
12. **Backup/restore identity** (backup v2); and **7-day purge** check later.
13. **Core-app-unaffected:** temporarily rename `app/google-services.json`, rebuild → app
    still runs via `NoOpBuddyRepository`. Restore the file after.

---

## Quick reference — order of operations
| # | Step | Needs Blaze? | Where |
|---|------|-------------|-------|
| 1 | Upgrade to Blaze | — | Console |
| 2 | Deploy firestore + storage rules | no | CLI |
| 3 | Deploy `onBuddyLinkWrite` function | **yes** | CLI |
| 4 | Enable TTL (`locations`, `trail`) | **yes** | gcloud / Console |
| 5 | Enable Anonymous Auth + Firestore | no | Console |
| 6 | Sideload V1.41 on 2 devices | — | Devices |
| 7 | Two-device walkthrough | — | Devices |

**Minimum to see Buddy Link work at all:** Steps 2, 5, 6, 7 (free plan).
**For killed-app push + auto-purge:** add Steps 1, 3, 4.

> Related docs: `deployment-runbook.md` (fuller operational notes),
> `play-data-safety.md` (Play Store disclosure text), `firebase-setup.md`.
