from __future__ import annotations

import logging
import os
from inspect import Parameter, signature
from typing import Any, Literal, Protocol

from pydantic import BaseModel

logger = logging.getLogger(__name__)


class IntentRoute(BaseModel):
    kind: Literal["execute", "reply", "clarify"]
    message: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False


class IntentAgentDecision(BaseModel):
    route: Literal["chat", "clarify", "execute", "refuse"]
    reply: str | None = None
    normalized_intent: str | None = None
    contact_id: str | None = None
    requires_confirmation: bool = False
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
- refuse: unsafe or unsupported request.

Rules:
- For execute, write normalized_intent as a clear, concrete phone task.
- Set contact_id only when you are confident it matches the known contacts.
- Use requires_confirmation for calls, sends, payments, location/media sharing, or other consequential actions.
- For clarify, reply with one short question.
- For chat, reply naturally but briefly.
- Use the MILF session context to resolve short follow-ups, such as a name
  supplied after a pending "who should I send that to?" question.

Known contacts:
- wei-grandson: Wei, relationship grandson, aliases grandson/cucu/Ah Xuan/Ah Boy, preferred WhatsApp video.
- buyer-daughter: Daughter, relationship daughter, preferred phone voice.

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
    model = os.environ.get("MILF_INTENT_MODEL") or os.environ.get("OPENAI_MODEL", "gpt-4o")
    return OpenAIIntentAgent(model)


async def route_intent_with_agent(
    intent: str,
    lang: str,
    agent: IntentAgent | None = None,
    session: Any | None = None,
) -> IntentRoute:
    if agent is None:
        raise RuntimeError("An intent model is required to route inputs.")

    if session is None:
        session_context = "No prior MILF session context."
    else:
        session_context = session.context_for_intent_router()
    decision = await _call_intent_agent(agent, intent, lang, session_context)
    logger.info(
        "MILF intent model decision.",
        extra={
            "intent_route": decision.route,
            "contact_id": decision.contact_id,
            "requires_confirmation": decision.requires_confirmation,
            "confidence": decision.confidence,
            "normalized_intent_present": decision.normalized_intent is not None,
        },
    )
    return _route_from_decision(decision)


def _route_from_decision(decision: IntentAgentDecision) -> IntentRoute:
    if decision.route == "execute":
        return IntentRoute(
            kind="execute",
            normalized_intent=decision.normalized_intent,
            contact_id=decision.contact_id,
            requires_confirmation=decision.requires_confirmation,
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
