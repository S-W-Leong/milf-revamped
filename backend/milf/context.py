from __future__ import annotations

import json
from functools import cache
from pathlib import Path

from pydantic import BaseModel


CONTACTS_PATH = Path(__file__).with_name("contacts.json")

WHATSAPP_APP_CARD = """
WhatsApp app card:
- Use WhatsApp for video calls; Android package is com.whatsapp.
- Open WhatsApp, then use the Calls tab or open the chat target directly.
- If the contact is not visible, use the search icon to find the target by display name.
- Start the video call with the video-call icon.
- Prefer accessibility text/content-description when identifying the Calls tab, search icon, chat target, and video-call icon.
"""

SAFETY_CONFIRMATION = (
    "SAFETY: before placing the call or any send/payment, MUST call confirm_action "
    "with a short summary and only proceed if confirmed."
)

AGENT_OVERLAY_INTERACTION = """
MILF overlay interaction rule:
- The phone may show MILF as an expanded bottom bar while you are acting.
- The first tap outside the bar only collapses MILF into a floating bubble and does not reach the underlying app.
- If a tap appears to do nothing except collapse MILF, do not treat it as a successful app interaction.
- Re-read the UI tree or screenshot after the collapse, then perform the intended tap on the underlying app once the bubble is collapsed.
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


def escape_contact() -> Contact:
    for contact in _contacts():
        if contact.escape:
            return contact
    raise RuntimeError("contacts.json must contain one escape contact")


def build_goal(intent: str) -> str:
    contact = resolve_contact(intent)
    parts = [f"Spoken intent: {intent!r}."]

    if contact is not None:
        parts.append(f"Contact id: {contact.id}.")
        parts.append(f"Intended contact: {contact.display_name}.")
        parts.append(f"Relationship: {contact.relationship}.")
        parts.append(
            f"Preferred channel: {contact.preferred_app} {contact.preferred_channel}."
        )

    parts.append(WHATSAPP_APP_CARD.strip())
    parts.append(AGENT_OVERLAY_INTERACTION.strip())
    parts.append(SAFETY_CONFIRMATION)

    return "\n\n".join(parts)


def acknowledgment(intent: str) -> str:
    contact = resolve_contact(intent)
    if contact is not None:
        return f"Okay, let me help you reach {contact.display_name}."
    return "Okay, let me help you with that."
