import logging
import sys
from types import ModuleType
from types import SimpleNamespace

from milf.agent_runner import build_agent, run_task
from milf.runtime_logging import configure_dependency_logging
from milf.settings import Settings
from milf.stt import MockSTT


class FakeHandler:
    def __init__(self):
        self.result = SimpleNamespace(success=True, reason="ok")

    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            return self.result

        return _r().__await__()


class FakeConn:
    def __init__(self):
        self.narrations = []

    async def send_narration(self, text, lang):
        self.narrations.append(text)


async def test_run_task_acks_then_builds_and_runs():
    captured = {}

    def fake_factory(goal, driver, custom_tools):
        captured["goal"] = goal
        captured["tools"] = custom_tools
        return SimpleNamespace(run=lambda: FakeHandler())

    conn = FakeConn()
    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
    )

    assert result.success is True
    assert "Wei" in captured["goal"]
    assert "confirm_action" in captured["tools"]
    assert conn.narrations and "Wei" in conn.narrations[0]


def test_dependency_logging_suppresses_sensitive_info_handlers():
    logger = logging.getLogger("mobilerun")
    original_level = logger.level
    handler = logging.StreamHandler()
    handler.setLevel(logging.INFO)
    logger.addHandler(handler)
    try:
        logger.setLevel(logging.INFO)

        configure_dependency_logging()

        assert logger.getEffectiveLevel() >= logging.WARNING
        assert handler.level >= logging.WARNING
        assert logging.getLogger("websockets").getEffectiveLevel() >= logging.WARNING
    finally:
        logger.removeHandler(handler)
        logger.setLevel(original_level)


def test_build_agent_disables_mobilerun_streaming_and_configures_logging(monkeypatch):
    captured = {}

    class FakeOpenAI:
        def __init__(self, model):
            self.model = model

    class FakeAgentConfig:
        def __init__(self, **kwargs):
            self.__dict__.update(kwargs)

    class FakeMobileConfig:
        def __init__(self, agent):
            self.agent = agent

    class FakeMobileAgent:
        def __init__(self, **kwargs):
            captured.update(kwargs)

    llama_index = ModuleType("llama_index")
    llms = ModuleType("llama_index.llms")
    openai = ModuleType("llama_index.llms.openai")
    openai.OpenAI = FakeOpenAI
    mobilerun = ModuleType("mobilerun")
    mobilerun.AgentConfig = FakeAgentConfig
    mobilerun.MobileAgent = FakeMobileAgent
    mobilerun.MobileConfig = FakeMobileConfig
    monkeypatch.setitem(sys.modules, "llama_index", llama_index)
    monkeypatch.setitem(sys.modules, "llama_index.llms", llms)
    monkeypatch.setitem(sys.modules, "llama_index.llms.openai", openai)
    monkeypatch.setitem(sys.modules, "mobilerun", mobilerun)
    logging.getLogger("mobilerun").setLevel(logging.INFO)

    build_agent("goal", driver=object(), custom_tools={}, settings=_settings())

    assert captured["config"].agent.streaming is False
    assert logging.getLogger("mobilerun").getEffectiveLevel() >= logging.WARNING


def _settings() -> Settings:
    return Settings(
        env="test",
        ws_host="127.0.0.1",
        ws_port=8765,
        ws_max_size_bytes=8_388_608,
        device_token="dev-token",
        action_timeout_seconds=30.0,
        max_audio_bytes=5_242_880,
        stt_backend="mock",
        mock_transcript="I want to see my grandson",
        openai_api_key=None,
        openai_model="gpt-4o",
        ilmu_api_url=None,
        ilmu_api_key=None,
        meralion_api_url=None,
        meralion_api_key=None,
    )
