from __future__ import annotations

import logging
import os
from inspect import Parameter, signature
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable

from milf.clarification import (
    ClarificationRequested,
    ClarificationState,
    build_clarification_tool,
)
from milf.confirmation import ConfirmationDeclined, build_confirmation_tool
from milf.connection import AppConnection
from milf.context import (
    acknowledgment,
    build_goal,
)
from milf.intent_router import (
    IntentRoute,
    build_default_intent_agent,
    route_intent_with_agent,
)
from milf.narration import narrate_events
from milf.runtime_failures import SAFE_FAILURE_COPY, caused_by_clean_client_close
from milf.session import MILFSession
from milf.stt import STTAdapter
from milf.ws_driver import WebSocketDriver

APP_CARDS_DIR = Path(__file__).resolve().parents[2] / "config" / "app_cards"
logger = logging.getLogger(__name__)


def build_mobile_config() -> Any:
    from mobilerun import (
        AgentConfig,
        AppCardConfig,
        ExecutorConfig,
        ManagerConfig,
        MobileConfig,
    )

    return MobileConfig(
        agent=AgentConfig(
            max_steps=30,
            reasoning=True,
            streaming=True,
            manager=ManagerConfig(vision=True),
            executor=ExecutorConfig(vision=True),
            app_cards=AppCardConfig(
                enabled=True,
                mode="local",
                app_cards_dir=str(APP_CARDS_DIR),
            ),
        ),
    )


def build_agent(goal: str, driver: WebSocketDriver, custom_tools: dict[str, Any]) -> Any:
    from llama_index.llms.openai import OpenAI
    from mobilerun import MobileAgent

    model = os.environ.get("OPENAI_MODEL", "gpt-4o")
    config = build_mobile_config()

    return MobileAgent(
        goal=goal,
        config=config,
        driver=driver,
        custom_tools=custom_tools,
        llms={
            "manager": OpenAI(model=model),
            "executor": OpenAI(model=model),
            "fast_agent": OpenAI(model=model),
            "app_opener": OpenAI(model="gpt-4o-mini"),
            "structured_output": OpenAI(model=model),
        },
    )


async def run_intent(
    connection: AppConnection,
    intent: str,
    lang: str,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
    intent_router: Callable[[str, str], Any] | None = None,
    session: MILFSession | None = None,
    memory: str = "",
) -> Any:
    session = session or MILFSession()
    if intent_router is None:
        route = await route_intent_with_agent(
            intent,
            lang,
            build_default_intent_agent(),
            session=session,
            memory=memory,
        )
    else:
        route = await _call_intent_router(intent_router, intent, lang, session, memory)
    session.record_user_route(intent, route)
    logger.info(
        "MILF intent route selected.",
        extra={
            "session_id": session.session_id,
            "route_kind": route.kind,
            "contact_id": route.contact_id,
            "requires_confirmation": route.requires_confirmation,
            "normalized_intent_present": route.normalized_intent is not None,
            "lang": lang,
        },
    )

    if route.kind in {"reply", "clarify"}:
        logger.info(
            "MILF responding without MobileRun.",
            extra={
                "session_id": session.session_id,
                "route_kind": route.kind,
                "lang": lang,
            },
        )
        message = route.message or "What would you like me to help you do on your phone?"
        await connection.send_narration(message, lang)
        await connection.send_task_complete(message, lang)
        return SimpleNamespace(success=route.kind == "reply", reason=route.kind)

    routed_intent = route.normalized_intent or intent
    await connection.send_narration(acknowledgment(routed_intent), lang)

    goal = build_goal(
        routed_intent,
        memory=memory,
        session_context=session.context_for_intent_router(),
    )
    driver = WebSocketDriver(connection)
    clarification_state = ClarificationState()
    custom_tools = build_confirmation_tool(connection, lang)
    custom_tools.update(build_clarification_tool(connection, lang, clarification_state))
    logger.info(
        "MILF starting MobileRun.",
        extra={
            "session_id": session.session_id,
            "contact_id": route.contact_id,
            "requires_confirmation": route.requires_confirmation,
            "lang": lang,
        },
    )
    agent = agent_factory(goal, driver, custom_tools)
    handler = agent.run()

    try:
        result = await narrate_events(handler, connection, lang)
    except ClarificationRequested as error:
        logger.info("Clarification requested during agent run.", exc_info=True)
        session.record_mobile_run_clarification(route, error.question, routed_intent)
        logger.info(
            "MILF MobileRun finished.",
            extra={
                "session_id": session.session_id,
                "mobile_run_status": "clarification_requested",
                "reason": "clarify",
                "contact_id": route.contact_id,
                "lang": lang,
            },
        )
        await connection.send_task_complete(error.question, lang)
        return SimpleNamespace(success=False, reason="clarify")
    except ConfirmationDeclined:
        logger.info("Confirmation declined during agent run.", exc_info=True)
        session.record_mobile_run_result(
            route, status="confirmation_declined", reason="confirmation_declined"
        )
        logger.info(
            "MILF MobileRun finished.",
            extra={
                "session_id": session.session_id,
                "mobile_run_status": "confirmation_declined",
                "reason": "confirmation_declined",
                "contact_id": route.contact_id,
                "lang": lang,
            },
        )
        await connection.send_task_failure(SAFE_FAILURE_COPY, lang)
        return SimpleNamespace(success=False, reason="confirmation_declined")
    except Exception as error:
        if caused_by_clean_client_close(error):
            logger.info(
                "Mobile client closed during agent run.",
                extra={
                    "session_id": session.session_id,
                    "contact_id": route.contact_id,
                    "lang": lang,
                },
            )
            session.record_mobile_run_result(
                route, status="failed", reason="client_closed"
            )
            logger.info(
                "MILF MobileRun finished.",
                extra={
                    "session_id": session.session_id,
                    "mobile_run_status": "failed",
                    "reason": "client_closed",
                    "contact_id": route.contact_id,
                    "lang": lang,
                },
            )
            return SimpleNamespace(success=False, reason="client_closed")

        logger.exception("Agent run failed.")
        session.record_mobile_run_result(route, status="agent_error", reason="agent_error")
        logger.info(
            "MILF MobileRun finished.",
            extra={
                "session_id": session.session_id,
                "mobile_run_status": "agent_error",
                "reason": "agent_error",
                "contact_id": route.contact_id,
                "lang": lang,
            },
        )
        await connection.send_task_failure(SAFE_FAILURE_COPY, lang)
        return SimpleNamespace(success=False, reason="agent_error")

    if clarification_state.question is not None:
        session.record_mobile_run_clarification(
            route, clarification_state.question, routed_intent
        )
        logger.info(
            "MILF MobileRun finished.",
            extra={
                "session_id": session.session_id,
                "mobile_run_status": "clarification_requested",
                "reason": "clarify",
                "contact_id": route.contact_id,
                "lang": lang,
            },
        )
        await connection.send_task_complete(clarification_state.question, lang)
        return SimpleNamespace(success=False, reason="clarify")

    session.record_mobile_run_result(result=result, route=route)
    logger.info(
        "MILF MobileRun finished.",
        extra={
            "session_id": session.session_id,
            "mobile_run_status": session.last_mobile_run.status
            if session.last_mobile_run is not None
            else "unknown",
            "reason": getattr(result, "reason", None),
            "contact_id": route.contact_id,
            "lang": lang,
        },
    )
    if getattr(result, "success", True):
        await connection.send_task_complete("Done.", lang)
    else:
        await connection.send_task_failure(SAFE_FAILURE_COPY, lang)

    return result


async def run_task(
    connection: AppConnection,
    audio: bytes,
    lang: str,
    stt: STTAdapter,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
    intent_router: Callable[[str, str], Any] | None = None,
    session: MILFSession | None = None,
    memory: str = "",
) -> Any:
    intent = await stt.transcribe(audio, lang)
    return await run_intent(
        connection,
        intent,
        lang,
        agent_factory,
        intent_router=intent_router,
        session=session,
        memory=memory,
    )


async def _call_intent_router(
    intent_router: Callable[..., Any],
    intent: str,
    lang: str,
    session: MILFSession,
    memory: str,
) -> IntentRoute:
    router_signature = signature(intent_router)
    parameters = list(router_signature.parameters.values())
    accepts_session_keyword = any(
        parameter.kind == Parameter.VAR_KEYWORD
        or parameter.name == "session"
        for parameter in parameters
    )
    accepts_memory_keyword = any(
        parameter.kind == Parameter.VAR_KEYWORD
        or parameter.name == "memory"
        for parameter in parameters
    )
    accepts_session_positionally = any(
        parameter.kind == Parameter.VAR_POSITIONAL for parameter in parameters
    ) or len(parameters) >= 3

    if accepts_session_keyword or accepts_memory_keyword:
        kwargs = {}
        if accepts_session_keyword:
            kwargs["session"] = session
        if accepts_memory_keyword:
            kwargs["memory"] = memory
        return await intent_router(intent, lang, **kwargs)
    if accepts_session_positionally:
        return await intent_router(intent, lang, session)
    return await intent_router(intent, lang)
