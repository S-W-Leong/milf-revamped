# MILF Android Demo Runbook

## Device Setup

- Use the demo Android phone with MILF installed.
- Enable `MILF phone control` in Android Accessibility settings.
- Keep WhatsApp logged in and the demo contact visible in recent chats when possible.
- Keep the second device signed in as the demo contact and ready to receive the call.

## Backend Setup

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. MILF_STT_BACKEND=mock python -m milf.server
```

Use the Mac LAN websocket URL in the app:

```text
ws://<Mac LAN IP>:8765
```

For emulator runs, use:

```text
ws://10.0.2.2:8765
```

## Android Build

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew -Dorg.gradle.java.home=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home :app:installDebug
```

`installDebug` requires a connected emulator or Android phone with USB debugging enabled.

## Live Flow

1. Open MILF.
2. Use Config to grant microphone, phone, overlay, accessibility, and assistant-app readiness.
3. In Config > Backend, enter the websocket URL and tap the connection check.
4. Return to Main.
5. Confirm Start Agent is enabled.
6. Tap Start Agent.
7. Verify Android home appears and the MILF bottom pill is centered near the bottom.
8. Tap the mic once. The pill should show a pulsing waveform and `Listening...`.
9. Say: I want to see my grandson.
10. Tap the mic again. The pill should show `Thinking...`, then `Acting...`.
11. Verify MILF shows an electric-cyan target box around each screen element it acts on.
12. When the confirmation card appears, tap Yes.
13. Verify the WhatsApp video-call screen opens and the pill returns to `Ask MILF to do something`.

## Success Criteria

- The app captures audio and backend starts the run.
- MILF opens WhatsApp.
- MILF reaches the demo contact.
- MILF asks for confirmation before connecting.
- The call connects only after approval.

## Rehearsal Log

For each attempt, record:

```text
Run number:
Language:
Reached WhatsApp:
Reached contact:
Confirmation shown:
Call connected after yes:
Failure note:
```

Target: at least 9 of 10 attempts connect after approval, or every failure has one repeated cause to fix.

## Backup

- Record one clean full run after the first successful rehearsal.
- Keep the video available locally on the presentation laptop.
- Confirm the backup recording includes audio narration, confirmation, and successful call connection.

## Floating Control Rail Checks

- Expanded bottom pill is a shorter centered rail and does not touch screen edges.
- Expanded outside tap collapses to the round waveform bubble and does not pass through.
- Collapsed bubble is draggable.
- Tapping the collapsed bubble re-expands the bottom pill.
- Taps outside the collapsed bubble pass through to Android normally.
- Text fallback shows `Ask MILF to do something`.
- Typing text turns the mic button into send.
- During `Thinking...` and `Acting...`, both the black-square run stop and white-cross exit are visible.
- Black-square run stop interrupts only the current run and keeps the rail available.
- White-cross exit removes the overlay and leaves the user on the current screen.

## Device matrix

Record these results for each target device:

| Device | Android version | Overlay works | Assist invocation works | Accessibility actions work | Notes |
|---|---:|---|---|---|---|
| Example emulator Pixel API 35 | 15 | yes | partial | yes | example baseline; replace after manual Task 9 test |

During Task 9, add one row per physical device only after the device has been tested. Use measured values such as `yes`, `no`, or `partial`, and include the exact device model in the first column.

## Verification status

Automated checks from this workstation:

- Backend: `.venv/bin/pytest backend -v` passes.
- Android: `JAVA_HOME=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home ./gradlew :app:testDebugUnitTest :app:assembleDebug` passes.

Manual UX verification is still pending. `adb devices` returned no attached emulator or phone, so the senior-mode, demo-mode, failure, assist-invocation, and cross-app bubble checks were not run in this environment. Do not mark assist invocation as working until the power/assistant gesture actually opens `AssistEntryActivity` on the target device.
