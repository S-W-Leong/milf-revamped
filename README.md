# MILF Revamped

MILF, short for Make Mobile Interfaces Less Frustrating, is a voice-first Android accessibility agent for helping SEA seniors complete a known WhatsApp video-call flow with narration and confirmation.

## Current Status

This repository is on the `UPGRADES` hardening branch. The code is not production-ready until the release checklist passes. The demo path exists, but transport authentication, confirmation policy, CI, privacy docs, and clean setup are being hardened.

## Architecture

- Android app: records the senior's spoken goal, connects to the backend, speaks narration, renders confirmation, and exposes a constrained AccessibilityService action surface.
- Backend: transcribes audio, runs MobileRun with OpenAI, sends actions over websocket, and enforces confirmation and session policy.

## Prerequisites

- Python 3.11 or 3.12
- JDK 17
- Android SDK with API 35
- Android emulator or physical device for install/rehearsal

## Backend Setup

Task 2 will add `backend/pyproject.toml`. After that lands, the target clean setup command is:

```powershell
cd backend
python -m pip install -e .[test]
copy ..\.env.example ..\.env
python -m pytest
```

Until Task 2 lands, clean backend setup is not complete. For the current branch state, run tests only when the existing local `.venv` already has the backend dependencies installed:

```powershell
cd backend
copy ..\.env.example ..\.env
..\.venv\Scripts\python -m pytest
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

Use `ws://10.0.2.2:8765?token=dev-token` only for emulator debug builds. Until Task 3 adds websocket authentication, keep the demo server bound to `127.0.0.1`.

## Production Rules

- Use `wss://` behind TLS.
- Require `MILF_DEVICE_TOKEN` or a stronger session-pairing token.
- Never expose an unauthenticated phone-control websocket.
- Do not log audio, transcripts, screenshots, tokens, or full UI trees.
