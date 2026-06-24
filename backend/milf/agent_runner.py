from __future__ import annotations

import logging
import os
from pathlib import Path
from types import SimpleNamespace
from typing import Any, Callable

from milf.confirmation import ConfirmationDeclined, build_confirmation_tool
from milf.connection import AppConnection
from milf.context import acknowledgment, build_goal, escape_contact, resolve_contact
from milf.narration import narrate_events
from milf.stt import STTAdapter
from milf.ws_driver import WebSocketDriver

SAFE_FAILURE_COPY = (
    "I'm having a little trouble with that. Please try again."
)
APP_CARDS_DIR = Path(__file__).resolve().parents[2] / "config" / "app_cards"
logger = logging.getLogger(__name__)


def build_mobile_config() -> Any:
    from mobilerun import AgentConfig, AppCardConfig, MobileConfig

    return MobileConfig(
        agent=AgentConfig(
            max_steps=30,
            reasoning=True,
            streaming=True,
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
) -> Any:
    contact = resolve_contact(intent)
    escape = escape_contact()
    await connection.send_narration(acknowledgment(intent), lang)

    goal = build_goal(intent)
    driver = WebSocketDriver(connection)
    custom_tools = build_confirmation_tool(
        connection,
        lang,
        contact_id=contact.id if contact is not None else None,
    )
    agent = agent_factory(goal, driver, custom_tools)
    handler = agent.run()

    try:
        result = await narrate_events(handler, connection, lang)
    except ConfirmationDeclined:
        logger.info("Confirmation declined during agent run.", exc_info=True)
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )
        return SimpleNamespace(success=False, reason="confirmation_declined")
    except Exception:
        logger.exception("Agent run failed.")
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )
        return SimpleNamespace(success=False, reason="agent_error")

    if getattr(result, "success", True):
        if contact is not None:
            await connection.send_task_complete(
                f"You're connected to {contact.display_name}.",
                lang,
                contact_id=contact.id,
            )
        else:
            await connection.send_task_complete("Done.", lang)
    else:
        await connection.send_task_failure(
            SAFE_FAILURE_COPY,
            lang,
            recovery_contact_id=escape.id,
        )

    return result


async def run_task(
    connection: AppConnection,
    audio: bytes,
    lang: str,
    stt: STTAdapter,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
) -> Any:
    intent = await stt.transcribe(audio, lang)
    return await run_intent(connection, intent, lang, agent_factory)
