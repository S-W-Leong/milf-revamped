import pytest

from milf.settings import Settings, SettingsError


def test_defaults_are_local_and_bounded(monkeypatch):
    monkeypatch.delenv("MILF_WS_HOST", raising=False)
    monkeypatch.delenv("MILF_WS_PORT", raising=False)
    monkeypatch.delenv("MILF_ACTION_TIMEOUT_SECONDS", raising=False)
    settings = Settings.from_env()
    assert settings.ws_host == "127.0.0.1"
    assert settings.ws_port == 8765
    assert settings.action_timeout_seconds == 30.0
    assert settings.ws_max_size_bytes >= settings.max_audio_bytes


def test_router_mode_requires_provider_env(monkeypatch):
    monkeypatch.setenv("MILF_STT_BACKEND", "router")
    for name in ("ILMU_API_URL", "ILMU_API_KEY", "MERALION_API_URL", "MERALION_API_KEY"):
        monkeypatch.delenv(name, raising=False)
    with pytest.raises(SettingsError, match="ILMU_API_URL"):
        Settings.from_env()


def test_production_requires_device_token(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "production")
    monkeypatch.delenv("MILF_DEVICE_TOKEN", raising=False)
    with pytest.raises(SettingsError, match="MILF_DEVICE_TOKEN"):
        Settings.from_env()
