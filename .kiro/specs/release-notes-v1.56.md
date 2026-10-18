# Release notes — V1.56

Version: **1.56** (versionCode 56). Tag `V1.56`. Successor to V1.55.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.56 — walks stay walks (higher run threshold)`

```
🚶 Walks no longer flip to "run" at a brisk pace
• The walk→run speed threshold was raised from 5 km/h to 6.5 km/h
• A brisk walk (up to ~6.4 km/h) now stays classified as a walk, not a run
```

---

## How to publish

```
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
.\archive-build.ps1
Copy-Item builds\joggin-v1.56.apk builds\joggin.apk -Force
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.56 builds\joggin.apk -t "Joggin V1.56 — walks stay walks (higher run threshold)" -F .kiro\specs\release-notes-v1.56.md
```

No Firestore rules deploy (no rules change).

---

## Internal changelog (since V1.55)

### Walk→run classification threshold raised 5 → 6.5 km/h
For walk-mode and legacy routes without an explicit activity type, the app infers "run"
from average speed. The threshold was `avgSpeed * 3.6f > 5f` (5 km/h), which misclassified
normal/brisk walks (a user's ~5.8 km/h walk showed as a run). Raised to `> 6.5f` in all three
derivations in `MainActivity.kt` — activity emoji, activity label, and activity colour — plus
the matching comment. Brisk walking tops out around 6.4 km/h, so walks below 6.5 stay walks;
genuine jogging/running (well above 6.5) still classifies as a run. No change to explicit
Run-mode or Cycle-mode routes (only the `activityType == 0` speed inference).

### Files
`app/src/main/java/com/example/joggingapp/MainActivity.kt`, `app/build.gradle` (55 → 56).
