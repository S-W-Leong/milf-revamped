from types import SimpleNamespace

from milf.agent_runner import run_task
from milf.stt import MockSTT


class FakeHandler:
    def __init__(self):
        self.result = SimpleNamespace(success=True, reason="ok")

    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            return self.result

        return _r().__await__()


class FakeConn:
    def __init__(self):
        self.narrations = []
        self.completions = []
        self.failures = []

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang, recovery_contact_id=None):
        self.failures.append((message, lang, recovery_contact_id))


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


class FailingHandler:
    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            raise RuntimeError("agent exploded")

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
