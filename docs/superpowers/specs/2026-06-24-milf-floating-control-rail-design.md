# MILF Floating Control Rail UX Design

**Date:** 2026-06-24
**Status:** Approved design direction
**Source references:** `docs/VISION.md`, `docs/brand-kit.html`, and the revised UX brainstorming session on 2026-06-24

## 1. Goal

Redesign the MILF Android UX around a simple branded app shell and a compact floating agent control rail.

The app itself should no longer be the primary senior-facing experience. It should provide launch, readiness, and configuration. Once the agent starts, MILF should live over Android as a small, interruptible, visible control surface that lets the user speak commands while watching the agent act on the real phone screen.

This design supersedes the previous full overlay state-machine direction for the near-term UI. Safety gates, voice-first command, and Android accessibility automation remain core product requirements.

## 2. Product Surfaces

MILF has three primary surfaces:

1. **Main screen** — polished branded entry point.
2. **Config screen** — debug/operator setup panel.
3. **Agent overlay** — centered floating bottom pill plus optional confirmation card and collapsed bubble.

The UX should feel like a live phone-control agent, not a standalone assistant app that traps the user inside a separate interface.

## 3. Main Screen

The Main screen appears when the app opens. It follows the MILF brand kit: obsidian background, dark/glass surfaces, off-white text, muted secondary text, sage readiness accents, compact premium typography, and the MILF waveform mark.

The screen has two primary buttons:

- **Config** — always enabled; opens the Config debug panel.
- **Start Agent** — disabled until all required readiness checks pass.

The Main screen also shows concise readiness summaries so the operator knows why Start Agent is enabled or disabled.

Required readiness checks:

- Accessibility Service enabled.
- Display over other apps / overlay permission granted.
- Microphone permission granted.
- Backend websocket URL connected.
- Default assistant app selected.
- Phone call permission granted.
- Language selected.

When all checks are ready, Start Agent becomes enabled with sage confirmation styling.

## 4. Config Screen

Config is a debug/operator panel for now. It does not need to feel like a polished buyer onboarding flow.

Config uses segmented tabs:

- **Permissions** — permission status and shortcuts to Android settings.
- **Backend** — websocket URL, connection status, reconnect controls.
- **Agent** — language, model/session options, test controls, and relevant agent state.
- **Logs** — recent connection, transcript, action, confirmation, and failure logs.

The screen can be utilitarian, but it should still use the brand kit's dark theme so the product feels visually consistent.

## 5. Agent Launch

When the user taps Start Agent:

1. MILF starts Agent Mode.
2. The app sends the user to the Android home screen.
3. MILF appears as a system overlay: a shorter centered bottom pill that does not touch the screen edges.

There is no intermediate in-app Agent Mode screen.

## 6. Expanded Bottom Pill

The expanded agent overlay is a centered floating pill anchored near the bottom of the screen.

It has three zones:

- A large left/center content area.
- A state-dependent action button.
- A persistent white-cross exit button.

The pill's default placeholder is:

```text
Ask MILF to do something
```

Voice remains the senior-facing primary path. The text field is a fallback/debug path.

If the bottom pill is expanded and the user taps outside it, that tap collapses MILF into the round floating bubble and does not pass through to the underlying app.

## 7. Collapsed Bubble

The collapsed overlay is a round floating window that uses the MILF waveform mark.

Behavior:

- It is draggable.
- Tapping it re-expands the bottom pill.
- Taps outside the collapsed bubble pass through to the underlying app normally.

The collapsed bubble is the low-interference state for when the user wants MILF present but out of the way.

## 8. Bottom Pill States

### Idle

The content area shows the fallback input with placeholder `Ask MILF to do something`.

If the input is empty, the action button is the mic. If the user types text, the mic button becomes the send button.

### Listening

Tapping the mic starts recording. The content area is replaced by a pulsing waveform and:

```text
Listening...
```

Tapping the mic again stops recording and immediately submits the captured speech. There is no transcript review step.

### Thinking

After a command is submitted, the input area is temporarily replaced by:

```text
Thinking...
```

The input is unavailable while MILF is thinking.

### Acting

While MILF executes phone actions, the input area is temporarily replaced by:

```text
Acting...
```

The input is unavailable while MILF is acting.

### Success

After a task succeeds, the pill quietly returns to Idle. No separate success screen is required for this version.

### Failure

After a task fails, the failure message appears briefly inside the pill. The pill then returns to Idle. No separate failure card is required for this version.

## 9. Stop Controls

There are two stop affordances during Thinking and Acting:

- **Black-square run stop** — a round white button with a black square. It interrupts the current run and keeps MILF available.
- **White-cross exit** — a round control with a white cross. It exits Agent Mode entirely and removes the overlay.

The two controls are visible side by side at the right edge of the bottom pill during Thinking and Acting.

When the white-cross exit is tapped, MILF closes the overlay and leaves the user on whatever screen or app they are currently viewing. It does not reopen the MILF app.

## 10. Agent Action Feedback

Whenever MILF acts on the live phone screen, it shows a MobileRun-style bounding box around the element it is about to use or is currently using.

Rules:

- Bounding boxes are visible by default.
- There is no separate senior mode versus demo mode.
- Bounding boxes use electric cyan for maximum visibility across arbitrary Android app screens.

The purpose is accountability: users and judges can see what MILF is targeting before or while it acts.

## 11. Confirmation Gate

For consequential actions such as starting a call, sending a message, or paying, MILF shows a compact centered confirmation card above the bottom pill.

The bottom pill remains anchored below the card.

The confirmation card:

- Asks one clear question.
- Uses large **Yes** and **No** buttons.
- Does not support voice confirmation in this version.
- Uses sage styling for Yes.
- Uses red styling for No because clarity matters more than strict palette purity for this action.

Consequential actions must not proceed until the user taps Yes.

## 12. Brand And Visual Rules

The UI should follow `docs/brand-kit.html`:

- Near-black / obsidian base.
- Dark and glass surfaces.
- Off-white primary text.
- Muted stone secondary text.
- Sage as the main earned accent.
- MILF waveform mark as the key brand signal.
- Inter / Geist-like typography.

Sage primarily means ready, available, confirmed, or complete.

Allowed exception colors:

- Electric cyan for live action bounding boxes.
- Red for No and hard-negative confirmation states.

Avoid the previous cream, yellow, blue, and generic big-button accessibility prototype look. The product should feel premium, quiet, compact, and technical enough for a live agent demo while keeping touch targets reliable.

## 13. Implementation Boundaries

This spec defines the revised UX and screen behavior. It does not require changing backend agent architecture, STT routing, confirmation protocol semantics, or accessibility automation contracts.

The implementation plan should focus on:

- Reworking Main and Config screen structure and styling.
- Replacing the full senior overlay UI with the centered bottom control rail.
- Adding expanded/collapsed overlay interaction behavior.
- Adding text fallback and mic/send button switching.
- Adding run-stop versus exit-stop behavior.
- Rendering cyan bounding boxes for agent target feedback.
- Rendering compact confirmation cards above the rail.

## 14. Acceptance Criteria

- Main screen is branded and shows readiness summaries.
- Start Agent is disabled until every required readiness check is ready.
- Config uses `Permissions`, `Backend`, `Agent`, and `Logs` segmented tabs.
- Start Agent backgrounds the app to Android home and shows the centered bottom pill.
- Expanded outside tap collapses to the draggable waveform bubble and does not pass through.
- Collapsed outside taps pass through to the underlying app.
- Idle, Listening, Thinking, Acting, Success, and Failure states behave as specified.
- Text fallback uses mic-as-send switching.
- Voice push-to-talk submits immediately after the second mic tap.
- Thinking/Acting show both black-square run stop and white-cross exit.
- Agent actions display visible electric-cyan bounding boxes.
- Consequential actions show compact Yes/No confirmation above the rail.
- Visual styling follows the MILF brand kit with only the approved exception colors.
