# Release notes — V1.58

Version: **1.58** (versionCode 58). Tag `V1.58`. Successor to V1.57.

---

## GitHub Release — paste into the release body

**Title:** `Joggin V1.58 — fix Buddy Link permission error (buddies now connect)`

```
🔧 Buddy Link works again for long-time accounts
• Fixed Buddy Link being completely broken — every buddy operation failed with a
  permission error, so buddies could never connect
• Only hit accounts created before V1.52 (the security-hardening release)
• No change to who can see what — sensitive fields still live only in the
  owner-only subdoc
```

---

## How to publish

```
.\gradlew.bat assembleDebug testDebugUnitTest --console=plain
.\archive-build.ps1
Copy-Item builds\joggin-v1.58.apk builds\joggin.apk -Force
& "$env:ProgramFiles\GitHub CLI\gh.exe" release create V1.58 builds\joggin.apk -t "Joggin V1.58 — fix Buddy Link permission error (buddies now connect)" -F .kiro\specs\release-notes-v1.58.md
```

No Firestore rules deploy (no rules change).

---

## Internal changelog (since V1.57)

### Buddy Link completely broken — PERMISSION_DENIED on every op (pre-V1.52 accounts)
On accounts created before V1.52, Buddy Link was entirely non-functional: every
owner-gated Firestore write failed with `PERMISSION_DENIED`, so a session was never
established and buddies could never connect. The in-app log showed a steady stream of
`❌ Firebase buddy op failed: PERMISSION_DENIED`, `❌ Buddy Link session attempt N
failed`, and `⚙ Buddy fix (interval) not sent — backend PERMISSION`.

Root cause: a **missing migration** from the V1.52 security hardening. V1.52 moved the
sensitive `authUid`/`fcmToken` fields off the public `users/{appId}` doc into an
owner-only `private/self` subdoc, and tightened the public-doc update rule with a field
whitelist (`hasOnly(...)`) that forbids `authUid`/`fcmToken` on the public doc. But it
never deleted those legacy fields from docs that already had them. Firestore evaluates
`hasOnly()` against the **whole post-merge document**, not just the fields in the write
— so on any pre-V1.52 account the stale `authUid`/`fcmToken` still sitting on the public
doc meant the post-merge doc contained forbidden fields, and every owner write was
rejected. The session could never be provisioned, so the whole feature failed closed.

Fix (client-side, no rules change): strip the legacy fields during session
provisioning. `ensureSession()` and `updateMyUser()` now add
`put("authUid", FieldValue.delete())` and `put("fcmToken", FieldValue.delete())` to the
public-doc set-merge, so the public doc converges to the whitelisted field set and the
`hasOnly()` update rule accepts the write. `FieldValue.delete()` on an absent field is a
no-op, so this is idempotent for already-migrated accounts. In support of this,
`ensureSession()` provisioning was made unconditional (a returning device with a cached
anon session still backfills `users/{appId}/private/self` on every enable), and the
location-broadcast loop awaits `ensureSession` once before its first write so the subdoc
always exists first.

No change to Firestore rules, no security weakening — the sensitive fields stay only in
the owner-only `private/self` subdoc. `firestore.rules` is byte-for-byte unchanged and
`authUid`/`fcmToken` were not added to any whitelist.

### Device verification (RZCW71PL1YB, pre-V1.52 account)
- **Before:** repeated `PERMISSION_DENIED` / `Buddy Link session attempt 1 failed` on
  every enable; session never established.
- **After (V1.58):** `👤 Buddy Link session established (attempt 1)` on launch; zero new
  `PERMISSION_DENIED` / session-attempt-failed / backend-PERMISSION lines; clean launch,
  no crash.

### Files
`app/src/firebase/java/com/example/joggingapp/FirebaseBuddyRepository.kt`,
`app/src/main/java/com/example/joggingapp/BuddyLinkController.kt`,
`app/src/main/java/com/example/joggingapp/BuddyLocationService.kt`,
`app/build.gradle` (57 → 58).
