# Release Checklist

No release is acceptable until every required gate is complete or has an owner-approved waiver recorded in the final production-readiness audit.

Checked items below are verified by the 2026-06-24 local audit unless the item explicitly requires CI, device, deployment, or owner evidence.

## Merge And CI Gates

- [ ] CI is green on the release candidate.
- [ ] Backend full suite passes in CI from a clean Python 3.11 install with `python -m pip install -e .[test]` and `python -m pytest`.
- [ ] Android unit tests pass in CI with JDK 17 and Android SDK API 35.
- [ ] Android debug build passes in CI with `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- [x] Hygiene check proves `.env`, `android/local.properties`, `.idea/`, and `android/.idea/` are not tracked.
- [x] Lightweight secret check or denylist check finds no obvious committed secrets.
- [x] Backend setup and tests pass from a fresh local clone with Python 3.12.7.
- [x] Android unit tests and debug build pass from a fresh local clone with JDK 17 and Android SDK API 35.

## Security Gates

- [ ] Device pairing/auth test passes.
- [x] Backend refuses unauthenticated websocket sessions.
- [x] Android release rules reject cleartext `ws://` URLs.
- [ ] Production deployment uses `wss://` behind TLS.
- [x] `MILF_DEVICE_TOKEN` or a stronger session-pairing token is required before phone-control requests.
- [x] Backend confirmation bypass tests pass.
- [x] Android confirmation bypass tests pass.
- [x] Server and client logs do not include tokens, audio, transcripts, screenshots, or full UI trees.

## Privacy Gates

- [x] `PRIVACY.md` reflects current audio handling.
- [x] `PRIVACY.md` reflects current transcript handling.
- [x] `PRIVACY.md` reflects current UI tree minimization.
- [x] `PRIVACY.md` reflects screenshot gating and retention rules.
- [x] User-facing consent and accessibility disclosure copy is implemented and covered by local tests.
- [ ] User-facing consent and accessibility disclosure copy has owner approval.
- [x] Third-party provider behavior is documented for OpenAI, ILMU, and MERaLiON.

## Device Rehearsal

- [ ] Supported Android device or emulator is named.
- [ ] Backend and Android setup commands were run from a clean checkout.
- [ ] Accessibility-service onboarding was verified.
- [ ] Device pairing/auth was verified.
- [ ] Known-contact WhatsApp video-call hero path was rehearsed 10 times.
- [ ] Hero rehearsal reached at least 9 of 10 successes or has an owner-approved waiver.
- [ ] Rehearsal evidence records date, device, build, backend commit, pass count, failures, and owner.

## Rollback And Signoff

- [x] Rollback procedure is documented for backend deployment.
- [x] Rollback procedure is documented for Android build distribution.
- [x] Known limitations and launch blockers are listed.
- [ ] Public display-name decision is documented.
- [ ] Privacy and security review is complete.
- [ ] Release owner has signed off.

## Rollback Procedure

Backend rollback must restore the last known-good deployment artifact and environment set, then verify `MILF_DEVICE_TOKEN`, provider credentials, websocket host/port, and TLS termination before accepting device sessions again. If authentication, confirmation policy, or provider failures are suspected, stop the websocket process or remove it from routing before investigation.

Android rollback must distribute the last known-good APK or app-store track and revoke the faulty build from testers or release channels. If a build exposes unauthenticated transport, cleartext production URLs, or confirmation bypass behavior, treat it as a security incident and rotate affected tokens.

## Known Launch Blockers

- GitHub Actions must be observed green on draft PR #1 for the latest `UPGRADES` head.
- Python 3.11 clean-environment backend evidence must be observed through GitHub Actions or a local Python 3.11 runtime.
- Accessibility onboarding and the 10-run WhatsApp hero rehearsal must be completed on a named device or emulator.
- Production `wss://` deployment and device-pairing procedure must be verified.
- Public display-name decision, disclosure-copy owner approval, privacy/security review, and release owner signoff are still pending.
