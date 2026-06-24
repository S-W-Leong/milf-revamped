import json
from pathlib import Path

from mobilerun.app_cards.providers import LocalAppCardProvider

from milf import agent_runner


PROJECT_ROOT = Path(__file__).resolve().parents[2]
APP_CARDS_DIR = PROJECT_ROOT / "config" / "app_cards"


def test_whatsapp_app_card_is_registered_for_mobilerun():
    mapping_path = APP_CARDS_DIR / "app_cards.json"
    mapping = json.loads(mapping_path.read_text(encoding="utf-8"))

    assert mapping["com.whatsapp"] == "social/whatsapp.md"


def test_whatsapp_app_card_is_general_purpose_not_demo_overfit():
    card = (APP_CARDS_DIR / "social" / "whatsapp.md").read_text(encoding="utf-8")
    normalized = card.casefold()

    assert "# WhatsApp App Guide" in card
    assert "## Search and Contacts" in card
    assert "## Messaging" in card
    assert "## Voice and Video Calls" in card
    assert "## Safety" in card
    assert "grandson" not in normalized
    assert "wei" not in normalized


async def test_local_app_card_provider_loads_whatsapp_card():
    provider = LocalAppCardProvider(app_cards_dir=str(APP_CARDS_DIR))

    card = await provider.load_app_card("com.whatsapp", "Send a WhatsApp message")

    assert "WhatsApp App Guide" in card
    assert "Voice and Video Calls" in card


def test_mobile_config_points_at_repo_app_cards_dir():
    config = agent_runner.build_mobile_config()

    assert Path(config.agent.app_cards.app_cards_dir) == APP_CARDS_DIR
    assert config.agent.app_cards.enabled is True
