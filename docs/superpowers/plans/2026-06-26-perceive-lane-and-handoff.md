# Perceive Lane and Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only perceive lane, richer MobileRun handoff context, lower routing latency, early spoken feedback, and conservative fast/planning MobileRun selection.

**Architecture:** Keep MILF's intent router non-actuating, then dispatch `perceive` to a new single-shot read-only module that uses only `WebSocketDriver.screenshot()` and `WebSocketDriver.get_ui_tree()`. MobileRun execution keeps the confirmation and clarification tools but receives session context in its goal and a per-task `reasoning` mode chosen by the router plus a code-side safety guardrail.

**Tech Stack:** Python 3.11, Pydantic v2, MobileRun, llama-index OpenAI LLMs, `websockets`, `pytest` + `pytest-asyncio`.

---

## File Structure

- Modify `backend/milf/intent_router.py`: add `perceive` route support, `fast_path`, prompt rules, and `gpt-4o-mini` default intent model.
- Create `backend/milf/runtime_failures.py`: shared `SAFE_FAILURE_COPY` and `caused_by_clean_client_close()` so both MobileRun and perceive can use the same failure semantics without circular imports.
- Create `backend/milf/perceive.py`: read-only perceive path, `PerceiveAgent` protocol, `OpenAIPerceiveAgent`, default model builder, and safe failure handling.
- Modify `backend/milf/context.py`: add optional `session_context` to `build_goal()`.
- Modify `backend/milf/session.py`: keep perceive turns in recent inputs only; set `last_contact_id` when MobileRun resolves one.
- Modify `backend/milf/agent_runner.py`: dispatch `perceive`, pass session context into `build_goal()`, emit early audio-path filler, and thread `reasoning` into MobileRun construction.
- Create `backend/tests/test_perceive.py`: unit tests for the read-only perceive path.
- Modify `backend/tests/test_intent_router.py`: router tests for `perceive`, `fast_path`, and the intent model default.
- Modify `backend/tests/test_context.py`: goal context block tests.
- Modify `backend/tests/test_agent_runner.py`: dispatch, session handoff, early filler, and reasoning guardrail tests.
- Modify or create `backend/tests/test_session.py`: session `last_contact_id` behavior.
- Modify `AGENTS.md` and `README.md`: document `MILF_PERCEIVE_MODEL` and `gpt-4o-mini` as the intent default.

## Global Constraints

- Use TDD for every behavior change: write the failing test, run it, then implement.
- Run commands from the repo root unless a task command says `cd backend`.
- Use `PYTHONPATH=.` for backend test commands when running from `backend/`.
- Do not add new actuation capability to the perceive path. It must only call screenshot, UI tree, narration, task complete, and task failure.
- Do not change Android protocol messages for this feature; the early acknowledgment is ordinary `Narration`.
- Every task ends with a focused commit.

---

### Task 1: Intent Router Adds `perceive`, `fast_path`, and Faster Default Model

**Files:**
- Modify: `backend/milf/intent_router.py`
- Modify: `backend/tests/test_intent_router.py`

- [ ] **Step 1: Add failing router tests**

Append these tests to `backend/tests/test_intent_router.py`:

```python
async def test_intent_model_maps_perceive_route():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="perceive",
            normalized_intent="Read aloud what is visible on the current screen.",
            confidence=0.88,
        )
    )

    route = await route_intent_with_agent("what's on my screen?", "en", agent)

    assert route == IntentRoute(
        kind="perceive",
        message=None,
        normalized_intent="Read aloud what is visible on the current screen.",
        contact_id=None,
        requires_confirmation=False,
        fast_path=False,
    )


async def test_intent_model_maps_fast_path_execute_route():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Open WhatsApp.",
            requires_confirmation=False,
            fast_path=True,
            confidence=0.91,
        )
    )

    route = await route_intent_with_agent("open WhatsApp", "en", agent)

    assert route == IntentRoute(
        kind="execute",
        message=None,
        normalized_intent="Open WhatsApp.",
        contact_id=None,
        requires_confirmation=False,
        fast_path=True,
    )


def test_intent_prompt_defines_perceive_and_fast_path_rules():
    assert "- perceive:" in INTENT_AGENT_PROMPT
    assert "screen" in INTENT_AGENT_PROMPT.casefold()
    assert "fast_path" in INTENT_AGENT_PROMPT
    assert "home/back navigation" in INTENT_AGENT_PROMPT
    assert "compose" in INTENT_AGENT_PROMPT.casefold()


def test_default_intent_agent_uses_fast_default_model(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.delenv("MILF_INTENT_MODEL", raising=False)
    monkeypatch.delenv("OPENAI_MODEL", raising=False)

    agent = build_default_intent_agent()

    assert agent.model == "gpt-4o-mini"
```

Update the existing `test_default_intent_agent_uses_smarter_default_model` assertion in the same file from:

```python
assert agent.model == "gpt-4o"
```

to:

```python
assert agent.model == "gpt-4o-mini"
```

- [ ] **Step 2: Run router tests and verify they fail**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_intent_router.py -v
```

Expected: FAIL because Pydantic rejects `route="perceive"` and `fast_path` is not on `IntentAgentDecision` or `IntentRoute`.

- [ ] **Step 3: Update router models, prompt, mapping, logging, and default**

In `backend/milf/intent_router.py`, replace `IntentRoute`, `IntentAgentDecision`, the route section of `INTENT_AGENT_PROMPT`, `_route_from_decision()`, and the default model line with the code below.

```python
class IntentRoute(BaseModel):
    kind: Literal["execute", "reply", "clarify", "perceive"]
    message: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False
    fast_path: bool = False


class IntentAgentDecision(BaseModel):
    route: Literal["chat", "clarify", "execute", "refuse", "perceive"]
    reply: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False
    fast_path: bool = False
    confidence: float = 0.0
```

Use this route and rules block inside `INTENT_AGENT_PROMPT`:

```python
Classify the user's utterance into one route:
- chat: casual talk or greeting that needs no phone action.
- clarify: a phone action may be intended, but key details are missing.
- execute: a concrete phone task is ready for the phone automation agent.
- perceive: the user wants to know or hear what is currently visible on the screen.
  Examples: describe the screen, read this aloud, what does this say, did it send.
  Read-only; the gate never actuates and never claims to have seen the screen.
- refuse: unsafe or unsupported request.

Rules:
- For execute, write normalized_intent as a clear, concrete phone task.
- For perceive, write normalized_intent as a clear read-only screen question. If
  the user's wording is already clear, preserve it closely.
- Set fast_path true ONLY for narrow single-action execute intents that need no
  planning: opening a named app, pressing Home, or pressing Back.
- Keep fast_path false for anything that composes, sends, calls, pays, shares,
  selects among visible items, reads screen content, depends on current screen
  state, or takes multiple steps.
- Use the optional Agent memory below for user-provided names, preferences, and
  context. Resolve relationship references, nicknames, and preferred apps from
  Agent memory before deciding whether the request is missing information. Do
  not assume relationships or contacts that are not present in the current
  utterance, session context, or Agent memory.
- Leave contact_id unset unless an explicit integration provides one.
- Use requires_confirmation for calls, sends, payments, location/media sharing, or other consequential actions.
- For clarify, reply with one short question.
- For chat, reply naturally but briefly.
- Use the MILF session context to resolve short follow-ups, such as a name
  supplied after a pending "who should I send that to?" question.
```

Replace `_route_from_decision()` with:

```python
def _route_from_decision(decision: IntentAgentDecision) -> IntentRoute:
    if decision.route == "execute":
        return IntentRoute(
            kind="execute",
            normalized_intent=decision.normalized_intent,
            contact_id=decision.contact_id,
            requires_confirmation=decision.requires_confirmation,
            fast_path=decision.fast_path,
        )
    if decision.route == "perceive":
        return IntentRoute(
            kind="perceive",
            normalized_intent=decision.normalized_intent,
        )
    if decision.route == "clarify":
        return IntentRoute(
            kind="clarify",
            message=decision.reply
            or "What would you like me to help you do on your phone?",
        )
    if decision.route == "refuse":
        return IntentRoute(
            kind="clarify",
            message=decision.reply or "I can't help with that phone action.",
        )
    return IntentRoute(
        kind="reply",
        message=decision.reply or GREETING_RESPONSE,
    )
```

In the router log `extra` dictionary, add:

```python
"fast_path": decision.fast_path,
```

In `build_default_intent_agent()`, replace the model line with:

```python
model = os.environ.get("MILF_INTENT_MODEL") or os.environ.get(
    "OPENAI_MODEL", "gpt-4o-mini"
)
```

- [ ] **Step 4: Run router tests and verify they pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_intent_router.py -v
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/intent_router.py backend/tests/test_intent_router.py
git commit -m "Add perceive and fast-path intent routing"
```

---

### Task 2: Session Context Enters the MobileRun Goal

**Files:**
- Modify: `backend/milf/context.py`
- Modify: `backend/milf/session.py`
- Modify: `backend/tests/test_context.py`
- Create: `backend/tests/test_session.py` if it does not exist

- [ ] **Step 1: Add failing context tests**

Append these tests to `backend/tests/test_context.py`:

```python
def test_build_goal_includes_session_context_when_provided():
    goal = build_goal(
        "Send hello to Wei on WhatsApp.",
        session_context=(
            "Recent user inputs: send hello\n"
            "Pending clarification: Who should I send that to?"
        ),
    )

    assert "MILF session context:" in goal
    assert "Recent user inputs: send hello" in goal
    assert "Pending clarification: Who should I send that to?" in goal
    assert "Spoken intent: 'Send hello to Wei on WhatsApp.'." in goal


def test_build_goal_omits_session_context_block_when_empty():
    goal = build_goal("Open WhatsApp.", session_context="")

    assert "MILF session context:" not in goal
    assert "Spoken intent: 'Open WhatsApp.'." in goal
```

- [ ] **Step 2: Add failing session tests**

Create `backend/tests/test_session.py` if it does not exist, or append these tests if the file is already present:

```python
from types import SimpleNamespace

from milf.intent_router import IntentRoute
from milf.session import MILFSession


def test_record_mobile_run_result_sets_last_contact_id():
    session = MILFSession()
    route = IntentRoute(
        kind="execute",
        normalized_intent="Send hello to Wei on WhatsApp.",
        contact_id="wei-grandson",
        requires_confirmation=True,
    )

    session.record_mobile_run_result(
        route=route,
        result=SimpleNamespace(success=True, reason="ok"),
    )

    assert session.last_contact_id == "wei-grandson"
    assert session.last_mobile_run is not None
    assert session.last_mobile_run.contact_id == "wei-grandson"


def test_perceive_route_records_recent_input_without_mobile_run_state():
    session = MILFSession()
    route = IntentRoute(
        kind="perceive",
        normalized_intent="Describe the current screen.",
    )

    session.record_user_route("what's on my screen?", route)

    assert session.recent_user_inputs == ["what's on my screen?"]
    assert session.pending_clarification is None
    assert session.last_normalized_intent is None
    assert session.last_mobile_run is None
```

- [ ] **Step 3: Run context and session tests and verify they fail**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_context.py tests/test_session.py -v
```

Expected: FAIL because `build_goal()` has no `session_context` parameter and `last_contact_id` is never assigned from `route.contact_id`.

- [ ] **Step 4: Implement `session_context` in `build_goal()`**

In `backend/milf/context.py`, replace `build_goal()` with:

```python
def build_goal(intent: str, memory: str = "", session_context: str = "") -> str:
    parts = [f"Spoken intent: {intent!r}."]

    session_context = session_context.strip()
    if session_context:
        parts.append(f"MILF session context:\n{session_context}")

    memory_section = format_agent_memory(memory)
    if memory_section:
        parts.append(memory_section)

    parts.append(AGENT_OVERLAY_INTERACTION.strip())
    parts.append(CLARIFICATION_RULE)
    parts.append(SAFETY_CONFIRMATION)
    parts.append(POST_SEND_COMPLETION_RULE)

    return "\n\n".join(parts)
```

- [ ] **Step 5: Store `last_contact_id` from MobileRun routes**

In `backend/milf/session.py`, inside `record_mobile_run_result()` after `status` is computed and before assigning `self.last_mobile_run`, insert:

```python
if route.contact_id is not None:
    self.last_contact_id = route.contact_id
```

The final method body must keep the existing `MobileRunResult` assignment:

```python
self.last_mobile_run = MobileRunResult(
    status=status,
    normalized_intent=route.normalized_intent,
    contact_id=route.contact_id,
    reason=reason,
)
```

- [ ] **Step 6: Run context and session tests and verify they pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_context.py tests/test_session.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/context.py backend/milf/session.py backend/tests/test_context.py backend/tests/test_session.py
git commit -m "Thread session context into MobileRun goals"
```

---

### Task 3: Shared Failure Helpers and Read-Only Perceive Module

**Files:**
- Create: `backend/milf/runtime_failures.py`
- Create: `backend/milf/perceive.py`
- Create: `backend/tests/test_perceive.py`
- Modify: `backend/milf/agent_runner.py`

- [ ] **Step 1: Add failing perceive tests**

Create `backend/tests/test_perceive.py`:

```python
from __future__ import annotations

from types import SimpleNamespace

from websockets.exceptions import ConnectionClosedOK

from milf.perceive import run_perceive


class FakeConn:
    def __init__(self):
        self.narrations = []
        self.completions = []
        self.failures = []
        self.actions = []

    async def send_action(self, name, args):
        self.actions.append((name, args))
        return SimpleNamespace(ok=True, result=None, error=None)

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang):
        self.failures.append((message, lang))


class FakeDriver:
    instances = []

    def __init__(self, connection):
        self.connection = connection
        self.screenshot_calls = 0
        self.ui_tree_calls = 0
        self.tap_calls = 0
        FakeDriver.instances.append(self)

    async def screenshot(self):
        self.screenshot_calls += 1
        return b"fake-png"

    async def get_ui_tree(self):
        self.ui_tree_calls += 1
        return {"a11y_tree": {"text": "Sent"}, "phone_state": {}, "device_context": {}}

    async def tap(self, x, y):
        self.tap_calls += 1


class FakePerceiveAgent:
    def __init__(self, answer="The screen says Sent."):
        self.answer = answer
        self.calls = []

    async def describe(self, query, screenshot, ui_tree, lang):
        self.calls.append((query, screenshot, ui_tree, lang))
        return self.answer


async def test_run_perceive_reads_screen_once_and_reports_answer():
    FakeDriver.instances = []
    conn = FakeConn()
    agent = FakePerceiveAgent()

    result = await run_perceive(
        conn,
        query="did it send?",
        lang="en",
        agent=agent,
        driver_factory=FakeDriver,
    )

    driver = FakeDriver.instances[0]
    assert result.success is True
    assert result.reason == "perceive"
    assert driver.screenshot_calls == 1
    assert driver.ui_tree_calls == 1
    assert driver.tap_calls == 0
    assert agent.calls == [
        (
            "did it send?",
            b"fake-png",
            {"a11y_tree": {"text": "Sent"}, "phone_state": {}, "device_context": {}},
            "en",
        )
    ]
    assert conn.narrations == [("The screen says Sent.", "en")]
    assert conn.completions == [("The screen says Sent.", "en", None)]
    assert conn.failures == []


async def test_run_perceive_uses_safe_failure_on_driver_error():
    class FailingDriver(FakeDriver):
        async def screenshot(self):
            raise RuntimeError("screenshot failed")

    conn = FakeConn()

    result = await run_perceive(
        conn,
        query="what's on my screen?",
        lang="en",
        agent=FakePerceiveAgent(),
        driver_factory=FailingDriver,
    )

    assert result.success is False
    assert result.reason == "perceive_error"
    assert conn.narrations == []
    assert conn.completions == []
    assert conn.failures == [
        ("I'm having a little trouble with that. Please try again.", "en")
    ]


async def test_run_perceive_treats_clean_client_close_as_closed_session():
    class ClosedDriver(FakeDriver):
        async def screenshot(self):
            raise Exception("closed") from ConnectionClosedOK(None, None)

    conn = FakeConn()

    result = await run_perceive(
        conn,
        query="read this",
        lang="en",
        agent=FakePerceiveAgent(),
        driver_factory=ClosedDriver,
    )

    assert result.success is False
    assert result.reason == "client_closed"
    assert conn.narrations == []
    assert conn.completions == []
    assert conn.failures == []
```

- [ ] **Step 2: Run perceive tests and verify they fail**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_perceive.py -v
```

Expected: FAIL with `ModuleNotFoundError: No module named 'milf.perceive'`.

- [ ] **Step 3: Create shared runtime failure helpers**

Create `backend/milf/runtime_failures.py`:

```python
from __future__ import annotations

from websockets.exceptions import ConnectionClosedOK

SAFE_FAILURE_COPY = "I'm having a little trouble with that. Please try again."


def caused_by_clean_client_close(error: BaseException) -> bool:
    seen: set[int] = set()
    current: BaseException | None = error
    while current is not None and id(current) not in seen:
        if isinstance(current, ConnectionClosedOK):
            return True
        seen.add(id(current))
        current = current.__cause__ or current.__context__
    return False
```

In `backend/milf/agent_runner.py`, replace:

```python
from websockets.exceptions import ConnectionClosedOK
```

with:

```python
from milf.runtime_failures import SAFE_FAILURE_COPY, caused_by_clean_client_close
```

Remove the local `SAFE_FAILURE_COPY` assignment from `backend/milf/agent_runner.py`.

Replace:

```python
if _caused_by_clean_client_close(error):
```

with:

```python
if caused_by_clean_client_close(error):
```

Delete the `_caused_by_clean_client_close()` function at the bottom of `backend/milf/agent_runner.py`.

- [ ] **Step 4: Create `perceive.py`**

Create `backend/milf/perceive.py`:

```python
from __future__ import annotations

import base64
import json
import logging
import os
from types import SimpleNamespace
from typing import Any, Callable, Protocol

from milf.connection import AppConnection
from milf.runtime_failures import SAFE_FAILURE_COPY, caused_by_clean_client_close
from milf.ws_driver import WebSocketDriver

logger = logging.getLogger(__name__)


class PerceiveAgent(Protocol):
    async def describe(
        self,
        query: str,
        screenshot: bytes,
        ui_tree: dict[str, Any],
        lang: str,
    ) -> str:
        raise NotImplementedError


class OpenAIPerceiveAgent:
    def __init__(self, model: str):
        self.model = model

    async def describe(
        self,
        query: str,
        screenshot: bytes,
        ui_tree: dict[str, Any],
        lang: str,
    ) -> str:
        from openai import AsyncOpenAI

        client = AsyncOpenAI()
        screenshot_b64 = base64.b64encode(screenshot).decode("ascii")
        tree_json = json.dumps(ui_tree, ensure_ascii=False, sort_keys=True)
        response = await client.responses.create(
            model=self.model,
            input=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "input_text",
                            "text": (
                                "You are MILF's read-only screen describer. "
                                "Answer the user's question using only the current "
                                "screenshot and UI tree. Do not suggest tapping, "
                                "scrolling, sending, or opening anything. "
                                f"Language: {lang}\n"
                                f"Question: {query}\n"
                                f"UI tree JSON: {tree_json}"
                            ),
                        },
                        {
                            "type": "input_image",
                            "image_url": f"data:image/png;base64,{screenshot_b64}",
                        },
                    ],
                }
            ],
        )
        return response.output_text.strip()


def build_default_perceive_agent() -> PerceiveAgent:
    if not os.environ.get("OPENAI_API_KEY"):
        raise RuntimeError(
            "OPENAI_API_KEY is required because perceive uses a vision model."
        )
    model = os.environ.get("MILF_PERCEIVE_MODEL") or os.environ.get(
        "OPENAI_MODEL", "gpt-4o"
    )
    return OpenAIPerceiveAgent(model)


async def run_perceive(
    connection: AppConnection,
    query: str,
    lang: str,
    agent: PerceiveAgent | None = None,
    driver_factory: Callable[[AppConnection], WebSocketDriver] = WebSocketDriver,
) -> Any:
    agent = agent or build_default_perceive_agent()
    driver = driver_factory(connection)
    try:
        screenshot = await driver.screenshot()
        ui_tree = await driver.get_ui_tree()
        answer = await agent.describe(query, screenshot, ui_tree, lang)
    except Exception as error:
        if caused_by_clean_client_close(error):
            logger.info("Mobile client closed during perceive.")
            return SimpleNamespace(success=False, reason="client_closed")
        logger.exception("Perceive run failed.")
        await connection.send_task_failure(SAFE_FAILURE_COPY, lang)
        return SimpleNamespace(success=False, reason="perceive_error")

    await connection.send_narration(answer, lang)
    await connection.send_task_complete(answer, lang)
    return SimpleNamespace(success=True, reason="perceive")
```

- [ ] **Step 5: Run perceive and agent-runner error tests**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_perceive.py tests/test_agent_runner.py::test_run_task_sends_safe_failure_on_agent_error tests/test_agent_runner.py::test_run_task_treats_clean_client_close_as_closed_session -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/runtime_failures.py backend/milf/perceive.py backend/milf/agent_runner.py backend/tests/test_perceive.py
git commit -m "Add read-only perceive runner"
```

---

### Task 4: Dispatch `perceive` and Preserve Session Handoff Context

**Files:**
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/tests/test_agent_runner.py`

- [ ] **Step 1: Add failing dispatch and handoff tests**

Append these tests to `backend/tests/test_agent_runner.py`:

```python
async def test_run_intent_dispatches_perceive_without_mobile_agent(monkeypatch):
    called_factory = False
    perceive_calls = []

    async def fake_router(intent, lang, session=None, memory=""):
        return IntentRoute(
            kind="perceive",
            normalized_intent="Describe the current screen.",
        )

    async def fake_run_perceive(connection, query, lang):
        perceive_calls.append((connection, query, lang))
        await connection.send_narration("The screen shows WhatsApp.", lang)
        await connection.send_task_complete("The screen shows WhatsApp.", lang)
        return SimpleNamespace(success=True, reason="perceive")

    def fake_factory(goal, driver, custom_tools):
        nonlocal called_factory
        called_factory = True
        return SimpleNamespace(run=lambda: FakeHandler())

    monkeypatch.setattr("milf.agent_runner.run_perceive", fake_run_perceive)
    conn = FakeConn()
    session = MILFSession()

    result = await run_intent(
        conn,
        intent="what's on my screen?",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
        session=session,
    )

    assert result.success is True
    assert result.reason == "perceive"
    assert called_factory is False
    assert perceive_calls == [(conn, "Describe the current screen.", "en")]
    assert session.recent_user_inputs == ["what's on my screen?"]
    assert session.last_mobile_run is None
    assert conn.narrations == [("The screen shows WhatsApp.", "en")]
    assert conn.completions == [("The screen shows WhatsApp.", "en", None)]


async def test_run_intent_perceive_falls_back_to_raw_utterance(monkeypatch):
    perceive_calls = []

    async def fake_router(intent, lang, session=None, memory=""):
        return IntentRoute(kind="perceive")

    async def fake_run_perceive(connection, query, lang):
        perceive_calls.append((query, lang))
        return SimpleNamespace(success=True, reason="perceive")

    monkeypatch.setattr("milf.agent_runner.run_perceive", fake_run_perceive)

    result = await run_intent(
        FakeConn(),
        intent="read this",
        lang="en",
        agent_factory=lambda goal, driver, custom_tools: None,
        intent_router=fake_router,
    )

    assert result.success is True
    assert perceive_calls == [("read this", "en")]


async def test_run_intent_execute_goal_includes_session_context():
    captured = {}
    session = MILFSession()

    async def fake_router(intent, lang, session=None, memory=""):
        if intent == "send hello":
            return IntentRoute(kind="clarify", message="Who should I send that to?")
        return IntentRoute(
            kind="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            contact_id="wei-grandson",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_intent(
        conn,
        intent="send hello",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
        session=session,
    )
    await run_intent(
        conn,
        intent="Wei",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
        session=session,
    )

    assert "MILF session context:" in captured["goal"]
    assert "Recent user inputs: send hello, Wei" in captured["goal"]
    assert "Pending clarification: Who should I send that to?" not in captured["goal"]
    assert "Spoken intent: 'Send hello to Wei on WhatsApp.'." in captured["goal"]
```

- [ ] **Step 2: Run dispatch tests and verify they fail**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest \
  tests/test_agent_runner.py::test_run_intent_dispatches_perceive_without_mobile_agent \
  tests/test_agent_runner.py::test_run_intent_perceive_falls_back_to_raw_utterance \
  tests/test_agent_runner.py::test_run_intent_execute_goal_includes_session_context \
  -v
```

Expected: FAIL because `agent_runner` has no `run_perceive` import, no `perceive` branch, and `build_goal()` is called without `session_context`.

- [ ] **Step 3: Import and dispatch perceive**

In `backend/milf/agent_runner.py`, add this import:

```python
from milf.perceive import run_perceive
```

After the existing `reply`/`clarify` short-circuit branch and before MobileRun acknowledgment, insert:

```python
if route.kind == "perceive":
    logger.info(
        "MILF dispatching read-only perceive route.",
        extra={
            "session_id": session.session_id,
            "lang": lang,
        },
    )
    perceive_query = route.normalized_intent or intent
    return await run_perceive(connection, perceive_query, lang)
```

- [ ] **Step 4: Pass session context into the MobileRun goal**

In `backend/milf/agent_runner.py`, replace:

```python
goal = build_goal(routed_intent, memory=memory)
```

with:

```python
goal = build_goal(
    routed_intent,
    memory=memory,
    session_context=session.context_for_intent_router(),
)
```

- [ ] **Step 5: Run dispatch tests and verify they pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest \
  tests/test_agent_runner.py::test_run_intent_dispatches_perceive_without_mobile_agent \
  tests/test_agent_runner.py::test_run_intent_perceive_falls_back_to_raw_utterance \
  tests/test_agent_runner.py::test_run_intent_execute_goal_includes_session_context \
  -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/agent_runner.py backend/tests/test_agent_runner.py
git commit -m "Dispatch perceive route before MobileRun"
```

---

### Task 5: Early Spoken Filler Before STT on Audio Path

**Files:**
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/tests/test_agent_runner.py`

- [ ] **Step 1: Add failing early-filler ordering test**

Append this test to `backend/tests/test_agent_runner.py`:

```python
async def test_run_task_sends_early_filler_before_transcribing():
    events = []

    class OrderingConn(FakeConn):
        async def send_narration(self, text, lang):
            events.append(("narration", text, lang))
            await super().send_narration(text, lang)

    class OrderingSTT:
        async def transcribe(self, audio, lang):
            events.append(("transcribe", audio, lang))
            return "hello"

    async def fake_router(intent, lang, session=None, memory=""):
        events.append(("router", intent, lang))
        return IntentRoute(kind="reply", message="Hi there.")

    conn = OrderingConn()

    result = await run_task(
        connection=conn,
        audio=b"audio",
        lang="en",
        stt=OrderingSTT(),
        agent_factory=lambda goal, driver, custom_tools: None,
        intent_router=fake_router,
    )

    assert result.success is True
    assert events[0] == ("narration", "Okay, one moment.", "en")
    assert events[1] == ("transcribe", b"audio", "en")
    assert events[2] == ("router", "hello", "en")
    assert conn.narrations == [
        ("Okay, one moment.", "en"),
        ("Hi there.", "en"),
    ]
```

- [ ] **Step 2: Run the early-filler test and verify it fails**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_agent_runner.py::test_run_task_sends_early_filler_before_transcribing -v
```

Expected: FAIL because `stt.transcribe()` runs before any narration.

- [ ] **Step 3: Add language-aware filler helper**

In `backend/milf/agent_runner.py`, add this function near `build_agent()`:

```python
def early_acknowledgment(lang: str) -> str:
    if lang == "zh":
        return "好的，请等一下。"
    if lang == "yue":
        return "好，等一等。"
    return "Okay, one moment."
```

- [ ] **Step 4: Emit the filler before STT**

In `backend/milf/agent_runner.py`, inside `run_task()` before:

```python
intent = await stt.transcribe(audio, lang)
```

insert:

```python
await connection.send_narration(early_acknowledgment(lang), lang)
```

- [ ] **Step 5: Update existing narration-count assertions**

In `backend/tests/test_agent_runner.py`, update tests that call `run_task()` and assert exact narrations. For example, change `test_run_task_acks_then_builds_and_runs` from:

```python
assert conn.narrations == [("Okay, let me help you with that.", "en")]
```

to:

```python
assert conn.narrations == [
    ("Okay, one moment.", "en"),
    ("Okay, let me help you with that.", "en"),
]
```

Change `test_run_task_surfaces_mobile_run_clarification_and_stops` from:

```python
assert conn.narrations == [
    ("Okay, let me help you with that.", "en"),
    ("Which listed Wei is your grandson?", "en"),
]
```

to:

```python
assert conn.narrations == [
    ("Okay, one moment.", "en"),
    ("Okay, let me help you with that.", "en"),
    ("Which listed Wei is your grandson?", "en"),
]
```

Leave `run_intent()` tests unchanged because text-only `run_intent()` does not cover STT silence.

- [ ] **Step 6: Run agent runner tests and verify they pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_agent_runner.py -v
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/agent_runner.py backend/tests/test_agent_runner.py
git commit -m "Narrate early filler before speech transcription"
```

---

### Task 6: Conservative Reasoning-Mode Selection

**Files:**
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/tests/test_agent_runner.py`

- [ ] **Step 1: Add failing reasoning-mode tests**

Append these tests to `backend/tests/test_agent_runner.py`:

```python
def test_mobile_config_accepts_reasoning_mode():
    fast_config = build_mobile_config(reasoning=False)
    planned_config = build_mobile_config(reasoning=True)

    assert fast_config.agent.reasoning is False
    assert planned_config.agent.reasoning is True
    assert planned_config.agent.manager.vision is True
    assert planned_config.agent.executor.vision is True


async def test_run_intent_fast_path_without_confirmation_uses_fast_agent():
    captured = {}

    async def fake_router(intent, lang, session=None, memory=""):
        return IntentRoute(
            kind="execute",
            normalized_intent="Open WhatsApp.",
            requires_confirmation=False,
            fast_path=True,
        )

    def fake_factory(goal, driver, custom_tools, reasoning):
        captured["reasoning"] = reasoning
        return SimpleNamespace(run=lambda: FakeHandler())

    await run_intent(
        FakeConn(),
        intent="open WhatsApp",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert captured["reasoning"] is False


async def test_run_intent_fast_path_with_confirmation_uses_planning_guardrail():
    captured = {}

    async def fake_router(intent, lang, session=None, memory=""):
        return IntentRoute(
            kind="execute",
            normalized_intent="Call Wei on WhatsApp.",
            requires_confirmation=True,
            fast_path=True,
        )

    def fake_factory(goal, driver, custom_tools, reasoning):
        captured["reasoning"] = reasoning
        return SimpleNamespace(run=lambda: FakeHandler())

    await run_intent(
        FakeConn(),
        intent="call Wei",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert captured["reasoning"] is True


async def test_run_intent_non_fast_path_uses_planning():
    captured = {}

    async def fake_router(intent, lang, session=None, memory=""):
        return IntentRoute(
            kind="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            requires_confirmation=True,
            fast_path=False,
        )

    def fake_factory(goal, driver, custom_tools, reasoning):
        captured["reasoning"] = reasoning
        return SimpleNamespace(run=lambda: FakeHandler())

    await run_intent(
        FakeConn(),
        intent="send hello to Wei",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert captured["reasoning"] is True
```

- [ ] **Step 2: Run reasoning tests and verify they fail**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest \
  tests/test_agent_runner.py::test_mobile_config_accepts_reasoning_mode \
  tests/test_agent_runner.py::test_run_intent_fast_path_without_confirmation_uses_fast_agent \
  tests/test_agent_runner.py::test_run_intent_fast_path_with_confirmation_uses_planning_guardrail \
  tests/test_agent_runner.py::test_run_intent_non_fast_path_uses_planning \
  -v
```

Expected: FAIL because `build_mobile_config()` takes no `reasoning` argument and `agent_factory` receives only three arguments.

- [ ] **Step 3: Make MobileRun config and agent construction parametric**

In `backend/milf/agent_runner.py`, replace `build_mobile_config()` and `build_agent()` with:

```python
def build_mobile_config(reasoning: bool = True) -> Any:
    from mobilerun import (
        AgentConfig,
        AppCardConfig,
        ExecutorConfig,
        ManagerConfig,
        MobileConfig,
    )

    return MobileConfig(
        agent=AgentConfig(
            max_steps=30,
            reasoning=reasoning,
            streaming=True,
            manager=ManagerConfig(vision=True),
            executor=ExecutorConfig(vision=True),
            app_cards=AppCardConfig(
                enabled=True,
                mode="local",
                app_cards_dir=str(APP_CARDS_DIR),
            ),
        ),
    )


def build_agent(
    goal: str,
    driver: WebSocketDriver,
    custom_tools: dict[str, Any],
    reasoning: bool = True,
) -> Any:
    from llama_index.llms.openai import OpenAI
    from mobilerun import MobileAgent

    model = os.environ.get("OPENAI_MODEL", "gpt-4o")
    config = build_mobile_config(reasoning=reasoning)

    return MobileAgent(
        goal=goal,
        config=config,
        driver=driver,
        custom_tools=custom_tools,
        llms={
            "manager": OpenAI(model=model),
            "executor": OpenAI(model=model),
            "fast_agent": OpenAI(model=model),
            "app_opener": OpenAI(model="gpt-4o-mini"),
            "structured_output": OpenAI(model=model),
        },
    )
```

- [ ] **Step 4: Update type signatures for factory injection**

In `backend/milf/agent_runner.py`, change both `agent_factory` annotations from:

```python
Callable[[str, WebSocketDriver, dict[str, Any]], Any]
```

to:

```python
Callable[[str, WebSocketDriver, dict[str, Any], bool], Any]
```

This applies to `run_intent()` and `run_task()`.

- [ ] **Step 5: Compute guarded reasoning and pass it to the factory**

In `backend/milf/agent_runner.py`, before the `"MILF starting MobileRun."` log, add:

```python
reasoning = not (route.fast_path and not route.requires_confirmation)
```

Add `fast_path` and `reasoning` to the MobileRun start log:

```python
"fast_path": route.fast_path,
"reasoning": reasoning,
```

Replace:

```python
agent = agent_factory(goal, driver, custom_tools)
```

with:

```python
agent = agent_factory(goal, driver, custom_tools, reasoning)
```

- [ ] **Step 6: Update existing fake factories to accept `reasoning=True`**

In `backend/tests/test_agent_runner.py`, update every fake factory that currently looks like:

```python
def fake_factory(goal, driver, custom_tools):
    return SimpleNamespace(run=lambda: FakeHandler())
```

to:

```python
def fake_factory(goal, driver, custom_tools, reasoning=True):
    return SimpleNamespace(run=lambda: FakeHandler())
```

For lambda factories, update:

```python
lambda goal, driver, custom_tools: None
```

to:

```python
lambda goal, driver, custom_tools, reasoning=True: None
```

- [ ] **Step 7: Run agent runner tests and verify they pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_agent_runner.py -v
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend/milf/agent_runner.py backend/tests/test_agent_runner.py
git commit -m "Select MobileRun reasoning mode per intent"
```

---

### Task 7: Document New Environment Variables and Defaults

**Files:**
- Modify: `AGENTS.md`
- Modify: `README.md`

- [ ] **Step 1: Update `AGENTS.md` environment list**

In `AGENTS.md`, replace the final environment-name sentence:

```markdown
Never commit secrets. Expected backend environment names include `OPENAI_API_KEY`, `ILMU_API_KEY`, `ILMU_API_URL`, `MERALION_API_KEY`, `MERALION_API_URL`, `MILF_STT_BACKEND`, `MILF_WS_HOST`, and `MILF_WS_PORT`.
```

with:

```markdown
Never commit secrets. Expected backend environment names include `OPENAI_API_KEY`, `OPENAI_MODEL`, `MILF_INTENT_MODEL`, `MILF_PERCEIVE_MODEL`, `ILMU_API_KEY`, `ILMU_API_URL`, `MERALION_API_KEY`, `MERALION_API_URL`, `MILF_STT_BACKEND`, `MILF_WS_HOST`, and `MILF_WS_PORT`. The intent router defaults to `gpt-4o-mini`; perceive defaults to `MILF_PERCEIVE_MODEL`, then `OPENAI_MODEL`, then `gpt-4o`.
```

- [ ] **Step 2: Update README backend configuration docs**

In `README.md`, after the existing required secret block shown here:

````markdown
Required secret:

```text
OPENAI_API_KEY
```
````

add:

````markdown
Optional backend model settings:

```text
OPENAI_MODEL          # default MobileRun and perceive fallback model; defaults to gpt-4o
MILF_INTENT_MODEL     # intent-router model; defaults to gpt-4o-mini
MILF_PERCEIVE_MODEL   # read-only screen-description model; defaults to OPENAI_MODEL, then gpt-4o
```
````

- [ ] **Step 3: Verify docs mention the new variables**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
rg "MILF_PERCEIVE_MODEL|MILF_INTENT_MODEL|gpt-4o-mini" AGENTS.md README.md
```

Expected: output includes all three strings in both `AGENTS.md` or `README.md`, with `MILF_PERCEIVE_MODEL` in both files.

- [ ] **Step 4: Commit**

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add AGENTS.md README.md
git commit -m "Document perceive and intent model settings"
```

---

### Task 8: Full Regression and Integration Sweep

**Files:**
- Verify only; no source files unless a regression test exposes a defect.

- [ ] **Step 1: Run all backend tests**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest -v
```

Expected: PASS.

- [ ] **Step 2: Run targeted router, perceive, and runner tests together**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. pytest tests/test_intent_router.py tests/test_perceive.py tests/test_agent_runner.py -v
```

Expected: PASS.

- [ ] **Step 3: Inspect route and handoff code for safety invariants**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
rg "kind == \"perceive\"|fast_path|requires_confirmation|session_context|send_task_failure|caused_by_clean_client_close" backend/milf
```

Expected:
- `agent_runner.py` dispatches perceive before MobileRun construction.
- `perceive.py` does not call `tap`, `swipe`, `input_text`, `press_button`, or `start_app`.
- `agent_runner.py` computes `reasoning = not (route.fast_path and not route.requires_confirmation)`.
- `agent_runner.py` passes `session_context=session.context_for_intent_router()` into `build_goal()`.

- [ ] **Step 4: Inspect git diff before final commit**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git diff --stat
git diff -- backend/milf/intent_router.py backend/milf/perceive.py backend/milf/agent_runner.py backend/milf/context.py backend/milf/session.py
```

Expected: diff is limited to the planned routing, perceive, handoff, latency, reasoning, and docs changes.

- [ ] **Step 5: Commit any regression fixes**

If Step 1 or Step 2 forced a small correction, commit it:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
git add backend AGENTS.md README.md
git commit -m "Stabilize perceive lane integration"
```

If no files changed after Task 7, skip this commit.

## Self-Review Notes

- Spec coverage: `perceive` route is covered by Tasks 1, 3, and 4; session-context handoff by Tasks 2 and 4; `gpt-4o-mini` intent default by Tasks 1 and 7; early spoken acknowledgment by Task 5; conservative reasoning-mode selection by Tasks 1 and 6; documentation by Task 7.
- Safety coverage: perceive is read-only and cannot create MobileRun tools; fast mode is guarded by `requires_confirmation`; clean client close and safe failure copy are shared by MobileRun and perceive.
- Testing coverage: unit tests cover router mapping, perceive success/failure/clean-close behavior, session state, goal construction, run_task ordering, perceive dispatch, and reasoning-mode guardrails.
