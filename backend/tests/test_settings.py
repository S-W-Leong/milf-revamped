import pytest

from milf.settings import Settings, SettingsError


SETTINGS_ENV_VARS = (
    "MILF_ENV",
    "MILF_WS_HOST",
    "MILF_WS_PORT",
    "MILF_WS_MAX_SIZE_BYTES",
    "MILF_DEVICE_TOKEN",
    "MILF_ACTION_TIMEOUT_SECONDS",
    "MILF_MAX_AUDIO_BYTES",
    "MILF_STT_BACKEND",
    "MILF_MOCK_TRANSCRIPT",
    "OPENAI_API_KEY",
    "OPENAI_MODEL",
    "ILMU_API_URL",
    "ILMU_API_KEY",
    "MERALION_API_URL",
    "MERALION_API_KEY",
)


@pytest.fixture(autouse=True)
def clear_settings_env(monkeypatch):
    for name in SETTINGS_ENV_VARS:
        monkeypatch.delenv(name, raising=False)


def test_defaults_are_local_and_bounded():
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


def test_production_env_is_stripped_and_case_normalized(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "Production ")
    with pytest.raises(SettingsError, match="MILF_DEVICE_TOKEN"):
        Settings.from_env()


def test_invalid_env_raises_settings_error(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "staging")
    with pytest.raises(SettingsError, match="MILF_ENV"):
        Settings.from_env()


def test_reads_openai_api_key(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    settings = Settings.from_env()
    assert settings.openai_api_key == "sk-test"


def test_production_requires_openai_api_key(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "production")
    monkeypatch.setenv("MILF_DEVICE_TOKEN", "dev-token")
    monkeypatch.setenv("MILF_STT_BACKEND", "router")
    monkeypatch.setenv("ILMU_API_URL", "https://ilmu.test/asr")
    monkeypatch.setenv("ILMU_API_KEY", "ilmu-key")
    monkeypatch.setenv("MERALION_API_URL", "https://meralion.test/asr")
    monkeypatch.setenv("MERALION_API_KEY", "meralion-key")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    with pytest.raises(SettingsError, match="OPENAI_API_KEY"):
        Settings.from_env()


def test_production_requires_router_stt(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "production")
    monkeypatch.setenv("MILF_DEVICE_TOKEN", "dev-token")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("MILF_STT_BACKEND", "mock")
    with pytest.raises(SettingsError, match="MILF_STT_BACKEND"):
        Settings.from_env()


def test_production_router_requires_provider_env(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "production")
    monkeypatch.setenv("MILF_DEVICE_TOKEN", "dev-token")
    monkeypatch.setenv("OPENAI_API_KEY", "sk-test")
    monkeypatch.setenv("MILF_STT_BACKEND", "router")
    for name in ("ILMU_API_URL", "ILMU_API_KEY", "MERALION_API_URL", "MERALION_API_KEY"):
        monkeypatch.delenv(name, raising=False)
    with pytest.raises(SettingsError, match="ILMU_API_URL"):
        Settings.from_env()


def test_ws_max_size_accounts_for_base64_audio_overhead(monkeypatch):
    monkeypatch.setenv("MILF_MAX_AUDIO_BYTES", "3000")
    monkeypatch.setenv("MILF_WS_MAX_SIZE_BYTES", "3000")
    with pytest.raises(SettingsError, match="MILF_WS_MAX_SIZE_BYTES"):
        Settings.from_env()
