# MILF — Build-Ready UX Spec (v1)

**Make Mobile Interfaces Less Frustrating**
Android accessibility agent for SEA seniors. Buyer = adult child. End user = senior.

> **Core principle:** Gemini makes a capable adult faster. MILF makes a phone safe to depend on for someone who can't. Google optimized for *power-with-control-on-the-user*. MILF optimizes for *trust-with-burden-off-the-user*.

---

## 1. Design philosophy (non-negotiables)

These four rules override any individual screen decision. When a design choice is ambiguous, resolve it in favor of these.

1. **The senior carries nothing.** No reading of plans, no typing, no decisions about *how*. They state a goal; the agent and the buyer carry everything else.
2. **A wrong action destroys trust permanently.** Confirmation before any consequential action is mandatory, and it must be recognizable to someone who can't read a plan (face + voice, not text).
3. **Failure never blames the senior.** The most-travelled path today is failure (~30–40% success). The failure experience IS the product. It ends in a human, not an error.
4. **Invocation must be effortless and reachable from inside confusion.** The senior is most often stuck *inside* an app, not at a home base.

---

## 2. What to clone vs. invert (the strategic spine)

| Pattern | Gemini does | MILF action | Why |
|---|---|---|---|
| Invocation | Long-press power → assistant overlay | **Clone** | Proven gesture, borrows existing mental model |
| Live overlay collapse | Floating pill collapses to draggable bubble during task | **Clone** | Anchor that says "still working" without blocking |
| Optional visibility | "View progress" to watch live + undismissable status chip | **Clone** | Solves visible-vs-hidden: default calm, opt-in watch |
| Multilingual messy speech | Rambler cleans "ums/ahs" + mixed languages | **Clone (critical)** | SEA seniors mix dialect + English mid-sentence |
| Captions | Always-visible captions toggle in Live overlay | **Clone, amplify** | Hearing loss — make captions louder/default |
| Confirmation | Review a multi-step **text action plan**, type corrections | **Invert** | Senior can't read/evaluate/retype a plan |
| Responsibility | "You're responsible for mistakes & purchases" | **Invert** | Burden moves to agent + buyer, not senior |
| Failure handling | Fails to a notification | **Invert** | Fails to a **human** (call the buyer) |
| Target device/locale | Pixel 10 / Galaxy S26, US/Korea, English, 18+ | **Invert** | MILF serves mid-range, multilingual, possibly managed accounts — Gemini's exclusion list IS the market |

---

## 3. The senior experience — screen states

The whole senior-facing UX is a single state machine living in an overlay. Five core states.

### State A — Idle / ground state
- **What it is:** A persistent, draggable **floating bubble** always present on screen, plus invocation via long-press power button.
- **Visual:** Large, friendly, high-contrast bubble. One recognizable icon (not a logo — something that reads as "helper"). Minimum 56dp touch target; recommend larger (~72dp) for tremor/low-dexterity.
- **Behavior:** Tapping the bubble OR long-pressing power → State B.
- **Rule:** The bubble never disappears and never needs to be "found." It is the senior's single point of certainty.

### State B — Listening
- **What it is:** Expanded overlay, waveform-centered (cloned from Gemini Live layout).
- **Visual:** Big animated waveform = "I'm hearing you." Huge captions ON by default showing what it heard, in plain language. A single obvious "stop" affordance.
- **Speech handling:** Rambler-equivalent tolerance — accepts mixed-language, dialect, filler words, slow/hesitant speech. Never penalizes a malformed request.
- **Voice prompt:** Agent speaks a short, warm prompt aloud ("What would you like to do?") — don't rely on reading.
- **Transition:** Once intent is parsed → State C.

### State C — Confirmation (the trust gate) ⚠️ most important screen
- **What it is:** A single spoken question before any consequential action.
- **Visual:** Big text + spoken voice: *"Calling Ah Xuan, your grandson?"*
- **Controls:** One large **green YES**, one large **red NO**. Nothing else. No plan, no list, no edit field.
- **Tiering (reduce friction without losing safety):**
  - **Confirm always:** calls, messages, anything sending/spending/contacting a person.
  - **Skip confirmation:** low-stakes, reversible, non-contact actions (open photos, increase volume, go back).
- **Timeout rule:** Auto-proceed on timeout ONLY for low-stakes actions. Consequential actions wait indefinitely for an explicit YES — silence is never consent.
- **Post-v1 enhancement:** Show the contact's photo on the confirmation screen (recognizing a face is effortless where reading a name is not — saves seniors with poor vision or multiple grandchildren with similar names).

### State D — Working
- **What it is:** Overlay collapses to the draggable bubble (cloned). Agent executes on the real screen underneath.
- **Default view (calm):** Bubble + short spoken/captioned narration: *"Finding your grandson… calling now."* The senior reads/hears the bubble, not the WhatsApp UI moving underneath.
- **Optional view (watch):** A "See what I'm doing" affordance → full live navigation visible (this is also your **demo mode** — judges watch the agent work).
- **Persistent status:** An undismissable status indicator while active (cloned), so it's always clear MILF is running. Senior can't accidentally "lose" the task.
- **Transition:** → State E (success) or State F (failure).

### State E — Success
- **Visual + voice:** Calm, warm confirmation. *"You're connected to Ah Xuan."* Large checkmark / call screen. No jargon, no metrics.
- **Then:** Gracefully hand off (e.g., the live video call) and return to State A afterward.

### State F — Failure (the real product) ⚠️
- **Rule:** Never "you did it wrong." Never a raw error. Never a dead end.
- **Visual + voice:** *"I'm having a little trouble with that. Please try again."*
- **Controls:** Big **YES (call daughter)** / big **Try again**.
- **Escape hatch:** "Call my daughter/son" connects the senior to the **buyer** — ties failure recovery directly to the relationship Gemini structurally lacks.
- **Telemetry:** Every failure silently logs to the buyer's ambient health view (see §5), so repeated failures surface to the person who can fix them.

---

## 4. State diagram (text)

```
        ┌──────────────┐
        │  A. IDLE      │◄────────────────────────────┐
        │  (bubble)     │                             │
        └──────┬───────┘                             │
   power-press / tap                                 │
        ┌──────▼───────┐                             │
        │ B. LISTENING  │                             │
        │ (waveform)    │                             │
        └──────┬───────┘                             │
        intent parsed                                 │
        ┌──────▼───────┐     low-stakes action        │
        │ C. CONFIRM    │─────────────────┐           │
        │ (face + Y/N)  │                 │           │
        └──────┬───────┘                 │           │
          YES  │                         │           │
        ┌──────▼───────┐◄────────────────┘           │
        │ D. WORKING    │  (calm bubble / watch mode) │
        │ + status chip │                             │
        └───┬──────┬───┘                             │
       success   failure                              │
     ┌─────▼─┐  ┌─▼─────────────┐                     │
     │E.DONE │  │ F. FAILURE     │                     │
     │       │  │ "call your     │                     │
     │       │  │  daughter?"    │                     │
     └───┬───┘  └───┬───────┬────┘                     │
         │       try again  call buyer                 │
         └──────────┴───────┴──────────────────────────┘
```

---

## 5. The buyer experience (adult child) — guardian, not co-pilot

Front-loaded and event-driven. **Never live co-piloting** (that reads as surveillance and kills the dignity proposition).

### Job 1 — Setup (one-time, high-touch)
The buyer does ALL of this; the senior never sees it:
- Install MILF, grant **Accessibility Service** + **assist-app role** (for power-button) + **floating-window/overlay** permission.
- (Optional, later) set MILF as **default launcher** for the calm home base.
- Build the **relationship graph**: add contacts with **photos + relationships** ("Ah Xuan = grandson, WhatsApp"). This is the senior's confirmation faces AND the agent's intent-resolution map.
- Set the **escape-hatch contact** (who "call my daughter" dials).
- Set per-action confirmation preferences if desired.

### Job 2 — Ambient reassurance (passive — the retention hook)
- Lightweight signal that things work: *"Mum made 3 calls this week."*
- Alert only on **repeated failures** of the same task (signal, not noise).
- Peace of mind, not a dashboard of surveillance. This is what the buyer is actually paying for.

### Job 3 — Remote rescue (on failure)
- When the senior hits State F and chooses "call my daughter," the buyer receives the call/context and can assist.
- Optional: buyer can see *what the agent was attempting* to help diagnose.

### Job 4 — Buyer-only escape to standard Android
- A deliberate, buyer-gated path back to normal Android for device management — never stumbled into by the senior.

---

## 6. The relationship graph (your defensible asset)

Gemini doesn't know "my grandson" = Ah Xuan on WhatsApp. MILF does, because the buyer set it up. This is the single most defensible asset — protect and prioritize it.

- **Per contact:** display name(s) incl. dialect/nickname ("Ah Boy", "大孙"), relationship, photo, preferred app + channel (WhatsApp video vs. voice vs. call).
- **Powers:** the agent's **intent→target resolution** ("my grandson" → Ah Xuan → WhatsApp video). Photo stored for post-v1 face-confirmation enhancement.
- **For the demo:** hardcode this graph if needed — it must *exist* in v1 even if not yet buyer-editable.

---

## 7. Invocation stack (layered, with fallbacks)

Build in this priority order; each layer is a fallback for the one above.

1. **Default assist app** → long-press power launches MILF (primary, Gemini-like).
   - Requires buyer to set MILF as default assist app in onboarding (one-time).
   - ⚠️ **Test early on real SEA-market devices.** Xiaomi/Redmi, Samsung, Oppo OEMs often remap or block the power long-press. If a target device won't yield the gesture, layer 2 must cover it.
2. **Persistent floating bubble** → always-tappable. Covers (a) devices that block the power gesture, (b) the "stuck mid-task" moment, which is the harder/more common senior failure.
3. **(Later) Launcher** → MILF as the home screen = calm ground state you boot into. Not v1; a comfort layer once the overlay loop is solid.

---

## 8. Accessibility requirements (hard constraints)

- **Text:** Minimum large; everything must be legible to presbyopic eyes. High contrast mandatory. Never rely on color alone (red/green YES/NO must also differ by position + icon + label + voice).
- **Voice:** Every state speaks aloud. Nothing critical is text-only.
- **Captions:** ON by default, large, for hearing loss. (Amplified vs. Gemini's optional toggle.)
- **Touch targets:** Oversized for tremor/low dexterity (≥72dp recommended for primary actions).
- **Timing:** No fast auto-dismiss on anything consequential. Seniors read and react slowly; never punish hesitation.
- **Language:** Multilingual + dialect + code-switching tolerant input (Rambler-equivalent). Output in the senior's language.
- **Reversibility:** Prefer reversible actions; make "undo / go back" always available and obvious.

---

## 9. Demo-mode vs. senior-mode (keep them separate on purpose)

These legitimately diverge — design both deliberately; don't let the demo version become the default.

| | Demo mode (judges) | Senior mode (real user) |
|---|---|---|
| Execution view | **Visible** live navigation (agent working is the wow) | **Calm bubble** + narration by default |
| Confirmation | Can show the face-confirm as a trust beat | Same — it's a feature to show off |
| Failure | Show the "call your daughter" recovery (differentiator) | Same — it's the product |
| Speech | Show multilingual/dialect input handling | Same |

The hero demo flow (locked): *"I want to see my grandson"* → navigate WhatsApp → find contact → spoken confirmation → connect video call, no tapping.

---

## 10. Build priority (v1 cut)

In order. Reliability of the hero flow gates everything.

1. **Invocation layers 1–2** (assist-app + floating bubble) — test power gesture on real SEA devices first.
2. **States B → C → D → E** for the single hero flow (WhatsApp video call to one hardcoded contact).
3. **State C face-confirmation** wired to a (hardcoded if needed) relationship graph.
4. **State F failure → call buyer** — do NOT ship without this; it's the most-travelled path.
5. **"View progress" / watch mode** (also serves demo).
6. Buyer setup flow (can be minimal/manual for v1; the graph just has to exist).
7. Ambient reassurance, launcher, per-action tiering — post-v1.

---

## Appendix — one-liners that fell out of the research

- "Gemini makes a capable adult faster. MILF makes a phone safe to depend on for someone who can't."
- "Gemini gives Android superpowers. MILF teaches grandma to fly." *(existing positioning)*
- "Seniors shouldn't have to learn the phone's mental model. The agent holds it for them." *(core thesis)*