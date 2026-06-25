from __future__ import annotations

import json
from functools import cache
from pathlib import Path

from pydantic import BaseModel


CONTACTS_PATH = Path(__file__).with_name("contacts.json")

SAFETY_CONFIRMATION = (
    "SAFETY: before placing the call or any send/payment, MUST call confirm_action "
    "with a short summary and only proceed if confirmed."
)

CLARIFICATION_RULE = (
    "CLARIFICATION: if the visible app state has multiple possible matches, missing "
    "details, or any ambiguity that would require guessing, MUST call "
    "request_clarification with one short question and stop. Do not wait in a loop "
    "and do not choose between ambiguous contacts. If clarification is needed, "
    "make request_clarification the first and only plan item; do not put phone "
    "actions before the clarification request."
)

POST_SEND_COMPLETION_RULE = (
    "POST-SEND COMPLETION: After confirm_action returns approval, tap Send exactly "
    "once for the approved message/payment/call action. Then verify with at most one "
    "screen check if needed and finish the task. Do not emit repeated wait actions "
    "after the irreversible action has been triggered."
)

AGENT_OVERLAY_INTERACTION = """
MILF overlay interaction rule:
- The phone may show MILF as an expanded bottom rail while you are acting.
- Only the Collapse MILF button collapses the bar into a floating bubble.
- Taps outside the rail go to the underlying app and should be treated as normal app interactions.
- Do not tap Collapse MILF unless you intentionally need the rail out of the way.
"""


class Contact(BaseModel):
    id: str
    display_name: str
    relationship: str
    aliases: list[str]
    preferred_app: str
    preferred_channel: str
    photo_asset: str
    escape: bool = False
    phone: str | None = None


@cache
def _contacts() -> list[Contact]:
    with CONTACTS_PATH.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    return [Contact.model_validate(item) for item in payload["contacts"]]


def resolve_contact(phrase: str) -> Contact | None:
    phrase_lower = phrase.casefold()
    for contact in _contacts():
        for alias in contact.aliases:
            if alias.casefold() in phrase_lower:
                return contact
    return None


def contact_by_id(contact_id: str | None) -> Contact | None:
    if contact_id is None:
        return None
    for contact in _contacts():
        if contact.id == contact_id:
            return contact
    return None


def escape_contact() -> Contact:
    for contact in _contacts():
        if contact.escape:
            return contact
    raise RuntimeError("contacts.json must contain one escape contact")


def build_goal(intent: str, contact: Contact | None = None) -> str:
    contact = contact or resolve_contact(intent)
    parts = [f"Spoken intent: {intent!r}."]

    if contact is not None:
        parts.append(f"Contact id: {contact.id}.")
        parts.append(f"Intended contact: {contact.display_name}.")
        parts.append(f"Relationship: {contact.relationship}.")
        parts.append(
            f"Preferred channel: {contact.preferred_app} {contact.preferred_channel}."
        )

    parts.append(AGENT_OVERLAY_INTERACTION.strip())
    parts.append(CLARIFICATION_RULE)
    parts.append(SAFETY_CONFIRMATION)
    parts.append(POST_SEND_COMPLETION_RULE)

    return "\n\n".join(parts)


def acknowledgment(intent: str, contact: Contact | None = None) -> str:
    contact = contact or resolve_contact(intent)
    if contact is not None:
        return f"Okay, let me help you reach {contact.display_name}."
    return "Okay, let me help you with that."
