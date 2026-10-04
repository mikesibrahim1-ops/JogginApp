# Maestro UI tests — JogginApp

[Maestro](https://maestro.mobile.dev) is the mobile equivalent of Playwright: it drives
the **installed APK** on an emulator or real device using simple YAML "flows". It's the
right tool for black-box UI testing this app (Playwright is web-only and can't see a
native Jetpack Compose UI).

> These flows do **not** run in the Kiro/IDE environment (no emulator here). Run them on
> your machine against an emulator or a plugged-in device.

## One-time setup
1. Install Maestro (needs Java 11+):
   ```bash
   # macOS / Linux
   curl -Ls "https://get.maestro.mobile.dev" | bash
   # Windows: use WSL, or see https://maestro.mobile.dev/getting-started/installing-maestro
   ```
2. (Optional) VS Code / Kiro extension: search the marketplace for **"Maestro"**
   (publisher *mobile.dev*) for syntax highlighting + a run button on flow files.
3. Have the app installed on a running device/emulator:
   ```bash
   adb install -r builds/joggin.apk
   adb devices        # confirm one device is listed
   ```

## Run
```bash
maestro test maestro/smoke.yaml
maestro test maestro/exercise_targets.yaml
# or run every flow in the folder:
maestro test maestro/
```
Add `maestro studio` to interactively inspect the UI and discover element text/ids.

## Notes on selectors
- The app is localized (EN/EL). These flows assume **English**. If the device is set to
  Greek, update the labels (e.g. `Add target` → `Προσθήκη στόχου`).
- First launch shows a **name-entry screen** if no name is saved. `smoke.yaml` handles
  both cases with `runFlow ... when:` guards.
- The options pane opens by swiping **left** on the bottom card (or via the on-screen
  "options ⟶" hint). Achievements open by swiping **right**.
- appId: `com.example.joggingapp`.
