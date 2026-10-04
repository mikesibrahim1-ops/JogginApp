# Release notes — V1.43

Version: **1.43** (versionCode 43). Tag `V1.43`. Successor to V1.42 (Exercise Targets + theme polish).

A maintenance release that fixes three Exercise Targets issues and adds grouping to the
Personal Targets list.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.43 — Exercise Targets fixes + Personal Targets grouping`

```
🛠️ Fixes
• Fixed a crash when opening the Personal Targets view on the Achievements page
• New targets no longer count reps or distance you logged earlier the same day — a target now starts fresh and only counts activity from after you create it
• Achievements header text (motivation line + percent complete) is now readable on the darker themes

🗂️ Personal Targets
• Group your completed targets by Day, Week or Month (defaults to Day), with a header for each group

Open the side panel → Exercise Targets, or the 🎯 badge on the Achievements page.
```

---

## How to publish (user action — `gh` CLI not available here)

1. Go to: https://github.com/mikesibrahim1-ops/JogginApp/releases/new
2. **Choose a tag:** select the existing `V1.43` (pushed with this release).
3. **Title:** `Joggin V1.43 — Exercise Targets fixes + Personal Targets grouping`.
4. **Description:** paste the block above.
5. **Attach binary:** upload `builds\joggin.apk` — keep the filename **lowercase `joggin.apk`**,
   same every release, so the install page's `latest` link keeps working.
6. Publish.

No Firebase change or redeploy is needed: the install page
(`https://joggin-a69a7.web.app/get`) links to
`https://github.com/mikesibrahim1-ops/JogginApp/releases/latest/download/joggin.apk`,
which auto-resolves to this new release once published.

> If you'd rather use the CLI: install GitHub CLI, `gh auth login`, then
> `gh release create V1.43 builds/joggin.apk -t "Joggin V1.43 — Exercise Targets fixes + Personal Targets grouping" -F .kiro/specs/release-notes-v1.43.md`

---

## Internal changelog (full)

### Fixed
- **Personal Targets crash:** opening the Personal Targets view (🎯 badge on the Achievements
  page) crashed with `IndexOutOfBoundsException` in Compose's `Stack.pop`. Caused by a
  `return@Column` early-return inside a composable content lambda, which left the composer's
  group stack unbalanced. Replaced the early returns with `if/else` so both branches are
  structurally balanced. (`AchievementsScreen.kt`)
- **Instant completion:** a newly created manual/distance target auto-completed immediately if
  reps or routes had been logged earlier in the same period. `evaluateTargets` now counts only
  activity logged at or after each target's `createdAt` (effective window start =
  `max(periodStart, createdAt)`), so a fresh target starts at 0 progress. (`ExerciseTargets.kt`)
- **Dim header text:** the Achievements header motivation line and percent-complete label used
  `textSecondary`, which was unreadable on the darker hero gradients (Forest Trail, Midnight
  Pulse). Both now use `onSecondary` (the same tone as the theme titles). (`AchievementsScreen.kt`)

### Added — Personal Targets grouping
- A **Group by: Day / Week / Month** control at the top of the Personal Targets section
  (defaults to Day). Completed targets are bucketed by their achievement date (`achievedAt`),
  newest group first, each bucket under a formatted header ("04 Oct 2026", "Week of …",
  "October 2026"). New `CompletionGrouping` enum, `CompletedGroup`, and `groupCompleted()`
  helper. Full English + Greek strings. (`ExerciseTargets.kt`, `AchievementsScreen.kt`, `Strings.kt`)

### Tests
- `ExerciseTargetsTest`: targets now set `createdAt` explicitly; added a regression test that a
  target ignores reps logged before it was created. Full unit suite green via `testDebugUnitTest`.

### Not included / unchanged
- Buddy Link backend still requires the user-side Firebase ops in
  `.kiro/specs/buddy-link/DEPLOY-GUIDE.md`. App runs normally with Buddy Link inert if the
  backend isn't configured.
