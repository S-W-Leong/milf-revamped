# Perceive Lane, Session-Context Handoff, and Intent-Model Latency

Date: 2026-06-26
Status: Approved (design)

## Problem

MILF routes every utterance through a non-actuating intent gate
(`intent_router.py`) before handing executable tasks to the MobileRun
`MobileAgent` (`agent_runner.py`). Two problems surfaced in use:

1. **Context loss across the handoff.** MobileRun receives only the rewritten
   `normalized_intent` plus memory (`build_goal` in `context.py`). The session
   context the gate used — recent inputs, pending clarification, last run
   result — is never forwarded, so MobileRun cannot resolve follow-ups that
   reference prior turns.
2. **Latency before the user sees anything.** The gate runs a full classify on
   `gpt-4o` (default) before MobileRun's manager even starts planning. For a
   4-way classification this model is oversized and adds avoidable pre-action
   latency.

A third gap motivated the work: a read-only query such as *"what's on my
screen?"* fits neither existing lane. It cannot be `chat` (the gate is
screen-blind) and routing it to `execute` boots the full multi-step,
vision, actuation-capable `MobileAgent` for a pure read — heavyweight and
semantically wrong (the manager has no actionable goal and may try to act).

## Scope

In scope:

- A new read-only `perceive` lane for screen-description / read-aloud queries.
- Enriching the MobileRun handoff with session context.
- Defaulting the intent model to `gpt-4o-mini`.
- An immediate spoken acknowledgment covering the pre-route silence
  (STT + classify).
- Conservative per-task reasoning-mode selection: fast mode (no planning pass)
  for a narrow single-action allowlist, planning mode for everything else.

Explicitly out of scope:

- A non-verbal earcon/tone for the acknowledgment (would need a new protocol
  message type and device-side handling); the acknowledgment ships as a spoken
  filler reusing existing narration. Earcon is a possible future refinement.
- Threading the original utterance, `contact_id`, or cached screen state into
  the handoff (session context only).
- Caching screen state across calls.

## Design

### 1. The `perceive` route (gate side — `intent_router.py`)

- Add `"perceive"` to the `IntentAgentDecision.route` `Literal` and to
  `IntentRoute.kind`.
- Extend `INTENT_AGENT_PROMPT` with a fifth route: *perceive — the user wants
  to know or hear what is currently on the screen (describe it, read it aloud,
  "what does this say", "did it send?"). Read-only; the gate never actuates.*
- `_route_from_decision` maps a `perceive` decision to
  `IntentRoute(kind="perceive", normalized_intent=decision.normalized_intent)`.
  The perceive question falls back to the raw utterance downstream when
  `normalized_intent` is unset.

The gate remains non-actuating; `perceive` only signals intent. No screen
access happens in the gate.

### 2. The perceive execution path (new `milf/perceive.py`)

A standalone single-shot path that reuses the driver's read-only primitives
(`WebSocketDriver.screenshot`, `WebSocketDriver.get_ui_tree`) without booting
`MobileAgent`:

```python
async def run_perceive(connection, query, lang, agent=...) -> result:
    driver = WebSocketDriver(connection)
    shot = await driver.screenshot()
    tree = await driver.get_ui_tree()
    answer = await agent.describe(query, shot, tree, lang)   # one vision call
    await connection.send_narration(answer, lang)
    await connection.send_task_complete(answer, lang)
    return SimpleNamespace(success=True, reason="perceive")
```

- `PerceiveAgent` Protocol plus `OpenAIPerceiveAgent`, mirroring the
  `IntentAgent` / `OpenAIIntentAgent` structure for isolation and testability.
- Vision model defaults to `OPENAI_MODEL`, overridable via a new
  `MILF_PERCEIVE_MODEL` environment variable.
- No `MobileAgent`, no actuation tools, no confirmation gate, no clarification
  loop. A read-only path cannot trigger an irreversible action, so no
  confirmation is required.
- The agent answers from what is currently visible; it does not scroll or act
  to find an answer.

**Error handling** reuses existing conventions:

- Driver/screenshot/UI-tree failures → `SAFE_FAILURE_COPY` via
  `send_task_failure`, return `success=False, reason="perceive_error"`.
- Clean client disconnects detected with the existing
  `_caused_by_clean_client_close` helper; treated as a benign close, not an
  error.

### 3. Dispatch (`agent_runner.py` → `run_intent`)

- Add an early branch alongside the existing `reply`/`clarify` returns, before
  the execute/MobileRun path:
  `if route.kind == "perceive": return await run_perceive(...)`.
- The perceive query is `route.normalized_intent or intent`.
- Record the perceive turn into `session.recent_user_inputs` so subsequent
  follow-ups remain contextual, but do **not** record it as a MobileRun result
  (it is not a mobile run).

### 4. Handoff enrichment (`context.py` + `session.py`)

- Extend `build_goal(intent, memory="", session_context="")`: when
  `session_context` is non-empty, append a `MILF session context:` block to the
  goal.
- The execute path in `run_intent` calls
  `build_goal(routed_intent, memory=memory,
  session_context=session.context_for_intent_router())`.
- Fix the half-wired session field: assign `self.last_contact_id` in
  `record_mobile_run_result` when `route.contact_id` is present. Today the field
  is declared but only `last_mobile_run.contact_id` is set.

### 5. Latency (`intent_router.py`)

- `build_default_intent_agent` defaults the model to `gpt-4o-mini` (was
  `gpt-4o`). The override precedence — `MILF_INTENT_MODEL`, then `OPENAI_MODEL`
  — is unchanged.
- Document `MILF_PERCEIVE_MODEL` and the `gpt-4o-mini` intent default in the
  `AGENTS.md` environment list and README.

### 6. Immediate acknowledgment (`agent_runner.py` → `run_task`)

The pre-route window — audio received → `stt.transcribe` → intent classify —
emits nothing today, so the senior hears dead air from the moment they stop
speaking. On the audio path STT is often the larger share of that silence.

- Emit a short, language-aware filler narration at the **top of `run_task`**,
  before `stt.transcribe`, via the existing `send_narration` (e.g. "Okay, one
  moment."). It fires for every route and commits to no action.
- Keep the existing execute-time `acknowledgment()` at `run_intent` (current
  line ~135) unchanged.

The two acknowledgments bracket two distinct silence gaps rather than repeating
each other:

| Gap | From → to | Covered by |
| --- | --- | --- |
| STT + classify | stopped speaking → route resolves | NEW early filler in `run_task` |
| Manager planning | route resolves → MobileRun's first step narration | existing `acknowledgment()` |

For `chat`/`clarify`/`perceive`, only the early filler precedes the actual
reply. The early filler lives in `run_task` (audio path) only; the text-only
`run_intent` entry point keeps its existing behavior, since that path has no STT
silence and is primarily for tests and development.

### 7. Reasoning-mode selection (`intent_router.py` + `agent_runner.py`)

MobileRun's `MobileAgent` supports two modes via `config.agent.reasoning`:
`False` runs `FastAgent` directly with no planning round-trip; `True` runs
`ManagerAgent` (planning) + `ExecutorAgent`. MILF currently hardcodes
`reasoning=True` in `build_mobile_config`, so every execute task pays for a
Manager planning pass — a full extra vision LLM round-trip on the critical path,
and the largest single latency cost in the execute flow.

Add conservative per-task selection with defense in depth:

- The gate emits `fast_path: bool` (default `False`) on `IntentAgentDecision`
  and `IntentRoute`. The prompt sets it `True` ONLY for narrow single-action
  intents that need no planning — opening a named app, or home/back navigation —
  with explicit counter-examples (anything that composes, sends, selects among
  items, or takes multiple steps stays `False`).
- `agent_runner` applies a hard code-side guardrail: fast mode is used only when
  `route.fast_path` is `True` **and** `route.requires_confirmation` is `False`.
  Any consequential action (call/send/payment/share) always runs planning mode +
  confirmation, even if the gate misclassified it:
  `reasoning = not (route.fast_path and not route.requires_confirmation)`.
- `build_mobile_config(reasoning)` and
  `build_agent(goal, driver, custom_tools, reasoning)` become parametric;
  `run_intent` computes `reasoning` and threads it through `agent_factory`
  (whose signature gains a `reasoning` argument).
- `custom_tools` (clarification, confirmation) stay wired in both modes;
  confirmation is moot on the fast path because `requires_confirmation` gates it
  out.

The gate is conservative and the code refuses to fast-path anything
irreversible. When in doubt, plan.

## Components and Boundaries

| Unit | Purpose | Depends on |
| --- | --- | --- |
| `intent_router` | Classify utterance into chat/clarify/execute/refuse/perceive | OpenAI model |
| `perceive.run_perceive` | Read-only single-shot screen description | `WebSocketDriver`, `PerceiveAgent` |
| `OpenAIPerceiveAgent` | One vision call: query + screenshot + UI tree → answer | OpenAI vision model |
| `context.build_goal` | Assemble MobileRun goal incl. session context | — |
| `agent_runner.run_intent` | Route, pick reasoning mode, dispatch to reply/clarify/perceive/execute | all of the above |
| `agent_runner.build_mobile_config` / `build_agent` | Build MobileRun config/agent for the chosen `reasoning` mode | MobileRun |
| `agent_runner.run_task` | Early filler ack, then STT → `run_intent` | `STTAdapter`, `run_intent` |

`run_perceive` is independently testable: inject a fake `PerceiveAgent` and a
fake `AppConnection`, assert one screenshot + one UI-tree call, the narrated
answer, and that no actuation occurs.

## Testing

pytest + pytest-asyncio.

- `test_perceive.py`: routes to perceive; `screenshot` and `get_ui_tree` each
  called once; narrates the agent's answer; no actuation tools touched; driver
  error → safe failure; clean client close handled.
- `test_intent_router.py`: `perceive` classification and `_route_from_decision`
  mapping.
- `test_agent_runner.py`: perceive dispatch branch is taken; execute goal now
  contains the session-context block; perceive records no MobileRun result.
- `test_context.py`: `build_goal` includes the session-context block when
  provided and omits it when empty.
- `test_session.py` (or existing session tests): `last_contact_id` is set after
  a run carrying a `contact_id`.
- `test_agent_runner.py`: `run_task` emits the early filler narration before
  `stt.transcribe` is called, on every route.
- `test_intent_router.py`: `fast_path` classification and its mapping into
  `IntentRoute`.
- `test_agent_runner.py`: `fast_path` + no confirmation → `reasoning=False`
  passed to the agent factory; `fast_path` + `requires_confirmation` →
  `reasoning=True` (guardrail wins); non-`fast_path` → `reasoning=True`.

## Rationale

The two-agent split (non-actuating gate + actuating MobileRun) is retained — it
is the safety boundary and keeps cheap paths off the heavy agent. The fixes
target the real causes: a coarse "no-screen vs full-actuation" binary (closed by
the read-only `perceive` lane), a lossy single-string handoff (enriched with
session context), an oversized routing model (downsized to `gpt-4o-mini`), an
unbroken pre-route silence (covered by an immediate spoken acknowledgment), and
an always-on planning pass (replaced by conservative fast/planning-mode
selection that still defaults to planning for anything non-trivial or
irreversible).
