# MILF Revamp — Design Spec

**Date:** 2026-06-22
**Status:** Approved (brainstorming complete)
**Context:** TheFirst Spark Challenge — submission 25 Jun 2026, demo 27 Jun 2026
**Source vision:** `VISION.md`

---

## 1. Goal

Build a scalable prototype of MILF — a voice-first Android agent that bridges a SEA
senior's spoken intent to phone actions — with a clean rebuild whose architecture
supports scale from day one, while keeping the immediate priority on **one rock-solid
hero flow** for the challenge.

**Build posture:** demo-first, scalable bones. One task that works perfectly (WhatsApp
video call) beats five that work sometimes. Multi-task and multi-language are the
post-challenge scale path the architecture already supports.

This is a **clean rebuild**. The May-2026 prize-winning prototype (Android Accessibility
Agent on the OpenAI Agents SDK) is reference only; its code is not in this repo.

## 2. Locked Decisions

| Layer | Decision | Notes / alternatives considered |
|---|---|---|
| Mobile | Own native **Android app**, public **APK**, controls the user's *own* phone via on-device **AccessibilityService** | A consumer APK cannot depend on ADB-from-laptop or MobileRun cloud phones, so on-device accessibility is the only viable standalone control path. Using the user's own phone also makes the real-grandson video call work. |
| Backend | Own cloud service running MobileRun's `MobileAgent` (Manager + Executor) brain | MobileRun = [droidrun](https://docs.mobilerun.ai) rebranded. We use it as a library, not its hosted devices. |
| Bridge | Custom **`WebSocketDriver(DeviceDriver)`** — agent actions ↔ app | MobileRun's layered design (`DeviceDriver` → `StateProvider` → `ActionContext` → platform-agnostic Agent) makes the driver pluggable. Confirmed: every `DeviceDriver` method raises `NotImplementedError` by default; concrete drivers override what they support and declare a `supported` set. |
| Brain LLM | **OpenAI** | User chose continuity with the original prototype, overriding a Claude recommendation. MobileRun supports per-agent models if cost-tuning later. |
| STT | SEA-tuned models via API behind a `RouterSTT`: **ILMU** (en/Manglish) + **MERaLiON** (Cantonese) | ILMU has no native Cantonese ASR, so Cantonese routes to MERaLiON (A*STAR, explicit Cantonese/Hokkien support). Language is a one-time per-user setting (`en`/`manglish`/`yue`); no live detection. VALSEA / mesolitica remain drop-in fallbacks. Native Android STT rejected — inadequate for SEA dialects. |
| TTS | **On-device Android TextToSpeech** for v1 | Output quality matters far less than input recognition. SEA-tuned TTS (ElevenLabs multilingual, etc.) is a later upgrade. |
| Safety | **Confirmation gate** — speak before any irreversible action (call / send / pay) | The trust/safety pitch beat; not optional. |
| Demo language | **English + Manglish + Cantonese** | EN/Manglish via ILMU, Cantonese via MERaLiON. The rest is config-driven scale path. |

**Explicitly out of scope for v1:** we do NOT fine-tune any speech model (consume an
already-tuned SEA model via API; fine-tuning is a post-challenge moat). We do NOT use
MobileRun ADB drivers or cloud phones. We do NOT build general intent NLP — contact
resolution is a seeded map for the demo.

## 3. Architecture

```
┌─ MILF Android App  (public APK, runs on the user's OWN phone) ──────────┐
│  • Voice UI:  mic capture · spoken narration (on-device TTS) ·           │
│               confirmation screen (big buttons + voice yes/no)           │
│  • AccessibilityService:  get_ui_tree · screenshot ·                     │
│                           tap/swipe/input_text/press_button · start_app  │
│  • WebSocket client  ───────────────┐                                    │
└─────────────────────────────────────┼───────────────────────────────────┘
                  actions ↓   UI state ↑   (websocket)
┌─────────────────────────────────────┼───────────────────────────────────┐
│  MILF Backend  (cloud)               ▼                                    │
│  • WebSocketDriver(DeviceDriver)  ← our custom bridge to the app          │
│  • MobileRun MobileAgent  (Manager + Executor, OpenAI models)            │
│  • ILMU STT adapter  (audio → intent)                                     │
│  • Confirmation-gate custom tool  (before any call/send/pay)             │
│  • WhatsApp app-instruction card · contact resolution (seeded)          │
└──────────────────────────────────────────────────────────────────────────┘
```

## 4. Components & Boundaries

Each unit has one purpose, a defined interface, and is independently testable.

### 4.1 MILF Android App
- **AccessibilityService driver** — *Does:* exposes device I/O over websocket
  (`get_ui_tree`, `screenshot`, `tap`, `swipe`, `input_text`, `press_button`,
  `start_app`). *Interface:* JSON messages mirroring MobileRun's `DeviceDriver` method
  names. *Depends on:* Android AccessibilityService APIs.
- **Audio capture** — *Does:* records the spoken goal, uploads/streams to backend.
  *Interface:* audio blob → backend STT endpoint. *Depends on:* mic permission.
- **Narration player** — *Does:* speaks backend narration text via on-device TTS.
  *Interface:* narration events from websocket. *Depends on:* Android TextToSpeech.
- **Confirmation UI** — *Does:* renders/speaks the confirmation, captures yes/no
  (button + voice). *Interface:* confirmation request/response over websocket.
- **WebSocket client** — *Does:* single duplex channel to backend. *Interface:* typed
  message envelope (action, ui_state, narration, confirm).

### 4.2 MILF Backend
- **`WebSocketDriver(DeviceDriver)`** — *Does:* translates MobileRun agent action calls
  into websocket messages to the app and returns UI state. *Interface:* MobileRun
  `DeviceDriver` contract + a `supported` set. *Depends on:* the app connection.
- **`MobileAgent` runner** — *Does:* runs the Manager+Executor loop with OpenAI against
  the custom driver. *Interface:* `goal` in → events/result out. *Depends on:* OpenAI,
  the driver.
- **STT adapter** — *Does:* audio → intent text. *Interface:* `transcribe(audio, lang)
  -> intent`. Pluggable backend (ILMU primary; VALSEA / mesolitica fallback). *Depends
  on:* the chosen STT API. The interface isolates vendor risk.
- **Confirmation-gate tool** — *Does:* a MobileRun custom tool that, before any
  irreversible action, emits a confirmation request and blocks on the user's yes/no.
  *Interface:* registered agent tool. *Depends on:* the websocket channel.
- **WhatsApp app card + contact resolution** — *Does:* app-specific navigation
  knowledge and ("my son"/"cucu") → contact mapping (seeded for demo). *Interface:*
  injected into agent context.

### 5. Data Flow — Hero Task

1. Senior speaks (e.g. *"nak tengok cucu"* / *"I want to see my grandson"*).
2. App captures audio → backend STT adapter (ILMU) → intent.
3. Backend builds the `MobileAgent` goal; agent plans.
4. Manager→Executor issue actions through `WebSocketDriver` → app performs them
   (open WhatsApp → find contact → reach video-call control), reading the
   accessibility tree (not coordinates) for stable element lookup.
5. **Before connecting**, the confirmation-gate tool fires: backend narrates
   *"Calling Wei now — betul?"*; app speaks it and waits for yes/no.
6. On **yes**, the call connects. On **no**, the agent aborts gracefully.

## 6. Error Handling & Reliability

- **Retry with fallback:** identify the single most common failure in the video-call
  path; add bounded retry then graceful spoken fallback ("I couldn't do that — try
  again?"). The agent recovers, never freezes.
- **Accessibility-tree-first element lookup** to survive WhatsApp UI variance; the app
  card encodes the known-good path.
- **STT vendor isolation:** the STT adapter interface lets us swap ILMU → VALSEA →
  mesolitica without touching the agent.
- **Reliability harness:** a repeatable script that runs the hero flow N times and
  reports success rate against the 90% target.
- **Backup:** record a clean run of the hero flow for the live demo.

## 7. Testing Strategy

- **Unit:** `WebSocketDriver` message translation; STT adapter contract (mocked API);
  confirmation-gate blocking behavior; contact resolution.
- **Integration:** backend ↔ app over a real websocket against a test screen.
- **End-to-end / reliability:** the hero-flow harness (success-rate measurement).
- TDD where practical for backend units (driver, adapter, gate).

## 8. Top Risks → De-risking

| Risk | Mitigation |
|---|---|
| ILMU **and** MERaLiON STT API access/schema not obtainable in 3 days | Verify BOTH Day 1. The `RouterSTT` + adapter interface keeps VALSEA / mesolitica-Whisper as drop-in fallbacks, and `MockSTT` keeps the whole pipeline runnable offline for the demo. |
| AccessibilityService onboarding UX (hard for seniors; also our trust story) | Scripted one-time setup; a pre-enabled device for the stage demo. |
| 3-day scope creep | Single-flow discipline; everything non-hero stubbed/seeded; clean recorded backup. |
| WhatsApp UI variance breaks navigation | Accessibility-tree element lookup + WhatsApp app-instruction card. |

## 9. Open Questions (resolve during/after challenge)

- Monetisation model named in the pitch (freemium D2C vs B2B2C telco/care).
- Full language/dialect roadmap beyond EN + Malay.
- Device/Android-version support matrix for judge questions.
- Backend hosting target (Render / Railway / Fly / Cloud Run) — implementation detail.
- Whether to expose the backend agent via MobileRun's MCP server later.
