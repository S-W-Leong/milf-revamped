from __future__ import annotations

import os
from typing import Any, Callable

from milf.confirmation import build_confirmation_tool
from milf.connection import AppConnection
from milf.context import acknowledgment, build_goal
from milf.narration import narrate_events
from milf.stt import STTAdapter
from milf.ws_driver import WebSocketDriver


def build_agent(goal: str, driver: WebSocketDriver, custom_tools: dict[str, Any]) -> Any:
    from llama_index.llms.openai import OpenAI
    from mobilerun import AgentConfig, MobileAgent, MobileConfig

    model = os.environ.get("OPENAI_MODEL", "gpt-4o")
    config = MobileConfig(
        agent=AgentConfig(max_steps=30, reasoning=True, streaming=True),
    )

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


async def run_task(
    connection: AppConnection,
    audio: bytes,
    lang: str,
    stt: STTAdapter,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any] = build_agent,
) -> Any:
    intent = await stt.transcribe(audio, lang)
    await connection.send_narration(acknowledgment(intent), lang)

    goal = build_goal(intent)
    driver = WebSocketDriver(connection)
    custom_tools = build_confirmation_tool(connection, lang)
    agent = agent_factory(goal, driver, custom_tools)
    handler = agent.run()

    return await narrate_events(handler, connection, lang)
