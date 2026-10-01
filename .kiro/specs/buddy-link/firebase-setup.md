# Buddy Link — Firebase Setup (one-time, YOUR action)

Buddy Link's live location relay needs a backend. We chose **Firebase** (free tier).
This is the one step that only you can do — it creates the cloud project and the
config file the app needs. Everything after this I can wire up in code.

Nothing here costs money at our scale (a small personal app is far inside the free
tier). No credit card is required for the Spark (free) plan.

---

## Step 1 — Create a Firebase project

1. Go to https://console.firebase.google.com/ and sign in with a Google account.
2. Click **Add project** (or **Create a project**).
3. Name it e.g. `JogginApp` (the name is just for you).
4. **Google Analytics:** you can turn this **OFF** — not needed for Buddy Link.
5. Click **Create project**, wait for it to finish, then **Continue**.

## Step 2 — Register the Android app

1. On the project overview, click the **Android** icon (“Add app” → Android).
2. **Android package name:** `com.example.joggingapp`  ← must match exactly.
3. **App nickname:** optional (e.g. "Joggin").
4. **Debug signing SHA-1:** optional for now (Anonymous Auth + Firestore don't need
   it). Leave blank; we can add it later if we add features that require it.
5. Click **Register app**.

## Step 3 — Download the config file

1. Firebase gives you **`google-services.json`**. Download it.
2. Place it here in the project:  **`app/google-services.json`**
   (same folder as `app/build.gradle`).
3. This file is per-project config, not a secret key, but it should still NOT be
   shared publicly. (See "gitignore" note below.)

## Step 4 — Enable Anonymous Authentication

1. In the console: **Build → Authentication → Get started**.
2. Open the **Sign-in method** tab.
3. Enable **Anonymous** and save.
   (This backs each install's session; the app maps it to our own App ID.)

## Step 5 — Create the Firestore database

1. In the console: **Build → Firestore Database → Create database**.
2. Choose a location close to your users (e.g. `europe-west` for Greece/Spain/UK).
3. Start in **Production mode** (we'll paste real security rules — don't use test
   mode, which is open to the world).
4. Create.

## Step 6 — Security rules & TTL (files are ready)

The Firebase code is now prepped. Two things to paste in the console after Steps 1–5:

### 6a. Security rules
1. Open the file **`firestore.rules`** in the project root.
2. In the console: **Firestore Database → Rules**, replace the contents with that
   file, and **Publish**.
   - These enforce the mutual-on gate (CONDITION-1) and block checks server-side
     (design §9): only owners write their data; a buddy's location + trail are
     readable only when an **accepted** link exists, **both** users are `sharing`,
     and neither has blocked the other.

### 6b. TTL policy (7-day auto-purge)
Create a TTL policy on the `expireAt` field so location + trail data self-deletes
after 7 days (Req 8.1):
1. Console: **Firestore Database → (⋯ / more) → TTL** (or the "TTL" tab).
2. Add policy — Collection group: **`locations`**, Timestamp field: **`expireAt`**.
3. Add a second policy — Collection group: **`trail`**, Timestamp field: **`expireAt`**.
   (Collection-group TTL covers `locations/{id}/trail` subcollections.)

### 6c. Activate the app side (zero code changes)
Once `app/google-services.json` is in `app/`:
- The build **auto-detects** it: the Google Services plugin + Firebase deps switch on,
  `FirebaseBuddyRepository` compiles (from `app/src/firebase/java`), and
  `BuildConfig.FIREBASE_ENABLED` becomes `true`, so `BuddyRepositoryProvider` returns
  the Firebase backend instead of the NoOp stub. No source edits needed.
- Just rebuild: `./gradlew assembleDebug`.
- Without the JSON, the app builds exactly as before and Buddy Link shows
  "temporarily unavailable" (Req 10).

---

## Tell me when Steps 1–5 are done

Once `app/google-services.json` is in place and Anonymous Auth + Firestore are
enabled, tell me and I will:
- add the Google Services Gradle plugin + Firebase dependencies (BoM, Auth,
  Firestore) to the build,
- implement `FirebaseBuddyRepository` against the interface,
- provide the security rules + TTL for you to paste in,
- verify the app still builds (and still works with Buddy Link OFF).

---

## Notes

- **gitignore:** consider adding `app/google-services.json` to `.gitignore` if this
  project is ever pushed to a public repo. It's config, not a private key, but
  there's no reason to publish it.
- **Free tier limits:** Firestore free tier is ~50k reads / 20k writes / 20k
  deletes per day and 1 GiB storage. Buddy Link writes one location every ~5 min per
  active user — negligible against these limits.
- **Package name caveat:** the app's applicationId is `com.example.joggingapp`.
  Firebase accepts it, but note `com.example.*` is the reserved sample namespace and
  cannot be used to *publish* on the Play Store. If you ever publish, you'll rename
  the package and must re-register the app in Firebase with the new name. Not a
  problem for sideloaded testing now.
