from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Any, Literal

from milf.clarification import ClarificationRequested


logger = logging.getLogger(__name__)

NarrationKind = Literal["progress", "blocker", "acknowledgement"]
NarrationPriority = Literal["low", "normal", "urgent"]


@dataclass(frozen=True)
class NarrationLine:
    text: str
    kind: NarrationKind = "progress"
    priority: NarrationPriority = "normal"


class NarrationPolicy:
    _GESTURE_RE = re.compile(
        r"^\s*(tap|tapping|click|clicking|swipe|swiping|scroll|scrolling|"
        r"press|pressing|long press|drag|dragging|type|typing|input|enter text)\b",
        re.IGNORECASE,
    )
    _INTERNAL_RE = re.compile(
        r"\b(coordinates?|a11y|accessibility tree|ui tree|screenshot|"
        r"xpath|selector|element id)\b",
        re.IGNORECASE,
    )
    _VERB_REWRITES = {
        "open": "opening",
        "opening": "opening",
        "find": "finding",
        "finding": "finding",
        "check": "checking",
        "checking": "checking",
        "look": "looking",
        "looking": "looking",
        "search": "searching",
        "searching": "searching",
        "send": "sending",
        "sending": "sending",
        "call": "calling",
        "calling": "calling",
        "select": "selecting",
        "selecting": "selecting",
        "wait": "waiting",
        "waiting": "waiting",
    }

    def __init__(self) -> None:
        self._last_text: str | None = None

    def render(self, event: object) -> NarrationLine | None:
        event_type = event.__class__.__name__

        if event_type == "ManagerPlanDetailsEvent":
            return self._suppress(event_type, "manager_plan")

        if event_type == "ExecutorActionEvent":
            return self._progress_line(getattr(event, "description", None), event_type)

        if event_type == "FastAgentResponseEvent":
            return self._progress_line(getattr(event, "description", None), event_type)

        return self._suppress(event_type, "unknown_event")

    def _progress_line(
        self,
        description: object,
        event_type: str,
    ) -> NarrationLine | None:
        if not isinstance(description, str):
            return self._suppress(event_type, "missing_description")

        description = _normalize_space(description)
        if not description:
            return self._suppress(event_type, "empty_description")
        if self._is_internal_or_gesture(description):
            return self._suppress(event_type, "internal_or_gesture")

        text = _friendly_progress_text(description)
        if text is None:
            return self._suppress(event_type, "not_user_worthy")
        if text == self._last_text:
            return self._suppress(event_type, "duplicate")

        self._last_text = text
        return NarrationLine(text=text, kind="progress")

    def _is_internal_or_gesture(self, description: str) -> bool:
        return bool(
            self._GESTURE_RE.search(description) or self._INTERNAL_RE.search(description)
        )

    def _suppress(self, event_type: str, reason: str) -> None:
        logger.debug(
            "MILF narration suppressed.",
            extra={"event_type": event_type, "reason": reason},
        )
        return None


def narration_for(event: object) -> str | None:
    line = NarrationPolicy().render(event)
    if line is None:
        return None
    return line.text


async def narrate_events(handler: Any, connection: Any, lang: str) -> Any:
    policy = NarrationPolicy()
    async for event in handler.stream_events():
        clarification_question = _successful_clarification_question(event)
        if clarification_question is not None:
            await _cancel_handler(handler)
            raise ClarificationRequested(clarification_question)

        line = policy.render(event)
        if line is not None:
            await connection.send_narration(line.text, lang)

    return await handler


def _successful_clarification_question(event: object) -> str | None:
    if event.__class__.__name__ != "ToolExecutionEvent":
        return None
    if getattr(event, "tool_name", None) != "request_clarification":
        return None
    if getattr(event, "success", False) is not True:
        return None

    tool_args = getattr(event, "tool_args", None)
    if isinstance(tool_args, dict):
        question = tool_args.get("question")
        if isinstance(question, str) and question.strip():
            return question
    return "Which option should I use?"


async def _cancel_handler(handler: Any) -> None:
    cancel_run = getattr(handler, "cancel_run", None)
    if cancel_run is None:
        return
    try:
        await cancel_run(timeout=0.1)
    except TypeError:
        await cancel_run()
    except Exception:
        logger.debug("MILF handler cancellation failed.", exc_info=True)


def _friendly_progress_text(description: str) -> str | None:
    match = re.match(r"^(?P<verb>[A-Za-z]+)\b(?P<rest>.*)$", description)
    if match is None:
        return None

    verb = match.group("verb").casefold()
    gerund = NarrationPolicy._VERB_REWRITES.get(verb)
    if gerund is None:
        return None

    rest = match.group("rest").strip()
    phrase = f"I'm {gerund}"
    if rest:
        phrase = f"{phrase} {rest}"
    return _ensure_period(phrase)


def _normalize_space(text: str) -> str:
    return " ".join(text.strip().split())


def _ensure_period(text: str) -> str:
    return text if text.endswith((".", "?", "!")) else f"{text}."
