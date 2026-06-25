from types import SimpleNamespace

import pytest

from milf.clarification import ClarificationRequested
from milf.narration import NarrationLine, NarrationPolicy, narration_for, narrate_events


def _named(cls_name, **attrs):
    return type(cls_name, (SimpleNamespace,), {})(**attrs)


def test_executor_action_event_narrates_friendly_progress():
    ev = _named("ExecutorActionEvent", description="Opening WhatsApp", thought="x")
    assert narration_for(ev) == "I'm opening WhatsApp."


def test_unknown_event_is_silent():
    assert narration_for(_named("RandomEvent")) is None


def test_manager_plan_details_event_is_silent():
    ev = _named("ManagerPlanDetailsEvent", subgoal="Open Wei's chat")
    assert narration_for(ev) is None


def test_fast_agent_response_event_narrates_description_not_thought():
    with_description = _named(
        "FastAgentResponseEvent",
        description="Checking the screen",
        thought="fallback",
    )
    with_thought = _named("FastAgentResponseEvent", description="", thought="Thinking")
    assert narration_for(with_description) == "I'm checking the screen."
    assert narration_for(with_thought) is None


def test_raw_ui_gesture_is_silent():
    ev = _named("ExecutorActionEvent", description="Tap at coordinates 120, 440")
    assert narration_for(ev) is None


def test_policy_returns_typed_narration_line():
    ev = _named("ExecutorActionEvent", description="Finding Wei")
    line = NarrationPolicy().render(ev)
    assert line == NarrationLine(text="I'm finding Wei.", kind="progress")


def test_policy_deduplicates_repeated_lines():
    policy = NarrationPolicy()
    first = _named("ExecutorActionEvent", description="Opening WhatsApp")
    second = _named("ExecutorActionEvent", description="Open WhatsApp")

    assert policy.render(first) == NarrationLine(
        text="I'm opening WhatsApp.", kind="progress"
    )
    assert policy.render(second) is None


class FakeHandler:
    def __init__(self, events, result):
        self._events = events
        self._result = result
        self.cancelled = False

    async def stream_events(self):
        for e in self._events:
            yield e

    async def cancel_run(self):
        self.cancelled = True

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
    assert sent == [("I'm finding Wei.", "en")]
    assert result.success is True


async def test_narrate_events_suppresses_noise_and_duplicates():
    sent = []

    class Conn:
        async def send_narration(self, text, lang):
            sent.append((text, lang))

    handler = FakeHandler(
        [
            _named("ManagerPlanDetailsEvent", subgoal="Open Wei's chat"),
            _named("ExecutorActionEvent", description="Tap at coordinates 120, 440"),
            _named("ExecutorActionEvent", description="Opening WhatsApp"),
            _named("ExecutorActionEvent", description="Open WhatsApp"),
        ],
        SimpleNamespace(success=True, reason="done"),
    )
    result = await narrate_events(handler, Conn(), "en")

    assert sent == [("I'm opening WhatsApp.", "en")]
    assert result.success is True


async def test_narrate_events_stops_after_successful_clarification_tool():
    class Conn:
        async def send_narration(self, text, lang):
            raise AssertionError("tool event should not produce progress narration")

    handler = FakeHandler(
        [
            _named(
                "ToolExecutionEvent",
                tool_name="request_clarification",
                success=True,
                tool_args={"question": "Which contact?"},
                summary="Clarification requested.",
            ),
            _named("ExecutorActionEvent", description="Waiting for user"),
        ],
        SimpleNamespace(success=True, reason="done"),
    )

    with pytest.raises(ClarificationRequested) as error:
        await narrate_events(handler, Conn(), "en")

    assert error.value.question == "Which contact?"
    assert handler.cancelled is True
