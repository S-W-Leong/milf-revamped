from milf.context import (
    AGENT_OVERLAY_INTERACTION,
    WHATSAPP_APP_CARD,
    acknowledgment,
    build_goal,
    escape_contact,
    resolve_contact,
)


def test_resolve_known_relation_en():
    contact = resolve_contact("I want to see my grandson")

    assert contact is not None
    assert contact.id == "wei-grandson"
    assert contact.display_name == "Wei"
    assert contact.relationship == "grandson"
    assert contact.preferred_app == "WhatsApp"
    assert contact.preferred_channel == "video"


def test_resolve_known_relation_ms():
    contact = resolve_contact("nak tengok cucu")

    assert contact is not None
    assert contact.id == "wei-grandson"


def test_resolve_unknown_returns_none():
    assert resolve_contact("open the weather") is None


def test_build_goal_includes_contact_card_and_confirmation():
    goal = build_goal("I want to see my grandson")
    assert "Contact id: wei-grandson." in goal
    assert "Intended contact: Wei." in goal
    assert "Relationship: grandson." in goal
    assert "Preferred channel: WhatsApp video." in goal
    assert "confirm_action" in goal
    assert WHATSAPP_APP_CARD.strip()[:10] in goal


def test_whatsapp_card_uses_direct_package_launch():
    assert "start_app" in WHATSAPP_APP_CARD
    assert "com.whatsapp" in WHATSAPP_APP_CARD
    assert "Do not use open_app" in WHATSAPP_APP_CARD


def test_build_goal_tells_agent_overlay_taps_pass_through_outside_rail():
    goal = build_goal("I want to see my grandson")

    assert AGENT_OVERLAY_INTERACTION in goal
    assert "Only the Collapse MILF button collapses the bar" in goal
    assert "Taps outside the rail go to the underlying app" in goal
    assert "first tap outside the bar only collapses MILF" not in goal


def test_escape_contact_returns_configured_contact():
    contact = escape_contact()

    assert contact.id == "buyer-daughter"
    assert contact.display_name == "Daughter"


def test_acknowledgment_names_contact_when_known():
    assert "Wei" in acknowledgment("I want to see my grandson")


def test_acknowledgment_is_generic_when_unknown():
    line = acknowledgment("open the weather")
    assert line and "Wei" not in line
