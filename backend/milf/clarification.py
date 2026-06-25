from __future__ import annotations

from milf.connection import AppConnection


class ClarificationRequested(Exception):
    """Raised when MobileRun needs a user answer before acting safely."""

    def __init__(self, question: str):
        super().__init__(question)
        self.question = question


def build_clarification_tool(connection: AppConnection, lang: str) -> dict:
    async def request_clarification(question: str, *, ctx=None, **kwargs) -> str:
        await connection.send_narration(question, lang)
        await connection.send_task_complete(question, lang)
        raise ClarificationRequested(question)

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
