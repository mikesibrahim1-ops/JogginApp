# Release notes — V1.42

Version: **1.42** (versionCode 42). Tag `V1.42` (pushed). Successor to V1.41 (Buddy Link).

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.42 — Exercise Targets + theme polish`

```
🎯 New: Exercise Targets
Set your own at-home goals and track them.
• Targets for sit-ups, push-ups, squats, walking and running
• Choose a daily, weekly or monthly goal for each
• Log reps as you go — they add up over the period; distance goals fill in automatically from your tracked runs
• Add several targets at once in one go
• Hit a target and get a 🎉 congratulations — it's saved to your Personal Targets with the date
• See your completed targets on the Achievements page via the new 🎯 badge (top-right)

🎨 Theme polish
• The options panel and the start button now share the one main theme colour
• Lighter, cleaner profile-picture ring
• Theme colour dots now have a thin outline so they're easy to see

Open the side panel → Exercise Targets to try it.
```

---

## How to publish (user action — `gh` CLI not available here)

1. Go to: https://github.com/mikesibrahim1-ops/JogginApp/releases/new
2. **Choose a tag:** select the existing `V1.42` (already pushed).
3. **Title:** `Joggin V1.42 — Exercise Targets + theme polish`.
4. **Description:** paste the block above.
5. **Attach binary:** upload `builds\joggin.apk` — keep the filename **lowercase `joggin.apk`**,
   same every release, so the install page's `latest` link keeps working.
6. Publish.

No Firebase change or redeploy is needed: the install page
(`https://joggin-a69a7.web.app/get`) links to
`https://github.com/mikesibrahim1-ops/JogginApp/releases/latest/download/joggin.apk`,
which auto-resolves to this new release once published.

> If you'd rather use the CLI: install GitHub CLI, `gh auth login`, then
> `gh release create V1.42 builds/joggin.apk -t "Joggin V1.42 — Exercise Targets + theme polish" -F .kiro/specs/release-notes-v1.42.md`

---

## Internal changelog (full)

### Added — Exercise Targets
- New **Exercise Targets** screen (options pane → 🎯 Exercise Targets): user-defined goals
  for sit-ups / push-ups / squats (manual rep logging) and walking / running (auto from GPS
  routes), each over a daily / weekly / monthly period.
- **Batch add:** compose several distinct targets (own type/period/amount) and save them at once.
- **Progress:** per-period summing — manual reps summed from a logged-bout store; distance
  summed from `SavedRoute` history in the current period. Progress bars + "x / y" labels.
- **Completion:** a 🏆 congratulations dialog when a target is met; the achievement is recorded
  (deduped per target+period) and listed under **Personal Targets** on the Achievements page,
  reached via a top-right badge, each with its achievement date.
- Full English + Greek strings.

### Changed — Theme
- `primary` now carries the main theme colour on BOTH the options pane and the start/play button.
- Profile-picture (avatar) circle uses a lighter `primaryDark` shade (was too dark).
- Theme-selector colour dots get a thin white ring so they stand out against the pane.
- Options-pane version footer now reads `BuildConfig.VERSION_NAME` dynamically (was a stale
  hardcoded "V1.40").

### Fixed
- Exercise Targets **log dialog** rebuilt as a real Compose `Dialog` with auto-focus — the
  previous hand-rolled overlay blocked keyboard input so no reps could be entered.

### Tests / tooling
- Added `ExerciseTargetsTest` (10 pure-JVM tests: period boundaries, rep/distance aggregation,
  clamping). Full unit suite green via `testDebugUnitTest`.
- Added a Maestro UI-test scaffold under `maestro/` (smoke + Exercise Targets e2e) for on-device
  testing.

### Not included / unchanged
- Buddy Link backend still requires the user-side Firebase ops in
  `.kiro/specs/buddy-link/DEPLOY-GUIDE.md` (Blaze, deploy rules/functions, TTL, Anonymous Auth).
  App runs normally with Buddy Link inert if the backend isn't configured.
