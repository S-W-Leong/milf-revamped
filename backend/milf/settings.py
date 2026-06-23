from __future__ import annotations

import os
from dataclasses import dataclass


class SettingsError(ValueError):
    pass


@dataclass(frozen=True)
class Settings:
    env: str
    ws_host: str
    ws_port: int
    ws_max_size_bytes: int
    device_token: str | None
    action_timeout_seconds: float
    max_audio_bytes: int
    stt_backend: str
    mock_transcript: str
    openai_api_key: str | None
    openai_model: str
    ilmu_api_url: str | None
    ilmu_api_key: str | None
    meralion_api_url: str | None
    meralion_api_key: str | None

    @classmethod
    def from_env(cls) -> "Settings":
        env = os.environ.get("MILF_ENV", "development").lower()
        max_audio_bytes = _int_env("MILF_MAX_AUDIO_BYTES", 5_242_880)
        ws_max_size_bytes = _int_env("MILF_WS_MAX_SIZE_BYTES", 8_388_608)
        minimum_ws_max_size_bytes = _minimum_ws_max_size_bytes(max_audio_bytes)
        if ws_max_size_bytes < minimum_ws_max_size_bytes:
            raise SettingsError(
                "MILF_WS_MAX_SIZE_BYTES must allow base64 audio overhead"
            )

        settings = cls(
            env=env,
            ws_host=os.environ.get("MILF_WS_HOST", "127.0.0.1"),
            ws_port=_int_env("MILF_WS_PORT", 8765),
            ws_max_size_bytes=ws_max_size_bytes,
            device_token=_optional("MILF_DEVICE_TOKEN"),
            action_timeout_seconds=_float_env("MILF_ACTION_TIMEOUT_SECONDS", 30.0),
            max_audio_bytes=max_audio_bytes,
            stt_backend=os.environ.get("MILF_STT_BACKEND", "mock").lower(),
            mock_transcript=os.environ.get("MILF_MOCK_TRANSCRIPT", "I want to see my grandson"),
            openai_api_key=_optional("OPENAI_API_KEY"),
            openai_model=os.environ.get("OPENAI_MODEL", "gpt-4o"),
            ilmu_api_url=_optional("ILMU_API_URL"),
            ilmu_api_key=_optional("ILMU_API_KEY"),
            meralion_api_url=_optional("MERALION_API_URL"),
            meralion_api_key=_optional("MERALION_API_KEY"),
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.action_timeout_seconds <= 0:
            raise SettingsError("MILF_ACTION_TIMEOUT_SECONDS must be positive")
        if self.max_audio_bytes <= 0:
            raise SettingsError("MILF_MAX_AUDIO_BYTES must be positive")
        if self.stt_backend not in {"mock", "router"}:
            raise SettingsError(f"Unknown MILF_STT_BACKEND: {self.stt_backend}")
        if self.env == "production":
            if not self.device_token:
                raise SettingsError("MILF_DEVICE_TOKEN is required in production")
            if not self.openai_api_key:
                raise SettingsError("OPENAI_API_KEY is required in production")
            if self.stt_backend != "router":
                raise SettingsError("MILF_STT_BACKEND must be router in production")
        if self.stt_backend == "router":
            missing = [
                name
                for name, value in {
                    "ILMU_API_URL": self.ilmu_api_url,
                    "ILMU_API_KEY": self.ilmu_api_key,
                    "MERALION_API_URL": self.meralion_api_url,
                    "MERALION_API_KEY": self.meralion_api_key,
                }.items()
                if not value
            ]
            if missing:
                raise SettingsError(f"Missing required router setting: {missing[0]}")


def _minimum_ws_max_size_bytes(max_audio_bytes: int) -> int:
    return ((max_audio_bytes * 4 + 2) // 3) + 1024


def _optional(name: str) -> str | None:
    value = os.environ.get(name)
    return value if value else None


def _int_env(name: str, default: int) -> int:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return int(value)
    except ValueError as exc:
        raise SettingsError(f"{name} must be an integer") from exc


def _float_env(name: str, default: float) -> float:
    value = os.environ.get(name)
    if value is None:
        return default
    try:
        return float(value)
    except ValueError as exc:
        raise SettingsError(f"{name} must be a number") from exc
