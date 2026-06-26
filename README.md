# MILF - Make Mobile Interfaces Less Frustrating

MILF is a voice-first Android accessibility agent for SEA seniors. Say what you
want to do on the phone, and MILF tries to carry the phone's mental model for you:
it reads the current screen, navigates apps through Android Accessibility APIs,
narrates what it is doing, and asks for confirmation before consequential actions
like calls or messages.

You can try natural phone requests such as:

```text
I want to see my grandson.
Call Wei on WhatsApp video.
Send Mei a WhatsApp message saying I reached home.
Open WhatsApp and find Wei.
Play Teresa Teng in YouTube Music.
```

The grandson video-call request is our reference scenario because it shows the
full loop: relationship memory, app navigation, spoken narration, and the safety
confirmation before starting the call.

## Try The APK

The judge/tester build is published as a GitHub release:

[MILF v0.1.0 beta](https://github.com/S-W-Leong/milf-revamped/releases/tag/milf-v0.1.0)

Download this asset from the release:

```text
milf-v0.1.0.apk
```

Then install it on an Android phone or emulator. Android may ask you to allow
installs from the browser or Files app. That setting is usually called
`Install unknown apps`, `Allow from this source`, or `Unknown app installs`.

For the full walkthrough, use [docs/tester-usage-guide.md](docs/tester-usage-guide.md).
It covers phone installation, emulator installation, permissions, backend setup,
sample prompts, expected behavior, and troubleshooting.

## Tester Quick Start

1. Install `milf-v0.1.0.apk`.
2. Open `MILF`.
3. Tap `Config`.
4. Grant the required setup items:
   - `Microphone`
   - `Phone calls`
   - `Overlay`
   - `Accessibility`
5. In `Backend`, keep `Deployed` selected.
6. Wait for the backend status to show `Connected`.
7. In `Agent`, keep speech input on `Native`.
8. Add memory hints if you want relationship-based requests:

   ```text
   Wei is my grandson. Use WhatsApp video calls for Wei.
   Mei is my daughter. Use WhatsApp messages for Mei.
   ```

9. Tap `Save memory`.
10. Return to `Main` and tap `Start Agent`.
11. Tap the floating MILF control, tap the microphone, and speak a request.
12. Approve any confirmation prompt only if the action target is correct.

`Assistant shortcut` setup is optional. It lets MILF be selected as a phone
assistant where supported, but the floating helper works without it.

The deployed backend is already live:

```text
wss://milf-revamped.onrender.com/
```

You do not need to run the backend locally for judge testing.

## What To Look For

- MILF accepts natural goal-level requests, not only one scripted phrase.
- It can use voice input or the typed fallback in the floating overlay.
- It narrates progress so the user is not left wondering what the phone is doing.
- It asks for confirmation before final calls, sends, account changes, or similar
  consequential actions.
- If the request is ambiguous or something blocks the task, it should ask for
  clarification or fail safely instead of showing raw backend errors.

## Phone Or Emulator?

A real Android phone is best for testing WhatsApp calls and real installed apps.
Use Android 11 or newer, keep the phone unlocked, and make sure the target apps
and contacts are available.

An Android emulator works for setup, backend connection, overlay behavior, typed
fallback, and general UI flow. Some emulator images may not support native speech
recognition or real WhatsApp calling cleanly. If speech input is unreliable, type
the request into the overlay instead.

To install on an emulator from your computer:

```bash
adb install -r milf-v0.1.0.apk
```

## Repository Map

```text
.
|-- android/                  # Native Android client
|-- assets/                   # Brand and demo assets
|-- backend/                  # Python websocket backend and tests
|-- config/app_cards/         # App-specific agent guidance
|-- docs/                     # Vision, tester guide, architecture, specs, plans
`-- AGENTS.md                 # Local agent and repository guidance
```

Key docs:

- [docs/tester-usage-guide.md](docs/tester-usage-guide.md) - tester setup and usage guide.
- [docs/VISION.md](docs/VISION.md) - product narrative, positioning, and build priorities.
- [docs/architecture.md](docs/architecture.md) - intent orchestration architecture.
- [docs/mobilerun_contract.md](docs/mobilerun_contract.md) - recorded MobileRun driver contract.

## Developer Notes

The rest of this README is for people building or modifying the project. Testers
using the released APK can stop at the guide above.

### Android Build

Requirements:

- JDK 17
- Android SDK with API 35 installed
- Android phone or emulator

Build and run Android tests:

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Install a local debug build:

```bash
cd android
./gradlew :app:installDebug
```

The debug APK is produced at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

If your machine requires an explicit JDK path:

```bash
cd android
./gradlew -Dorg.gradle.java.home=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home :app:assembleDebug
```

Adjust the JDK path for your machine.

### Backend Development

Requirements:

- Python 3.11
- `uv`
- OpenAI API access for real agent runs

Install dependencies:

```bash
uv venv --python 3.11 .venv
source .venv/bin/activate
uv pip install -r backend/requirements.txt
```

Run the backend locally:

```bash
cd backend
PYTHONPATH=. python -m milf.server
```

By default, the websocket server listens on `0.0.0.0:8765`.

Use these websocket URLs in the Android app for local development:

```text
ws://<Mac LAN IP>:8765
ws://10.0.2.2:8765
```

Use `10.0.2.2` for Android emulator runs.

Run backend tests:

```bash
source .venv/bin/activate
cd backend
PYTHONPATH=. pytest -v
```

### Render Deployment

The backend deploys to Render from [render.yaml](render.yaml). Render uses
`backend/` as the service root, installs [backend/requirements.txt](backend/requirements.txt),
and starts the websocket server with the platform-provided `$PORT`.

Required secret:

```text
OPENAI_API_KEY
```

Optional backend model settings:

```text
OPENAI_MODEL          # default MobileRun and perceive fallback model; defaults to gpt-4o
MILF_INTENT_MODEL     # intent-router model; defaults to gpt-4o-mini
MILF_PERCEIVE_MODEL   # read-only screen-description model; defaults to OPENAI_MODEL, then gpt-4o
```

Current deployed websocket:

```text
wss://milf-revamped.onrender.com/
```

Backend environment variables:

```text
OPENAI_API_KEY
OPENAI_MODEL
MILF_INTENT_MODEL
MILF_PERCEIVE_MODEL
MILF_STT_BACKEND
MILF_MOCK_TRANSCRIPT
MILF_WS_HOST
MILF_WS_PORT
ILMU_API_KEY
ILMU_API_URL
MERALION_API_KEY
MERALION_API_URL
```

The Android app uses native on-device speech recognition for the main mic flow
and sends the transcript as a `TextGoal`, so ILMU/MERaLiON keys are not required
for normal device demos. Backend audio STT is still available for the `Backend
STT` input mode.

## Security And Safety

MILF is designed around explicit user intent and confirmation. The app should
narrate important actions, and calls, sends, payments, account changes, or other
irreversible operations must wait for a confirmation request and response before
proceeding.

Never commit secrets, `.env`, `.venv/`, Android build outputs, local SDK paths,
or generated cache files.
