# Privacy Policy

## Scope

MILF is a voice-first Android accessibility agent for a constrained WhatsApp video-call flow. The app processes spoken goals, transcripts, UI tree data, confirmation text, and, when explicitly allowed by policy, screenshots needed to complete that flow.

## Audio Handling

- Audio is recorded only for the active user request.
- Local test runs may use `MILF_STT_BACKEND=mock` and should avoid sending real audio to providers.
- Provider-backed runs may send audio to configured STT providers.
- Audio must not be logged, committed, or retained beyond the active request unless a separate owner-approved debugging process documents retention and redaction.

## Transcript Handling

- Transcripts are used to build the current MobileRun goal and confirmation copy.
- Transcripts must not be logged, committed, or included in crash reports.
- Transcripts shared for debugging must be redacted and limited to the minimum text needed to reproduce the issue.

## UI Tree Handling

- UI trees are accessibility data and may include app labels, visible text, content descriptions, view ids, enabled/clickable state, focus state, bounds, package names, and class names.
- UI tree collection must be minimized to fields needed for navigation.
- Empty, disabled, invisible, or irrelevant nodes should be excluded where safe for the hero path.
- Full UI trees must not be logged, committed, or retained as routine telemetry.

## Screenshot Handling

- Screenshots are sensitive and must be treated as a confirmation-gated action.
- Screenshots must not be captured before local and backend policy allow the action.
- Screenshots must not be logged, committed, or retained unless an owner-approved debugging process documents the need and redaction.

## Retention

- Default retention is in-memory for the active request only.
- Audio, transcripts, UI trees, screenshots, tokens, and provider credentials must not be persisted by default.
- Any future retention must define purpose, duration, access controls, deletion process, and redaction rules before release.

## User Consent

- The user must be told when the app records audio and when accessibility access is required.
- Sensitive phone-control actions require a fresh confirmation before execution.
- Debug cleartext websocket use is allowed only for trusted emulator or local rehearsal.

## Third-Party Providers

- OpenAI, ILMU, and MERaLiON configuration is controlled through environment variables.
- Provider-backed STT or LLM runs may send audio-derived text or request context to those services.
- Production provider use must follow each provider's data handling terms and must be documented before release.
