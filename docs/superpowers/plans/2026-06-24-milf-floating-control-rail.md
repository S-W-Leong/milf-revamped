# MILF Floating Control Rail Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current full-screen senior overlay with the approved branded Main screen, Config debug tabs, centered bottom control rail, draggable collapsed waveform bubble, compact confirmation card, text fallback, and electric-cyan action target boxes.

**Architecture:** Keep the existing `MilfSessionController`, foreground overlay service, WebSocket client, backend protocol, and accessibility dispatcher as the core. Extend the session model with readiness, text-command, rail-state, and action-target data; then replace the Compose surfaces and overlay sizing around those states. Add a small protocol extension so the text fallback can send a typed goal without pretending it is audio.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `WindowManager.TYPE_APPLICATION_OVERLAY`, Android AccessibilityService, OkHttp websocket, Python 3.11, Pydantic v2, pytest, JUnit.

---

## Scope

Implements the approved spec in `docs/superpowers/specs/2026-06-24-milf-floating-control-rail-design.md`.

In scope:

- Branded Main screen with readiness summaries.
- Debug Config screen with `Permissions`, `Backend`, `Agent`, and `Logs` tabs.
- `Start Agent` disabled until all readiness checks pass.
- `Start Agent` backgrounds to Android home and shows the centered bottom pill.
- Bottom pill idle, listening, thinking, acting, transient success/failure, run-stop, and exit-stop behavior.
- Text fallback with mic-as-send switching.
- Draggable collapsed waveform bubble.
- Compact confirmation card above the rail with button-only Yes/No.
- Electric-cyan action target boxes for agent actions.
- Removal of demo/watch-mode UI from the new user experience.

Out of scope:

- Buyer onboarding polish.
- Ambient reassurance dashboard.
- Remote rescue UI.
- Payment-specific confirmation copy beyond the generic confirmation card mechanics.

## File Structure

### Android Client

- Modify: `android/app/src/main/java/ai/milf/client/session/SessionModels.kt`
  - Owns UX enums, readiness flags, typed command text, transient messages, and action target model.
- Modify: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`
  - Owns state transitions for listening, thinking, acting, typed commands, run stop, transient failure reset, confirmations, and action target clearing.
- Modify: `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt`
  - Adds `startText(goalText, lang, callbacks)`.
- Modify: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`
  - Sends `TextGoal` as the first websocket frame for typed fallback.
- Modify: `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`
  - Adds Android-side `TextGoal`.
- Modify: `android/app/src/main/java/ai/milf/client/session/AndroidSessionDependencies.kt`
  - Adds assistant-app readiness and action-target extraction before dispatch.
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
  - Exposes Config tab selection, command text setters, typed submission, run stop, and transient-message clearing.
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
  - Starts overlay service and sends user to Android home.
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`
  - Replaces current setup page with branded Main + Config segmented debug panel.
- Modify: `android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt`
  - Replaces cream/yellow/blue palette with brand-kit colors plus red and electric cyan exceptions.
- Modify: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`
  - Replaces full-screen state surfaces with the control rail, collapsed bubble, confirmation card, and action target layer.
- Modify: `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`
  - Implements expanded full-screen transparent touch catcher, centered rail sizing, collapsed bubble sizing, drag behavior, and outside-tap collapse.
- Modify: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`
  - Wires rail callbacks to controller commands and handles transient failure reset.
- Modify: `android/app/src/main/res/drawable/ic_helper.xml`
  - Replaces chat-bubble helper icon with MILF waveform mark.
- Modify: `android/app/src/main/res/values/strings.xml`
  - Adds labels for Main, Config tabs, readiness rows, rail actions, and notification copy.
- Modify: `docs/android-demo-runbook.md`
  - Updates manual demo instructions for the control rail flow.

### Backend

- Modify: `backend/milf/protocol.py`
  - Adds `TextGoal`.
- Modify: `backend/milf/agent_runner.py`
  - Splits `run_task` so text and audio share a common `run_intent` path.
- Modify: `backend/milf/server.py`
  - Accepts either `Audio` or `TextGoal` as the first frame.

### Tests

- Modify: `backend/tests/test_protocol.py`
- Modify: `backend/tests/test_server.py`
- Modify: `backend/tests/test_agent_runner.py`
- Modify: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`
- Modify: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`
- Modify: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`
- Create: `android/app/src/test/java/ai/milf/client/overlay/OverlayWindowSizingTest.kt`

---

### Task 1: Add TextGoal Protocol For Fallback Input

Add a typed-goal first frame so the bottom rail text fallback can start the same backend task path without sending fake audio.

**Files:**

- Modify: `backend/milf/protocol.py`
- Modify: `backend/tests/test_protocol.py`
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/tests/test_agent_runner.py`
- Modify: `backend/milf/server.py`
- Modify: `backend/tests/test_server.py`
- Modify: `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`
- Modify: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`
- Modify: `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt`
- Modify: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`
- Modify: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`

- [ ] **Step 1: Add backend protocol failing test**

Append to `backend/tests/test_protocol.py`:

```python
from milf.protocol import TextGoal, decode, encode


def test_text_goal_round_trips():
    raw = encode(TextGoal(goal_text="I want to see my grandson", lang="en"))

    assert decode(raw) == TextGoal(
        goal_text="I want to see my grandson",
        lang="en",
    )
```

- [ ] **Step 2: Run backend protocol test and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_protocol.py::test_text_goal_round_trips -v
```

Expected: FAIL with `ImportError` or `AttributeError` because `TextGoal` is not defined.

- [ ] **Step 3: Implement backend `TextGoal` model**

In `backend/milf/protocol.py`, add this class after `Audio`:

```python
class TextGoal(BaseModel):
    goal_text: str
    lang: str
```

Update `_MESSAGE_TYPES` to include `TextGoal`:

```python
_MESSAGE_TYPES: dict[str, type[BaseModel]] = {
    cls.__name__: cls
    for cls in (
        Action,
        ActionResult,
        Narration,
        ConfirmRequest,
        ConfirmResponse,
        TaskComplete,
        TaskFailure,
        Audio,
        TextGoal,
    )
}
```

- [ ] **Step 4: Run backend protocol test and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_protocol.py::test_text_goal_round_trips -v
```

Expected: PASS.

- [ ] **Step 5: Add backend runner tests for shared text/audio path**

Append to `backend/tests/test_agent_runner.py`:

```python
import pytest

from milf.agent_runner import run_intent, run_task
from milf.stt import MockSTT


class _Connection:
    def __init__(self):
        self.narrations = []
        self.completed = []
        self.failed = []

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completed.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang, recovery_contact_id=None):
        self.failed.append((message, lang, recovery_contact_id))


class _Handler:
    async def stream_events(self):
        if False:
            yield None


class _Agent:
    def run(self):
        return _Handler()


def _agent_factory(goal, driver, custom_tools):
    return _Agent()


@pytest.mark.asyncio
async def test_run_intent_uses_text_without_stt():
    conn = _Connection()

    await run_intent(
        conn,
        intent="I want to see my grandson",
        lang="en",
        agent_factory=_agent_factory,
    )

    assert conn.narrations
    assert conn.completed == [("You're connected to Wei.", "en", "wei-grandson")]


@pytest.mark.asyncio
async def test_run_task_still_transcribes_audio():
    conn = _Connection()

    await run_task(
        conn,
        audio=b"audio",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=_agent_factory,
    )

    assert conn.narrations
    assert conn.completed == [("You're connected to Wei.", "en", "wei-grandson")]
```

- [ ] **Step 6: Run backend runner tests and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_agent_runner.py::test_run_intent_uses_text_without_stt tests/test_agent_runner.py::test_run_task_still_transcribes_audio -v
```

Expected: FAIL because `run_intent` does not exist.

- [ ] **Step 7: Split backend runner into `run_intent` and `run_task`**

In `backend/milf/agent_runner.py`, replace the current `run_task` function with:

```python
async def run_intent(
    connection: AppConnection,
    intent: str,
    lang: str,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
) -> Any:
    contact = resolve_contact(intent)
    escape = escape_contact()
    await connection.send_narration(acknowledgment(intent), lang)

    goal = build_goal(intent)
    driver = WebSocketDriver(connection)
    custom_tools = build_confirmation_tool(
        connection,
        lang,
        contact_id=contact.id if contact is not None else None,
    )
    agent = agent_factory(goal, driver, custom_tools)
    handler = agent.run()

    try:
        result = await narrate_events(handler, connection, lang)
    except ConfirmationDeclined:
        logger.info("Confirmation declined during agent run.", exc_info=True)
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )
        return SimpleNamespace(success=False, reason="confirmation_declined")
    except Exception:
        logger.exception("Agent run failed.")
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )
        return SimpleNamespace(success=False, reason="agent_error")

    if getattr(result, "success", True):
        if contact is not None:
            await connection.send_task_complete(
                f"You're connected to {contact.display_name}.",
                lang,
                contact_id=contact.id,
            )
        else:
            await connection.send_task_complete("Done.", lang)
    else:
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )

    return result


async def run_task(
    connection: AppConnection,
    audio: bytes,
    lang: str,
    stt: STTAdapter,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
) -> Any:
    intent = await stt.transcribe(audio, lang)
    return await run_intent(connection, intent, lang, agent_factory)
```

- [ ] **Step 8: Run backend runner tests and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_agent_runner.py::test_run_intent_uses_text_without_stt tests/test_agent_runner.py::test_run_task_still_transcribes_audio -v
```

Expected: PASS.

- [ ] **Step 9: Add backend server tests for first-frame branching**

Append to `backend/tests/test_server.py`:

```python
import json

import pytest

from milf.protocol import TextGoal, encode
from milf.server import _dispatch_first_frame


class _Connection:
    def __init__(self):
        self.calls = []


@pytest.mark.asyncio
async def test_dispatch_first_frame_routes_text_goal(monkeypatch):
    conn = _Connection()
    called = []

    async def fake_run_intent(connection, intent, lang):
        called.append((connection, intent, lang))

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)

    await _dispatch_first_frame(conn, TextGoal(goal_text="call Wei", lang="en"))

    assert called == [(conn, "call Wei", "en")]


@pytest.mark.asyncio
async def test_dispatch_first_frame_rejects_unknown_message():
    conn = _Connection()

    with pytest.raises(TypeError, match="first frame must be Audio or TextGoal"):
        await _dispatch_first_frame(conn, json.loads("{}"))
```

- [ ] **Step 10: Run backend server tests and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_server.py::test_dispatch_first_frame_routes_text_goal tests/test_server.py::test_dispatch_first_frame_rejects_unknown_message -v
```

Expected: FAIL because `_dispatch_first_frame` is not defined.

- [ ] **Step 11: Implement backend first-frame dispatch**

In `backend/milf/server.py`, update imports:

```python
from milf.agent_runner import SAFE_FAILURE_COPY, run_intent, run_task
from milf.protocol import Audio, TextGoal, decode
```

Add this function above `_handler`:

```python
async def _dispatch_first_frame(conn: AppConnection, first) -> None:
    if isinstance(first, Audio):
        stt = make_stt()
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
        await run_task(conn, audio, first.lang, stt)
        return

    if isinstance(first, TextGoal):
        await run_intent(conn, first.goal_text, first.lang)
        return

    raise TypeError("first frame must be Audio or TextGoal")
```

Replace the first-frame handling inside `_handler` with:

```python
    first = decode(await ws.recv())
    if not isinstance(first, Audio | TextGoal):
        await ws.close(code=PROTOCOL_ERROR, reason="first frame must be Audio or TextGoal")
        return
```

Replace this block:

```python
        stt = make_stt()
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
        pump_task = asyncio.create_task(pump())
        await run_task(conn, audio, first.lang, stt)
```

with:

```python
        pump_task = asyncio.create_task(pump())
        await _dispatch_first_frame(conn, first)
```

In the exception handler, compute the language safely:

```python
        lang = getattr(first, "lang", "en")
        await conn.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id="buyer-daughter",
        )
```

- [ ] **Step 12: Run backend server tests and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_server.py::test_dispatch_first_frame_routes_text_goal tests/test_server.py::test_dispatch_first_frame_rejects_unknown_message -v
```

Expected: PASS.

- [ ] **Step 13: Add Android protocol test**

Append to `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`:

```kotlin
@Test
fun textGoalRoundTrips() {
    val raw = MilfProtocol.encode(
        TextGoal(goalText = "I want to see my grandson", lang = "en")
    )

    assertEquals(
        TextGoal(goalText = "I want to see my grandson", lang = "en"),
        MilfProtocol.decode(raw)
    )
}
```

- [ ] **Step 14: Run Android protocol test and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.protocol.MilfProtocolTest.textGoalRoundTrips
```

Expected: FAIL with unresolved reference `TextGoal`.

- [ ] **Step 15: Implement Android `TextGoal`**

In `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`, add:

```kotlin
data class TextGoal(
    val goalText: String,
    val lang: String
) : MilfMessage
```

In `encode`, add:

```kotlin
            is TextGoal -> JSONObject()
                .put("goal_text", message.goalText)
                .put("lang", message.lang)
```

In `decode`, add:

```kotlin
            "TextGoal" -> TextGoal(
                goalText = data.getString("goal_text"),
                lang = data.getString("lang")
            )
```

In `typeName`, add:

```kotlin
        is TextGoal -> "TextGoal"
```

- [ ] **Step 16: Run Android protocol test and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.protocol.MilfProtocolTest.textGoalRoundTrips
```

Expected: PASS.

- [ ] **Step 17: Add Android websocket typed-start test**

Append to `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`:

```kotlin
@Test
fun startTextSendsTextGoalOnOpen() {
    val socket = FakeSocket()
    val client = MilfWebSocketClient(
        url = "ws://localhost:8765",
        socketFactory = FakeSocketFactory(socket)
    )
    val callbacks = RecordingCallbacks()

    client.startText("I want to see my grandson", "en", callbacks)
    socket.listener?.onOpen()

    assertEquals(
        """{"type":"TextGoal","data":{"goal_text":"I want to see my grandson","lang":"en"}}""",
        socket.sent.single()
    )
}
```

- [ ] **Step 18: Run Android websocket test and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.ws.MilfWebSocketClientTest.startTextSendsTextGoalOnOpen
```

Expected: FAIL because `startText` is not defined.

- [ ] **Step 19: Implement `startText` in Android websocket boundary**

In `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt`, change the interface to:

```kotlin
interface SessionSocketClient {
    fun start(goalAudio: ByteArray, lang: String, callbacks: MilfWebSocketClient.Callbacks)
    fun startText(goalText: String, lang: String, callbacks: MilfWebSocketClient.Callbacks)
    fun send(message: MilfMessage): Boolean
    fun close()
}
```

In `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`, add:

```kotlin
    override fun startText(goalText: String, lang: String, callbacks: Callbacks) {
        startSession(callbacks) { sessionId ->
            send(sessionId, TextGoal(goalText = goalText, lang = lang))
        }
    }
```

Refactor the existing `start` body so both audio and text use:

```kotlin
    private fun startSession(callbacks: Callbacks, onOpenSend: (Long) -> Unit) {
        val newSessionId: Long
        val oldSocket = synchronized(lock) {
            sessionId += 1
            newSessionId = sessionId
            val existingSocket = socket
            socket = null
            pendingMessages.clear()
            this.callbacks = callbacks
            existingSocket
        }
        oldSocket?.close()

        val openedSocket = socketFactory.open(url, object : TextListener {
            override fun onOpen() {
                onOpenSend(newSessionId)
            }

            override fun onText(text: String) {
                handleText(newSessionId, text)
            }

            override fun onClosed(reason: String?) {
                callbacksFor(newSessionId)?.let { activeCallbacks ->
                    runCatching { activeCallbacks.onClosed(reason) }
                        .onFailure { reportFailure(newSessionId, it.failureMessage()) }
                }
            }

            override fun onFailure(message: String) {
                reportFailure(newSessionId, message)
            }
        })

        val shouldCloseOpenedSocket = synchronized(lock) {
            if (sessionId == newSessionId && this.callbacks === callbacks) {
                socket = openedSocket
                pendingMessages.forEach { openedSocket.send(it) }
                pendingMessages.clear()
                false
            } else {
                true
            }
        }
        if (shouldCloseOpenedSocket) {
            openedSocket.close()
        }
    }
```

Then replace `start` with:

```kotlin
    override fun start(goalAudio: ByteArray, lang: String, callbacks: Callbacks) {
        startSession(callbacks) { sessionId ->
            val encodedAudio = audioEncoder(goalAudio)
            send(sessionId, Audio(goalAudioB64 = encodedAudio, lang = lang))
        }
    }
```

Update all fake `SessionSocketClient` classes in tests to include:

```kotlin
override fun startText(goalText: String, lang: String, callbacks: MilfWebSocketClient.Callbacks) {
    this.callbacks = callbacks
}
```

- [ ] **Step 20: Run Android websocket test and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.ws.MilfWebSocketClientTest.startTextSendsTextGoalOnOpen
```

Expected: PASS.

- [ ] **Step 21: Run protocol/websocket regression tests**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_protocol.py tests/test_agent_runner.py tests/test_server.py -v
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.protocol.MilfProtocolTest --tests ai.milf.client.ws.MilfWebSocketClientTest
```

Expected: PASS.

- [ ] **Step 22: Commit Task 1**

```bash
git add backend/milf/protocol.py backend/milf/agent_runner.py backend/milf/server.py backend/tests/test_protocol.py backend/tests/test_agent_runner.py backend/tests/test_server.py android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt
git commit -m "Add typed goal protocol for rail input"
```

---

### Task 2: Update Session State, Readiness, And Command Semantics

Make the controller express the approved rail states and readiness gate before changing Compose UI.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/session/SessionModels.kt`
- Modify: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`
- Modify: `android/app/src/main/java/ai/milf/client/session/AndroidSessionDependencies.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`

- [ ] **Step 1: Add failing controller tests for readiness, typed command, and rail states**

Append to `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`:

```kotlin
import ai.milf.client.protocol.Action

@Test
fun startGateRequiresAssistantAndLanguage() = runTest {
    val controller = MilfSessionController(
        dependencies = testDependencies(
            clients = listOf(FakeClient()),
            setupStatus = {
                SetupStatus(
                    microphoneGranted = true,
                    callPhoneGranted = true,
                    overlayGranted = true,
                    accessibilityEnabled = true,
                    assistantSelected = false
                )
            }
        ),
        graph = RelationshipGraph.demo()
    )

    controller.setBackendConnectionStatus(BackendConnectionStatus.Connected)
    assertEquals(false, controller.refreshSetupStatus().canStartHelper)

    controller.setLang("")
    assertEquals(false, controller.uiState.value.canStartHelper)
}

@Test
fun finishListeningMovesToThinkingUntilFirstAction() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.beginListening()
    controller.finishListeningAndRun()

    assertEquals(SeniorUxScreen.Thinking, controller.uiState.value.screen)
    assertEquals("Thinking...", controller.uiState.value.captions)
}

@Test
fun firstActionMovesToActingAndSetsActionTarget() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.beginListening()
    controller.finishListeningAndRun()
    client.callbacks?.onAction(
        Action(
            id = "a1",
            name = "tap",
            args = mapOf("x" to 100, "y" to 200)
        )
    )

    val state = controller.uiState.value
    assertEquals(SeniorUxScreen.Acting, state.screen)
    assertEquals("Acting...", state.captions)
    assertEquals(ActionTarget(x = 52, y = 168, width = 96, height = 64), state.actionTarget)
}

@Test
fun submitTextCommandStartsTextSessionAndMovesToThinking() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.setCommandText("  I want to see my grandson  ")
    controller.submitTextCommand()

    assertEquals("I want to see my grandson", client.startedText)
    assertEquals(SeniorUxScreen.Thinking, controller.uiState.value.screen)
    assertEquals("", controller.uiState.value.commandText)
}

@Test
fun runStopClosesClientAndReturnsToIdleWithoutRemovingOverlay() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.beginListening()
    controller.finishListeningAndRun()
    controller.stopActiveRun()

    val state = controller.uiState.value
    assertEquals(true, client.closed)
    assertEquals(SeniorUxScreen.Idle, state.screen)
    assertEquals(false, state.isRecording)
    assertEquals(false, state.isRunning)
    assertEquals("Ask MILF to do something", state.captions)
}

@Test
fun taskCompleteReturnsQuietlyToIdle() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.beginListening()
    controller.finishListeningAndRun()
    client.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")

    val state = controller.uiState.value
    assertEquals(SeniorUxScreen.Idle, state.screen)
    assertEquals("Ask MILF to do something", state.captions)
    assertEquals(null, state.success)
}
```

Update `FakeClient` in the same test file:

```kotlin
var startedText: String? = null

override fun startText(goalText: String, lang: String, callbacks: MilfWebSocketClient.Callbacks) {
    startedText = goalText
    this.callbacks = callbacks
}
```

Update the default `testDependencies` `setupStatus` in the same file so existing tests start ready:

```kotlin
setupStatus: () -> SetupStatus = {
    SetupStatus(
        microphoneGranted = true,
        callPhoneGranted = true,
        overlayGranted = true,
        accessibilityEnabled = true,
        assistantSelected = true
    )
}
```

- [ ] **Step 2: Run controller tests and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.session.MilfSessionControllerTest
```

Expected: FAIL with unresolved references for `assistantSelected`, `Thinking`, `Acting`, `ActionTarget`, `setCommandText`, `submitTextCommand`, and `stopActiveRun`.

- [ ] **Step 3: Update session models**

In `android/app/src/main/java/ai/milf/client/session/SessionModels.kt`, replace `SeniorUxScreen` with:

```kotlin
enum class SeniorUxScreen {
    Idle,
    Listening,
    Thinking,
    Acting,
    Confirming,
    Failure
}
```

Add:

```kotlin
data class ActionTarget(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)
```

Update `SetupStatus`:

```kotlin
data class SetupStatus(
    val microphoneGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val assistantSelected: Boolean = false
)
```

Update `SeniorUiState` fields:

```kotlin
data class SeniorUiState(
    val backendUrl: String = "ws://10.0.2.2:8765",
    val lang: String = "en",
    val screen: SeniorUxScreen = SeniorUxScreen.Idle,
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val captions: String = READY_PROMPT,
    val commandText: String = "",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val failure: FailureState? = null,
    val actionTarget: ActionTarget? = null,
    val backendConnectionStatus: BackendConnectionStatus = BackendConnectionStatus.Unknown,
    val microphonePermissionGranted: Boolean = false,
    val callPhonePermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val assistantSelected: Boolean = false,
    val overlayEnabled: Boolean = false,
    val appScreen: AppScreen = AppScreen.Main,
    val selectedConfigTab: ConfigTab = ConfigTab.Permissions
)
```

Add:

```kotlin
enum class ConfigTab {
    Permissions,
    Backend,
    Agent,
    Logs
}

const val READY_PROMPT = "Ask MILF to do something"
const val LISTENING_PROMPT = "Listening..."
const val THINKING_PROMPT = "Thinking..."
const val ACTING_PROMPT = "Acting..."

enum class AppScreen {
    Main,
    Config
}
```

Update `canStartHelper`:

```kotlin
val SeniorUiState.canStartHelper: Boolean
    get() = backendUrl.isNotBlank() &&
        lang.isNotBlank() &&
        backendConnectionStatus == BackendConnectionStatus.Connected &&
        microphonePermissionGranted &&
        callPhonePermissionGranted &&
        overlayPermissionGranted &&
        accessibilityEnabled &&
        assistantSelected
```

- [ ] **Step 4: Update Android setup status for assistant readiness**

In `android/app/src/main/java/ai/milf/client/session/AndroidSessionDependencies.kt`, update `setupStatus`:

```kotlin
        setupStatus = {
            SetupStatus(
                microphoneGranted = application.hasPermission(Manifest.permission.RECORD_AUDIO),
                callPhoneGranted = application.hasPermission(Manifest.permission.CALL_PHONE),
                overlayGranted = Settings.canDrawOverlays(application),
                accessibilityEnabled = MilfAccessibilityService.instance != null,
                assistantSelected = application.isMilfAssistantSelected()
            )
        },
```

Add:

```kotlin
private fun Context.isMilfAssistantSelected(): Boolean {
    val selected = Settings.Secure.getString(contentResolver, "assistant") ?: return false
    return selected.contains(packageName)
}
```

- [ ] **Step 5: Update controller transitions and typed commands**

In `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`, add:

```kotlin
fun setCommandText(text: String) {
    _uiState.update { it.copy(commandText = text) }
}

fun setAppScreen(screen: AppScreen) {
    _uiState.update { it.copy(appScreen = screen) }
}

fun setConfigTab(tab: ConfigTab) {
    _uiState.update { it.copy(appScreen = AppScreen.Config, selectedConfigTab = tab) }
}
```

Update `beginListening` state copy:

```kotlin
it.copy(
    screen = SeniorUxScreen.Listening,
    isRecording = true,
    isRunning = false,
    captions = LISTENING_PROMPT,
    commandText = "",
    lastNarration = null,
    confirmation = null,
    failure = null,
    actionTarget = null
)
```

Update `finishListeningAndRun` state copy:

```kotlin
it.copy(
    screen = SeniorUxScreen.Thinking,
    isRecording = false,
    isRunning = true,
    captions = THINKING_PROMPT,
    commandText = "",
    confirmation = null,
    failure = null,
    actionTarget = null
)
```

Add:

```kotlin
fun submitTextCommand() {
    val state = _uiState.value
    val goal = state.commandText.trim()
    if (goal.isBlank() || state.isRunning || state.isRecording) return

    nextSessionId()
    closeActiveClient()
    dependencies.narrator.stop()
    val client = try {
        dependencies.clientFactory(state.backendUrl)
    } catch (exception: RuntimeException) {
        moveLocalSessionToFailure()
        return
    }
    val callbackSessionId = sessionId.get()
    dependencies.activeClient = client
    _uiState.update {
        it.copy(
            screen = SeniorUxScreen.Thinking,
            isRecording = false,
            isRunning = true,
            captions = THINKING_PROMPT,
            commandText = "",
            confirmation = null,
            failure = null,
            actionTarget = null
        )
    }
    try {
        client.startText(goal, state.lang, callbacks(callbackSessionId, client))
    } catch (exception: RuntimeException) {
        moveLocalSessionToFailure(expectedClient = client)
    }
}
```

In `callbacks.onAction`, set acting state before dispatch:

```kotlin
                _uiState.update {
                    if (!isCurrentSession(callbackSessionId)) {
                        it
                    } else {
                        it.copy(
                            screen = SeniorUxScreen.Acting,
                            captions = ACTING_PROMPT,
                            actionTarget = ActionTarget.from(action)
                        )
                    }
                }
                return dependencies.dispatch(action)
```

Add this private extension in the same file:

```kotlin
private fun ActionTarget.Companion.from(action: Action): ActionTarget? = when (action.name) {
    "tap" -> {
        val x = action.args["x"]?.numberToInt() ?: return null
        val y = action.args["y"]?.numberToInt() ?: return null
        ActionTarget(x = x - 48, y = y - 32, width = 96, height = 64)
    }

    "swipe" -> {
        val x1 = action.args["x1"]?.numberToInt() ?: return null
        val y1 = action.args["y1"]?.numberToInt() ?: return null
        val x2 = action.args["x2"]?.numberToInt() ?: return null
        val y2 = action.args["y2"]?.numberToInt() ?: return null
        val left = minOf(x1, x2) - 24
        val top = minOf(y1, y2) - 24
        ActionTarget(
            x = left,
            y = top,
            width = kotlin.math.abs(x2 - x1) + 48,
            height = kotlin.math.abs(y2 - y1) + 48
        )
    }

    else -> null
}

private fun Any.numberToInt(): Int? = when (this) {
    is Int -> this
    is Long -> toInt()
    is Double -> toInt()
    is Float -> toInt()
    is Number -> toInt()
    else -> null
}
```

Add a companion to `ActionTarget` in `SessionModels.kt`:

```kotlin
data class ActionTarget(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    companion object
}
```

Update `onTaskComplete` to return to idle:

```kotlin
it.copy(
    screen = SeniorUxScreen.Idle,
    isRecording = false,
    isRunning = false,
    captions = READY_PROMPT,
    confirmation = null,
    failure = null,
    actionTarget = null
)
```

Update all old `SeniorUxScreen.Working` references to `SeniorUxScreen.Acting`.

Add:

```kotlin
fun stopActiveRun() {
    cancelActiveSession()
}

fun clearTransientMessage() {
    _uiState.update {
        if (it.screen == SeniorUxScreen.Failure) {
            it.copy(
                screen = SeniorUxScreen.Idle,
                captions = READY_PROMPT,
                failure = null,
                actionTarget = null
            )
        } else {
            it
        }
    }
}
```

Update `cancelActiveSession` captions to `READY_PROMPT`.

- [ ] **Step 6: Update MainViewModel façade**

In `android/app/src/main/java/ai/milf/client/MainViewModel.kt`, add:

```kotlin
fun setCommandText(text: String) = controller.setCommandText(text)
fun submitTextCommand() = controller.submitTextCommand()
fun stopActiveRun() = controller.stopActiveRun()
fun clearTransientMessage() = controller.clearTransientMessage()
fun setAppScreen(screen: AppScreen) = controller.setAppScreen(screen)
fun setConfigTab(tab: ConfigTab) = controller.setConfigTab(tab)
```

Remove `setWatchMode` and `setDemoMode` only after Task 5 removes all call sites.

- [ ] **Step 7: Run controller tests and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.session.MilfSessionControllerTest
```

Expected: PASS after updating tests that previously expected `Working`, `Success`, `watchMode`, or `demoMode`.

- [ ] **Step 8: Commit Task 2**

```bash
git add android/app/src/main/java/ai/milf/client/session/SessionModels.kt android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/main/java/ai/milf/client/session/AndroidSessionDependencies.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt
git commit -m "Refine session state for floating control rail"
```

---

### Task 3: Rebrand Main And Config Screens

Replace the current setup page with a branded Main screen and segmented Config debug panel.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`
- Modify: `android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add brand colors and dimensions**

Replace `MilfColors` in `android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt` with:

```kotlin
object MilfColors {
    val Obsidian = Color(0xFF0A0A0A)
    val DarkSurface = Color(0xFF141414)
    val CardSurface = Color(0xFF1C1C1C)
    val Border = Color(0x14FFFFFF)
    val BorderStrong = Color(0x2EFFFFFF)
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFA0A0A0)
    val TextMuted = Color(0xFF555555)
    val Sage = Color(0xFFA8C5B5)
    val SageDim = Color(0x26A8C5B5)
    val ConfirmSage = Color(0xFF7FB5A0)
    val NoRed = Color(0xFFB91C1C)
    val ElectricCyan = Color(0xFF00E5FF)
    val RunStopWhite = Color(0xFFF5F5F5)
}

object MilfDimens {
    val PrimaryTarget = 56.dp
    val RailHeight = 62.dp
    val RailMaxWidth = 560.dp
    val RailHorizontalMargin = 24.dp
    val RailCorner = 22.dp
    val BubbleSize = 66.dp
    val BubbleCorner = 33.dp
    val CardCorner = 18.dp
}
```

- [ ] **Step 2: Add strings**

In `android/app/src/main/res/values/strings.xml`, add:

```xml
<string name="main_title">MILF</string>
<string name="main_tagline">Make Interfaces Less Frustrating</string>
<string name="main_headline">Ready to hold the phone's mental model.</string>
<string name="main_subhead">Start Agent unlocks once permissions, backend, voice, calls, language, and assistant settings are ready.</string>
<string name="start_agent">Start Agent</string>
<string name="config">Config</string>
<string name="config_permissions">Permissions</string>
<string name="config_backend">Backend</string>
<string name="config_agent">Agent</string>
<string name="config_logs">Logs</string>
<string name="status_ready">Ready</string>
<string name="status_missing">Missing</string>
<string name="status_connected">Connected</string>
<string name="status_not_connected">Not connected</string>
```

- [ ] **Step 3: Replace `MilfUi` with Main/Config split**

Replace the body of `android/app/src/main/java/ai/milf/client/MilfUi.kt` with composables matching this structure:

```kotlin
@Composable
fun MilfUi(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onCheckBackendConnection: () -> Unit,
    onLangChange: (String) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAssistSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onSetAppScreen: (AppScreen) -> Unit,
    onConfigTabChange: (ConfigTab) -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MilfColors.Obsidian
        ) {
            if (state.appScreen == AppScreen.Main) {
                MainScreen(state, onStartOverlay, onConfigClick = {
                    onSetAppScreen(AppScreen.Config)
                })
            } else {
                ConfigScreen(
                    state = state,
                    onBackendUrlChange = onBackendUrlChange,
                    onCheckBackendConnection = onCheckBackendConnection,
                    onLangChange = onLangChange,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onOpenAssistSettings = onOpenAssistSettings,
                    onRequestAudioPermission = onRequestAudioPermission,
                    onRequestCallPermission = onRequestCallPermission,
                    onStartOverlay = onStartOverlay,
                    onStopOverlay = onStopOverlay,
                    onSetAppScreen = onSetAppScreen,
                    onConfigTabChange = onConfigTabChange
                )
            }
        }
    }
}
```

Use this readiness row model inside `MilfUi.kt`:

```kotlin
private data class ReadinessRow(
    val label: String,
    val ready: Boolean,
    val readyText: String = "Ready",
    val missingText: String = "Missing"
)

private fun SeniorUiState.readinessRows(): List<ReadinessRow> = listOf(
    ReadinessRow("Microphone", microphonePermissionGranted),
    ReadinessRow("Phone calls", callPhonePermissionGranted),
    ReadinessRow("Overlay", overlayPermissionGranted),
    ReadinessRow("Accessibility", accessibilityEnabled),
    ReadinessRow("Assistant app", assistantSelected, readyText = "Selected", missingText = "Not selected"),
    ReadinessRow("Language", lang.isNotBlank(), readyText = lang.ifBlank { "Selected" }),
    ReadinessRow(
        "Backend",
        backendConnectionStatus == BackendConnectionStatus.Connected,
        readyText = "Connected",
        missingText = backendConnectionStatus.name.lowercase().replaceFirstChar { it.uppercase() }
    )
)
```

Implement `MainScreen` with:

```kotlin
Button(
    onClick = onStartOverlay,
    enabled = state.canStartHelper,
    modifier = Modifier
        .fillMaxWidth()
        .height(56.dp),
    shape = RoundedCornerShape(14.dp),
    colors = ButtonDefaults.buttonColors(
        containerColor = MilfColors.SageDim,
        contentColor = MilfColors.Sage,
        disabledContainerColor = MilfColors.CardSurface,
        disabledContentColor = MilfColors.TextMuted
    )
) {
    Text("Start Agent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
}
```

Implement `ConfigScreen` tabs with four buttons:

```kotlin
ConfigTab.entries.forEach { tab ->
    val selected = state.selectedConfigTab == tab
    Button(
        onClick = { onConfigTabChange(tab) },
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MilfColors.SageDim else MilfColors.DarkSurface,
            contentColor = if (selected) MilfColors.Sage else MilfColors.TextSecondary
        )
    ) {
        Text(tab.name)
    }
}
```

Make tab contents explicit:

- `Permissions`: microphone, phone calls, overlay, accessibility, assistant app buttons.
- `Backend`: websocket text field and connection check button.
- `Agent`: language buttons and overlay start/stop controls.
- `Logs`: show `state.captions`, `state.lastNarration ?: "No narration yet"`, and `state.backendConnectionStatus.name`.

- [ ] **Step 4: Wire MainActivity to app screen and config tab callbacks**

In `android/app/src/main/java/ai/milf/client/MainActivity.kt`, pass:

```kotlin
onConfigTabChange = viewModel::setConfigTab,
onSetAppScreen = viewModel::setAppScreen
```

If using a single `onConfigTabChange` callback to enter Config, set screen in `setConfigTab`.

- [ ] **Step 5: Run Android compile tests**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add android/app/src/main/java/ai/milf/client/MilfUi.kt android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/main/java/ai/milf/client/session/SessionModels.kt android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/main/res/values/strings.xml
git commit -m "Rebrand main and config screens"
```

---

### Task 4: Implement Overlay Window Sizing And Collapse Behavior

Make the overlay window match the rail model: expanded full-screen transparent touch catcher with centered rail, collapsed pass-through bubble with drag.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`
- Create: `android/app/src/test/java/ai/milf/client/overlay/OverlayWindowSizingTest.kt`

- [ ] **Step 1: Add sizing tests**

Create `android/app/src/test/java/ai/milf/client/overlay/OverlayWindowSizingTest.kt`:

```kotlin
package ai.milf.client.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayWindowSizingTest {
    @Test
    fun expandedWindowCoversScreenForOutsideTapCatcher() {
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, OverlayWindowSizing.expandedWidthPx())
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, OverlayWindowSizing.expandedHeightPx())
    }

    @Test
    fun collapsedWindowUsesBubbleBounds() {
        assertEquals(66, OverlayWindowSizing.collapsedSizeDp())
    }

    @Test
    fun railWidthLeavesSideMargins() {
        assertEquals(312, OverlayWindowSizing.railWidthPx(screenWidthPx = 360, density = 1f))
        assertEquals(560, OverlayWindowSizing.railWidthPx(screenWidthPx = 900, density = 1f))
    }
}
```

- [ ] **Step 2: Run sizing tests and verify failure**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.overlay.OverlayWindowSizingTest
```

Expected: FAIL because the sizing helpers do not exist.

- [ ] **Step 3: Implement sizing helpers**

In `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`, replace `OverlayWindowSizing` with:

```kotlin
internal object OverlayWindowSizing {
    fun expandedWidthPx(): Int = WindowManager.LayoutParams.MATCH_PARENT
    fun expandedHeightPx(): Int = WindowManager.LayoutParams.MATCH_PARENT
    fun collapsedSizeDp(): Int = 66

    fun railWidthPx(screenWidthPx: Int, density: Float): Int {
        val marginPx = (24 * density).toInt()
        val maxWidthPx = (560 * density).toInt()
        return minOf(screenWidthPx - marginPx * 2, maxWidthPx)
    }
}
```

- [ ] **Step 4: Update expanded/collapsed param selection**

In `paramsFor`, use full-screen expanded params for all non-collapsed states:

```kotlin
private fun paramsFor(state: SeniorUiState): WindowManager.LayoutParams {
    val expanded = !state.isCollapsed
    return WindowManager.LayoutParams(
        if (expanded) OverlayWindowSizing.expandedWidthPx() else dp(OverlayWindowSizing.collapsedSizeDp()),
        if (expanded) OverlayWindowSizing.expandedHeightPx() else dp(OverlayWindowSizing.collapsedSizeDp()),
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = if (expanded) Gravity.TOP or Gravity.START else Gravity.TOP or Gravity.END
        x = if (expanded) 0 else collapsedX
        y = if (expanded) 0 else collapsedY
    }
}
```

Add `isCollapsed: Boolean = false` to `SeniorUiState`.

Add controller methods:

```kotlin
fun collapseOverlay() {
    _uiState.update { it.copy(isCollapsed = true) }
}

fun expandOverlay() {
    _uiState.update { it.copy(isCollapsed = false) }
}
```

- [ ] **Step 5: Update drag/tap listener**

In `DragTouchListener`, use `currentState.isCollapsed`:

```kotlin
if (!currentState.isCollapsed) return false
```

On tap:

```kotlin
callbacks.onExpandOverlay()
```

Update `Callbacks`:

```kotlin
fun onExpandOverlay()
```

- [ ] **Step 6: Run sizing tests and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.overlay.OverlayWindowSizingTest
```

Expected: PASS.

- [ ] **Step 7: Commit Task 4**

```bash
git add android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt android/app/src/main/java/ai/milf/client/session/SessionModels.kt android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/test/java/ai/milf/client/overlay/OverlayWindowSizingTest.kt
git commit -m "Implement rail overlay sizing behavior"
```

---

### Task 5: Replace SeniorOverlayUi With Control Rail

Build the actual overlay UI: bottom pill, collapsed waveform bubble, listening waveform, thinking/acting status, run stop, exit stop, transient failure, and confirmation card.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`
- Modify: `android/app/src/main/res/drawable/ic_helper.xml`
- Modify: `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`
- Modify: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`

- [ ] **Step 1: Replace helper icon with waveform mark**

Replace `android/app/src/main/res/drawable/ic_helper.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="@android:color/transparent"
        android:pathData="M8,31 Q17,12 24,22 Q31,32 40,13"
        android:strokeColor="#A8C5B5"
        android:strokeLineCap="round"
        android:strokeWidth="3" />
    <path
        android:fillColor="#555555"
        android:pathData="M8,31m-2.5,0a2.5,2.5 0,1 1,5 0a2.5,2.5 0,1 1,-5 0" />
    <path
        android:fillColor="#A8C5B5"
        android:pathData="M40,13m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0" />
</vector>
```

- [ ] **Step 2: Replace overlay callbacks**

In `SeniorOverlayUi`, use this signature:

```kotlin
fun SeniorOverlayUi(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onOutsideExpandedTap: () -> Unit,
    onExpandOverlay: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTransientMessageShown: () -> Unit
)
```

Update `OverlayWindowController.Callbacks` and `setOverlayContent` to match these names.

- [ ] **Step 3: Implement collapsed bubble**

In `SeniorOverlayUi.kt`, add:

```kotlin
@Composable
private fun CollapsedBubble(onExpandOverlay: () -> Unit) {
    Button(
        onClick = onExpandOverlay,
        modifier = Modifier.size(MilfDimens.BubbleSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.DarkSurface),
        border = BorderStroke(1.dp, MilfColors.Border)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helper),
            contentDescription = "Expand MILF",
            tint = Color.Unspecified,
            modifier = Modifier.size(34.dp)
        )
    }
}
```

- [ ] **Step 4: Implement expanded overlay shell**

Add:

```kotlin
@Composable
private fun ExpandedOverlayShell(
    state: SeniorUiState,
    onOutsideExpandedTap: () -> Unit,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTransientMessageShown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onOutsideExpandedTap
            )
    ) {
        state.actionTarget?.let { target ->
            ActionTargetBox(target)
        }
        if (state.screen == SeniorUxScreen.Confirming) {
            ConfirmationCard(
                state = state,
                onApprove = onApprove,
                onDeny = onDeny,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 104.dp)
            )
        }
        ControlRail(
            state = state,
            onMicTap = onMicTap,
            onCommandTextChange = onCommandTextChange,
            onSubmitText = onSubmitText,
            onRunStop = onRunStop,
            onExitAgent = onExitAgent,
            onTransientMessageShown = onTransientMessageShown,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 22.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                )
        )
    }
}
```

- [ ] **Step 5: Implement rail content states**

Add:

```kotlin
@Composable
private fun ControlRail(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onTransientMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.screen == SeniorUxScreen.Failure) {
        LaunchedEffect(state.failure?.message) {
            delay(2400)
            onTransientMessageShown()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = MilfDimens.RailMaxWidth)
            .padding(horizontal = MilfDimens.RailHorizontalMargin)
            .height(MilfDimens.RailHeight),
        shape = RoundedCornerShape(MilfDimens.RailCorner),
        color = MilfColors.DarkSurface,
        border = BorderStroke(1.dp, MilfColors.Border),
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            RailCenterContent(
                state = state,
                onCommandTextChange = onCommandTextChange,
                modifier = Modifier.weight(1f)
            )
            RailPrimaryAction(
                state = state,
                onMicTap = onMicTap,
                onSubmitText = onSubmitText,
                onRunStop = onRunStop
            )
            ExitAgentButton(onExitAgent)
        }
    }
}
```

Implement `RailCenterContent`:

```kotlin
@Composable
private fun RailCenterContent(
    state: SeniorUiState,
    onCommandTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MilfColors.CardSurface,
        border = BorderStroke(1.dp, MilfColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            when (state.screen) {
                SeniorUxScreen.Idle -> BasicTextField(
                    value = state.commandText,
                    onValueChange = onCommandTextChange,
                    singleLine = true,
                    textStyle = TextStyle(color = MilfColors.TextPrimary, fontSize = 14.sp),
                    decorationBox = { inner ->
                        if (state.commandText.isBlank()) {
                            Text(READY_PROMPT, color = MilfColors.TextSecondary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )

                SeniorUxScreen.Listening -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniWaveform()
                    Text(LISTENING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                }

                SeniorUxScreen.Thinking -> Text(THINKING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Acting -> Text(ACTING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Confirming -> Text("Waiting for confirmation", color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Failure -> Text(state.failure?.message ?: state.captions, color = MilfColors.TextPrimary, fontSize = 13.sp)
            }
        }
    }
}
```

Implement primary buttons:

```kotlin
@Composable
private fun RailPrimaryAction(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit
) {
    when {
        state.screen == SeniorUxScreen.Thinking || state.screen == SeniorUxScreen.Acting -> RunStopButton(onRunStop)
        state.screen == SeniorUxScreen.Idle && state.commandText.isNotBlank() -> SendButton(onSubmitText)
        else -> MicButton(onMicTap)
    }
}
```

Use `Icons.Default.Mic`, `Icons.Default.Send`, `Icons.Default.Close`, and draw the black square with a `Box`:

```kotlin
@Composable
private fun RunStopButton(onRunStop: () -> Unit) {
    IconButton(
        onClick = onRunStop,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.RunStopWhite, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(Color.Black, RoundedCornerShape(2.dp))
        )
    }
}
```

- [ ] **Step 6: Implement confirmation card**

Add:

```kotlin
@Composable
private fun ConfirmationCard(
    state: SeniorUiState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confirmation = state.confirmation ?: return
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(MilfDimens.CardCorner),
        color = MilfColors.DarkSurface,
        border = BorderStroke(1.dp, MilfColors.Border),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                confirmation.summary,
                color = MilfColors.TextPrimary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDeny,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
                ) {
                    Text("No", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MilfColors.SageDim,
                        contentColor = MilfColors.Sage
                    )
                ) {
                    Text("Yes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
```

- [ ] **Step 7: Update service callbacks**

In `SeniorOverlayService`, map callbacks:

```kotlin
override fun onMicTap() {
    val state = controller.uiState.value
    if (state.isRecording) {
        controller.finishListeningAndRun()
    } else {
        beginListeningSafely(controller)
    }
}

override fun onCommandTextChange(text: String) = controller.setCommandText(text)
override fun onSubmitText() = controller.submitTextCommand()
override fun onRunStop() = controller.stopActiveRun()
override fun onExitAgent() = stopSelf()
override fun onOutsideExpandedTap() = controller.collapseOverlay()
override fun onExpandOverlay() = controller.expandOverlay()
override fun onTransientMessageShown() = controller.clearTransientMessage()
```

Remove confirmation voice recognizer usage from the overlay flow. Keep the class in the codebase if other tests still cover it; do not call it from the new confirmation card.

- [ ] **Step 8: Run Android tests and assemble**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 9: Commit Task 5**

```bash
git add android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt android/app/src/main/res/drawable/ic_helper.xml
git commit -m "Replace overlay with floating control rail"
```

---

### Task 6: Draw Electric-Cyan Action Target Boxes

Render the target box above the live phone screen while preserving the bottom rail.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`
- Modify: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`
- Modify: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`

- [ ] **Step 1: Add target-clearing test**

Append to `MilfSessionControllerTest.kt`:

```kotlin
@Test
fun actionTargetClearsWhenRunEnds() = runTest {
    val client = FakeClient()
    val controller = fakeController(client = client)

    controller.beginListening()
    controller.finishListeningAndRun()
    client.callbacks?.onAction(
        Action(
            id = "a1",
            name = "tap",
            args = mapOf("x" to 100, "y" to 200)
        )
    )
    client.callbacks?.onTaskFailure("Need help.", "en", "buyer-daughter")

    assertEquals(null, controller.uiState.value.actionTarget)
}
```

- [ ] **Step 2: Run target-clearing test and verify pass**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests ai.milf.client.session.MilfSessionControllerTest.actionTargetClearsWhenRunEnds
```

Expected: PASS. If it fails with a non-null target, update the Task 2 terminal-state copies so `actionTarget = null` when the run ends.

- [ ] **Step 3: Implement visual target box**

In `SeniorOverlayUi.kt`, add:

```kotlin
@Composable
private fun ActionTargetBox(target: ActionTarget) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(target.x, target.y) }
            .width(with(density) { target.width.toDp() })
            .height(with(density) { target.height.toDp() })
            .border(3.dp, MilfColors.ElectricCyan, RoundedCornerShape(8.dp))
            .background(MilfColors.ElectricCyan.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
    )
}
```

Use raw-pixel offset with density-converted size because accessibility action coordinates are screen pixels.

- [ ] **Step 4: Run Android tests and assemble**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 5: Commit Task 6**

```bash
git add android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt
git commit -m "Show cyan target boxes for agent actions"
```

---

### Task 7: Start Agent To Android Home And Update Assist Entry

Make `Start Agent` immediately background to Android home while keeping the overlay active.

**Files:**

- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`

- [ ] **Step 1: Update MainActivity launch flow**

In `MainActivity`, replace `onStartOverlay` body with:

```kotlin
onStartOverlay = {
    if (viewModel.canStartHelper()) {
        SeniorOverlayService.start(this, startListening = false)
        val homeIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(homeIntent)
    }
}
```

- [ ] **Step 2: Update AssistEntryActivity to expand rail and start listening**

In `AssistEntryActivity`, keep the existing service start, but ensure it uses:

```kotlin
SeniorOverlayService.start(this, startListening = true)
finish()
```

Do not open the Main screen when readiness is valid.

- [ ] **Step 3: Ensure overlay close does not reopen app**

In `SeniorOverlayService.onDestroy`, keep:

```kotlin
controller.cancelActiveSession()
controller.setOverlayEnabled(false)
```

Do not call `openSetupActivity()` from `onDestroy`.

- [ ] **Step 4: Run Android tests and assemble**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 5: Commit Task 7**

```bash
git add android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt
git commit -m "Launch rail agent from main screen"
```

---

### Task 8: Update Runbook And Perform Full Verification

Update docs and run full automated verification. Manual overlay checks still need a device/emulator.

**Files:**

- Modify: `docs/android-demo-runbook.md`

- [ ] **Step 1: Update runbook live flow**

In `docs/android-demo-runbook.md`, replace `Live Flow` with:

```markdown
## Live Flow

1. Open MILF.
2. Use Config to grant microphone, phone, overlay, accessibility, and assistant-app readiness.
3. In Config > Backend, enter the websocket URL and tap the connection check.
4. Return to Main.
5. Confirm Start Agent is enabled.
6. Tap Start Agent.
7. Verify Android home appears and the MILF bottom pill is centered near the bottom.
8. Tap the mic once. The pill should show a pulsing waveform and `Listening...`.
9. Say: I want to see my grandson.
10. Tap the mic again. The pill should show `Thinking...`, then `Acting...`.
11. Verify MILF shows an electric-cyan target box around each screen element it acts on.
12. When the confirmation card appears, tap Yes.
13. Verify the WhatsApp video-call screen opens and the pill returns to `Ask MILF to do something`.
```

Replace `UX overlay demo path` with:

```markdown
## Floating Control Rail Checks

- Expanded bottom pill is a shorter centered rail and does not touch screen edges.
- Expanded outside tap collapses to the round waveform bubble and does not pass through.
- Collapsed bubble is draggable.
- Tapping the collapsed bubble re-expands the bottom pill.
- Taps outside the collapsed bubble pass through to Android normally.
- Text fallback shows `Ask MILF to do something`.
- Typing text turns the mic button into send.
- During `Thinking...` and `Acting...`, both the black-square run stop and white-cross exit are visible.
- Black-square run stop interrupts only the current run and keeps the rail available.
- White-cross exit removes the overlay and leaves the user on the current screen.
```

- [ ] **Step 2: Run backend full tests**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
.venv/bin/pytest backend -v
```

Expected: PASS.

- [ ] **Step 3: Run Android full tests and build**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: PASS.

- [ ] **Step 4: Manual verification on emulator or device**

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:installDebug
```

Verify:

- Main screen uses dark brand theme.
- Start Agent is disabled while any readiness item is missing.
- Config tabs are visible and selectable.
- Start Agent sends Android to home.
- Bottom rail appears centered and compact.
- Expanded outside tap collapses the rail.
- Collapsed bubble drags and re-expands.
- Collapsed outside taps pass through.
- Mic toggles Listening on and off.
- Text input turns mic into send.
- Thinking/Acting shows both stop controls.
- Confirmation card appears above rail and uses button-only Yes/No.
- Cyan target box appears on tap actions.

- [ ] **Step 5: Commit Task 8**

```bash
git add docs/android-demo-runbook.md
git commit -m "Update demo runbook for floating control rail"
```

---

## Final Verification

Run:

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped
.venv/bin/pytest backend -v
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
git status --short
```

Expected:

- Backend tests PASS.
- Android unit tests PASS.
- Debug APK builds.
- `git status --short` shows only intentional uncommitted manual-test notes, or is clean after commits.

## Manual Acceptance Checklist

- Main screen is branded and shows readiness summaries.
- Start Agent is disabled until every required readiness check is ready.
- Config uses `Permissions`, `Backend`, `Agent`, and `Logs` segmented tabs.
- Start Agent backgrounds the app to Android home and shows the centered bottom pill.
- Expanded outside tap collapses to the draggable waveform bubble and does not pass through.
- Collapsed outside taps pass through to the underlying app.
- Idle, Listening, Thinking, Acting, quiet success, and transient failure behavior matches the approved spec.
- Text fallback uses mic-as-send switching.
- Voice push-to-talk submits immediately after the second mic tap.
- Thinking/Acting show both black-square run stop and white-cross exit.
- Agent actions display visible electric-cyan bounding boxes.
- Consequential actions show compact Yes/No confirmation above the rail.
- Visual styling follows the MILF brand kit with only the approved exception colors.
