from milf.context import AGENT_OVERLAY_INTERACTION, acknowledgment, build_goal


def test_build_goal_uses_memory_instead_of_injected_contacts():
    goal = build_goal(
        "I want to see my grandson",
        memory="Wei is my grandson. Use WhatsApp video.",
    )

    assert "Agent memory:" in goal
    assert "Use Agent memory to resolve relationship references" in goal
    assert "Wei is my grandson. Use WhatsApp video." in goal
    assert "Contact id:" not in goal
    assert "Intended contact:" not in goal
    assert "Relationship:" not in goal
    assert "Preferred channel:" not in goal
    assert "confirm_action" in goal
    assert "WhatsApp app card:" not in goal
    assert "com.whatsapp" not in goal
    assert "Start the video call with the video-call icon" not in goal


def test_build_goal_tells_agent_not_to_wait_loop_after_send():
    goal = build_goal("Send hello to Quick notes on WhatsApp.")

    assert "After confirm_action returns approval" in goal
    assert "tap Send exactly once" in goal
    assert "Do not emit repeated wait actions" in goal


def test_build_goal_tells_agent_clarification_must_be_first_plan_item():
    goal = build_goal("Send hello to Quick notes on WhatsApp.")

    assert "make request_clarification the first and only plan item" in goal
    assert "do not put phone actions before the clarification request" in goal
    assert "After request_clarification succeeds" in goal


def test_build_goal_omits_memory_section_when_empty():
    goal = build_goal("What's up")

    assert "Spoken intent: \"What's up\"." in goal
    assert "Agent memory:" not in goal
    assert "WhatsApp app card:" not in goal
    assert "com.whatsapp" not in goal
    assert "confirm_action" in goal


def test_build_goal_tells_agent_overlay_taps_pass_through_outside_rail():
    goal = build_goal("I want to see my grandson")

    assert AGENT_OVERLAY_INTERACTION in goal
    assert "Only the Collapse MILF button collapses the bar" in goal
    assert "Taps outside the rail go to the underlying app" in goal
    assert "first tap outside the bar only collapses MILF" not in goal

def test_acknowledgment_is_generic():
    line = acknowledgment("open the weather")
    assert line and "Wei" not in line
