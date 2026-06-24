# MILF Production Readiness Design

**Date:** 2026-06-23
**Status:** Approved for execution on branch `UPGRADES`
**Source baseline:** `docs/superpowers/specs/2026-06-22-milf-design.md`, backend audit, Android audit, docs/devops audit

## 1. Goal

Turn the current demo-first MILF backend and Android client into a production-ready
foundation. Production-ready means the repository is reproducible from a clean checkout,
the phone-control channel is authenticated and bounded, sensitive actions cannot bypass
confirmation policy, accessibility data is minimized and disclosed, and every release is
blocked by automated checks.

This branch does not expand the product beyond the original hero path. The first
production target is a safe, supportable WhatsApp video-call assistant for known contacts,
not a general phone-control agent.

## 2. Operating Definition

The project is production-ready only when all of these are true:

1. A new engineer can clone the repo, install backend dependencies, run backend tests,
   run Android unit tests, and build a debug APK using documented commands.
2. The backend and Android client refuse unauthenticated phone-control sessions.
3. Production defaults use `wss://` behind TLS and never bind a public unauthenticated
   websocket to `0.0.0.0`.
4. The app enforces local safety policy before sensitive device actions even if the
   backend prompt or tool path fails.
5. Pending backend action and confirmation requests always finish, fail, or time out.
6. Malformed websocket frames, invalid audio payloads, oversized messages, provider
   failures, and client disconnects fail with controlled errors, not tracebacks or hangs.
7. Accessibility UI trees, screenshots, audio, and transcripts have explicit minimization,
   retention, redaction, and user-facing disclosure rules.
8. CI blocks merges unless backend tests, Android unit tests, Android build, and hygiene
   checks pass.
9. Release documentation names supported devices, runtime permissions, setup steps,
   rollback procedure, privacy behavior, and known limitations.

## 3. Chosen Approach

### Option A: Patch only the current demo path

This is fastest but insufficient. It would leave install, CI, auth, privacy, and release
gates weak, so the same failures would reappear whenever the demo moves to a real user.

### Option B: Production foundation first, then hero reliability

This is the chosen approach. It keeps the existing architecture and hero scope, but adds
the missing production foundations before feature expansion: reproducible setup, transport
auth, local action policy, protocol hardening, lifecycle handling, privacy docs, and CI.

### Option C: Rebuild around a managed mobile automation platform

This could reduce some backend risk but contradicts the approved design constraint that
the app controls the user's own phone without ADB or hosted devices. It also increases
vendor risk before the current architecture is properly hardened.

## 4. Scope

### In Scope

- Backend dependency packaging with pinned runtime expectations and test extras.
- Android reproducibility cleanup, including removal of committed local IDE and SDK files.
- Root setup documentation, `.env.example`, security documentation, privacy documentation,
  and release checklist.
- Backend websocket authentication, default binding changes, message size limits, explicit
  close codes, and structured client-error handling.
- Backend action and confirmation timeout/cancellation semantics.
- Backend server-side confirmation policy for sensitive actions.
- Android websocket session authentication and production/debug URL rules.
- Android local action policy for sensitive device actions.
- Android permission, recorder, TTS, speech-recognition, and websocket lifecycle hardening.
- Accessibility data minimization, action allowlists, package allowlists, and user-facing
  disclosure copy.
- CI for backend tests and Android unit/build verification.
- Tests for every new behavior.

### Out of Scope For This Production Foundation

- New task categories beyond the known-contact WhatsApp video-call path.
- Fine-tuning STT or TTS models.
- Replacing MobileRun.
- Building account management, payments, subscriptions, dashboards, or analytics.
- Renaming every internal package/module. The acronym has public-launch risk, but a broad
  rename is a separate product decision. This branch must document the risk and make the
  public display name configurable.

## 5. Architecture

The existing split stays intact:

- Android app records the senior's intent, speaks narration, displays confirmation, and
  exposes a constrained accessibility action surface.
- Backend runs STT, builds the MobileRun goal, enforces server policy, drives the custom
  `WebSocketDriver`, and streams narration/confirmation messages.

The production additions are:

1. **Session security layer:** a shared `MILF_DEVICE_TOKEN` or generated session token
   authenticates the websocket handshake. Production deployment must use `wss://` through
   a TLS reverse proxy or managed hosting. Debug cleartext is allowed only for emulator or
   trusted LAN rehearsal and must be clearly marked.
2. **Policy layer:** both backend and Android classify sensitive actions. Backend refuses
   sensitive driver calls without a fresh approved confirmation. Android refuses sensitive
   action frames when its local policy says confirmation is missing or stale.
3. **Failure layer:** all request/response waits have timeouts. Disconnects cancel pending
   futures. Protocol errors close with explicit websocket close codes.
4. **Configuration layer:** env vars are parsed through a single backend settings module,
   Android defaults live in build config/resources, and sample env files document every
   required secret or endpoint.
5. **Release layer:** CI, docs, and release checklists become part of the merge criteria.

## 6. Backend Requirements

### 6.1 Packaging And Runtime

- Add `backend/pyproject.toml` or root `pyproject.toml` with a package named
  `milf-backend`.
- Pin runtime dependencies at the same major/minor surface currently used:
  `mobilerun`, `llama-index-llms-openai`, `websockets`, `pydantic`, and `httpx`.
- Add test dependencies: `pytest`, `pytest-asyncio`.
- Document Python support as `>=3.11,<3.13` unless MobileRun requires a narrower range.
- Provide one canonical command for local tests:
  `python -m pytest` from `backend/`, after dependency sync.
- Keep `MockSTT` available for local tests but make production config explicit.

### 6.2 Settings

- Create a settings module that reads and validates:
  `OPENAI_API_KEY`, `OPENAI_MODEL`, `ILMU_API_KEY`, `ILMU_API_URL`,
  `MERALION_API_KEY`, `MERALION_API_URL`, `MILF_STT_BACKEND`, `MILF_WS_HOST`,
  `MILF_WS_PORT`, `MILF_DEVICE_TOKEN`, `MILF_ACTION_TIMEOUT_SECONDS`,
  `MILF_MAX_AUDIO_BYTES`, and `MILF_WS_MAX_SIZE_BYTES`.
- Defaults:
  - `MILF_WS_HOST`: `127.0.0.1`
  - `MILF_WS_PORT`: `8765`
  - `MILF_STT_BACKEND`: `mock` only for local development and tests
  - `MILF_ACTION_TIMEOUT_SECONDS`: `30`
  - `MILF_MAX_AUDIO_BYTES`: value large enough for the Android M4A hero recording
  - `MILF_WS_MAX_SIZE_BYTES`: greater than `MILF_MAX_AUDIO_BYTES` after base64 overhead
- Production mode must fail startup if required secrets or provider URLs are missing.

### 6.3 Websocket Authentication

- Reject connections before processing `Audio` unless the client proves knowledge of the
  configured device/session token.
- Token may be passed as a websocket subprotocol, query parameter, or authorization header;
  the implementation must document the chosen transport and test rejection/acceptance.
- Unauthenticated connections close with policy-violation semantics and no agent run.
- Server logs must not print tokens, audio, transcripts, UI trees, or screenshots.

### 6.4 Protocol Hardening

- `decode()` must distinguish malformed JSON, unknown message type, and schema errors.
- First-frame handling must catch invalid JSON, non-`Audio`, invalid base64, and oversized
  audio payloads.
- Server close behavior must be deterministic:
  - malformed JSON or schema error: protocol error
  - unauthenticated session: policy violation
  - oversized message/audio: message too big
  - unsupported first frame: protocol error
- Tests must cover each close path.

### 6.5 Pending Request Lifecycle

- `AppConnection.send_action()` and `request_confirmation()` must time out.
- A websocket disconnect must fail all pending futures.
- Late responses after timeout or disconnect must be ignored safely.
- Timeout errors must include the action or confirmation id and operation name.
- Tests must cover timeout, disconnect, wrong response type, and late response behavior.

### 6.6 Confirmation Policy

- Define a backend policy module with:
  - a list of sensitive action names and argument patterns
  - confirmation freshness window
  - confirmation approval record
  - policy error for unconfirmed sensitive action
- Sensitive actions include at least:
  `tap`, `input_text`, `press_button`, `start_app`, `screenshot`, and any future send,
  call, payment, install, uninstall, or package-management action.
- For the current hero path, the implementation can classify the final call tap as
  sensitive by requiring the agent to call `confirm_action` before proceeding to a
  configured action phase. If the MobileRun event stream cannot expose action intent
  precisely enough, the fallback is conservative: block all high-impact actions until a
  session-level confirmation has been approved.
- Tests must prove sensitive actions fail before approval and pass after approval.

### 6.7 STT And LLM Provider Handling

- STT HTTP calls must have timeouts.
- STT HTTP errors must surface a user-safe failure narration.
- Provider schema assumptions must be isolated in parser functions with tests.
- `router` mode must fail startup if ILMU/MERaLiON env is incomplete.
- OpenAI model selection must be explicit in settings and documented.

## 7. Android Requirements

### 7.1 Reproducible Project

- Stop tracking `android/local.properties` and IDE state files.
- `.gitignore` must ignore `android/local.properties`, `.idea/`, and `android/.idea/`.
- Android build instructions must rely on `ANDROID_HOME` or a locally generated
  `local.properties`, never a committed machine path.

### 7.2 Transport Security

- Debug builds may default to `ws://10.0.2.2:8765` for emulator testing.
- Release builds must default to an empty or `wss://` production URL.
- Release builds must reject cleartext `ws://` URLs unless an explicit debug flag is set.
- The websocket client must attach the chosen device/session token.
- Authentication failures must be visible in UI state.

### 7.3 Local Action Policy

- Add a local policy before `ActionDispatcher.dispatch()`.
- The policy must reject sensitive actions unless a local confirmation state is approved
  and fresh.
- The policy must allow safe read-only actions only where needed for the agent:
  `get_ui_tree` may be allowed before confirmation; `screenshot` must be treated as
  sensitive unless the privacy spec allows a narrower exception.
- Tests must cover sensitive action rejection and approval.

### 7.4 Accessibility Data Minimization

- `UiTreeSerializer` must exclude empty, disabled, invisible, or irrelevant nodes where
  safe for the hero path.
- It must preserve enough fields for MobileRun to navigate: text, content description,
  class, package, clickable state, enabled state, bounds, focus, and view id where present.
- Add package allowlist support. Initial allowlist: MILF app package, Android launcher,
  WhatsApp package, and system permission/accessibility surfaces required for setup.
- Screenshot capture must be gated by policy and must return controlled errors when the
  platform API fails.

### 7.5 Runtime Permissions And Lifecycle

- Microphone denial must update UI state with a clear recoverable message.
- Recorder start/stop errors must not crash the app.
- Recordings must be cancelled when the activity is destroyed or a new session starts.
- Voice confirmation recognizer errors must be shown as recoverable UI state.
- TTS initialization failure must be visible and must not block button confirmation.

### 7.6 Senior-Facing UI

- Main screen and confirmation overlay must handle large font scale and long confirmation
  text using scrollable layouts.
- Buttons must remain reachable at common phone sizes.
- The app must show when accessibility service is disabled and provide the settings link.
- The app must show transport/auth failure in plain language.

## 8. Docs, Security, Privacy, And Release Requirements

### 8.1 Required Docs

- `README.md`: architecture, status, prerequisites, setup, backend commands, Android
  commands, test commands, production limitations, and branch expectations.
- `.env.example`: every backend env var with safe placeholder values.
- `SECURITY.md`: supported versions, reporting path, threat model summary, secrets
  handling, and no-plaintext-production rule.
- `PRIVACY.md`: audio handling, transcript handling, UI tree handling, screenshot
  handling, retention, user consent, and third-party providers.
- `docs/release-checklist.md`: merge/release gates, device rehearsal, rollback, and
  known-risk signoff.

### 8.2 CI Requirements

- Add a workflow that runs on pull requests and pushes to `UPGRADES` and `master`.
- Backend job:
  - set up supported Python
  - install backend dependencies
  - run backend tests
- Android job:
  - set up JDK 17
  - run `./gradlew :app:testDebugUnitTest :app:assembleDebug`
- Hygiene job:
  - verify ignored local files are not tracked
  - fail if `.env` or `local.properties` is tracked
  - run a lightweight secret scan or denylist check for obvious secrets

### 8.3 Release Gates

No release is acceptable until:

1. CI is green.
2. Backend full suite is green in a clean env.
3. Android unit tests and debug build are green in CI.
4. Device pairing/auth test passes.
5. Confirmation bypass tests pass on backend and Android.
6. Privacy and security docs are up to date.
7. Hero rehearsal achieves 9 of 10 successes or has a documented owner-approved waiver.
8. Public display-name decision is documented.

## 9. Test Strategy

### Backend

- Unit tests for settings validation.
- Unit tests for auth accept/reject.
- Unit tests for protocol errors and close codes.
- Unit tests for action/confirmation timeout and disconnect cancellation.
- Unit tests for confirmation policy.
- Existing protocol, STT, context, narration, driver, mock-app, and harness tests must
  continue passing.

### Android

- Unit tests for URL/security validation.
- Unit tests for authenticated websocket opening.
- Unit tests for local action policy.
- Unit tests for permission denial and recorder error UI state.
- Unit tests for confirmation speech error handling.
- Existing protocol, websocket, dispatcher, parser, and view-model tests must continue
  passing.

### Manual Verification

- Emulator debug run with `ws://10.0.2.2:8765` and a local token.
- Physical-device rehearsal with `wss://` or trusted debug LAN settings.
- Accessibility-service onboarding check.
- 10-run hero rehearsal log.

## 10. Implementation Order

1. Repository hygiene and reproducibility docs.
2. Backend packaging/settings/test command.
3. Backend websocket auth and protocol hardening.
4. Backend pending lifecycle and confirmation policy.
5. Android transport/auth and cleartext release rules.
6. Android local action policy and accessibility minimization.
7. Android recorder, permission, recognizer, TTS, and UI resilience.
8. CI and release checklist.
9. Full verification and production-readiness audit update.

## 11. Acceptance Checklist

- [ ] `UPGRADES` branch contains committed production-readiness spec and plan.
- [ ] Local/IDE files are ignored and untracked.
- [ ] Clean backend install and tests are documented and verified.
- [ ] Backend websocket requires authentication.
- [ ] Backend protocol errors are controlled and tested.
- [ ] Backend action/confirmation requests time out and cancel on disconnect.
- [ ] Backend confirmation policy blocks sensitive actions before approval.
- [ ] Android release builds reject plaintext websocket URLs.
- [ ] Android websocket client sends authentication material.
- [ ] Android local policy blocks sensitive actions before approval.
- [ ] Android accessibility data is minimized and package-scoped.
- [ ] Android runtime permission and recorder failures are recoverable.
- [ ] UI remains usable with large text and long confirmation copy.
- [ ] CI runs backend and Android gates.
- [ ] Security, privacy, release, setup, and env docs exist.
- [ ] Final verification evidence is recorded in the branch summary.

## 12. Self-Review

- Placeholder scan: no placeholder markers or unspecified implementation slots are present.
- Scope check: the spec covers multiple subsystems, so implementation must be split into
  bite-sized plan tasks and executed with subagent-driven development.
- Consistency check: backend and Android both enforce confirmation policy; transport auth
  is required on both sides; docs and CI are release gates, not optional cleanup.
