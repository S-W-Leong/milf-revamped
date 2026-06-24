# MILF Revamped

MILF, short for Make Mobile Interfaces Less Frustrating, is a voice-first Android accessibility agent for helping SEA seniors complete a known WhatsApp video-call flow with narration and confirmation.

## Current Status

This repository is on the `UPGRADES` hardening branch. The backend and Android client now have authenticated transport, bounded confirmation policy, Android local action policy, failure handling, production setup docs, and CI gates. The code is not production-ready until the release checklist and final production-readiness audit pass.

## Architecture

- Android app: records the senior's spoken goal, connects to the backend, speaks narration, renders confirmation, and exposes a constrained AccessibilityService action surface.
- Backend: transcribes audio, runs MobileRun with OpenAI, sends actions over websocket, and enforces confirmation and session policy.

## Prerequisites

- Python 3.11 or 3.12
- JDK 17
- Android SDK with API 35
- Android emulator or physical device for install/rehearsal

## Backend Setup

```powershell
cd backend
python -m pip install -e .[test]
copy ..\.env.example ..\.env
python -m pytest
```

## Android Setup

Create `android/local.properties` locally or configure `ANDROID_HOME`. Do not commit `local.properties`.

```powershell
cd android
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Local Demo Server

```powershell
cd backend
$env:MILF_STT_BACKEND='mock'
$env:MILF_WS_HOST='127.0.0.1'
$env:MILF_DEVICE_TOKEN='dev-token'
python -m milf.server
```

Use `ws://10.0.2.2:8765?token=dev-token` only for emulator debug builds. Production deployments must use authenticated `wss://` websocket transport.

## CI

GitHub Actions runs three required gates:

- backend tests from a clean Python 3.11 install with `python -m pip install -e .[test]` and `python -m pytest`
- Android unit tests and debug build with JDK 17 and Android SDK API 35
- repository hygiene that fails if `.env`, `android/local.properties`, `.idea/`, or `android/.idea/` are tracked

## Production Rules

- Use `wss://` behind TLS.
- Require `MILF_DEVICE_TOKEN` or a stronger session-pairing token.
- Never expose an unauthenticated phone-control websocket.
- Do not log audio, transcripts, screenshots, tokens, or full UI trees.
