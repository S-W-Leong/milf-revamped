# MILF - Make Mobile Interfaces Less Frustrating

MILF is a voice-first Android accessibility agent for SEA seniors. A senior speaks a goal in natural language, the backend agent turns that intent into mobile actions, and the Android client executes those actions through Accessibility APIs while narrating progress and asking for confirmation before irreversible steps.

The current hero flow is:

> "I want to see my grandson."

MILF records the request, routes it to the backend, navigates the phone toward the WhatsApp video-call flow, asks for confirmation, and only proceeds after approval.

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
- `docs/android-demo-runbook.md` - device setup and live demo checklist.
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

Run the backend with the mock STT adapter:

```bash
cd backend
PYTHONPATH=. MILF_STT_BACKEND=mock python -m milf.server
```

By default, the websocket server listens on `0.0.0.0:8765`.

Use these websocket URLs in the Android app:

```text
ws://<Mac LAN IP>:8765
ws://10.0.2.2:8765
```

Use `10.0.2.2` for Android emulator runs.

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

`MILF_STT_BACKEND=mock` is enough for local smoke tests. Use `MILF_STT_BACKEND=router` for ILMU/MERaLiON routing, with the corresponding API keys and URLs set.

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

1. Start the backend:

   ```bash
   cd backend
   PYTHONPATH=. MILF_STT_BACKEND=mock python -m milf.server
   ```

2. Install the Android app:

   ```bash
   cd android
   ./gradlew :app:installDebug
   ```

3. On the Android device, enable the accessibility service and open MILF.
4. Select English, Chinese, or Cantonese.
5. Tap Speak.
6. Say: `I want to see my grandson.`
7. Tap Stop.
8. Wait for narration and WhatsApp navigation.
9. Approve the confirmation prompt before the call connects.

For rehearsal notes, device matrix, and backup-demo guidance, see `docs/android-demo-runbook.md`.

## Development Notes

- Backend code lives in `backend/milf/`.
- Backend tests live in `backend/tests/`.
- Android app code lives in `android/app/src/main/java/ai/milf/client/`.
- Android unit tests live in `android/app/src/test/java/ai/milf/client/`.
- Keep the websocket protocol aligned between `backend/milf/protocol.py` and `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`.
- Do not commit secrets, `.env`, `.venv/`, Android build outputs, or local SDK paths.

## Security and Safety

MILF is designed around explicit user intent and confirmation. The app should narrate important actions, and calls, sends, payments, or other irreversible operations must wait for a confirmation request and response before proceeding.
