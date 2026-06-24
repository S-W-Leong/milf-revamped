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

1. Select English or Manglish.
2. Tap Speak.
3. Say: I want to see my grandson.
4. Tap Stop.
5. Let MILF narrate.
6. When the app asks to confirm the call, tap Yes or tap Speak yes/no and say yes.

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

## UX overlay demo path

1. Start the backend:
   ```bash
   cd backend
   python -m milf.server
   ```
2. Install the debug APK:
   ```bash
   cd android
   ./gradlew :app:installDebug
   ```
3. On the Android device, open MILF buyer setup.
4. Grant microphone, phone, overlay, and accessibility permissions.
5. Open default assistant settings and set MILF as the assistant app if the OEM allows it.
6. Turn on Demo watch mode for judges; leave it off for senior-mode testing.
7. Tap Start helper. The floating bubble should appear over any app.
8. Hero flow: tap bubble or invoke assistant, say "I want to see my grandson", tap Stop, wait for WhatsApp navigation, approve "Calling Wei, your grandson?", and verify the video-call screen opens.
9. Failure flow: stop the backend, repeat the hero request, tap Stop, and verify the failure screen says it is having trouble and offers to call daughter.

## Device matrix

Record these results for each target device:

| Device | Android version | Overlay works | Assist invocation works | Accessibility actions work | Notes |
|---|---:|---|---|---|---|
| Example emulator Pixel API 35 | 15 | yes | partial | yes | example baseline; replace after manual Task 9 test |

During Task 9, add one row per physical device only after the device has been tested. Use measured values such as `yes`, `no`, or `partial`, and include the exact device model in the first column.
