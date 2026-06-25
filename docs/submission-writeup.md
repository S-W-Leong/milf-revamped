# MILF — Submission Writeup

## Inspiration

It started with a grandmother and a smartphone she barely used.

She owns the phone. She charges it every night. But she uses it for exactly one thing — calling her children. Not because she wants less from it, but because the distance between what she wants and what the phone demands is too wide to cross alone. To make a video call she'd need to know: open WhatsApp → find Contacts → find the right person → recognise the video icon → tap it. She doesn't hold that map. She just wants to see her grandson.

This is the reality for hundreds of millions of seniors across Southeast Asia. In Thailand, nearly half of surveyed seniors have only basic mobile skills like calling and texting. In Singapore, 89% of seniors owned a smartphone by 2023 — but ownership and capability are completely different things. The majority of adults over 60 say technology was never designed with them in mind. They're right.

It isn't a motivation problem. It isn't an access problem. It's the **mental model** — the invisible map of taps and menus the phone assumes you already carry. We decided the senior shouldn't have to learn that map. The agent should hold it for them.

## What it does

MILF is a voice-first Android agent that bridges intent and action for seniors.

Grandma speaks a goal in her own words — *"I want to see my grandson"* — and MILF does the rest. It reasons over the live screen, navigates to WhatsApp, finds the right contact, and starts the video call. Along the way it **narrates what it's doing out loud**, and before anything irreversible — a call, a send, a payment — it **stops and asks for spoken confirmation**: *"Calling Wei on video now — is that right?"* It only proceeds on "yes."

She didn't choose an app. She didn't tap through menus. She didn't need to know what a home screen is. She expressed a human wish, and her phone answered it.

The same pattern extends across her real daily life: video-calling and voice-messaging family, playing a familiar Hokkien drama or Teresa Teng song on YouTube, and — gated behind the confirmation step — errands like topping up phone credit. One interaction model, spoken in plain language, for the whole phone.

Crucially, MILF is **summoned, not watching**. It acts on spoken intent, not ambient surveillance — independence without intrusion.

## How we built it

MILF is split into a Python agent backend and a native Android client, connected over a websocket.

- **Backend (Python 3.11, async):** An LLM agent reasons over the device's current UI state and decides the next action. It runs on top of the MobileRun driver contract — a typed event stream (`ResultEvent`, `ExecutorActionEvent`, `ManagerPlanDetailsEvent`, and friends) and a `DeviceDriver` interface of primitives like `tap` and `swipe`. Messages are Pydantic v2 models serialized over a websocket server (`milf.server`).
- **Android client (Kotlin):** Executes the agent's chosen actions through Android's **Accessibility APIs**, reads back the screen, and speaks narration and confirmation prompts aloud. The websocket protocol is kept in lockstep between `protocol.py` and `MilfProtocol.kt`.
- **Voice intent:** The main mic flow uses Android's **on-device speech recognition** and sends the transcript as a `TextGoal`, so the hero demo needs no cloud STT. For audio-upload paths we built a pluggable STT router with adapters for ILMU and MERaLiON to handle SEA languages and dialects.
- **The confirmation gate:** A first-class step in the agent loop, not an afterthought — the agent must speak the consequence and receive an explicit yes before executing anything irreversible.

## Challenges we ran into

- **Reliability was the whole game.** The original prototype completed the hero flow only 30–40% of the time, with failures spread across every layer — STT, intent parsing, UI element identification, and execution. On stage, "works sometimes" is the same as "doesn't work." Our entire sprint rule became **reliability > features**: one task that works perfectly beats five that work sometimes.
- **Real screens are messy.** WhatsApp's contact list and call buttons don't always present consistently to the Accessibility tree. Making contact lookup deterministic — and adding retry-with-graceful-fallback so the agent recovers instead of freezing — took real work.
- **Senior speech is not clean input.** Slow pace, dialect, incomplete sentences, vague references ("call my son"). The intent layer had to resolve fuzzy human wishes into concrete contacts and actions.
- **Designing the confirmation beat for trust, not friction.** It had to be audible, clear, and genuinely block execution — without making the experience feel like a permissions nag.

## Accomplishments that we're proud of

- We added a **confirmation gate** that makes autonomous phone control *safe* for a vulnerable user — the agent visibly refuses to act on anything irreversible until it hears "yes."
- We have **spoken narration** so the senior is never lost — the phone explains itself as it works and avoids sharing too much nitties gritties that might feel overwhelming.
- We reframed a generic "agent that can do anything" into a product with a **specific user, a specific moment, and a specific outcome** — a grandmother seeing her grandson, by herself, today.
- We did it on the **mid-range Androids SEA seniors actually own**, using on-device speech — not a premium-flagship-only experience.

## What we learned

- **Trust is a feature you can demonstrate.** The pause before the agent acts turned out to be the most persuasive part of the product, not the most boring.
- **Narration is accessibility.** Speaking each step aloud isn't polish — for a senior, it's the difference between confidence and confusion.
- **Specificity wins.** Narrowing from "everyone who finds phones hard" to "a SEA senior who wants to reach her family" made every product decision — tasks, language, safety, buyer — fall into place.
- **The buyer and the user are different people**, and a product that gives a parent independence while reducing their adult child's interruption load is one a family will pay for.

## What's next for Make Interfaces Less Frustrating

- **Broaden the task set** beyond the hero flow — voice messages, photo sharing, entertainment, and confirmation-gated errands like phone top-ups and bill payments.
- **Deepen SEA language and dialect support** — Manglish, Cantonese, Tamil — so seniors can speak exactly as they speak at home. Wire in better TTS (we wanted to add Meralion & ILMU but ran outta time) 
- **Harden device coverage** across the mid-range Android phones most common in the region.
- **Validate with more real seniors**, not demo stand-ins — put MILF in grandmothers' hands and learn from how they actually talk to it.
- **Build the family layer** — let the adult child set up contacts, trusted actions, and confirmation preferences, turning peace of mind into the product they pay for.
- **Earn trust at the edges** — expand the confirmation gate into a dependable guardian for higher-stakes tasks like health appointments and medication reminders.

We're not giving grandma superpowers. We're giving her back the phone she already owns.