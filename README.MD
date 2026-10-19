# Joggin

An Android GPS fitness tracker for walking, running, and cycling — 100% Kotlin with a
Jetpack Compose UI and osmdroid maps. Tracks routes via a foreground location service,
awards achievements, supports personal exercise targets, and includes **Buddy Link**, an
opt-in 1:1 live-location sharing feature backed by Firebase.

## Features

- **Activity tracking** — walk / run / cycle with live GPS map, distance, pace, calories,
  steps, and per-route stats. Routes are auto-classified by speed when not explicitly set.
- **Achievements & Personal Targets** — earnable medals (grouped Earned / Outstanding) plus
  user-defined rep/distance goals, with completion notifications.
- **Buddy Link** — consent-gated live-location sharing with a buddy (Ping or Route mode),
  backed by Firebase (Auth, Firestore). Invite by a short Buddy code.
- **Internationalization** — English and Greek, via a Kotlin string table (`Strings.kt`).
- **Themes** — multiple color themes.

## Tech stack

- Kotlin, Jetpack Compose
- osmdroid (OpenStreetMap) for maps
- Firebase Authentication (anonymous) + Cloud Firestore for Buddy Link
- SharedPreferences + Gson for local route/target storage
- Foreground service for background GPS tracking
- `minSdk 24`, `targetSdk 34`

## Build

```
./gradlew assembleDebug            # build the debug APK
./gradlew testDebugUnitTest        # run unit tests
```

The output APK is at `app/build/outputs/apk/debug/app-debug.apk`.

### Firebase flavor

Buddy Link's Firebase code lives in a separate source set (`app/src/firebase/`) that is
folded into the build when `app/google-services.json` is present. Without it, the app builds
and runs with an inert no-op Buddy repository, so Firebase config is not required to build.

## Distribution

Distributed as a sideloaded debug-signed APK via GitHub Releases; a Firebase Hosting install
page links to the latest release. `archive-build.ps1` archives each build under `builds/`.

## Repository layout

- `app/src/main/` — the app (UI, tracking, achievements, storage)
- `app/src/firebase/` — Firebase-gated Buddy Link backend code
- `firebase-hosting/` — Firebase project config, Firestore rules, Cloud Functions, install page
- `firestore.rules` — Firestore security rules (kept byte-identical to the hosting copy)
