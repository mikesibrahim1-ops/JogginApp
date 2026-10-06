# Release notes — V1.44

Version: **1.44** (versionCode 44). Tag `V1.44`. Successor to V1.43.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.44 — Rep deduction + custom number pad`

```
➖ Rep deduction
Made a mistake logging reps? No problem.
• Tap the minus key on the new number pad to switch to deduction mode
• The button turns red and reads "Deduct" so you know what's happening
• Your progress is adjusted — it won't go below zero

🔢 Custom number pad
• The log-reps dialog now uses its own clean number pad (0–9, minus, backspace)
• No more system keyboard popping up with unwanted symbols
• Cleaner, faster rep entry

Open the side panel → Exercise Targets → tap Log on any target to try it.
```

---

## Internal changelog

### Added
- **Rep deduction:** users can now enter a negative number when logging reps to correct
  mistakes. The `-` key on the custom pad toggles the sign; the confirm button turns red
  and reads "Deduct" (`exDeductReps`, EN + EL). (`ExerciseTargetsScreen.kt`, `Strings.kt`)

### Changed
- **Custom number pad:** `LogRepsDialog` rebuilt with an in-app 4×3 grid (1–9, minus, 0,
  backspace) replacing the system soft keyboard entirely. No `TextField` / no IME — digits
  and minus only, no stray symbols. (`ExerciseTargetsScreen.kt`)
- **`logReps`** now accepts negative counts (guard changed from `count <= 0` to `count == 0`).
  (`ExerciseTargets.kt`)
- **`evaluateTargets`** clamps the computed `current` value to `0f` minimum via
  `coerceAtLeast(0f)`, so deductions can never display negative progress.
  (`ExerciseTargets.kt`)

### Not included / unchanged
- Buddy Link backend still requires user-side Firebase ops.
