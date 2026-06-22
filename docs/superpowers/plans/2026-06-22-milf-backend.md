# MILF Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the MILF cloud backend — the agent brain that turns a senior's spoken intent into reliable WhatsApp-video-call actions on the user's phone, driven over a websocket to the (separately-built) Android app.

**Architecture:** A Python service runs MobileRun's `MobileAgent` (Manager+Executor, OpenAI) against a custom `WebSocketDriver(DeviceDriver)` that bridges agent actions to the app over a websocket. Speech-to-intent is a pluggable STT adapter (ILMU primary, mock for tests). A confirmation-gate custom tool blocks on the user's spoken yes/no before any irreversible action. Agent events are streamed back as spoken narration. The whole backend is testable end-to-end against a mock app client before any Android code exists.

**Tech Stack:** Python 3.11, MobileRun (`mobilerun`), llama-index OpenAI LLMs, `websockets`, Pydantic v2, `pytest` + `pytest-asyncio`, `httpx` (ILMU calls), `uv` for env/deps.

## Global Constraints

- Python: **3.11** (existing `.venv` is 3.11.14, managed by `uv`). Do not require 3.14.
- Package manager: **`uv`** — `uv pip install <pkg>` into the existing `.venv`.
- All backend code lives under `backend/` as a `milf` package; tests under `backend/tests/`.
- Agent LLM provider: **OpenAI** only. Never introduce Anthropic/Google LLMs.
- Driver: never use ADB or MobileRun cloud devices. The only driver is our `WebSocketDriver`.
- STT: behind the `STTAdapter` interface. ILMU is primary; the interface MUST allow swapping to VALSEA/mesolitica without touching agent code.
- Async everywhere: all I/O (driver actions, STT, websocket, confirmation) is `async`.
- Demo language scope: **English + Manglish (→ ILMU) + Cantonese (→ MERaLiON)**. Language is a one-time per-user setting sent as `lang` (`en`, `manglish`, `yue`); pass it through, never hardcode English, no live language detection.
- Env vars (exact names): `OPENAI_API_KEY`, `ILMU_API_KEY`, `ILMU_API_URL`, `MERALION_API_KEY`, `MERALION_API_URL`, `MILF_STT_BACKEND` (`router`|`mock`), `MILF_WS_HOST`, `MILF_WS_PORT`.
- Every task ends with a green test run and a commit.

---

### Task 1: Project scaffold + MobileRun contract spike

Pins the exact MobileRun surface we build against, so later tasks use real signatures rather than guesses. Deliverable: installed deps + a recorded contract doc.

**Files:**
- Create: `backend/milf/__init__.py` (empty)
- Create: `backend/tests/__init__.py` (empty)
- Create: `backend/pytest.ini`
- Create: `backend/scripts/spike_contract.py`
- Create: `docs/mobilerun_contract.md`

**Interfaces:**
- Produces: a confirmed list of `DeviceDriver` abstract method signatures and the `mobilerun` import paths for `MobileAgent`, `MobileConfig`, `AgentConfig`, `DeviceConfig`, `DeviceDriver`, and the event classes — consumed by Tasks 4, 7, 9.

- [ ] **Step 1: Install dependencies into the existing venv**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
uv pip install mobilerun "llama-index-llms-openai" websockets "pydantic>=2" httpx pytest pytest-asyncio
```
Expected: installs succeed; `uv pip show mobilerun` prints a version.

- [ ] **Step 2: Create the package + pytest config**

`backend/milf/__init__.py` and `backend/tests/__init__.py` are empty files.

`backend/pytest.ini`:
```ini
[pytest]
asyncio_mode = auto
testpaths = tests
```

- [ ] **Step 3: Write the contract spike script**

`backend/scripts/spike_contract.py`:
```python
"""Print the real MobileRun surface we depend on. Run once; copy output into docs/mobilerun_contract.md."""
import inspect

from mobilerun.tools.base import DeviceDriver  # adjust path if import fails; try alternatives below

PRINT_METHODS = [
    "connect", "ensure_connected", "tap", "swipe", "drag", "input_text",
    "press_button", "start_app", "install_app", "list_packages", "get_apps",
    "screenshot", "get_ui_tree", "get_date",
]

def main() -> None:
    print("== DeviceDriver methods ==")
    for name in PRINT_METHODS:
        member = getattr(DeviceDriver, name, None)
        if member is None:
            print(f"{name}: MISSING")
            continue
        try:
            print(f"{name}{inspect.signature(member)}")
        except (TypeError, ValueError):
            print(f"{name}: <no signature>")
    print("== supported attr ==")
    print("supported:", getattr(DeviceDriver, "supported", "MISSING"))

if __name__ == "__main__":
    main()
```

- [ ] **Step 4: Run the spike and resolve imports**

Run:
```bash
cd backend && python scripts/spike_contract.py
```
Expected: prints each method's signature. If `from mobilerun.tools.base import DeviceDriver` raises `ImportError`, find the real path and fix the import:
```bash
python -c "import mobilerun, pkgutil; [print(m.name) for m in pkgutil.walk_packages(mobilerun.__path__, 'mobilerun.')]" | grep -i -E "driver|base|tools"
python -c "from mobilerun import DeviceDriver; print('top-level ok')"
```
Use whichever import works.

- [ ] **Step 5: Record the contract**

Create `docs/mobilerun_contract.md` and paste: (a) the working import paths for `DeviceDriver`, `MobileAgent`, `MobileConfig`, `AgentConfig`, `DeviceConfig`; (b) the printed method signatures; (c) the event import path — find it with:
```bash
python -c "import mobilerun, pkgutil; [print(m.name) for m in pkgutil.walk_packages(mobilerun.__path__, 'mobilerun.')]" | grep -i event
```
Record the module that defines `ExecutorActionEvent` / `ManagerPlanDetailsEvent`.

- [ ] **Step 6: Commit**

```bash
git add backend/ docs/mobilerun_contract.md
git commit -m "chore: scaffold backend + pin MobileRun contract"
```

---

### Task 2: Websocket protocol models

The typed message envelope both sides speak. Pure data + serialization — no I/O.

**Files:**
- Create: `backend/milf/protocol.py`
- Test: `backend/tests/test_protocol.py`

**Interfaces:**
- Produces:
  - `Action(BaseModel)`: `id: str`, `name: str`, `args: dict`
  - `ActionResult(BaseModel)`: `id: str`, `ok: bool`, `result: Any = None`, `error: str | None = None`
  - `Narration(BaseModel)`: `text: str`, `lang: str`
  - `ConfirmRequest(BaseModel)`: `id: str`, `summary: str`, `lang: str`
  - `ConfirmResponse(BaseModel)`: `id: str`, `approved: bool`
  - `Audio(BaseModel)`: `goal_audio_b64: str`, `lang: str`
  - `encode(msg) -> str` and `decode(raw: str) -> BaseModel` using a `{"type": <classname>, "data": {...}}` envelope.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_protocol.py`:
```python
from milf.protocol import Action, ConfirmResponse, encode, decode

def test_action_roundtrip():
    a = Action(id="1", name="tap", args={"x": 10, "y": 20})
    raw = encode(a)
    back = decode(raw)
    assert isinstance(back, Action)
    assert back == a

def test_confirm_response_roundtrip():
    c = ConfirmResponse(id="c1", approved=True)
    assert decode(encode(c)) == c

def test_decode_unknown_type_raises():
    import pytest
    with pytest.raises(ValueError):
        decode('{"type": "Nope", "data": {}}')
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_protocol.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.protocol'`

- [ ] **Step 3: Write the implementation**

`backend/milf/protocol.py`:
```python
from __future__ import annotations
import json
from typing import Any
from pydantic import BaseModel

class Action(BaseModel):
    id: str
    name: str
    args: dict = {}

class ActionResult(BaseModel):
    id: str
    ok: bool
    result: Any = None
    error: str | None = None

class Narration(BaseModel):
    text: str
    lang: str

class ConfirmRequest(BaseModel):
    id: str
    summary: str
    lang: str

class ConfirmResponse(BaseModel):
    id: str
    approved: bool

class Audio(BaseModel):
    goal_audio_b64: str
    lang: str

_REGISTRY = {c.__name__: c for c in (Action, ActionResult, Narration, ConfirmRequest, ConfirmResponse, Audio)}

def encode(msg: BaseModel) -> str:
    return json.dumps({"type": type(msg).__name__, "data": msg.model_dump()})

def decode(raw: str) -> BaseModel:
    obj = json.loads(raw)
    cls = _REGISTRY.get(obj.get("type"))
    if cls is None:
        raise ValueError(f"Unknown message type: {obj.get('type')}")
    return cls(**obj["data"])
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_protocol.py -v`
Expected: 3 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/protocol.py backend/tests/test_protocol.py
git commit -m "feat: websocket protocol models"
```

---

### Task 3: AppConnection (request/response correlation)

Decouples everything from the raw websocket. Holds a send callable + correlates replies by id. Testable with an in-memory fake transport.

**Files:**
- Create: `backend/milf/connection.py`
- Test: `backend/tests/test_connection.py`

**Interfaces:**
- Consumes: `Action`, `ActionResult`, `Narration`, `ConfirmRequest`, `ConfirmResponse`, `encode`, `decode` (Task 2).
- Produces: `AppConnection`:
  - `__init__(self, send: Callable[[str], Awaitable[None]])`
  - `async send_action(self, name: str, args: dict) -> ActionResult`
  - `async request_confirmation(self, summary: str, lang: str) -> bool`
  - `async send_narration(self, text: str, lang: str) -> None`
  - `on_message(self, raw: str) -> None` — feeds inbound raw frames; resolves pending futures.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_connection.py`:
```python
import asyncio
import pytest
from milf.connection import AppConnection
from milf.protocol import ActionResult, ConfirmResponse, decode, encode

async def test_send_action_resolves_on_result():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    task = asyncio.create_task(conn.send_action("tap", {"x": 1, "y": 2}))
    await asyncio.sleep(0)  # let the action be sent
    action = decode(sent[0])
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result="done")))
    res = await task
    assert res.ok and res.result == "done"

async def test_request_confirmation_returns_decision():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    task = asyncio.create_task(conn.request_confirmation("Call Wei now?", "en"))
    await asyncio.sleep(0)
    req = decode(sent[0])
    conn.on_message(encode(ConfirmResponse(id=req.id, approved=True)))
    assert await task is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_connection.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.connection'`

- [ ] **Step 3: Write the implementation**

`backend/milf/connection.py`:
```python
from __future__ import annotations
import asyncio
import uuid
from typing import Awaitable, Callable
from milf.protocol import (
    Action, ActionResult, ConfirmRequest, ConfirmResponse, Narration, decode, encode,
)

class AppConnection:
    def __init__(self, send: Callable[[str], Awaitable[None]]):
        self._send = send
        self._pending: dict[str, asyncio.Future] = {}

    def _new_future(self, key: str) -> asyncio.Future:
        fut = asyncio.get_event_loop().create_future()
        self._pending[key] = fut
        return fut

    async def send_action(self, name: str, args: dict) -> ActionResult:
        msg = Action(id=str(uuid.uuid4()), name=name, args=args)
        fut = self._new_future(msg.id)
        await self._send(encode(msg))
        return await fut

    async def request_confirmation(self, summary: str, lang: str) -> bool:
        msg = ConfirmRequest(id=str(uuid.uuid4()), summary=summary, lang=lang)
        fut = self._new_future(msg.id)
        await self._send(encode(msg))
        resp: ConfirmResponse = await fut
        return resp.approved

    async def send_narration(self, text: str, lang: str) -> None:
        await self._send(encode(Narration(text=text, lang=lang)))

    def on_message(self, raw: str) -> None:
        msg = decode(raw)
        if isinstance(msg, (ActionResult, ConfirmResponse)):
            fut = self._pending.pop(msg.id, None)
            if fut and not fut.done():
                fut.set_result(msg)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_connection.py -v`
Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/connection.py backend/tests/test_connection.py
git commit -m "feat: AppConnection request/response correlation"
```

---

### Task 4: WebSocketDriver (custom DeviceDriver)

A dumb transport: maps MobileRun driver calls to `AppConnection.send_action`. It does NOT interpret UI trees (that is MobileRun's StateProvider job).

**Files:**
- Create: `backend/milf/ws_driver.py`
- Test: `backend/tests/test_ws_driver.py`

**Interfaces:**
- Consumes: `AppConnection` (Task 3); `DeviceDriver` base + exact method signatures from `docs/mobilerun_contract.md` (Task 1).
- Produces: `WebSocketDriver(DeviceDriver)` with `supported = {"tap","swipe","input_text","press_button","start_app","screenshot","get_ui_tree"}` and a passthrough for each.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_ws_driver.py`:
```python
import asyncio
import pytest
from milf.connection import AppConnection
from milf.protocol import ActionResult, decode, encode
from milf.ws_driver import WebSocketDriver

def _wire():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    return sent, conn

async def test_tap_sends_action_and_returns_result():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.tap(100, 200))
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "tap" and action.args == {"x": 100, "y": 200}
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result=None)))
    await task

async def test_get_ui_tree_returns_payload_verbatim():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_ui_tree())
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "get_ui_tree"
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result={"nodes": [1, 2]})))
    assert await task == {"nodes": [1, 2]}

async def test_unsupported_method_raises():
    _, conn = _wire()
    driver = WebSocketDriver(conn)
    with pytest.raises(NotImplementedError):
        await driver.install_app("com.example")
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_ws_driver.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.ws_driver'`

- [ ] **Step 3: Write the implementation**

`backend/milf/ws_driver.py` — adjust the `DeviceDriver` import to match `docs/mobilerun_contract.md`, and align method parameter names with the recorded signatures if they differ:
```python
from __future__ import annotations
from typing import Any
from milf.connection import AppConnection

try:
    from mobilerun.tools.base import DeviceDriver  # confirm in docs/mobilerun_contract.md
except ImportError:  # fallback to top-level export
    from mobilerun import DeviceDriver  # type: ignore

class WebSocketDriver(DeviceDriver):
    supported = {
        "tap", "swipe", "input_text", "press_button",
        "start_app", "screenshot", "get_ui_tree",
    }

    def __init__(self, connection: AppConnection):
        self._conn = connection

    async def _do(self, name: str, **args: Any) -> Any:
        res = await self._conn.send_action(name, args)
        if not res.ok:
            raise RuntimeError(f"{name} failed: {res.error}")
        return res.result

    async def connect(self) -> None:  # connection already established by the server
        return None

    async def ensure_connected(self) -> None:
        return None

    async def tap(self, x: int, y: int, **kwargs: Any) -> Any:
        return await self._do("tap", x=x, y=y)

    async def swipe(self, x1: int, y1: int, x2: int, y2: int, **kwargs: Any) -> Any:
        return await self._do("swipe", x1=x1, y1=y1, x2=x2, y2=y2)

    async def input_text(self, text: str, **kwargs: Any) -> Any:
        return await self._do("input_text", text=text)

    async def press_button(self, button: str, **kwargs: Any) -> Any:
        return await self._do("press_button", button=button)

    async def start_app(self, package: str, **kwargs: Any) -> Any:
        return await self._do("start_app", package=package)

    async def screenshot(self, **kwargs: Any) -> Any:
        return await self._do("screenshot")

    async def get_ui_tree(self, **kwargs: Any) -> Any:
        return await self._do("get_ui_tree")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_ws_driver.py -v`
Expected: 3 passed. (If `install_app` does not raise, the base class does not default to `NotImplementedError` for it — add an explicit override that raises `NotImplementedError` and re-run.)

- [ ] **Step 5: Commit**

```bash
git add backend/milf/ws_driver.py backend/tests/test_ws_driver.py
git commit -m "feat: WebSocketDriver custom DeviceDriver"
```

---

### Task 5: STT adapters + language router (ILMU + MERaLiON + mock)

Speech-to-intent behind a swappable interface, with a router that dispatches by the
per-user `lang` setting. Demo languages: English + Manglish → ILMU; Cantonese → MERaLiON.
Mock used by all tests/harness. Providers never touch the agent.

**Files:**
- Create: `backend/milf/stt.py`
- Test: `backend/tests/test_stt.py`

**Interfaces:**
- Produces:
  - `STTAdapter` (ABC): `async transcribe(self, audio: bytes, lang: str) -> str`
  - `MockSTT(STTAdapter)`: `__init__(self, canned: str)`; returns `canned`.
  - `IlmuSTT(STTAdapter)`: `__init__(self, api_url: str, api_key: str, http: httpx.AsyncClient | None = None)`; POSTs audio, returns transcript string. Handles `en`, `manglish`.
  - `MERaLiONSTT(STTAdapter)`: `__init__(self, api_url: str, api_key: str, http: httpx.AsyncClient | None = None)`; POSTs audio, returns transcript string. Handles `yue` (Cantonese).
  - `RouterSTT(STTAdapter)`: `__init__(self, routes: dict[str, STTAdapter], default: STTAdapter)`; `transcribe` dispatches by `lang`, falling back to `default`.
  - `make_stt() -> STTAdapter`: factory from env. In `mock` mode returns `MockSTT`; otherwise builds a `RouterSTT` with `{"en": ilmu, "manglish": ilmu, "yue": meralion}` and `default=ilmu`.

> NOTE: Neither ILMU's nor MERaLiON's exact request/response schema is visible without
> credentials (the Day-1 external risk — verify BOTH). Both clients are written against a
> configurable JSON contract: multipart file `audio`, form field `lang`, response
> `{"text": "..."}`. Adjust each `_parse` only if the real API differs; the `STTAdapter`
> interface and every caller stay unchanged. Language is a one-time per-user setting sent
> by the app as `lang`; there is no live language detection.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_stt.py`:
```python
import httpx
import pytest
from milf.stt import MockSTT, IlmuSTT, MERaLiONSTT, RouterSTT

async def test_mock_returns_canned():
    stt = MockSTT("nak tengok cucu")
    assert await stt.transcribe(b"audio", "manglish") == "nak tengok cucu"

async def test_ilmu_posts_and_parses_text():
    captured = {}
    def handler(request: httpx.Request) -> httpx.Response:
        captured["url"] = str(request.url)
        return httpx.Response(200, json={"text": "call my son"})
    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        stt = IlmuSTT(api_url="https://ilmu.test/asr", api_key="k", http=client)
        out = await stt.transcribe(b"bytes", "manglish")
    assert out == "call my son"
    assert captured["url"] == "https://ilmu.test/asr"

async def test_meralion_posts_and_parses_text():
    def handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"text": "我想见我孙"})
    transport = httpx.MockTransport(handler)
    async with httpx.AsyncClient(transport=transport) as client:
        stt = MERaLiONSTT(api_url="https://meralion.test/asr", api_key="k", http=client)
        out = await stt.transcribe(b"bytes", "yue")
    assert out == "我想见我孙"

async def test_router_dispatches_by_lang_and_falls_back():
    ilmu = MockSTT("from-ilmu")
    meralion = MockSTT("from-meralion")
    router = RouterSTT(routes={"manglish": ilmu, "yue": meralion}, default=ilmu)
    assert await router.transcribe(b"x", "yue") == "from-meralion"
    assert await router.transcribe(b"x", "manglish") == "from-ilmu"
    assert await router.transcribe(b"x", "unknown") == "from-ilmu"  # default
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_stt.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.stt'`

- [ ] **Step 3: Write the implementation**

`backend/milf/stt.py`:
```python
from __future__ import annotations
import os
from abc import ABC, abstractmethod
import httpx

class STTAdapter(ABC):
    @abstractmethod
    async def transcribe(self, audio: bytes, lang: str) -> str: ...

class MockSTT(STTAdapter):
    def __init__(self, canned: str):
        self._canned = canned
    async def transcribe(self, audio: bytes, lang: str) -> str:
        return self._canned

class _HttpSTT(STTAdapter):
    """Shared multipart-POST client. Subclasses set the response parser."""
    def __init__(self, api_url: str, api_key: str, http: httpx.AsyncClient | None = None):
        self._url = api_url
        self._key = api_key
        self._http = http
        self._owns = http is None

    async def transcribe(self, audio: bytes, lang: str) -> str:
        client = self._http or httpx.AsyncClient()
        try:
            resp = await client.post(
                self._url,
                headers={"Authorization": f"Bearer {self._key}"},
                files={"audio": ("goal.wav", audio, "audio/wav")},
                data={"lang": lang},
            )
            resp.raise_for_status()
            return self._parse(resp.json())
        finally:
            if self._owns:
                await client.aclose()

    @staticmethod
    def _parse(payload: dict) -> str:
        return payload["text"]

class IlmuSTT(_HttpSTT):
    """English + Manglish (YTL ILMU). Confirm schema against ILMU docs on Day 1."""

class MERaLiONSTT(_HttpSTT):
    """Cantonese (A*STAR MERaLiON). Confirm schema against MERaLiON API docs on Day 1."""

class RouterSTT(STTAdapter):
    def __init__(self, routes: dict[str, STTAdapter], default: STTAdapter):
        self._routes = routes
        self._default = default
    async def transcribe(self, audio: bytes, lang: str) -> str:
        return await self._routes.get(lang, self._default).transcribe(audio, lang)

def make_stt() -> STTAdapter:
    backend = os.getenv("MILF_STT_BACKEND", "mock")
    if backend == "mock":
        return MockSTT(os.getenv("MILF_MOCK_TRANSCRIPT", "I want to see my grandson"))
    ilmu = IlmuSTT(api_url=os.environ["ILMU_API_URL"], api_key=os.environ["ILMU_API_KEY"])
    meralion = MERaLiONSTT(api_url=os.environ["MERALION_API_URL"], api_key=os.environ["MERALION_API_KEY"])
    return RouterSTT(routes={"en": ilmu, "manglish": ilmu, "yue": meralion}, default=ilmu)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_stt.py -v`
Expected: 4 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/stt.py backend/tests/test_stt.py
git commit -m "feat: STT adapters + language router (ILMU + MERaLiON + mock)"
```

---

### Task 6: Confirmation-gate custom tool

A MobileRun custom tool that blocks on the app's spoken yes/no before any irreversible action. This is the safety beat.

**Files:**
- Create: `backend/milf/confirmation.py`
- Test: `backend/tests/test_confirmation.py`

**Interfaces:**
- Consumes: `AppConnection.request_confirmation` (Task 3).
- Produces: `build_confirmation_tool(connection: AppConnection, lang: str) -> dict` — a MobileRun `custom_tools` entry under key `"confirm_action"`. Its function `async confirm_action(summary: str, *, ctx=None, **kwargs) -> str` returns a proceed/stop string based on the user's decision.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_confirmation.py`:
```python
import pytest
from milf.confirmation import build_confirmation_tool

class FakeConn:
    def __init__(self, approved): self.approved = approved; self.calls = []
    async def request_confirmation(self, summary, lang):
        self.calls.append((summary, lang)); return self.approved

async def test_tool_proceeds_when_approved():
    conn = FakeConn(True)
    tool = build_confirmation_tool(conn, "en")
    fn = tool["confirm_action"]["function"]
    out = await fn(summary="Call Wei now?", ctx=None)
    assert "proceed" in out.lower()
    assert conn.calls == [("Call Wei now?", "en")]

async def test_tool_stops_when_denied():
    conn = FakeConn(False)
    fn = build_confirmation_tool(conn, "ms")["confirm_action"]["function"]
    out = await fn(summary="Bayar bil?", ctx=None)
    assert "stop" in out.lower() or "do not" in out.lower()
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_confirmation.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.confirmation'`

- [ ] **Step 3: Write the implementation**

`backend/milf/confirmation.py`:
```python
from __future__ import annotations
from milf.connection import AppConnection

def build_confirmation_tool(connection: AppConnection, lang: str) -> dict:
    async def confirm_action(summary: str, *, ctx=None, **kwargs) -> str:
        approved = await connection.request_confirmation(summary, lang)
        if approved:
            return "User confirmed. Proceed with the action."
        return "User declined. Do not perform the action; stop and end the task."

    return {
        "confirm_action": {
            "parameters": {
                "summary": {"type": "string", "required": True},
            },
            "description": (
                "MANDATORY before any irreversible action (placing a call, sending a "
                "message, making a payment). Pass a short plain-language summary of what "
                "is about to happen. Only proceed if this returns confirmation."
            ),
            "function": confirm_action,
        }
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_confirmation.py -v`
Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/confirmation.py backend/tests/test_confirmation.py
git commit -m "feat: confirmation-gate custom tool"
```

---

### Task 7: Narration from agent events

Turns MobileRun event objects into short spoken lines sent to the app. Dispatches on the event class *name* (string) to avoid coupling to exact import paths.

**Files:**
- Create: `backend/milf/narration.py`
- Test: `backend/tests/test_narration.py`

**Interfaces:**
- Consumes: `AppConnection.send_narration` (Task 3).
- Produces:
  - `narration_for(event) -> str | None` — maps an event to a line, or `None` to stay silent.
  - `async narrate_events(handler, connection, lang) -> Any` — iterates `handler.stream_events()`, sends non-None lines, then `return await handler`.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_narration.py`:
```python
import asyncio
from types import SimpleNamespace
from milf.narration import narration_for, narrate_events

def _named(cls_name, **attrs):
    obj = SimpleNamespace(**attrs)
    obj.__class__ = type(cls_name, (SimpleNamespace,), {})
    return obj

def test_executor_action_event_narrates_description():
    ev = _named("ExecutorActionEvent", description="Opening WhatsApp", thought="x")
    assert narration_for(ev) == "Opening WhatsApp"

def test_unknown_event_is_silent():
    assert narration_for(_named("RandomEvent")) is None

class FakeHandler:
    def __init__(self, events, result): self._events = events; self._result = result
    async def stream_events(self):
        for e in self._events:
            yield e
    def __await__(self):
        async def _r(): return self._result
        return _r().__await__()

async def test_narrate_events_sends_lines_and_returns_result():
    sent = []
    class Conn:
        async def send_narration(self, text, lang): sent.append(text)
    handler = FakeHandler(
        [_named("ExecutorActionEvent", description="Finding Wei", thought="")],
        SimpleNamespace(success=True, reason="done"),
    )
    result = await narrate_events(handler, Conn(), "en")
    assert sent == ["Finding Wei"]
    assert result.success is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_narration.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.narration'`

- [ ] **Step 3: Write the implementation**

`backend/milf/narration.py`:
```python
from __future__ import annotations
from typing import Any

def narration_for(event: Any) -> str | None:
    name = type(event).__name__
    if name == "ExecutorActionEvent":
        return getattr(event, "description", None)
    if name == "ManagerPlanDetailsEvent":
        sub = getattr(event, "subgoal", None)
        return f"Next: {sub}" if sub else None
    if name == "FastAgentResponseEvent":
        return getattr(event, "description", None) or getattr(event, "thought", None)
    return None

async def narrate_events(handler: Any, connection: Any, lang: str) -> Any:
    async for event in handler.stream_events():
        line = narration_for(event)
        if line:
            await connection.send_narration(line, lang)
    return await handler
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_narration.py -v`
Expected: 3 passed. (If `narration_for` for `ExecutorActionEvent` reads the wrong attribute, correct it against the field names recorded in `docs/mobilerun_contract.md`.)

- [ ] **Step 5: Commit**

```bash
git add backend/milf/narration.py backend/tests/test_narration.py
git commit -m "feat: narration from agent events"
```

---

### Task 8: Contact resolution + WhatsApp app card

Seeded "my son"/"cucu" → contact map and the WhatsApp navigation knowledge injected into the agent.

**Files:**
- Create: `backend/milf/context.py`
- Create: `backend/milf/contacts.json`
- Test: `backend/tests/test_context.py`

**Interfaces:**
- Produces:
  - `resolve_contact(phrase: str) -> str | None` — case-insensitive lookup over `contacts.json` (keys are relation phrases EN+MS, value is the WhatsApp display name).
  - `WHATSAPP_APP_CARD: str` — guidance text appended to the goal.
  - `build_goal(intent: str) -> str` — combines the intent, any resolved contact, the app card, and the mandatory-confirmation instruction into the agent goal string.
  - `acknowledgment(intent: str) -> str` — a short immediate spoken line ("no dead air" / live-feel) said right after transcription, before the agent plans. Names the resolved contact when known.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_context.py`:
```python
from milf.context import resolve_contact, build_goal, acknowledgment, WHATSAPP_APP_CARD

def test_resolve_known_relation_en_and_ms():
    assert resolve_contact("I want to call my grandson") == "Wei"
    assert resolve_contact("nak tengok cucu") == "Wei"

def test_resolve_unknown_returns_none():
    assert resolve_contact("open the weather") is None

def test_build_goal_includes_contact_card_and_confirmation():
    goal = build_goal("I want to see my grandson")
    assert "Wei" in goal
    assert "confirm_action" in goal
    assert WHATSAPP_APP_CARD.strip()[:10] in goal

def test_acknowledgment_names_contact_when_known():
    assert "Wei" in acknowledgment("I want to see my grandson")

def test_acknowledgment_is_generic_when_unknown():
    line = acknowledgment("open the weather")
    assert line and "Wei" not in line
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_context.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.context'`

- [ ] **Step 3: Write the implementation**

`backend/milf/contacts.json`:
```json
{
  "grandson": "Wei",
  "cucu": "Wei",
  "my son": "Ah Beng",
  "anak": "Ah Beng"
}
```

`backend/milf/context.py`:
```python
from __future__ import annotations
import json
from pathlib import Path

_CONTACTS = json.loads((Path(__file__).parent / "contacts.json").read_text())

WHATSAPP_APP_CARD = """
WhatsApp video-call path: open WhatsApp (package com.whatsapp) -> tap the Calls tab or
open the chat with the target contact -> tap the video-call icon (top-right of the chat).
Identify elements by their accessibility text/content-description, never by fixed
coordinates. The contact list is searchable via the search icon.
""".strip()

def resolve_contact(phrase: str) -> str | None:
    low = phrase.lower()
    for relation, name in _CONTACTS.items():
        if relation in low:
            return name
    return None

def build_goal(intent: str) -> str:
    contact = resolve_contact(intent)
    target = f'\nThe intended contact is "{contact}".' if contact else ""
    return (
        f"User intent (spoken by a senior, may be informal): {intent!r}.{target}\n\n"
        f"{WHATSAPP_APP_CARD}\n\n"
        "SAFETY: Before placing the call (or any send/payment), you MUST call the "
        "confirm_action tool with a short summary and only proceed if it confirms."
    )

def acknowledgment(intent: str) -> str:
    """Immediate 'no dead air' line spoken before the agent starts planning."""
    contact = resolve_contact(intent)
    if contact:
        return f"Okay, let me help you reach {contact}."
    return "Okay, let me help you with that."
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_context.py -v`
Expected: 5 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/context.py backend/milf/contacts.json backend/tests/test_context.py
git commit -m "feat: contact resolution + WhatsApp app card"
```

---

### Task 9: Agent runner (wire it together)

Composes STT → goal → MobileAgent(driver, tools, OpenAI) → narrated events → result. Agent construction is isolated behind a factory so the orchestration is testable with a fake agent.

**Files:**
- Create: `backend/milf/agent_runner.py`
- Test: `backend/tests/test_agent_runner.py`

**Interfaces:**
- Consumes: `make_stt`/`STTAdapter` (5), `WebSocketDriver` (4), `build_confirmation_tool` (6), `narrate_events` (7), `build_goal` + `acknowledgment` (8), `AppConnection` (3).
- Produces:
  - `build_agent(goal, driver, custom_tools) -> Any` — constructs the real `MobileAgent` (the only MobileRun-touching function; mocked in tests).
  - `async run_task(connection, audio, lang, stt, agent_factory=build_agent) -> Any` — full pipeline; returns the agent result.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_agent_runner.py`:
```python
from types import SimpleNamespace
from milf.agent_runner import run_task
from milf.stt import MockSTT

class FakeHandler:
    def __init__(self): self.result = SimpleNamespace(success=True, reason="ok")
    async def stream_events(self):
        if False:
            yield None
    def __await__(self):
        async def _r(): return self.result
        return _r().__await__()

class FakeConn:
    def __init__(self): self.narrations = []
    async def send_narration(self, text, lang): self.narrations.append(text)

async def test_run_task_acks_then_builds_and_runs():
    captured = {}
    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        captured["tools"] = custom_tools
        return SimpleNamespace(run=lambda: FakeHandler())
    conn = FakeConn()
    result = await run_task(
        connection=conn, audio=b"x", lang="en",
        stt=MockSTT("I want to see my grandson"), agent_factory=fake_factory,
    )
    assert result.success is True
    assert "Wei" in captured["goal"]
    assert "confirm_action" in captured["tools"]
    # live-feel: an immediate acknowledgment is spoken before the agent runs
    assert conn.narrations and "Wei" in conn.narrations[0]
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_agent_runner.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.agent_runner'`

- [ ] **Step 3: Write the implementation**

`backend/milf/agent_runner.py`:
```python
from __future__ import annotations
import os
from typing import Any, Callable
from milf.connection import AppConnection
from milf.confirmation import build_confirmation_tool
from milf.context import build_goal, acknowledgment
from milf.narration import narrate_events
from milf.stt import STTAdapter
from milf.ws_driver import WebSocketDriver

def build_agent(goal: str, driver: Any, custom_tools: dict) -> Any:
    from mobilerun import MobileAgent, MobileConfig, AgentConfig
    from llama_index.llms.openai import OpenAI
    model = os.getenv("OPENAI_MODEL", "gpt-4o")
    config = MobileConfig(agent=AgentConfig(max_steps=30, reasoning=True, streaming=True))
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

async def run_task(
    connection: AppConnection,
    audio: bytes,
    lang: str,
    stt: STTAdapter,
    agent_factory: Callable[..., Any] = build_agent,
) -> Any:
    intent = await stt.transcribe(audio, lang)
    # live-feel: speak immediately so there is no dead air while the agent plans
    await connection.send_narration(acknowledgment(intent), lang)
    goal = build_goal(intent)
    driver = WebSocketDriver(connection)
    tools = build_confirmation_tool(connection, lang)
    agent = agent_factory(goal=goal, driver=driver, custom_tools=tools)
    handler = agent.run()
    return await narrate_events(handler, connection, lang)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_agent_runner.py -v`
Expected: 1 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/agent_runner.py backend/tests/test_agent_runner.py
git commit -m "feat: agent runner wiring"
```

---

### Task 10: Mock app client + websocket server

The mock app simulates the AccessibilityService (canned UI states, auto-confirm) so the whole backend runs without Android. The server hosts the real app connection.

**Files:**
- Create: `backend/milf/mock_app.py`
- Create: `backend/milf/server.py`
- Test: `backend/tests/test_mock_app.py`

**Interfaces:**
- Consumes: protocol (2), `AppConnection` (3), `run_task` (9), `MockSTT` (5).
- Produces:
  - `MockApp`: `__init__(self, scripted: dict[str, dict])`; `async handle(self, raw: str) -> str | None` — given an inbound `Action`/`ConfirmRequest` frame, returns the `ActionResult`/`ConfirmResponse` frame. Returns `None` for `Narration`.
  - `serve(host, port)` in `server.py` — `websockets` server that, per connection, builds an `AppConnection`, reads the opening `Audio` frame, runs `run_task`, forwards driver/confirm frames to the socket and inbound replies to `connection.on_message`.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_mock_app.py`:
```python
import asyncio
from milf.connection import AppConnection
from milf.mock_app import MockApp
from milf.protocol import decode

async def test_driver_action_against_mock_app_returns_result():
    mock = MockApp(scripted={"get_ui_tree": {"nodes": []}, "tap": None})
    conn = AppConnection(send=None)  # send set below
    async def send(raw):
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)
    conn._send = send
    res = await conn.send_action("get_ui_tree", {})
    assert res.ok and res.result == {"nodes": []}

async def test_confirm_request_auto_approved():
    mock = MockApp(scripted={}, auto_approve=True)
    conn = AppConnection(send=None)
    async def send(raw):
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)
    conn._send = send
    assert await conn.request_confirmation("Call Wei now?", "en") is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_mock_app.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.mock_app'`

- [ ] **Step 3: Write the implementations**

`backend/milf/mock_app.py`:
```python
from __future__ import annotations
from typing import Any
from milf.protocol import (
    Action, ActionResult, ConfirmRequest, ConfirmResponse, Narration, decode, encode,
)

class MockApp:
    def __init__(self, scripted: dict[str, Any], auto_approve: bool = True):
        self._scripted = scripted
        self._auto_approve = auto_approve

    async def handle(self, raw: str) -> str | None:
        msg = decode(raw)
        if isinstance(msg, Action):
            result = self._scripted.get(msg.name)
            return encode(ActionResult(id=msg.id, ok=True, result=result))
        if isinstance(msg, ConfirmRequest):
            return encode(ConfirmResponse(id=msg.id, approved=self._auto_approve))
        if isinstance(msg, Narration):
            return None
        return None
```

`backend/milf/server.py`:
```python
from __future__ import annotations
import asyncio
import base64
import os
import websockets
from milf.connection import AppConnection
from milf.protocol import Audio, decode
from milf.stt import make_stt
from milf.agent_runner import run_task

async def _handler(ws) -> None:
    async def send(raw: str) -> None:
        await ws.send(raw)
    conn = AppConnection(send=send)
    stt = make_stt()
    first = decode(await ws.recv())
    assert isinstance(first, Audio), "first frame must be Audio"
    audio = base64.b64decode(first.goal_audio_b64)

    async def pump() -> None:
        async for raw in ws:
            conn.on_message(raw)

    pump_task = asyncio.create_task(pump())
    try:
        await run_task(conn, audio, first.lang, stt)
    finally:
        pump_task.cancel()

async def serve(host: str | None = None, port: int | None = None) -> None:
    host = host or os.getenv("MILF_WS_HOST", "0.0.0.0")
    port = port or int(os.getenv("MILF_WS_PORT", "8765"))
    async with websockets.serve(_handler, host, port):
        await asyncio.Future()

if __name__ == "__main__":
    asyncio.run(serve())
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_mock_app.py -v`
Expected: 2 passed

- [ ] **Step 5: Commit**

```bash
git add backend/milf/mock_app.py backend/milf/server.py backend/tests/test_mock_app.py
git commit -m "feat: mock app client + websocket server"
```

---

### Task 11: Reliability harness

Runs the hero flow N times against the mock app with a fake agent that exercises the real driver + confirmation + narration path, and reports a success rate against the 90% target.

**Files:**
- Create: `backend/harness/run_hero.py`
- Test: `backend/tests/test_harness.py`

**Interfaces:**
- Consumes: `AppConnection` (3), `MockApp` (10), `run_task` (9), `MockSTT` (5).
- Produces:
  - `async run_once(scripted, transcript, agent_factory) -> bool` — wires a `MockApp` to an `AppConnection`, runs `run_task`, returns `result.success`.
  - `async run_n(n, scripted, transcript, agent_factory) -> float` — returns success ratio.

- [ ] **Step 1: Write the failing test**

`backend/tests/test_harness.py`:
```python
from types import SimpleNamespace
from milf.harness_support import run_n  # re-exported for testability

class ScriptedHandler:
    def __init__(self, conn, succeed):
        self._conn = conn; self._succeed = succeed
    async def stream_events(self):
        # exercise a real driver action + a confirmation through the connection
        await self._conn.send_action("get_ui_tree", {})
        await self._conn.request_confirmation("Call Wei now?", "en")
        if False:
            yield None
    def __await__(self):
        async def _r(): return SimpleNamespace(success=self._succeed, reason="x")
        return _r().__await__()

async def test_run_n_reports_full_success():
    def factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: ScriptedHandler(driver._conn, True))
    ratio = await run_n(5, scripted={"get_ui_tree": {"nodes": []}}, transcript="see grandson", agent_factory=factory)
    assert ratio == 1.0
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && pytest tests/test_harness.py -v`
Expected: FAIL with `ModuleNotFoundError: No module named 'milf.harness_support'`

- [ ] **Step 3: Write the implementation**

`backend/milf/harness_support.py`:
```python
from __future__ import annotations
from typing import Any, Callable
from milf.connection import AppConnection
from milf.mock_app import MockApp
from milf.stt import MockSTT
from milf.agent_runner import run_task

async def run_once(scripted: dict, transcript: str, agent_factory: Callable[..., Any]) -> bool:
    mock = MockApp(scripted=scripted, auto_approve=True)
    conn = AppConnection(send=None)
    async def send(raw: str) -> None:
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)
    conn._send = send
    result = await run_task(conn, b"audio", "en", MockSTT(transcript), agent_factory)
    return bool(result.success)

async def run_n(n: int, scripted: dict, transcript: str, agent_factory: Callable[..., Any]) -> float:
    wins = 0
    for _ in range(n):
        if await run_once(scripted, transcript, agent_factory):
            wins += 1
    return wins / n
```

`backend/harness/run_hero.py`:
```python
"""CLI: run the hero flow N times against the mock and print the success rate."""
import asyncio
import sys
from milf.harness_support import run_n

def _real_agent_factory(goal, driver, custom_tools):
    from milf.agent_runner import build_agent
    return build_agent(goal, driver, custom_tools)

async def _main(n: int) -> None:
    ratio = await run_n(
        n,
        scripted={"get_ui_tree": {"nodes": []}, "tap": None, "start_app": None},
        transcript="I want to see my grandson",
        agent_factory=_real_agent_factory,
    )
    print(f"Hero flow success: {ratio:.0%} over {n} runs (target >= 90%)")

if __name__ == "__main__":
    asyncio.run(_main(int(sys.argv[1]) if len(sys.argv) > 1 else 10))
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && pytest tests/test_harness.py -v`
Expected: 1 passed

- [ ] **Step 5: Run the full suite + commit**

Run: `cd backend && pytest -v`
Expected: all tests pass.
```bash
git add backend/milf/harness_support.py backend/harness/run_hero.py backend/tests/test_harness.py
git commit -m "feat: reliability harness"
```

---

## Self-Review

**Spec coverage:**
- Own backend running MobileRun MobileAgent → Tasks 1, 9. ✓
- Custom `WebSocketDriver(DeviceDriver)` → Task 4. ✓
- OpenAI brain → Task 9 (`llms`). ✓
- STT ILMU (en/manglish) + MERaLiON (yue/Cantonese) behind a router, vendor-isolated → Task 5. ✓
- Confirmation gate (speak before irreversible) → Task 6, enforced in goal Task 8. ✓
- Spoken narration from events (incremental, live-feel) → Task 7. ✓
- Live-feel "no dead air" immediate acknowledgment → Tasks 8 (`acknowledgment`) + 9 (spoken before agent runs). VAD capture / barge-in / responsive TTS are Plan 2 (Android). ✓
- WhatsApp app card + contact resolution → Task 8. ✓
- Reliability harness (90% target) → Task 11. ✓
- Websocket protocol/transport → Tasks 2, 3, 10. ✓
- Testable without Android → Tasks 10, 11 (mock app). ✓
- On-device TTS / AccessibilityService / audio capture / confirmation UI → **Plan 2 (Android)**, out of scope here. ✓

**Placeholder scan:** No TBD/TODO. The external-API unknowns (MobileRun exact import/signatures; ILMU and MERaLiON request schemas) are handled by a concrete spike (Task 1) and configurable clients with mocked-HTTP tests (Task 5), not placeholders. Both ILMU and MERaLiON schemas must be verified Day 1.

**Type consistency:** `AppConnection.send_action/request_confirmation/send_narration/on_message` used consistently across Tasks 4, 6, 7, 9, 10, 11. `WebSocketDriver(connection)` constructor consistent. `run_task(connection, audio, lang, stt, agent_factory)` signature consistent across Tasks 9 and 11. Event attribute names (`description`/`subgoal`) flagged to verify against `docs/mobilerun_contract.md` in Tasks 7.

**Scope:** Single subsystem (backend), single hero flow. Android app is Plan 2.
