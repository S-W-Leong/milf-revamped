# Release Checklist

No release is acceptable until every required gate is complete or has an owner-approved waiver recorded in the final production-readiness audit.

## Merge And CI Gates

- [ ] CI is green on the release candidate.
- [ ] Backend full suite passes in a clean environment.
- [ ] Android unit tests pass in CI.
- [ ] Android debug build passes in CI.
- [ ] Hygiene check proves `.env`, `android/local.properties`, `.idea/`, and `android/.idea/` are not tracked.
- [ ] Lightweight secret check or denylist check finds no obvious committed secrets.

## Security Gates

- [ ] Device pairing/auth test passes.
- [ ] Backend refuses unauthenticated websocket sessions.
- [ ] Android release rules reject cleartext `ws://` URLs.
- [ ] Production deployment uses `wss://` behind TLS.
- [ ] `MILF_DEVICE_TOKEN` or a stronger session-pairing token is required before phone-control requests.
- [ ] Backend confirmation bypass tests pass.
- [ ] Android confirmation bypass tests pass.
- [ ] Server and client logs do not include tokens, audio, transcripts, screenshots, or full UI trees.

## Privacy Gates

- [ ] `PRIVACY.md` reflects current audio handling.
- [ ] `PRIVACY.md` reflects current transcript handling.
- [ ] `PRIVACY.md` reflects current UI tree minimization.
- [ ] `PRIVACY.md` reflects screenshot gating and retention rules.
- [ ] User-facing consent and accessibility disclosure copy has been reviewed.
- [ ] Third-party provider behavior is documented for OpenAI, ILMU, and MERaLiON.

## Device Rehearsal

- [ ] Supported Android device or emulator is named.
- [ ] Backend and Android setup commands were run from a clean checkout.
- [ ] Accessibility-service onboarding was verified.
- [ ] Device pairing/auth was verified.
- [ ] Known-contact WhatsApp video-call hero path was rehearsed 10 times.
- [ ] Hero rehearsal reached at least 9 of 10 successes or has an owner-approved waiver.
- [ ] Rehearsal evidence records date, device, build, backend commit, pass count, failures, and owner.

## Rollback And Signoff

- [ ] Rollback procedure is documented for backend deployment.
- [ ] Rollback procedure is documented for Android build distribution.
- [ ] Known limitations and launch blockers are listed.
- [ ] Public display-name decision is documented.
- [ ] Privacy and security review is complete.
- [ ] Release owner has signed off.
