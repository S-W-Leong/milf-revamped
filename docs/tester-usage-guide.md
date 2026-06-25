# MILF Tester Usage Guide

This guide is for judges and testers trying MILF with the live Render backend and
an Android device or emulator.

MILF is a voice-first Android accessibility agent. You can give it natural phone
requests such as opening apps, finding contacts, starting WhatsApp calls, drafting
messages, or playing media. It reasons from the live phone screen, uses the
deployed backend, narrates progress, and asks for confirmation before final
consequential actions.

The project team uses this request as a reference scenario because it demonstrates
relationship resolution, app navigation, narration, and the confirmation gate in
one pass:

> "I want to see my grandson."

## Quick Start

Use this path for the published judge build.

1. Open the latest GitHub release:

   ```text
   https://github.com/S-W-Leong/milf-revamped/releases/tag/milf-v0.1.0
   ```

2. Download the APK asset:

   ```text
   milf-v0.1.0.apk
   ```

3. Install the APK on an Android phone or emulator.
4. Open MILF and complete `Config`.
5. Confirm all permissions are `Ready` and the backend is `Connected`.
6. Tap `Start Agent`.
7. Tap the floating MILF control, tap the microphone, and say a natural request,
   for example:

   ```text
   I want to see my grandson.
   ```

8. Tap the microphone again to stop listening if it does not stop automatically.
9. Watch MILF navigate and listen for narration.
10. When MILF asks for confirmation, approve only if the contact and action are
   correct.

## Installing The APK

### Android Phone

1. Download `milf-v0.1.0.apk` from the GitHub release on the phone, or transfer
   it to the phone after downloading it on a computer.
2. Open the APK from the browser downloads screen or Files app.
3. If Android blocks the install, allow installs from that source when prompted.
4. Finish the install and open `MILF`.

Android may call this setting `Install unknown apps`, `Allow from this source`,
or `Unknown app installs`, depending on the device.

### Android Emulator

Download the release APK on your computer, then install it with `adb`:

```bash
adb install -r milf-v0.1.0.apk
```

If `adb` cannot find a device, start the emulator first and run:

```bash
adb devices
```

You can also drag the APK onto many Android Emulator windows to install it.

## First Run Setup

After installation:

1. Open the MILF app.
2. Tap `Config`.
3. In `Permissions`, grant every required setup item:
   - `Microphone`
   - `Phone calls`
   - `Overlay`
   - `Accessibility`
4. In `Backend`, keep `Deployed` selected and wait for `Backend` to show
   `Connected`.
5. In `Agent`, keep speech input on `Native`.
6. Add memory hints if needed. Memory is where you can tell MILF about contacts,
   relationships, nicknames, and preferred apps:

   ```text
   Wei is my grandson. Use WhatsApp video calls for Wei.
   My daughter is Mei. Use WhatsApp for family messages.
   ```

7. Tap `Save memory`.
8. Return to `Main`.
9. Tap `Start Agent`.
10. Open the app you want to test, or leave the phone on the home screen.
11. Tap the floating MILF control, tap the microphone, and say a natural request,
    for example:

    ```text
    I want to see my grandson.
    ```

12. Tap the microphone again to stop listening if it does not stop
    automatically.
13. Watch MILF navigate and listen for narration.
14. When MILF asks for confirmation, approve only if the contact and action are
    correct.

## What Judges Should Observe

- The senior-facing control is simple: a floating helper, microphone, typed
  fallback, and clear confirmation buttons.
- The agent is not limited to a single scripted demo command. It should accept
  natural requests and either act, ask a clarification question, or fail safely.
- The app uses Android speech recognition by default, so the main test path does
  not require cloud STT keys.
- The backend websocket is already deployed at:

  ```text
  wss://milf-revamped.onrender.com/
  ```

- MILF narrates progress out loud instead of silently moving through the phone.
- MILF blocks irreversible actions behind a confirmation gate.
- If something fails, the app should show safe failure copy instead of raw
  websocket or agent errors.

## Android Phone Setup

Use a real Android phone for the most realistic test, especially if you want to
verify WhatsApp behavior.

Requirements:

- Android 11 or newer.
- Internet connection.
- The provided APK installed.
- The target apps for your test installed and logged in, for example WhatsApp or
  YouTube Music.
- Reachable test contacts or media items for the requests you want to try.

## Android Emulator Setup

An emulator works for checking the app setup, backend connection, overlay, native
speech path, typed fallback, and general UI flow.

Limitations:

- WhatsApp may not be installed or logged in.
- Calling may not complete in the same way as a real phone.
- Native speech recognition depends on the emulator image and microphone setup.

Recommended emulator path:

1. Use an emulator with Android 11 or newer.
2. Install the APK.
3. Grant the same permissions as a physical device.
4. If voice input is unreliable, type a request into the overlay text field:

   ```text
   Play Teresa Teng in YouTube Music.
   ```

5. Tap the send button in the overlay.

## Permissions Checklist

The `Main` screen keeps `Start Agent` disabled until setup is ready. In
`Config > Permissions`, every row should say `Ready`.

| Permission    | Why it is needed                                           |
| ------------- | ---------------------------------------------------------- |
| Microphone    | Captures the senior's spoken goal.                         |
| Phone calls   | Allows call-related Android intents and call flow support. |
| Overlay       | Shows the floating MILF helper above other apps.           |
| Accessibility | Lets MILF inspect the screen and perform taps/swipes.      |

Optional shortcut:

| Setup item         | Why it can help                                           |
| ------------------ | --------------------------------------------------------- |
| Assistant shortcut | Lets MILF be summoned as the phone helper where supported. |

If a row still says `Missing` after granting it, return to the MILF app or reopen
`Config` so the app can refresh setup status.

## Backend Configuration

Use `Config > Backend`.

For judge testing, leave the backend target set to `Deployed`.

Expected deployed websocket:

```text
wss://milf-revamped.onrender.com/
```

The `Backend` readiness row should become `Connected`. Render free-tier services
can take a short moment to wake up, so wait and retry `Connect` if the first check
fails.

Use `Custom` only for local development. Common local URLs are:

```text
ws://10.0.2.2:8765
ws://<computer-lan-ip>:8765
```

## Agent Configuration

Use `Config > Agent`.

Recommended judge settings:

- Language: `English`
- Speech input: `Native`
- Memory:

  ```text
  Wei is my grandson. Use WhatsApp video calls for Wei.
  Mei is my daughter. Use WhatsApp messages for Mei.
  ```

Tap `Save memory` after editing the memory field.

The memory hint helps the agent resolve relationship language such as
`my grandson`, nicknames, and preferred apps into concrete phone actions. You can
change the memory to match the contacts and apps on your own test device.

## Try Natural Requests

MILF is intended to handle goal-level requests, not just one memorized sentence.
Try a few requests that match the apps and accounts available on the test device.

Good test prompts include:

```text
I want to see my grandson.
Call Wei on WhatsApp video.
Send Mei a WhatsApp message saying I reached home.
Open WhatsApp and find Wei.
Play Teresa Teng in YouTube Music.
Play something relaxing in YouTube Music.
```

For consequential actions such as starting a call or sending a message, MILF
should pause and ask for approval before the final tap. If the request is
ambiguous, MILF should ask a clarification question or stop safely instead of
guessing.

## Reference Scenario

Use this script if you want a repeatable baseline after trying your own prompts.

1. Confirm backend status is `Connected`.
2. Confirm all setup items are `Ready`.
3. Make sure WhatsApp is installed, logged in, and has the intended contact.
4. Return to MILF and tap `Start Agent`.
5. Tap the floating helper if it is collapsed.
6. Tap the microphone.
7. Say:

   ```text
   I want to see my grandson.
   ```

8. If needed, tap the microphone again to stop listening.
9. Wait while MILF thinks and acts.
10. Confirm the action only when the prompt names the right call target.
11. Verify MILF proceeds only after approval.

Typed fallback:

1. Tap the floating helper.
2. Type:

   ```text
   Send Mei a WhatsApp message saying I reached home.
   ```

3. Tap send.

## Expected States

| State      | What testers should see                                                                            |
| ---------- | -------------------------------------------------------------------------------------------------- |
| Ready      | Floating helper is available; text field says `Ask MILF to do something`.                          |
| Listening  | The overlay says `Listening...` after tapping the microphone.                                      |
| Thinking   | The overlay says `Thinking...` after the command is captured.                                      |
| Acting     | MILF may move through apps while narrating progress.                                               |
| Confirming | MILF asks for approval before a final call, send, account change, or similar consequential action. |
| Failure    | MILF says it is having trouble and returns to a safe state.                                        |

## Troubleshooting

### `Start Agent` Is Disabled

Open `Config` and check:

- All permission rows say `Ready`.
- `Backend` says `Connected`.
- A language is selected in `Agent`.

### Backend Does Not Connect

- Confirm `Deployed` is selected.
- Wait 30-60 seconds in case Render is waking the service.
- Tap `Connect` again.
- Confirm the device has internet access.
- If testing local development, switch to `Custom` and use a `ws://` URL, not
  `wss://`.

### Voice Input Does Not Capture Speech

- Confirm microphone permission is granted.
- Speak after the overlay says `Listening...`.
- Try the typed fallback in the overlay.
- On emulator, check host microphone passthrough or use typed fallback.

### Overlay Does Not Appear

- Confirm overlay permission is granted.
- Tap `Start Agent` again from the `Main` screen.
- Check that Android has not blocked the foreground service notification.
- On some OEM Android builds, disable battery saver for the test.

### Accessibility Actions Do Not Work

- Confirm `MILF phone control` is enabled in Android Accessibility settings.
- Reopen the MILF app after enabling accessibility.
- Keep the phone unlocked.
- Open the target app before running the request.

### App-Specific Request Does Not Complete

- Confirm the target app is installed and logged in.
- Confirm the target contact, chat, media item, or control can be found manually.
- Add or adjust the agent memory hint so the contact, nickname, or app preference
  is explicit.
- Try the typed fallback to remove speech recognition as a variable.

## Optional Repository Commands

These are only needed if a tester wants to build from source.

Build and test the Android app:

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The debug APK is produced at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Run backend tests:

```bash
cd backend
PYTHONPATH=. pytest -v
```

Run the backend locally:

```bash
cd backend
PYTHONPATH=. python -m milf.server
```

Local backend defaults to:

```text
ws://0.0.0.0:8765
```

Use `ws://10.0.2.2:8765` from an Android emulator.

## Current Release

The current published judge build is:

```text
MILF v0.1.0 beta
```

Release page:

```text
https://github.com/S-W-Leong/milf-revamped/releases/tag/milf-v0.1.0
```

APK asset:

```text
milf-v0.1.0.apk
```
