import logging

from milf.intent_router import (
    INTENT_AGENT_PROMPT,
    IntentAgentDecision,
    IntentRoute,
    build_default_intent_agent,
    route_intent_with_agent,
)


class FakeIntentAgent:
    def __init__(self, decision):
        self.decision = decision
        self.calls = []

    async def classify(self, intent, lang, session_context=""):
        self.calls.append((intent, lang, session_context))
        return self.decision


async def test_intent_model_handles_greeting():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="chat",
            reply="Hi, what would you like me to help you do on your phone?",
            confidence=0.93,
        )
    )

    route = await route_intent_with_agent("helo", "en", agent)

    assert route == IntentRoute(
        kind="reply",
        message="Hi, what would you like me to help you do on your phone?",
    )
    assert agent.calls == [("helo", "en", "No prior MILF session context.")]


async def test_intent_model_handles_incomplete_message_command():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="clarify",
            reply="Who should I send that to?",
            confidence=0.89,
        )
    )

    route = await route_intent_with_agent("send hello", "en", agent)

    assert route == IntentRoute(kind="clarify", message="Who should I send that to?")
    assert agent.calls == [("send hello", "en", "No prior MILF session context.")]


async def test_intent_model_handles_messy_middle_intent():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="clarify",
            reply="Who do you mean by her?",
            confidence=0.86,
        )
    )

    route = await route_intent_with_agent("send her the thing", "en", agent)

    assert route == IntentRoute(kind="clarify", message="Who do you mean by her?")
    assert agent.calls == [
        ("send her the thing", "en", "No prior MILF session context.")
    ]


async def test_intent_model_can_normalize_executable_intent():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            reply="Okay, I will help you call Wei.",
            normalized_intent="Start a WhatsApp video call with Wei.",
            contact_id="wei-grandson",
            requires_confirmation=True,
            confidence=0.92,
        )
    )

    route = await route_intent_with_agent("can see boy ah", "en", agent)

    assert route == IntentRoute(
        kind="execute",
        message=None,
        normalized_intent="Start a WhatsApp video call with Wei.",
        contact_id="wei-grandson",
        requires_confirmation=True,
    )


async def test_intent_model_maps_perceive_route():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="perceive",
            normalized_intent="Read aloud what is visible on the current screen.",
            confidence=0.88,
        )
    )

    route = await route_intent_with_agent("what's on my screen?", "en", agent)

    assert route == IntentRoute(
        kind="perceive",
        message=None,
        normalized_intent="Read aloud what is visible on the current screen.",
        contact_id=None,
        requires_confirmation=False,
        fast_path=False,
    )


async def test_intent_model_maps_fast_path_execute_route():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Open WhatsApp.",
            requires_confirmation=False,
            fast_path=True,
            confidence=0.91,
        )
    )

    route = await route_intent_with_agent("open WhatsApp", "en", agent)

    assert route == IntentRoute(
        kind="execute",
        message=None,
        normalized_intent="Open WhatsApp.",
        contact_id=None,
        requires_confirmation=False,
        fast_path=True,
    )


async def test_known_contact_request_still_goes_to_intent_model():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            contact_id="wei-grandson",
            requires_confirmation=True,
            confidence=0.95,
        )
    )

    route = await route_intent_with_agent("I want to see my grandson", "en", agent)

    assert route.kind == "execute"
    assert route.contact_id == "wei-grandson"
    assert agent.calls == [
        ("I want to see my grandson", "en", "No prior MILF session context.")
    ]


async def test_intent_model_receives_milf_session_context():
    from milf.session import MILFSession

    session = MILFSession()
    session.record_user_route(
        "send hello",
        IntentRoute(kind="clarify", message="Who should I send that to?"),
    )
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Send hello to Wei on WhatsApp.",
            contact_id="wei-grandson",
            requires_confirmation=True,
            confidence=0.9,
        )
    )

    route = await route_intent_with_agent("Wei", "en", agent, session=session)

    assert route.kind == "execute"
    assert route.contact_id == "wei-grandson"
    assert agent.calls == [
        (
            "Wei",
            "en",
            "Recent user inputs: send hello\n"
            "Pending clarification: Who should I send that to?\n"
            "Clarifying original input: send hello",
        )
    ]


async def test_intent_model_receives_user_memory_without_known_contact_injection():
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            requires_confirmation=True,
            confidence=0.9,
        )
    )

    route = await route_intent_with_agent(
        "call my grandson",
        "en",
        agent,
        memory="Wei is my grandson.",
    )

    assert route.kind == "execute"
    assert agent.calls == [
        (
            "call my grandson",
            "en",
            "No prior MILF session context.\n"
            "Agent memory:\n"
            "Use Agent memory to resolve relationship references, nicknames, "
            "preferred apps, and other user-specific details before deciding "
            "whether the request is missing information.\n"
            "Wei is my grandson.",
        )
    ]
    assert "buyer-daughter" not in INTENT_AGENT_PROMPT
    assert "wei-grandson" not in INTENT_AGENT_PROMPT


async def test_intent_router_logs_model_decision(caplog):
    caplog.set_level(logging.INFO, logger="milf.intent_router")
    agent = FakeIntentAgent(
        IntentAgentDecision(
            route="execute",
            normalized_intent="Start a WhatsApp video call with Wei.",
            contact_id="wei-grandson",
            requires_confirmation=True,
            confidence=0.91,
        )
    )

    route = await route_intent_with_agent("see boy", "en", agent)

    assert route.kind == "execute"
    record = _find_log(caplog.records, "MILF intent model decision.")
    assert record.intent_route == "execute"
    assert record.contact_id == "wei-grandson"
    assert record.requires_confirmation is True
    assert record.confidence == 0.91


def test_default_intent_agent_disabled_without_api_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)

    import pytest

    with pytest.raises(RuntimeError, match="OPENAI_API_KEY"):
        build_default_intent_agent()


def test_default_intent_agent_uses_openai_when_api_key_present(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("MILF_INTENT_MODEL", "test-model")

    agent = build_default_intent_agent()

    assert agent is not None
    assert agent.model == "test-model"


def test_intent_prompt_defines_perceive_and_fast_path_rules():
    assert "- perceive:" in INTENT_AGENT_PROMPT
    assert "screen" in INTENT_AGENT_PROMPT.casefold()
    assert "fast_path" in INTENT_AGENT_PROMPT
    assert "home/back navigation" in INTENT_AGENT_PROMPT
    assert "compose" in INTENT_AGENT_PROMPT.casefold()


def test_default_intent_agent_uses_fast_default_model(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.delenv("MILF_INTENT_MODEL", raising=False)
    monkeypatch.delenv("OPENAI_MODEL", raising=False)

    agent = build_default_intent_agent()

    assert agent.model == "gpt-4o-mini"


def test_default_intent_agent_uses_smarter_default_model(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.delenv("MILF_INTENT_MODEL", raising=False)
    monkeypatch.delenv("OPENAI_MODEL", raising=False)

    agent = build_default_intent_agent()

    assert agent.model == "gpt-4o-mini"


def _find_log(records, message):
    for record in records:
        if record.getMessage() == message:
            return record
    raise AssertionError(f"Missing log message: {message}")
