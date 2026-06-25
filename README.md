# MILF - Make Mobile Interfaces Less Frustrating

MILF is a voice-first Android accessibility agent for SEA seniors. A senior speaks a goal in natural language, the backend agent turns that intent into mobile actions, and the Android client executes those actions through Accessibility APIs while narrating progress and asking for confirmation before irreversible steps.

One reference flow is:

> "I want to see my grandson."

MILF records the request, routes it to the backend, navigates the phone toward the WhatsApp video-call flow, asks for confirmation, and only proceeds after approval. The same interaction model is meant for broader natural phone requests such as finding contacts, opening apps, sending messages, and playing media.

## Repository Structure

```text
.
|-- android/                  # Native Android client
|-- assets/                   # Brand and demo assets
|-- backend/                  # Python websocket backend and tests
|-- docs/                     # Vision, plans, runbooks, and specs
`-- AGENTS.md                 # Local agent and repository guidance
```

Key docs:

- `docs/VISION.md` - product narrative, positioning, demo strategy, and build priorities.
- `docs/tester-usage-guide.md` - judge/tester setup, APK usage, emulator notes, and release guidance.
- `docs/mobilerun_contract.md` - recorded MobileRun driver contract.
- `docs/superpowers/plans/` - implementation plans for backend, Android, and UX overlay work.

## Prerequisites

Backend:

- Python 3.11
- `uv`
- Access to the `.venv/` virtual environment, or permission to create one
- OpenAI API access for real agent runs

Android:

- JDK 17
- Android SDK with API 35 installed
- Android device or emulator for install/demo runs
- Accessibility permission enabled for the MILF app on the target device

## Backend Setup

From the repository root:

```bash
uv venv --python 3.11 .venv
source .venv/bin/activate
uv pip install mobilerun llama-index-llms-openai websockets "pydantic>=2" httpx pytest pytest-asyncio
```

Run the backend for Android native-STT demos:

```bash
cd backend
PYTHONPATH=. python -m milf.server
```

By default, the websocket server listens on `0.0.0.0:8765`.

Use these websocket URLs in the Android app:

```text
ws://<Mac LAN IP>:8765
ws://10.0.2.2:8765
```

Use `10.0.2.2` for Android emulator runs.

### Render Deployment

The backend can deploy to Render from `render.yaml` at the repository root.
Render uses `backend/` as the service root, installs `backend/requirements.txt`,
and starts the websocket server with the platform-provided `$PORT`.

Required secret:

```text
OPENAI_API_KEY
```

The deployed Android websocket URL should use `wss`. The current judge demo
backend is:

```text
wss://milf-revamped.onrender.com/
```

### Backend Environment Variables

```text
OPENAI_API_KEY
OPENAI_MODEL
MILF_STT_BACKEND
MILF_MOCK_TRANSCRIPT
MILF_WS_HOST
MILF_WS_PORT
ILMU_API_KEY
ILMU_API_URL
MERALION_API_KEY
MERALION_API_URL
```

The Android app uses native on-device speech recognition for the main mic flow and sends the transcript as a `TextGoal`, so ILMU/MERaLiON keys are not required for device demos. Backend audio STT defaults to `MILF_STT_BACKEND=router` for ILMU/MERaLiON. Set `MILF_STT_BACKEND=mock` only when you want local smoke tests or legacy audio-upload clients to use `MILF_MOCK_TRANSCRIPT`.

The app's Agent config tab can switch the mic input between `Native` and `Backend STT`. `Native` uses Android speech recognition and sends text; `Backend STT` uploads audio and uses the backend STT setting above.

## Android Setup

Build and run tests from the Android project directory:

```bash
cd android
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Install the debug APK on a connected emulator or Android phone:

```bash
cd android
./gradlew :app:installDebug
```

On the device:

1. Open Android Accessibility settings.
2. Enable `MILF phone control`.
3. Grant microphone permission when prompted.
4. Point the app at the backend websocket URL.
5. Keep WhatsApp logged in and ready for the demo contact.

If the local machine requires an explicit JDK path, use:

```bash
cd android
./gradlew -Dorg.gradle.java.home=/opt/homebrew/Cellar/openjdk@17/17.0.18/libexec/openjdk.jdk/Contents/Home :app:assembleDebug
```

Adjust the JDK path for your machine.

## Running Tests

Backend:

```bash
source .venv/bin/activate
cd backend
PYTHONPATH=. pytest -v
```

Android:

```bash
cd android
./gradlew :app:testDebugUnitTest
```

Full Android build check:

```bash
cd android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

## Demo Flow

For a judge-facing walkthrough with Android phone and emulator setup, see
`docs/tester-usage-guide.md`.

For local development, start the backend:

```bash
cd backend
PYTHONPATH=. python -m milf.server
```

For the deployed judge demo, use the Render backend configured in the app:

```text
wss://milf-revamped.onrender.com/
```

Then install or open the Android app:

```bash
cd android
./gradlew :app:installDebug
```

On the Android device:

1. Open MILF and complete `Config`.
2. Confirm all permissions are ready and the backend is connected.
3. Tap `Start Agent`.
4. Tap the floating helper microphone.
5. Say a natural phone request, for example: `I want to see my grandson.`
6. Tap the microphone again to stop listening if needed.
7. Wait for narration and app navigation.
8. Approve any confirmation prompt only if the action target is correct.


## Development Notes

- Backend code lives in `backend/milf/`.
- Backend tests live in `backend/tests/`.
- Android app code lives in `android/app/src/main/java/ai/milf/client/`.
- Android unit tests live in `android/app/src/test/java/ai/milf/client/`.
- Keep the websocket protocol aligned between `backend/milf/protocol.py` and `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`.
- Do not commit secrets, `.env`, `.venv/`, Android build outputs, or local SDK paths.

## Security and Safety

MILF is designed around explicit user intent and confirmation. The app should narrate important actions, and calls, sends, payments, or other irreversible operations must wait for a confirmation request and response before proceeding.
