# Production Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the current MILF backend and Android client reproducible, authenticated, policy-enforced, failure-bounded, documented, and CI-gated for a production foundation.

**Architecture:** Keep the existing Python backend plus Kotlin Android split. Add a backend settings layer, authenticated websocket sessions, protocol error handling, request lifecycle timeouts, confirmation policy, Android local action policy, release-safe networking defaults, repo hygiene, security/privacy docs, and CI gates.

**Tech Stack:** Python 3.11-3.12, Pydantic v2, websockets, httpx, MobileRun, pytest, pytest-asyncio, Kotlin, Android Gradle Plugin 8.7.2, Kotlin 2.0.0, JDK 17, GitHub Actions.

---

## File Structure

- Modify: `.gitignore` - ignore local SDK, IDE, env, cache, and build files.
- Remove from git index: `.idea/`, `android/.idea/`, `android/local.properties`.
- Create: `README.md` - authoritative setup and current status.
- Create: `.env.example` - backend env sample.
- Create: `SECURITY.md` - security policy and production rules.
- Create: `PRIVACY.md` - audio, transcript, UI tree, screenshot, and retention rules.
- Create: `docs/release-checklist.md` - release gates and rehearsal evidence.
- Create: `backend/pyproject.toml` - backend package metadata and dependencies.
- Create: `backend/milf/settings.py` - typed backend settings.
- Modify: `backend/milf/protocol.py` - controlled decode errors.
- Modify: `backend/milf/server.py` - auth, limits, close codes, settings, cleanup.
- Modify: `backend/milf/connection.py` - timeout and pending failure handling.
- Modify: `backend/milf/confirmation.py` - record approval state.
- Create: `backend/milf/policy.py` - sensitive action policy.
- Modify: `backend/milf/ws_driver.py` - enforce backend policy.
- Modify: `backend/milf/stt.py` - HTTP timeouts and settings integration.
- Modify: `backend/milf/agent_runner.py` - settings integration.
- Add/modify backend tests under `backend/tests/`.
- Create: `android/app/src/main/res/xml/network_security_config.xml` - debug cleartext policy.
- Modify: `android/app/src/main/AndroidManifest.xml` - network config and permission cleanup.
- Modify: `android/app/build.gradle.kts` - BuildConfig fields and release/debug config.
- Create: `android/app/src/main/java/ai/milf/client/security/ClientSecurity.kt`.
- Create: `android/app/src/main/java/ai/milf/client/security/ActionPolicy.kt`.
- Modify: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`.
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`.
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`.
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`.
- Modify Android tests under `android/app/src/test/java/`.
- Create: `.github/workflows/ci.yml`.

## Task 1: Repository Hygiene And Production Docs

**Files:**
- Modify: `.gitignore`
- Delete from index: `.idea/`, `android/.idea/`, `android/local.properties`
- Create: `README.md`
- Create: `.env.example`
- Create: `SECURITY.md`
- Create: `PRIVACY.md`
- Create: `docs/release-checklist.md`

- [ ] **Step 1: Confirm tracked local files**

Run:

```powershell
git ls-files .idea android/.idea android/local.properties
```

Expected: prints the currently tracked local files that must leave git history from this commit forward.

- [ ] **Step 2: Update ignore rules**

Patch `.gitignore` to contain exactly these project-local rules, preserving existing ignored build/cache directories:

```gitignore
.venv/
__pycache__/
*.pyc
.env
.env.*
!.env.example
.DS_Store
.idea/
android/.idea/
android/local.properties
android/.gradle/
android/**/build/
```

- [ ] **Step 3: Remove local-only files from the git index**

Run:

```powershell
git rm -r --cached .idea android/.idea android/local.properties
```

Expected: files are staged as deleted from git, but developers can recreate their local SDK/IDE files.

- [ ] **Step 4: Create `.env.example`**

Add:

```dotenv
# Backend runtime
OPENAI_API_KEY=replace-with-openai-key
OPENAI_MODEL=gpt-4o

# Speech-to-text routing: mock for local tests, router for provider-backed runs
MILF_STT_BACKEND=mock
MILF_MOCK_TRANSCRIPT=I want to see my grandson
ILMU_API_URL=https://example.invalid/ilmu/asr
ILMU_API_KEY=replace-with-ilmu-key
MERALION_API_URL=https://example.invalid/meralion/asr
MERALION_API_KEY=replace-with-meralion-key

# Websocket server
MILF_WS_HOST=127.0.0.1
MILF_WS_PORT=8765
MILF_DEVICE_TOKEN=replace-with-random-dev-token
MILF_ACTION_TIMEOUT_SECONDS=30
MILF_MAX_AUDIO_BYTES=5242880
MILF_WS_MAX_SIZE_BYTES=8388608
MILF_ENV=development
```

- [ ] **Step 5: Create root `README.md`**

Include these exact sections:

```markdown
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

```powershell
cd backend
python -m pip install -e .[test]
copy ..\\.env.example ..\\.env
python -m pytest
```

## Android Setup

Create `android/local.properties` locally or configure `ANDROID_HOME`. Do not commit `local.properties`.

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

## Local Demo Server

```powershell
cd backend
$env:MILF_STT_BACKEND='mock'
$env:MILF_DEVICE_TOKEN='dev-token'
python -m milf.server
```

Use `ws://10.0.2.2:8765?token=dev-token` only for emulator debug builds.

## Production Rules

- Use `wss://` behind TLS.
- Require `MILF_DEVICE_TOKEN` or a stronger session-pairing token.
- Never expose an unauthenticated phone-control websocket.
- Do not log audio, transcripts, screenshots, tokens, or full UI trees.
```

- [ ] **Step 6: Create `SECURITY.md`, `PRIVACY.md`, and `docs/release-checklist.md`**

Each doc must include the concrete rules from `docs/superpowers/specs/2026-06-23-production-readiness-design.md` sections 8.1 through 8.3. The release checklist must include checkboxes for CI, backend tests, Android tests/build, auth pairing, confirmation bypass tests, privacy/security review, and 10-run hero rehearsal.

- [ ] **Step 7: Verify hygiene**

Run:

```powershell
git status --short
git ls-files .idea android/.idea android/local.properties
git check-ignore -v android/local.properties .env
```

Expected: local-only files are staged for removal, `git ls-files` prints nothing for them after staging, and ignore rules explain both ignored paths.

- [ ] **Step 8: Commit**

Run:

```powershell
git add .gitignore README.md .env.example SECURITY.md PRIVACY.md docs/release-checklist.md
git commit -m "docs: add production setup and security baseline"
```

## Task 2: Backend Packaging And Settings

**Files:**
- Create: `backend/pyproject.toml`
- Create: `backend/milf/settings.py`
- Modify: `backend/milf/server.py`
- Modify: `backend/milf/stt.py`
- Modify: `backend/milf/agent_runner.py`
- Create: `backend/tests/test_settings.py`

- [ ] **Step 1: Write settings tests**

Add `backend/tests/test_settings.py`:

```python
import pytest

from milf.settings import Settings, SettingsError


def test_defaults_are_local_and_bounded(monkeypatch):
    monkeypatch.delenv("MILF_WS_HOST", raising=False)
    monkeypatch.delenv("MILF_WS_PORT", raising=False)
    monkeypatch.delenv("MILF_ACTION_TIMEOUT_SECONDS", raising=False)
    settings = Settings.from_env()
    assert settings.ws_host == "127.0.0.1"
    assert settings.ws_port == 8765
    assert settings.action_timeout_seconds == 30.0
    assert settings.ws_max_size_bytes >= settings.max_audio_bytes


def test_router_mode_requires_provider_env(monkeypatch):
    monkeypatch.setenv("MILF_STT_BACKEND", "router")
    for name in ("ILMU_API_URL", "ILMU_API_KEY", "MERALION_API_URL", "MERALION_API_KEY"):
        monkeypatch.delenv(name, raising=False)
    with pytest.raises(SettingsError, match="ILMU_API_URL"):
        Settings.from_env()


def test_production_requires_device_token(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "production")
    monkeypatch.delenv("MILF_DEVICE_TOKEN", raising=False)
    with pytest.raises(SettingsError, match="MILF_DEVICE_TOKEN"):
        Settings.from_env()
```

- [ ] **Step 2: Run settings tests to verify failure**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest tests\\test_settings.py -v
```

Expected: fails with `ModuleNotFoundError: No module named 'milf.settings'`.

- [ ] **Step 3: Add `backend/pyproject.toml`**

Create:

```toml
[project]
name = "milf-backend"
version = "0.1.0"
description = "MILF backend websocket bridge and MobileRun agent runner"
requires-python = ">=3.11,<3.13"
dependencies = [
  "httpx>=0.28,<0.29",
  "llama-index-llms-openai>=0.3,<0.4",
  "mobilerun>=0.6.7,<0.7",
  "pydantic>=2.10,<3",
  "websockets>=15,<17",
]

[project.optional-dependencies]
test = [
  "pytest>=9,<10",
  "pytest-asyncio>=1.4,<2",
]

[tool.pytest.ini_options]
asyncio_mode = "auto"
testpaths = ["tests"]

[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[tool.setuptools.packages.find]
include = ["milf*"]
```

- [ ] **Step 4: Implement settings**

Create `backend/milf/settings.py`:

```python
from __future__ import annotations

import os
from dataclasses import dataclass


class SettingsError(ValueError):
    pass


@dataclass(frozen=True)
class Settings:
    env: str
    ws_host: str
    ws_port: int
    ws_max_size_bytes: int
    device_token: str | None
    action_timeout_seconds: float
    max_audio_bytes: int
    stt_backend: str
    mock_transcript: str
    openai_model: str
    ilmu_api_url: str | None
    ilmu_api_key: str | None
    meralion_api_url: str | None
    meralion_api_key: str | None

    @classmethod
    def from_env(cls) -> "Settings":
        env = os.environ.get("MILF_ENV", "development").lower()
        max_audio_bytes = _int_env("MILF_MAX_AUDIO_BYTES", 5_242_880)
        ws_max_size_bytes = _int_env("MILF_WS_MAX_SIZE_BYTES", 8_388_608)
        if ws_max_size_bytes < max_audio_bytes:
            raise SettingsError("MILF_WS_MAX_SIZE_BYTES must be >= MILF_MAX_AUDIO_BYTES")

        settings = cls(
            env=env,
            ws_host=os.environ.get("MILF_WS_HOST", "127.0.0.1"),
            ws_port=_int_env("MILF_WS_PORT", 8765),
            ws_max_size_bytes=ws_max_size_bytes,
            device_token=_optional("MILF_DEVICE_TOKEN"),
            action_timeout_seconds=_float_env("MILF_ACTION_TIMEOUT_SECONDS", 30.0),
            max_audio_bytes=max_audio_bytes,
            stt_backend=os.environ.get("MILF_STT_BACKEND", "mock").lower(),
            mock_transcript=os.environ.get("MILF_MOCK_TRANSCRIPT", "I want to see my grandson"),
            openai_model=os.environ.get("OPENAI_MODEL", "gpt-4o"),
            ilmu_api_url=_optional("ILMU_API_URL"),
            ilmu_api_key=_optional("ILMU_API_KEY"),
            meralion_api_url=_optional("MERALION_API_URL"),
            meralion_api_key=_optional("MERALION_API_KEY"),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.action_timeout_seconds <= 0:
            raise SettingsError("MILF_ACTION_TIMEOUT_SECONDS must be positive")
        if self.max_audio_bytes <= 0:
            raise SettingsError("MILF_MAX_AUDIO_BYTES must be positive")
        if self.stt_backend not in {"mock", "router"}:
            raise SettingsError(f"Unknown MILF_STT_BACKEND: {self.stt_backend}")
        if self.env == "production" and not self.device_token:
            raise SettingsError("MILF_DEVICE_TOKEN is required in production")
        if self.stt_backend == "router":
            missing = [
                name
                for name, value in {
                    "ILMU_API_URL": self.ilmu_api_url,
                    "ILMU_API_KEY": self.ilmu_api_key,
                    "MERALION_API_URL": self.meralion_api_url,
                    "MERALION_API_KEY": self.meralion_api_key,
                }.items()
                if not value
            ]
            if missing:
                raise SettingsError(f"Missing required router setting: {missing[0]}")


def _optional(name: str) -> str | None:
    value = os.environ.get(name)
    return value if value else None


def _int_env(name: str, default: int) -> int:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise SettingsError(f"{name} must be an integer") from exc


def _float_env(name: str, default: float) -> float:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return float(value)
    except ValueError as exc:
        raise SettingsError(f"{name} must be a number") from exc
```

- [ ] **Step 5: Wire settings into backend factories**

Update `make_stt()` to accept an optional `Settings` object. Update `build_agent()` to accept optional settings and use `settings.openai_model`. Update `serve()` to use `Settings.from_env()` for host, port, and websocket max size.

- [ ] **Step 6: Run backend tests**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest
```

Expected: all backend tests pass.

- [ ] **Step 7: Commit**

Run:

```powershell
git add backend/pyproject.toml backend/milf/settings.py backend/milf/server.py backend/milf/stt.py backend/milf/agent_runner.py backend/tests/test_settings.py
git commit -m "feat: add backend settings and packaging"
```

## Task 3: Backend Websocket Auth And Protocol Errors

**Files:**
- Modify: `backend/milf/protocol.py`
- Modify: `backend/milf/server.py`
- Modify: `backend/tests/test_protocol.py`
- Modify: `backend/tests/test_mock_app.py`

- [ ] **Step 1: Write protocol and auth tests**

Add tests for:

- `decode("{")` raises a custom protocol decode error.
- unknown message type raises the same custom error with a safe message.
- websocket without token is rejected when `MILF_DEVICE_TOKEN` is set.
- invalid first frame closes with protocol error.
- invalid base64 closes with protocol error.
- audio payload larger than `MILF_MAX_AUDIO_BYTES` closes with message-too-big behavior.

- [ ] **Step 2: Run new tests to verify failure**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest tests\\test_protocol.py tests\\test_mock_app.py -v
```

Expected: fails on missing auth and decode error behavior.

- [ ] **Step 3: Implement decode and server hardening**

Add a `ProtocolDecodeError` in `protocol.py`. Add a token check in `server.py` that reads the token from the websocket path query string named `token`. Keep this token path documented for the Android client until a stronger pairing protocol replaces it.

- [ ] **Step 4: Run targeted tests**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest tests\\test_protocol.py tests\\test_mock_app.py -v
```

Expected: targeted tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add backend/milf/protocol.py backend/milf/server.py backend/tests/test_protocol.py backend/tests/test_mock_app.py
git commit -m "feat: secure backend websocket entrypoint"
```

## Task 4: Backend Request Lifecycle And Confirmation Policy

**Files:**
- Create: `backend/milf/policy.py`
- Modify: `backend/milf/connection.py`
- Modify: `backend/milf/confirmation.py`
- Modify: `backend/milf/ws_driver.py`
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/tests/test_connection.py`
- Create: `backend/tests/test_policy.py`
- Modify: `backend/tests/test_confirmation.py`
- Modify: `backend/tests/test_ws_driver.py`

- [ ] **Step 1: Write lifecycle tests**

Add tests proving:

- `send_action()` times out when no `ActionResult` arrives.
- `request_confirmation()` times out when no `ConfirmResponse` arrives.
- `fail_pending(RuntimeError("disconnected"))` causes pending waits to raise.
- late responses after timeout or disconnect are ignored.

- [ ] **Step 2: Write policy tests**

Add tests proving:

- `get_ui_tree` is allowed before confirmation.
- `tap`, `input_text`, `press_button`, `start_app`, and `screenshot` are blocked before confirmation.
- after approval, a sensitive action succeeds.
- after the freshness window expires, sensitive actions are blocked again.

- [ ] **Step 3: Run tests to verify failure**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest tests\\test_connection.py tests\\test_policy.py tests\\test_confirmation.py tests\\test_ws_driver.py -v
```

Expected: fails on missing timeout and policy behavior.

- [ ] **Step 4: Implement lifecycle and policy**

Implement:

- `AppConnection(timeout_seconds: float = 30.0)`
- `AppConnection.fail_pending(error: BaseException)`
- `ConfirmationPolicy.record_approval(summary, lang)`
- `ConfirmationPolicy.require_allowed(action_name, args)`
- `WebSocketDriver` policy check before `_send_supported()`

- [ ] **Step 5: Run backend suite**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest
```

Expected: all backend tests pass.

- [ ] **Step 6: Commit**

Run:

```powershell
git add backend/milf/policy.py backend/milf/connection.py backend/milf/confirmation.py backend/milf/ws_driver.py backend/milf/agent_runner.py backend/tests/test_connection.py backend/tests/test_policy.py backend/tests/test_confirmation.py backend/tests/test_ws_driver.py
git commit -m "feat: enforce backend confirmation policy"
```

## Task 5: Android Transport Security

**Files:**
- Create: `android/app/src/main/res/xml/network_security_config.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/ai/milf/client/security/ClientSecurity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Create: `android/app/src/test/java/ai/milf/client/security/ClientSecurityTest.kt`
- Modify: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`

- [ ] **Step 1: Write Android security tests**

Tests must prove:

- debug allows `ws://10.0.2.2:8765`.
- release rules reject cleartext `ws://`.
- `wss://` is accepted.
- token is appended or sent according to the chosen websocket token transport.
- missing token produces a visible view-model failure before connection.

- [ ] **Step 2: Run Android tests to verify failure**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest --tests "ai.milf.client.security.ClientSecurityTest"
```

Expected: fails because `ClientSecurity` does not exist.

- [ ] **Step 3: Implement client security**

Implement a small pure Kotlin security helper that validates websocket URLs and injects a token query parameter named `token`. Use `BuildConfig.DEBUG` and build config fields for defaults. Do not log the token.

- [ ] **Step 4: Run Android unit tests**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest
```

Expected: Android unit tests pass on a machine with JDK 17 and Android SDK.

- [ ] **Step 5: Commit**

Run:

```powershell
git add android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/app/src/main/res/xml/network_security_config.xml android/app/src/main/java/ai/milf/client/security/ClientSecurity.kt android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/test/java/ai/milf/client/security/ClientSecurityTest.kt android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt
git commit -m "feat: secure Android websocket configuration"
```

## Task 6: Android Local Action Policy And Accessibility Minimization

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/security/ActionPolicy.kt`
- Modify: `android/app/src/main/java/ai/milf/client/accessibility/ActionDispatcher.kt`
- Modify: `android/app/src/main/java/ai/milf/client/accessibility/UiTreeSerializer.kt`
- Modify: `android/app/src/main/java/ai/milf/client/accessibility/ScreenshotCapture.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/test/java/ai/milf/client/accessibility/ActionDispatcherTest.kt`
- Create: `android/app/src/test/java/ai/milf/client/security/ActionPolicyTest.kt`

- [ ] **Step 1: Write local policy tests**

Tests must prove:

- `get_ui_tree` can run before confirmation.
- `tap`, `input_text`, `press_button`, `start_app`, and `screenshot` are rejected before local confirmation approval.
- approval allows one sensitive action within freshness.
- stale approval rejects sensitive actions.

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest --tests "ai.milf.client.security.ActionPolicyTest"
```

Expected: fails because `ActionPolicy` does not exist.

- [ ] **Step 3: Implement policy and dispatcher integration**

`ActionDispatcher` must take an `ActionPolicy` dependency and call it before device actions. `MainViewModel` must record approval when a confirmation is approved. `UiTreeSerializer` must keep navigation fields while excluding empty disabled nodes.

- [ ] **Step 4: Run Android tests**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest
```

Expected: Android unit tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add android/app/src/main/java/ai/milf/client/security/ActionPolicy.kt android/app/src/main/java/ai/milf/client/accessibility/ActionDispatcher.kt android/app/src/main/java/ai/milf/client/accessibility/UiTreeSerializer.kt android/app/src/main/java/ai/milf/client/accessibility/ScreenshotCapture.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/test/java/ai/milf/client/accessibility/ActionDispatcherTest.kt android/app/src/test/java/ai/milf/client/security/ActionPolicyTest.kt
git commit -m "feat: enforce Android action policy"
```

## Task 7: Android Lifecycle, Permission, And UI Resilience

**Files:**
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`
- Modify: `android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt`
- Modify: `android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt`
- Modify: `android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt`
- Modify: `android/app/src/test/java/ai/milf/client/MainViewModelTest.kt`
- Modify: `android/app/src/test/java/ai/milf/client/audio/ConfirmationVoiceParserTest.kt`

- [ ] **Step 1: Write ViewModel failure tests**

Tests must prove:

- microphone denial sets a visible status.
- recorder start failure sets a visible status and does not mark recording true.
- recorder stop failure sets a visible status and stops running state.
- confirmation speech error sets a visible status.
- websocket send failure on confirmation keeps the user informed.

- [ ] **Step 2: Run tests to verify failure**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest --tests "ai.milf.client.MainViewModelTest"
```

Expected: fails on missing failure paths.

- [ ] **Step 3: Implement failure handling and scrollable UI**

Wrap recorder and websocket calls in recoverable state updates. Add a scroll container to the main UI and confirmation overlay so long text and large font scale keep buttons reachable.

- [ ] **Step 4: Run Android tests**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest
```

Expected: Android unit tests pass.

- [ ] **Step 5: Commit**

Run:

```powershell
git add android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/main/java/ai/milf/client/MilfUi.kt android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt android/app/src/test/java/ai/milf/client/MainViewModelTest.kt android/app/src/test/java/ai/milf/client/audio/ConfirmationVoiceParserTest.kt
git commit -m "fix: harden Android runtime failures"
```

## Task 8: CI And Release Gates

**Files:**
- Create: `.github/workflows/ci.yml`
- Modify: `docs/release-checklist.md`
- Modify: `README.md`

- [ ] **Step 1: Add CI workflow**

Create `.github/workflows/ci.yml` with jobs:

- backend: Python 3.11, `python -m pip install -e .[test]`, `python -m pytest`
- android: JDK 17, Gradle cache, `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- hygiene: fail if `.env`, `android/local.properties`, `.idea/`, or `android/.idea/` are tracked

- [ ] **Step 2: Run hygiene command locally**

Run:

```powershell
$tracked = git ls-files .env android/local.properties .idea android/.idea
if ($tracked) { Write-Error "Local-only files tracked: $tracked"; exit 1 }
```

Expected: exits successfully.

- [ ] **Step 3: Run local backend verification**

Run:

```powershell
cd backend
..\\.venv\\Scripts\\python -m pytest
```

Expected: backend tests pass.

- [ ] **Step 4: Run Android verification where toolchain is available**

Run:

```powershell
cd android
.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: passes on a machine with JDK 17 and Android SDK. If local machine lacks Java, record the exact blocked output in the final branch summary.

- [ ] **Step 5: Commit**

Run:

```powershell
git add .github/workflows/ci.yml docs/release-checklist.md README.md
git commit -m "ci: add production readiness gates"
```

## Task 9: Final Production Readiness Audit

**Files:**
- Create: `docs/production-readiness-audit.md`
- Modify: `docs/release-checklist.md`

- [ ] **Step 1: Re-run required checks**

Run:

```powershell
git status --short --branch
cd backend
..\\.venv\\Scripts\\python -m pytest
cd ..\\android
.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Expected: backend passes; Android passes where JDK 17 and Android SDK are installed.

- [ ] **Step 2: Write final audit**

Create `docs/production-readiness-audit.md` with:

- branch name and latest commit
- checks run and exact pass/fail/blocked status
- backend security status
- Android security status
- docs/CI status
- remaining launch blockers
- release checklist status

- [ ] **Step 3: Commit**

Run:

```powershell
git add docs/production-readiness-audit.md docs/release-checklist.md
git commit -m "docs: record production readiness audit"
```

## Self-Review

**Spec coverage:** Tasks cover repo hygiene, setup docs, env docs, backend settings, backend auth, protocol hardening, timeout/cancellation, confirmation policy, Android auth, Android local policy, accessibility minimization, lifecycle/UI resilience, CI, release docs, and final audit.

**Completeness scan:** No unspecified implementation slots are intentionally left in this plan.

**Type consistency:** Backend token name is `MILF_DEVICE_TOKEN`; websocket query parameter is `token`; Android helper is `ClientSecurity`; action policy names are shared conceptually but implemented per platform.

**Execution mode:** Use subagent-driven development for code tasks. Use a fresh worker per task and review each task with spec compliance and code-quality checks before moving to the next task.
