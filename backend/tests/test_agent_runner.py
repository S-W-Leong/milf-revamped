import logging
from types import SimpleNamespace

from milf.agent_runner import run_intent, run_task
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

    async def send_task_failure(self, message, lang, recovery_contact_id=None):
        self.failures.append((message, lang, recovery_contact_id))

    async def request_confirmation(self, summary, lang, contact_id=None):
        self.confirmation_requests.append((summary, lang, contact_id))
        return self.confirmation_approved


async def wei_router(intent, lang):
    return IntentRoute(
        kind="execute",
        normalized_intent="I want to see my grandson",
        contact_id="wei-grandson",
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
    assert "Wei" in captured["goal"]
    assert "confirm_action" in captured["tools"]
    assert conn.narrations and "Wei" in conn.narrations[0][0]
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]
    assert conn.failures == []


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
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


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


async def test_run_intent_uses_normalized_intent_from_router():
    captured = {}

    async def fake_router(intent, lang):
        return IntentRoute(
            kind="execute",
            normalized_intent="I want to see my grandson",
            contact_id="wei-grandson",
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
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


async def test_run_intent_uses_contact_id_from_router():
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

    assert "Contact id: wei-grandson." in captured["goal"]
    assert captured["tools"]["confirm_action"]["function"]
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


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

    assert conn.narrations == [("Okay, let me help you reach Wei.", "en")]


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
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


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
            "buyer-daughter",
        )
    ]


async def test_run_task_surfaces_mobile_run_clarification_and_stops():
    async def message_router(intent, lang, session=None):
        return IntentRoute(
            kind="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            contact_id="wei-grandson",
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
        ("Okay, let me help you reach Wei.", "en"),
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
            "buyer-daughter",
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
    assert conn.confirmation_requests == [("Call Wei now?", "en", "wei-grandson")]
    assert conn.completions == []
    assert conn.failures == [
        (
            "I'm having a little trouble with that. Please try again.",
            "en",
            "buyer-daughter",
        )
    ]
