from __future__ import annotations

from milf.connection import AppConnection
from milf.policy import ConfirmationPolicy


def build_confirmation_tool(
    connection: AppConnection,
    lang: str,
    policy: ConfirmationPolicy,
) -> dict:
    async def confirm_action(summary: str, *, ctx=None, **kwargs) -> str:
        approved = await connection.request_confirmation(summary, lang)
        if approved:
            policy.record_approval(summary, lang)
            return "User confirmed. Proceed with the action."
        return "User declined. Do not perform the action; stop and end the task."

    return {
        "confirm_action": {
            "parameters": {
                "summary": {"type": "string", "required": True},
            },
            "description": (
                "MANDATORY before any irreversible action (placing a call, sending a "
                "message, making a payment). Pass a short plain-language summary of what "
                "is about to happen. Only proceed if this returns confirmation."
            ),
            "function": confirm_action,
        }
    }
