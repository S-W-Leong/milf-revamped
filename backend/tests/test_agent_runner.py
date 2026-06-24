from types import SimpleNamespace

from milf.agent_runner import run_intent, run_task
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
    )

    assert conn.narrations
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


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
    )

    assert conn.narrations
    assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]


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
    )

    assert result.success is False
    assert conn.failures == [
        (
            "I'm having a little trouble doing that. Want me to call your daughter to help?",
            "en",
            "buyer-daughter",
        )
    ]


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
    )

    assert result.success is False
    assert conn.completions == []
    assert conn.failures == [
        (
            "I'm having a little trouble doing that. Want me to call your daughter to help?",
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
    )

    assert result.success is False
    assert result.reason == "confirmation_declined"
    assert conn.confirmation_requests == [("Call Wei now?", "en", "wei-grandson")]
    assert conn.completions == []
    assert conn.failures == [
        (
            "I'm having a little trouble doing that. Want me to call your daughter to help?",
            "en",
            "buyer-daughter",
        )
    ]
