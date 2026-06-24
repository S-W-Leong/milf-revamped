# Production Readiness Audit

**Date:** 2026-06-24  
**Branch:** `UPGRADES`  
**Implementation head under audit:** `241de22 fix: close runtime and CI review gaps`  
**Result:** Not release-ready. Automated local and clean-checkout gates pass, but external CI evidence, device rehearsal, production deployment evidence, and owner signoff remain open.

## Checks Run

| Check | Status | Evidence |
| --- | --- | --- |
| Branch status | Pass | `git status --short --branch` returned `## UPGRADES` before this audit document was edited. |
| Local-only tracked file hygiene | Pass | `git ls-files .env android/local.properties .idea android/.idea` returned no tracked files. |
| Lightweight secret denylist | Pass | `git grep -nI -E` denylist for private keys, AWS access keys, GitHub tokens, and OpenAI-style `sk-` keys, including hyphenated `sk-*` keys, found no matches outside `.env.example`. |
| Backend tests | Pass | `cd backend && ..\.venv\Scripts\python -m pytest` returned `76 passed` on Windows with Python 3.12.7. |
| Clean-checkout backend setup and tests | Pass with local Python 3.12 | From a fresh local clone at `C:\Users\USER\AppData\Local\Temp\mythos-clean-1395f1740a9541439220284fe7bde7d2`, `python -m venv .venv`, `.\.venv\Scripts\python -m pip install -e ".[test]"`, and `.\.venv\Scripts\python -m pytest` returned `76 passed`. This machine does not have Python 3.11 installed, so exact Python 3.11 evidence still depends on GitHub Actions or a local 3.11 runtime. |
| Android runtime regression tests | Pass | `cd android && .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests "ai.milf.client.MainViewModelTest"` passed after adding regression coverage for TTS confirmation failure and stale confirmation cleanup. |
| Android tests and debug build | Pass | `cd android && .\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug` returned `BUILD SUCCESSFUL` using JDK 17 and Android SDK API 35. |
| Clean-checkout Android setup and build | Pass | From the same fresh local clone, with only a generated `android/local.properties` pointing to the local SDK, `.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug` returned `BUILD SUCCESSFUL` with `43 actionable tasks: 43 executed`. |
| GitHub Actions CI | Pending | Workflow exists in `.github/workflows/ci.yml`, but this audit has not observed a GitHub-hosted run on the pushed branch or PR. |
| Device or emulator rehearsal | Pending | No accessibility onboarding evidence or 10-run WhatsApp hero rehearsal log is recorded. |

## Backend Status

- Backend packaging exists in `backend/pyproject.toml` with runtime and test dependencies.
- Settings validate production-critical environment, including `MILF_DEVICE_TOKEN`, provider URLs, audio size, websocket size, and action timeout bounds.
- Websocket entry rejects unauthenticated sessions when a device token is configured and closes malformed first frames deterministically.
- Protocol decode errors, invalid base64, oversized audio, timeout behavior, disconnect cleanup, and late response handling are covered by tests.
- Backend confirmation policy blocks sensitive actions before approval and clears stale or denied approval state.

## Android Status

- Android no longer relies on tracked `local.properties`; local SDK state remains ignored.
- `ClientSecurity` enforces release cleartext rejection, accepts `wss://`, and attaches the configured `token` query parameter without logging the token.
- The local `ActionPolicy` blocks sensitive actions before fresh confirmation and allows constrained read-only UI tree access.
- Accessibility serialization is minimized and package-scoped for the approved hero path.
- Microphone denial, recorder failures, confirmation speech errors, websocket send failures, TTS confirmation failures, stale websocket terminal events, and long confirmation text have recoverable UI paths covered by unit tests.
- TTS unavailability is visible instead of silently succeeding; confirmation buttons remain available if audio playback fails while the backend is waiting for approval.

## Docs And CI Status

- `README.md`, `.env.example`, `SECURITY.md`, `PRIVACY.md`, and `docs/release-checklist.md` exist and describe current setup, security, privacy, and release rules.
- CI now has backend, Android, hygiene, and lightweight secret-denylist jobs.
- Release remains blocked until CI is observed green on GitHub and manual release gates are filled with dated evidence.

## Remaining Launch Blockers

1. Push `UPGRADES` and confirm GitHub Actions is green for the release candidate, including Python 3.11 backend verification.
2. Run accessibility-service onboarding on a named supported Android device or emulator.
3. Rehearse the known-contact WhatsApp video-call hero path 10 times and record at least 9 successes or an owner-approved waiver.
4. Record production deployment evidence for authenticated `wss://` transport behind TLS.
5. Complete privacy/security owner review, public display-name decision, and release owner signoff.

## Release Checklist Status

The checklist is partially complete. Automated local gates, clean-checkout setup gates, and several tested security controls are complete; CI-hosted gates, manual device rehearsal, production deployment evidence, and signoff gates remain unchecked.
