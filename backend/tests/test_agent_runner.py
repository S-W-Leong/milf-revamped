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

    async def send_narration(self, text, lang):
        self.narrations.append(text)


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
    assert conn.narrations and "Wei" in conn.narrations[0]
