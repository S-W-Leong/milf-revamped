# Security Policy

## Supported Versions

The `UPGRADES` branch is the active hardening branch. No production release is supported until the release checklist passes and the final production-readiness audit is recorded.

## Reporting Security Issues

Report suspected vulnerabilities through the repo's GitHub private advisory path: https://github.com/S-W-Leong/milf-revamped/security/advisories/new. GitHub private vulnerability reporting/security advisories must be enabled before public release. If the private advisory page is unavailable, release remains blocked until a monitored private contact is configured. Do not open public issues for unauthenticated phone-control access, token leakage, privacy exposure, or confirmation bypass findings.

## Threat Model Summary

MILF controls a user's Android phone through a backend websocket and AccessibilityService action surface. The primary risks are unauthenticated websocket sessions, confirmation bypass for sensitive actions, accidental exposure of audio or screen data, unsafe provider configuration, and local IDE or SDK files leaking machine-specific paths.

## Secrets Handling

- Never commit `.env`, API keys, websocket tokens, provider credentials, screenshots, transcripts, audio samples, or full UI trees.
- Use `.env.example` for placeholder configuration only.
- Treat `OPENAI_API_KEY`, `ILMU_API_KEY`, `MERALION_API_KEY`, and `MILF_DEVICE_TOKEN` as secrets.
- Rotate any secret that appears in git history, logs, build output, crash reports, or shared screenshots.

## Production Transport Rules

- Production deployments must use `wss://` behind TLS.
- Production must require `MILF_DEVICE_TOKEN` or a stronger session-pairing token before processing phone-control requests.
- Never expose an unauthenticated phone-control websocket.
- Never bind a public production websocket to `0.0.0.0` without TLS and authentication controls.
- Server logs must not print tokens, audio, transcripts, screenshots, or full UI trees.

## Release Security Gates

- CI must be green before release.
- Backend tests must pass in a clean environment.
- Android unit tests and debug build must pass in CI.
- Device pairing and authentication tests must pass.
- Backend and Android confirmation bypass tests must pass.
- Privacy and security docs must be reviewed and current.
- Hero rehearsal must reach 9 of 10 successes or have an owner-approved waiver.
- The public display-name decision must be documented.
