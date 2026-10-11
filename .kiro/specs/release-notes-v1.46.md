# Release notes — V1.46

Version: **1.46** (versionCode 46). Tag `V1.46`. Successor to V1.45.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.46 — Buddy Link invites fixed, colourful targets`

```
🤝 Buddy Link invites that actually pair
• Opening a buddy invite now shows a clear Accept / Ignore dialog
• Accepting links both people straight away — no more one-sided invites that never connect
• Received invites are listed in the Buddy Link section and can be removed any time
• Opening an invite before you've turned on Buddy Link no longer loses the invite

🎨 Colour-coded Personal Targets
• Each exercise type now has its own colour in the Achievements screen
• Push-ups, sit-ups, squats and every other exercise are easy to tell apart at a glance
```

---

## How to publish (CLI, gh keyring auth)

```
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.46 builds\joggin.apk -t "Joggin V1.46 — Buddy Link invites fixed, colourful targets" -F .kiro\specs\release-notes-v1.46.md
```

The install page (`https://joggin-a69a7.web.app/get`) links to
`releases/latest/download/joggin.apk`, which auto-resolves once published — no Firebase redeploy.

---

## Internal changelog (since V1.45)

### Fixed — Buddy Link invite pairing (`0ebf67d`)
- Root cause was a role inversion: the invite link embedded the SENDER's appId, so opening it
  made the recipient the requester and the Accept prompt landed on the sender → pairing never
  completed.
- Opening a valid invite now shows an Accept / Ignore dialog. **Accept** has the recipient
  create `links/{sorted(me,sender)}` directly in `state="accepted"` (`a=recipient, b=sender`),
  pairing both sides immediately. **Ignore** discards cleanly.
- Received invites are listed in the Buddy Link section and removable at any time.
- Enable-gating fix: an invite opened before Buddy Link is enabled is held (via `BuddyInviteBus`)
  and processed after the enable flow instead of being silently dropped.
- New `acceptInvite(fromAppId)` on the `BuddyRepository` interface + both impls (Firebase + NoOp).
  New `BuddyInviteTest.kt`. No Firestore rules change needed.
- `firebase-hosting/functions/index.js` gained an additive accepted-on-create sender push — this
  requires `firebase deploy --only functions` on the Blaze plan and is NOT deployed; pairing does
  not depend on it.
  Files: `FirebaseBuddyRepository.kt`, `BuddyRepository.kt`, `NoOpBuddyRepository.kt`,
  `BuddyLinkController.kt`, `BuddyLinkSection.kt`, `MainActivity.kt`, `Strings.kt` (EN+EL),
  `functions/index.js`, `BuddyInviteTest.kt`.

### Changed — per-type colours in Personal Targets (`1d029a8`)
- The Achievements → Personal Targets boxes now colour themselves by `ExerciseType` instead of a
  single fixed colour, so different exercises (e.g. push-ups vs sit-ups) are distinguishable.
- `targetColor` moved to `ExerciseTargets.kt` as one shared `internal` function (used by both the
  Achievements and Exercise Targets screens — single source of truth), exhaustive over all 12
  types with no `else`. The seven manual types got distinct hues.
  Files: `ExerciseTargets.kt`, `ExerciseTargetsScreen.kt`, `AchievementsScreen.kt`.

### Not included / unchanged
- The history-card speed heuristic (walk with avg > 5 km/h shows as "Run") is left as-is by user
  decision.
- Buddy Link killed-app sender push requires Blaze + `firebase deploy --only functions` (deferred).
- Real two-device Buddy Link pairing with the new dialog not yet verified on-device.
