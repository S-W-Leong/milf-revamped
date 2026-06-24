from __future__ import annotations

from milf.connection import AppConnection


def build_confirmation_tool(
    connection: AppConnection, lang: str, contact_id: str | None = None
) -> dict:
    async def confirm_action(summary: str, *, ctx=None, **kwargs) -> str:
        if contact_id is None:
            approved = await connection.request_confirmation(summary, lang)
        else:
            approved = await connection.request_confirmation(
                summary, lang, contact_id=contact_id
            )
        if approved:
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
