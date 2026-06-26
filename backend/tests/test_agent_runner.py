import logging
from types import SimpleNamespace

from websockets.exceptions import ConnectionClosedOK

from milf.agent_runner import build_mobile_config, run_intent, run_task
from milf.intent_router import IntentRoute
from milf.session import MILFSession
from milf.stt import MockSTT


class FakeHandler:
    def __init__(self, result=None):
        self.result = result or SimpleNamespace(success=True, reason="ok")

    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            return self.result

        return _r().__await__()


class FakeConn:
    def __init__(self, confirmation_approved=True):
        self.confirmation_approved = confirmation_approved
        self.narrations = []
        self.completions = []
        self.failures = []
        self.confirmation_requests = []

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang):
        self.failures.append((message, lang))

    async def request_confirmation(self, summary, lang, contact_id=None):
        self.confirmation_requests.append((summary, lang, contact_id))
        return self.confirmation_approved


async def wei_router(intent, lang):
    return IntentRoute(
        kind="execute",
        normalized_intent="I want to see my grandson",
        requires_confirmation=True,
    )


async def test_run_task_acks_then_builds_and_runs():
    captured = {}

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        captured["tools"] = custom_tools
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()
    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert result.success is True
    assert "Contact id:" not in captured["goal"]
    assert "Intended contact:" not in captured["goal"]
    assert "confirm_action" in captured["tools"]
    assert conn.narrations == [("Okay, let me help you with that.", "en")]
    assert conn.completions == [("Done.", "en", None)]
    assert conn.failures == []


def test_mobile_config_enables_manager_and_executor_vision():
    config = build_mobile_config()

    assert config.agent.manager.vision is True
    assert config.agent.executor.vision is True


async def test_run_task_includes_agent_memory_in_goal_and_router():
    captured = {}

    async def fake_router(intent, lang, session=None, memory=""):
        captured["router_memory"] = memory
        return IntentRoute(
            kind="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        return SimpleNamespace(run=lambda: FakeHandler())

    await run_task(
        connection=FakeConn(),
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=fake_router,
        memory="Wei is my grandson. Use WhatsApp video.",
    )

    assert captured["router_memory"] == "Wei is my grandson. Use WhatsApp video."
    assert "Agent memory:" in captured["goal"]
    assert "Wei is my grandson. Use WhatsApp video." in captured["goal"]


async def test_run_intent_uses_text_without_stt():
    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_intent(
        conn,
        intent="I want to see my grandson",
        lang="en",
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert conn.narrations
    assert conn.completions == [("Done.", "en", None)]


async def test_run_intent_short_circuits_greeting_before_agent():
    called = False

    def fake_factory(goal, driver, custom_tools):
        nonlocal called
        called = True
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    async def fake_router(intent, lang):
        return IntentRoute(
            kind="reply",
            message="Hi, what would you like me to help you do on your phone?",
        )

    result = await run_intent(
        conn,
        intent="helo",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert result.success is True
    assert result.reason == "reply"
    assert called is False
    assert conn.narrations == [
        ("Hi, what would you like me to help you do on your phone?", "en")
    ]
    assert conn.completions == [
        ("Hi, what would you like me to help you do on your phone?", "en", None)
    ]


async def test_run_intent_short_circuits_incomplete_command_before_agent():
    called = False

    def fake_factory(goal, driver, custom_tools):
        nonlocal called
        called = True
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    async def fake_router(intent, lang):
        return IntentRoute(
            kind="clarify",
            message="Who should I send that to, and which app should I use?",
        )

    result = await run_intent(
        conn,
        intent="send hello",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert result.success is False
    assert result.reason == "clarify"
    assert called is False
    assert conn.narrations
    assert "who" in conn.narrations[0][0].lower()
    assert conn.completions == [(conn.narrations[0][0], "en", None)]


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


async def test_run_intent_uses_normalized_intent_from_router():
    captured = {}

    async def fake_router(intent, lang):
        return IntentRoute(
            kind="execute",
            normalized_intent="I want to see my grandson",
        )

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_intent(
        conn,
        intent="can see boy ah",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert "Spoken intent: 'I want to see my grandson'." in captured["goal"]
    assert conn.completions == [("Done.", "en", None)]


async def test_run_intent_does_not_inject_contact_context_from_router():
    captured = {}

    async def fake_router(intent, lang):
        return IntentRoute(
            kind="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            contact_id="wei-grandson",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        captured["tools"] = custom_tools
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_intent(
        conn,
        intent="can see boy ah",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert "Contact id:" not in captured["goal"]
    assert "Intended contact:" not in captured["goal"]
    assert captured["tools"]["confirm_action"]["function"]
    assert conn.completions == [("Done.", "en", None)]


async def test_run_intent_uses_router_contact_for_acknowledgment():
    async def fake_router(intent, lang, session=None):
        return IntentRoute(
            kind="execute",
            normalized_intent="Start a WhatsApp video call.",
            contact_id="wei-grandson",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_intent(
        conn,
        intent="can see boy ah",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
    )

    assert conn.narrations == [("Okay, let me help you with that.", "en")]


async def test_run_intent_records_clarification_in_session():
    async def fake_router(intent, lang, session=None):
        return IntentRoute(kind="clarify", message="Who should I send that to?")

    conn = FakeConn()
    session = MILFSession()

    result = await run_intent(
        conn,
        intent="send hello",
        lang="en",
        agent_factory=lambda goal, driver, custom_tools: None,
        intent_router=fake_router,
        session=session,
    )

    assert result.success is False
    assert session.recent_user_inputs == ["send hello"]
    assert session.pending_clarification is not None
    assert session.pending_clarification.question == "Who should I send that to?"
    assert session.pending_clarification.original_intent == "send hello"


async def test_run_intent_uses_pending_session_context_for_follow_up():
    captured = {}
    session = MILFSession()

    async def fake_router(intent, lang, session=None):
        if intent == "send hello":
            return IntentRoute(kind="clarify", message="Who should I send that to?")
        assert session is not None
        assert session.pending_clarification is not None
        assert session.pending_clarification.original_intent == "send hello"
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

    assert "Spoken intent: 'Send hello to Wei on WhatsApp.'." in captured["goal"]
    assert "MILF session context:" in captured["goal"]
    assert "Recent user inputs: send hello, Wei" in captured["goal"]
    assert session.pending_clarification is None
    assert session.last_contact_id == "wei-grandson"
    assert session.last_normalized_intent == "Send hello to Wei on WhatsApp."
    assert session.last_mobile_run is not None
    assert session.last_mobile_run.status == "completed"


async def test_run_intent_records_blocked_mobile_run_result_in_session():
    async def fake_router(intent, lang, session=None):
        return IntentRoute(
            kind="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            contact_id="wei-grandson",
        )

    def fake_factory(goal, driver, custom_tools):
        result = SimpleNamespace(success=False, reason="blocked_on_login")
        return SimpleNamespace(run=lambda: FakeHandler(result))

    conn = FakeConn()
    session = MILFSession()

    result = await run_intent(
        conn,
        intent="send hello to Wei",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
        session=session,
    )

    assert result.success is False
    assert session.last_mobile_run is not None
    assert session.last_mobile_run.status == "blocked"
    assert session.last_mobile_run.reason == "blocked_on_login"


async def test_run_intent_logs_route_handoff_and_result(caplog):
    caplog.set_level(logging.INFO, logger="milf.agent_runner")

    async def fake_router(intent, lang, session=None):
        return IntentRoute(
            kind="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            contact_id="wei-grandson",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()
    session = MILFSession()

    await run_intent(
        conn,
        intent="can see boy ah",
        lang="en",
        agent_factory=fake_factory,
        intent_router=fake_router,
        session=session,
    )

    route_record = _find_log(caplog.records, "MILF intent route selected.")
    assert route_record.session_id == session.session_id
    assert route_record.route_kind == "execute"
    assert route_record.contact_id == "wei-grandson"
    assert route_record.requires_confirmation is True

    handoff_record = _find_log(caplog.records, "MILF starting MobileRun.")
    assert handoff_record.session_id == session.session_id
    assert handoff_record.contact_id == "wei-grandson"

    result_record = _find_log(caplog.records, "MILF MobileRun finished.")
    assert result_record.session_id == session.session_id
    assert result_record.mobile_run_status == "completed"


async def test_run_intent_logs_short_circuit_route(caplog):
    caplog.set_level(logging.INFO, logger="milf.agent_runner")

    async def fake_router(intent, lang, session=None):
        return IntentRoute(kind="reply", message="Hi there.")

    conn = FakeConn()
    session = MILFSession()

    await run_intent(
        conn,
        intent="what's up",
        lang="en",
        agent_factory=lambda goal, driver, custom_tools: None,
        intent_router=fake_router,
        session=session,
    )

    route_record = _find_log(caplog.records, "MILF intent route selected.")
    assert route_record.route_kind == "reply"
    short_circuit_record = _find_log(
        caplog.records, "MILF responding without MobileRun."
    )
    assert short_circuit_record.session_id == session.session_id
    assert short_circuit_record.route_kind == "reply"


async def test_run_task_still_transcribes_audio():
    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()

    await run_task(
        conn,
        audio=b"audio",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert conn.narrations
    assert conn.completions == [("Done.", "en", None)]


def _find_log(records, message):
    for record in records:
        if record.getMessage() == message:
            return record
    raise AssertionError(f"Missing log message: {message}")


class FailingHandler:
    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            raise RuntimeError("agent exploded")

        return _r().__await__()


class CleanClientCloseHandler:
    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            raise Exception("Failed to get state") from ConnectionClosedOK(None, None)

        return _r().__await__()


class ConfirmingHandler:
    def __init__(self, custom_tools):
        self.custom_tools = custom_tools

    async def stream_events(self):
        fn = self.custom_tools["confirm_action"]["function"]
        await fn(summary="Call Wei now?", ctx=None)
        if False:
            yield None

    def __await__(self):
        async def _r():
            return SimpleNamespace(success=True, reason="ok")

        return _r().__await__()


class ClarifyingHandler:
    def __init__(self, custom_tools):
        self.custom_tools = custom_tools

    async def stream_events(self):
        fn = self.custom_tools["request_clarification"]["function"]
        await fn(
            question="Which listed Wei is your grandson?",
            ctx=None,
        )
        if False:
            yield None

    def __await__(self):
        async def _r():
            return SimpleNamespace(success=True, reason="ok")

        return _r().__await__()


async def test_run_task_sends_safe_failure_on_agent_error():
    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FailingHandler())

    conn = FakeConn()

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert result.success is False
    assert conn.failures == [
        (
            "I'm having a little trouble with that. Please try again.",
            "en",
        )
    ]


async def test_run_task_treats_clean_client_close_as_closed_session(caplog):
    caplog.set_level(logging.INFO, logger="milf.agent_runner")

    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: CleanClientCloseHandler())

    conn = FakeConn()

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert result.success is False
    assert result.reason == "client_closed"
    assert conn.failures == []
    assert _find_log(caplog.records, "Mobile client closed during agent run.")


async def test_run_task_surfaces_mobile_run_clarification_and_stops():
    async def message_router(intent, lang, session=None):
        return IntentRoute(
            kind="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            requires_confirmation=True,
        )

    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: ClarifyingHandler(custom_tools))

    conn = FakeConn()
    session = MILFSession()

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("send hello to my grandson"),
        agent_factory=fake_factory,
        intent_router=message_router,
        session=session,
    )

    assert result.success is False
    assert result.reason == "clarify"
    assert conn.narrations == [
        ("Okay, let me help you with that.", "en"),
        ("Which listed Wei is your grandson?", "en"),
    ]
    assert conn.completions == [("Which listed Wei is your grandson?", "en", None)]
    assert conn.failures == []
    assert session.pending_clarification is not None
    assert session.pending_clarification.question == "Which listed Wei is your grandson?"
    assert session.pending_clarification.original_intent == "Send hello to Wei on WhatsApp."
    assert session.last_mobile_run is not None
    assert session.last_mobile_run.status == "clarification_requested"


async def test_run_task_sends_safe_failure_when_agent_reports_failure():
    def fake_factory(goal, driver, custom_tools):
        result = SimpleNamespace(success=False, reason="agent_failed")
        return SimpleNamespace(run=lambda: FakeHandler(result))

    conn = FakeConn()

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert result.success is False
    assert conn.completions == []
    assert conn.failures == [
        (
            "I'm having a little trouble with that. Please try again.",
            "en",
        )
    ]


async def test_run_task_sends_safe_failure_when_confirmation_declined():
    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: ConfirmingHandler(custom_tools))

    conn = FakeConn(confirmation_approved=False)

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
        intent_router=wei_router,
    )

    assert result.success is False
    assert result.reason == "confirmation_declined"
    assert conn.confirmation_requests == [("Call Wei now?", "en", None)]
    assert conn.completions == []
    assert conn.failures == [
        (
            "I'm having a little trouble with that. Please try again.",
            "en",
        )
    ]
