from __future__ import annotations

import time
from collections.abc import Callable
from dataclasses import dataclass
from typing import Any


READ_ONLY_ACTIONS = frozenset({"get_ui_tree"})
SENSITIVE_ACTIONS = frozenset(
    {
        "tap",
        "swipe",
        "input_text",
        "press_button",
        "start_app",
        "screenshot",
    }
)


@dataclass(frozen=True)
class ApprovalRecord:
    summary: str
    lang: str
    approved_at: float


class ConfirmationPolicy:
    def __init__(
        self,
        *,
        freshness_seconds: float = 120.0,
        clock: Callable[[], float] = time.monotonic,
    ):
        if freshness_seconds <= 0:
            raise ValueError("freshness_seconds must be positive")
        self._freshness_seconds = freshness_seconds
        self._clock = clock
        self._approval: ApprovalRecord | None = None

    def record_approval(self, summary: str, lang: str) -> None:
        self._approval = ApprovalRecord(
            summary=summary,
            lang=lang,
            approved_at=self._clock(),
        )

    def require_allowed(self, action_name: str, args: dict[str, Any]) -> None:
        if action_name in READ_ONLY_ACTIONS:
            return
        if self._has_fresh_approval():
            return
        raise PermissionError(
            f"Action '{action_name}' requires fresh user confirmation"
        )

    def _has_fresh_approval(self) -> bool:
        if self._approval is None:
            return False
        return self._clock() - self._approval.approved_at <= self._freshness_seconds
