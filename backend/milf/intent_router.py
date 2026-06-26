from __future__ import annotations

import logging
import os
from inspect import Parameter, signature
from typing import Any, Literal, Protocol

from milf.context import format_agent_memory
from pydantic import BaseModel

logger = logging.getLogger(__name__)


class IntentRoute(BaseModel):
    kind: Literal["execute", "reply", "clarify", "perceive"]
    message: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False
    fast_path: bool = False


class IntentAgentDecision(BaseModel):
    route: Literal["chat", "clarify", "execute", "refuse", "perceive"]
    reply: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False
    fast_path: bool = False
    confidence: float = 0.0


class IntentAgent(Protocol):
    async def classify(
        self, intent: str, lang: str, session_context: str = ""
    ) -> IntentAgentDecision:
        ...


GREETING_RESPONSE = "Hi, what would you like me to help you do on your phone?"
INTENT_AGENT_PROMPT = """
You are MILF's non-actuating intent router. You never tap, type, open apps,
send messages, start calls, or operate the phone.

Classify the user's utterance into one route:
- chat: casual talk or greeting that needs no phone action.
- clarify: a phone action may be intended, but key details are missing.
- execute: a concrete phone task is ready for the phone automation agent.
- perceive: the user wants to know or hear what is currently visible on the screen.
  Examples: describe the screen, read this aloud, what does this say, did it send.
  Read-only; the gate never actuates and never claims to have seen the screen.
- refuse: unsafe or unsupported request.

Rules:
- For execute, write normalized_intent as a clear, concrete phone task.
- For perceive, write normalized_intent as a clear read-only screen question. If
  the user's wording is already clear, preserve it closely.
- Set fast_path true ONLY for narrow single-action execute intents that need no
  planning: opening a named app, pressing Home, pressing Back, or home/back navigation.
- Keep fast_path false for anything that composes, sends, calls, pays, shares,
  selects among visible items, reads screen content, depends on current screen
  state, or takes multiple steps.
- Use the optional Agent memory below for user-provided names, preferences, and
  context. Resolve relationship references, nicknames, and preferred apps from
  Agent memory before deciding whether the request is missing information. Do
  not assume relationships or contacts that are not present in the current
  utterance, session context, or Agent memory.
- Leave contact_id unset unless an explicit integration provides one.
- Use requires_confirmation for calls, sends, payments, location/media sharing, or other consequential actions.
- For clarify, reply with one short question.
- For chat, reply naturally but briefly.
- Use the MILF session context to resolve short follow-ups, such as a name
  supplied after a pending "who should I send that to?" question.

MILF session context:
{session_context}

User utterance: {intent}
Language: {lang}
"""


class OpenAIIntentAgent:
    def __init__(self, model: str):
        self.model = model

    async def classify(
        self, intent: str, lang: str, session_context: str = ""
    ) -> IntentAgentDecision:
        from llama_index.core import PromptTemplate
        from llama_index.llms.openai import OpenAI

        llm = OpenAI(model=self.model, temperature=0)
        prompt = PromptTemplate(INTENT_AGENT_PROMPT)
        return await llm.astructured_predict(
            IntentAgentDecision,
            prompt,
            intent=intent,
            lang=lang,
            session_context=session_context,
        )


def build_default_intent_agent() -> IntentAgent:
    if not os.environ.get("OPENAI_API_KEY"):
        raise RuntimeError(
            "OPENAI_API_KEY is required because every input is routed through the intent model."
        )
    model = os.environ.get("MILF_INTENT_MODEL") or os.environ.get(
        "OPENAI_MODEL", "gpt-4o-mini"
    )
    return OpenAIIntentAgent(model)


async def route_intent_with_agent(
    intent: str,
    lang: str,
    agent: IntentAgent | None = None,
    session: Any | None = None,
    memory: str = "",
) -> IntentRoute:
    if agent is None:
        raise RuntimeError("An intent model is required to route inputs.")

    if session is None:
        session_context = "No prior MILF session context."
    else:
        session_context = session.context_for_intent_router()
    memory_section = format_agent_memory(memory)
    if memory_section:
        session_context = f"{session_context}\n{memory_section}"
    decision = await _call_intent_agent(agent, intent, lang, session_context)
    logger.info(
        "MILF intent model decision.",
        extra={
            "intent_route": decision.route,
            "contact_id": decision.contact_id,
            "requires_confirmation": decision.requires_confirmation,
            "fast_path": decision.fast_path,
            "confidence": decision.confidence,
            "normalized_intent_present": decision.normalized_intent is not None,
        },
    )
    route = _route_from_decision(decision)
    logger.info(
        "MILF intent router response.",
        extra={
            "route_kind": route.kind,
            "response_message": route.message,
            "normalized_intent": route.normalized_intent,
            "contact_id": route.contact_id,
            "requires_confirmation": route.requires_confirmation,
            "fast_path": route.fast_path,
        },
    )
    return route


def _route_from_decision(decision: IntentAgentDecision) -> IntentRoute:
    if decision.route == "execute":
        return IntentRoute(
            kind="execute",
            normalized_intent=decision.normalized_intent,
            contact_id=decision.contact_id,
            requires_confirmation=decision.requires_confirmation,
            fast_path=decision.fast_path,
        )
    if decision.route == "perceive":
        return IntentRoute(
            kind="perceive",
            normalized_intent=decision.normalized_intent,
        )
    if decision.route == "clarify":
        return IntentRoute(
            kind="clarify",
            message=decision.reply
            or "What would you like me to help you do on your phone?",
        )
    if decision.route == "refuse":
        return IntentRoute(
            kind="clarify",
            message=decision.reply or "I can't help with that phone action.",
        )
    return IntentRoute(
        kind="reply",
        message=decision.reply or GREETING_RESPONSE,
    )


async def _call_intent_agent(
    agent: IntentAgent,
    intent: str,
    lang: str,
    session_context: str,
) -> IntentAgentDecision:
    classify = agent.classify
    classify_signature = signature(classify)
    parameters = list(classify_signature.parameters.values())
    accepts_context_keyword = any(
        parameter.kind == Parameter.VAR_KEYWORD
        or parameter.name == "session_context"
        for parameter in parameters
    )
    accepts_context_positionally = any(
        parameter.kind == Parameter.VAR_POSITIONAL for parameter in parameters
    ) or len(parameters) >= 3

    if accepts_context_keyword:
        return await classify(intent, lang, session_context)
    if accepts_context_positionally:
        return await classify(intent, lang, session_context)
    return await classify(intent, lang)
