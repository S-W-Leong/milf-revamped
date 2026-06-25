from __future__ import annotations

from dataclasses import dataclass

from mobilerun.agent.action_result import ActionResult

from milf.connection import AppConnection


class ClarificationRequested(Exception):
    """Raised when MobileRun needs a user answer before acting safely."""

    def __init__(self, question: str):
        super().__init__(question)
        self.question = question


@dataclass
class ClarificationState:
    question: str | None = None


def build_clarification_tool(
    connection: AppConnection,
    lang: str,
    state: ClarificationState | None = None,
) -> dict:
    state = state or ClarificationState()

    async def request_clarification(
        question: str, *, ctx=None, **kwargs
    ) -> ActionResult:
        state.question = question
        await connection.send_narration(question, lang)
        return ActionResult(
            success=True,
            summary=(
                "Clarification requested. Stop now and mark the request as "
                "not complete until the user answers."
            ),
        )

    return {
        "request_clarification": {
            "parameters": {
                "question": {"type": "string", "required": True},
            },
            "description": (
                "Use when the app state is ambiguous or a safe next step needs a user "
                "answer, such as multiple matching contacts. Ask one short question. "
                "This stops the current automation so the user can answer."
            ),
            "function": request_clarification,
        }
    }
