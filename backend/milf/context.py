from __future__ import annotations

import json
from functools import cache
from pathlib import Path


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


@cache
def _contacts() -> dict[str, str]:
    with CONTACTS_PATH.open(encoding="utf-8") as handle:
        return json.load(handle)


def resolve_contact(phrase: str) -> str | None:
    phrase_lower = phrase.casefold()
    for relation, display_name in _contacts().items():
        if relation.casefold() in phrase_lower:
            return display_name
    return None


def build_goal(intent: str) -> str:
    contact = resolve_contact(intent)
    parts = [f"Spoken intent: {intent!r}."]

    if contact is not None:
        parts.append(f"Intended contact: {contact}.")

    parts.append(WHATSAPP_APP_CARD.strip())
    parts.append(SAFETY_CONFIRMATION)

    return "\n\n".join(parts)


def acknowledgment(intent: str) -> str:
    contact = resolve_contact(intent)
    if contact is not None:
        return f"Okay, let me help you reach {contact}."
    return "Okay, let me help you with that."
