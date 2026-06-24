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
    parts.append(SAFETY_CONFIRMATION)

    return "\n\n".join(parts)


def acknowledgment(intent: str, contact: Contact | None = None) -> str:
    contact = contact or resolve_contact(intent)
    if contact is not None:
        return f"Okay, let me help you reach {contact.display_name}."
    return "Okay, let me help you with that."
