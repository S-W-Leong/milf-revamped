from types import SimpleNamespace

from milf.narration import narration_for, narrate_events


def _named(cls_name, **attrs):
    return type(cls_name, (SimpleNamespace,), {})(**attrs)


def test_executor_action_event_narrates_description():
    ev = _named("ExecutorActionEvent", description="Opening WhatsApp", thought="x")
    assert narration_for(ev) == "Opening WhatsApp"


def test_unknown_event_is_silent():
    assert narration_for(_named("RandomEvent")) is None


def test_manager_plan_details_event_narrates_subgoal():
    ev = _named("ManagerPlanDetailsEvent", subgoal="Open Wei's chat")
    assert narration_for(ev) == "Next: Open Wei's chat"


def test_fast_agent_response_event_narrates_description_or_thought():
    with_description = _named(
        "FastAgentResponseEvent",
        description="Checking the screen",
        thought="fallback",
    )
    with_thought = _named("FastAgentResponseEvent", description="", thought="Thinking")
    assert narration_for(with_description) == "Checking the screen"
    assert narration_for(with_thought) == "Thinking"


class FakeHandler:
    def __init__(self, events, result):
        self._events = events
        self._result = result

    async def stream_events(self):
        for e in self._events:
            yield e

    def __await__(self):
        async def _r():
            return self._result

        return _r().__await__()


async def test_narrate_events_sends_lines_and_returns_result():
    sent = []

    class Conn:
        async def send_narration(self, text, lang):
            sent.append((text, lang))

    handler = FakeHandler(
        [_named("ExecutorActionEvent", description="Finding Wei", thought="")],
        SimpleNamespace(success=True, reason="done"),
    )
    result = await narrate_events(handler, Conn(), "en")
    assert sent == [("Finding Wei", "en")]
    assert result.success is True
