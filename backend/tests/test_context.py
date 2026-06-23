from milf.context import resolve_contact, build_goal, acknowledgment, WHATSAPP_APP_CARD


def test_resolve_known_relation_en_and_ms():
    assert resolve_contact("I want to call my grandson") == "Wei"
    assert resolve_contact("nak tengok cucu") == "Wei"


def test_resolve_unknown_returns_none():
    assert resolve_contact("open the weather") is None


def test_build_goal_includes_contact_card_and_confirmation():
    goal = build_goal("I want to see my grandson")
    assert "Wei" in goal
    assert "confirm_action" in goal
    assert WHATSAPP_APP_CARD.strip()[:10] in goal


def test_acknowledgment_names_contact_when_known():
    assert "Wei" in acknowledgment("I want to see my grandson")


def test_acknowledgment_is_generic_when_unknown():
    line = acknowledgment("open the weather")
    assert line and "Wei" not in line
