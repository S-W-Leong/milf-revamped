from __future__ import annotations

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

AGENT_MEMORY_RULE = (
    "Use Agent memory to resolve relationship references, nicknames, preferred "
    "apps, and other user-specific details before deciding whether the request "
    "is missing information."
)


def format_agent_memory(memory: str) -> str:
    memory = memory.strip()
    if not memory:
        return ""
    return f"Agent memory:\n{AGENT_MEMORY_RULE}\n{memory}"


def build_goal(intent: str, memory: str = "") -> str:
    parts = [f"Spoken intent: {intent!r}."]

    memory_section = format_agent_memory(memory)
    if memory_section:
        parts.append(memory_section)

    parts.append(AGENT_OVERLAY_INTERACTION.strip())
    parts.append(CLARIFICATION_RULE)
    parts.append(SAFETY_CONFIRMATION)
    parts.append(POST_SEND_COMPLETION_RULE)

    return "\n\n".join(parts)


def acknowledgment(intent: str) -> str:
    return "Okay, let me help you with that."
