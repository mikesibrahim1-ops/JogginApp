# Release notes — V1.45

Version: **1.45** (versionCode 45). Tag `V1.45`. Successor to V1.43 (last published release).

> Note: V1.44 was bumped/tagged internally but never published to GitHub; its changes are
> rolled into this V1.45 release along with the newer features.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.45 — More exercises, rep deduction, activity summary`

```
🏋️ More exercise types
Set targets for even more at-home exercises:
• Bicep curls, Tricep dips, Lunges, Crunches, Burpees, Jumping jacks and Plank (seconds)
• All alongside the existing sit-ups, push-ups, squats, walking and running

➖ Correct your rep log
• A clean in-app number pad for logging reps — no more fiddly keyboard
• Tap the minus key to deduct reps if you logged too many (the button turns red and reads "Deduct")
• Progress never drops below zero

📋 Activity summary
• The end-of-activity summary now shows the activity type (Walk, Run, Cycle, or a blend)

🗂️ Other
• The history page is now called "Activity History"
```

---

## How to publish (CLI)

```
gh release create V1.45 builds/joggin.apk -t "Joggin V1.45 — More exercises, rep deduction, activity summary" -F .kiro/specs/release-notes-v1.45.md
```

Or via the web UI: https://github.com/mikesibrahim1-ops/JogginApp/releases/new → pick tag `V1.45`,
paste the title/body above, attach `builds\joggin.apk` (keep the lowercase filename), publish.

No Firebase redeploy needed — the install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published.

---

## Internal changelog (since V1.43)

### Added — exercise types
- Seven new manual `ExerciseType` values: `BICEP_CURLS`, `TRICEP_DIPS`, `LUNGES`, `CRUNCHES`,
  `BURPEES`, `JUMPING_JACKS`, `PLANK_SECONDS` (rep/second-counted). Wired into
  `exerciseTypeLabel` and `targetColor`; EN + EL strings added. (`ExerciseTargets.kt`,
  `ExerciseTargetsScreen.kt`, `Strings.kt`)

### Added — rep deduction + number pad
- `LogRepsDialog` rebuilt as a custom in-app number pad (0–9, minus, backspace) — no system
  keyboard. Minus toggles negative; confirm button turns red / reads "Deduct" for negatives.
  (`ExerciseTargetsScreen.kt`)
- `logReps` accepts negative counts; `evaluateTargets` clamps progress to `0f` minimum.
  (`ExerciseTargets.kt`)

### Added — activity in summary
- `SummaryScreen` shows an "Activity" row (emoji + localized label) derived from the saved
  route, handling blended runs. New `activity` string (EN/EL). (`MainActivity.kt`, `Strings.kt`)

### Changed
- History page title "Past Runs" → "Activity History" (English; Greek already localized).
  (`Strings.kt`)

### Not included / unchanged
- The history-card speed heuristic (walk with avg > 5 km/h shows as "Run") is left as-is by
  user decision.
- Buddy Link backend still requires user-side Firebase ops.
