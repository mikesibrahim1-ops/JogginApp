---
inclusion: always
---

# Autonomous Quality Workflow

## Project commands (JogginApp — Kotlin / Android / Jetpack Compose / Gradle)

This is an Android app built with the Gradle wrapper. Only `gradlew.bat` exists, so
on this Windows/pwsh machine always invoke it as `.\gradlew.bat` from the repo root.
There is **no separate type-checker** (Kotlin compilation IS the type-check) and **no
dedicated Kotlin linter** (ktlint/detekt/spotless are not configured) — the Android
Gradle `lint` task is the linter.

- **Build**: `.\gradlew.bat :app:assembleDebug`
- **Type-check** (faster than a full build; Kotlin compile): `.\gradlew.bat :app:compileDebugKotlin`
- **Lint**: `.\gradlew.bat :app:lintDebug`
- **Test** (JUnit 4 + Robolectric unit tests): `.\gradlew.bat :app:testDebugUnitTest`

Source files: `app/src/main/java/**/*.kt` and the Firebase-gated `app/src/firebase/java/**/*.kt`.
Unit tests: `app/src/test/java/**` (JUnit 4 + Robolectric). On-device testing uses `adb`
against a connected device (see the android-device-debug-workflow memory).

## Core directive

ALL work must follow this workflow in order with no skipped steps:
**Idea → Creation → Verification → Implementation → Testing → Cleanup.**

Work autonomously through the full loop. Never stop after writing code and wait for the
user to test. Testing and verification are the agent's responsibility.

## Phase 1 — Idea / Scope

- Restate what's being built.
- List every file that will be touched.
- Identify existing code that may be affected.
- Flag any code that will become redundant or stale and add it to a cleanup list immediately.

## Phase 2 — Creation

- Follow existing project conventions.
- Search the codebase before creating new utilities/components — never duplicate existing ones.
- No placeholder code, no commented-out blocks, no "TODO: implement later" unless explicitly requested.

## Phase 3 — Verification (MANDATORY)

- Run the build / type-check / lint — zero errors and zero new warnings:
  - `.\gradlew.bat :app:compileDebugKotlin` (type-check)
  - `.\gradlew.bat :app:assembleDebug` (build)
  - `.\gradlew.bat :app:lintDebug` (lint)
- Check for unused imports, unused variables, unreachable code, missing dependencies.
- Verify every import resolves.
- Search for references to anything renamed, moved, or deleted and update ALL call sites.

## Phase 4 — Implementation

- Wire new code into the app (routes, exports, registration, Compose navigation, manifest entries).
- Confirm it's actually reachable — no orphan code.
- If replacing old code, remove the old code in the same change — never leave old and new versions side by side.

## Phase 5 — Testing (MANDATORY)

- Start the app and confirm no crashes. On this project that means installing the debug
  APK on a connected device and launching it, then reading the real logs
  (`app_log.txt` via `run-as`, and `logcat`) — a clean build is NOT proof a feature works.
- Exercise the feature end-to-end including empty states, error states, invalid input,
  and navigation in/out of affected areas.
- Run existing tests — all must pass: `.\gradlew.bat :app:testDebugUnitTest`.
- Write/update tests for new behavior (JUnit 4 + Robolectric under `app/src/test/java`).
- If the app crashes or tests fail, FIX IT before proceeding.

## Phase 6 — Cleanup (MANDATORY)

- Remove all unused code, files, imports, variables, functions, styles, and assets touched by the change.
- Remove debug logging and temporary scaffolding.
- Delete files made redundant — don't leave them "just in case."
- Confirm no dead routes, dead exports, or stale references remain.
- Run the build + app one final time after cleanup.

## Debugging discipline

- Fix root causes, not symptoms.
- No swallowed errors or silent try/catch blocks hiding failures.
- When a feature "doesn't work," read the app's real error output FIRST (in-app
  `app_log.txt`, then `logcat`) before theorizing about the cause.
- Reproduce the original failure, then verify the fix resolves it.
- Never silently leave known-broken behavior — fix it or explicitly report it.
- A crash during testing is a blocker; the task is not done until it's fixed and re-verified.

## Definition of Done

A task is complete ONLY when ALL are true:

- Build / type-check / lint passes with no errors and no new warnings.
- App starts and runs without crashing.
- Feature works end-to-end including edge cases.
- Existing tests pass; new behavior is covered where applicable.
- No unused imports, variables, functions, files, or dead code remain.
- No references to renamed/removed/moved code exist anywhere.
- No debug logging or temporary scaffolding remains.
- Known issues are fixed or explicitly reported.

If any item can't be checked, say so explicitly — never present incomplete work as finished.

## End-of-task report

End every task with a report covering:

- What was built / changed.
- How it was verified (exact commands run + results).
- What was cleaned up / removed.
- Any remaining issues or risks.
