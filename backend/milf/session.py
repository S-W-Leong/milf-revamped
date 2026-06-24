from __future__ import annotations

from typing import Literal
from uuid import uuid4

from pydantic import BaseModel, Field

from milf.intent_router import IntentRoute


MobileRunStatus = Literal[
    "completed",
    "failed",
    "blocked",
    "confirmation_declined",
    "agent_error",
]


class PendingClarification(BaseModel):
    question: str
    original_intent: str


class MobileRunResult(BaseModel):
    status: MobileRunStatus
    normalized_intent: str | None = None
    contact_id: str | None = None
    reason: str | None = None


class MILFSession(BaseModel):
    session_id: str = Field(default_factory=lambda: uuid4().hex)
    recent_user_inputs: list[str] = Field(default_factory=list)
    pending_clarification: PendingClarification | None = None
    last_contact_id: str | None = None
    last_normalized_intent: str | None = None
    last_mobile_run: MobileRunResult | None = None

    def context_for_intent_router(self) -> str:
        lines: list[str] = []
        if self.recent_user_inputs:
            lines.append(f"Recent user inputs: {', '.join(self.recent_user_inputs)}")
        if self.pending_clarification is not None:
            lines.append(
                f"Pending clarification: {self.pending_clarification.question}"
            )
            lines.append(
                "Clarifying original input: "
                f"{self.pending_clarification.original_intent}"
            )
        if self.last_contact_id is not None:
            lines.append(f"Last resolved contact id: {self.last_contact_id}")
        if self.last_normalized_intent is not None:
            lines.append(f"Last executable intent: {self.last_normalized_intent}")
        if self.last_mobile_run is not None:
            lines.append(
                "Last MobileRun result: "
                f"{self.last_mobile_run.status}"
                f" ({self.last_mobile_run.reason or 'no reason'})"
            )
        return "\n".join(lines) or "No prior MILF session context."

    def record_user_route(self, user_input: str, route: IntentRoute) -> None:
        self.recent_user_inputs.append(user_input)
        self.recent_user_inputs = self.recent_user_inputs[-6:]

        if route.kind == "clarify":
            self.pending_clarification = PendingClarification(
                question=route.message
                or "What would you like me to help you do on your phone?",
                original_intent=user_input,
            )
            return

        if route.kind == "execute":
            self.pending_clarification = None
            self.last_contact_id = route.contact_id or self.last_contact_id
            self.last_normalized_intent = (
                route.normalized_intent or self.last_normalized_intent
            )

    def record_mobile_run_result(
        self,
        route: IntentRoute,
        result: object | None = None,
        *,
        status: MobileRunStatus | None = None,
        reason: str | None = None,
    ) -> None:
        if reason is None and result is not None:
            reason = getattr(result, "reason", None)

        if status is None:
            success = bool(getattr(result, "success", False))
            if success:
                status = "completed"
            elif reason is not None and "block" in reason.casefold():
                status = "blocked"
            else:
                status = "failed"

        self.last_mobile_run = MobileRunResult(
            status=status,
            normalized_intent=route.normalized_intent,
            contact_id=route.contact_id,
            reason=reason,
        )
