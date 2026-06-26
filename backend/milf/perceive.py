from __future__ import annotations

import base64
import json
import logging
import os
from types import SimpleNamespace
from typing import Any, Callable, Protocol

from milf.connection import AppConnection
from milf.runtime_failures import SAFE_FAILURE_COPY, caused_by_clean_client_close
from milf.ws_driver import WebSocketDriver

logger = logging.getLogger(__name__)


class PerceiveAgent(Protocol):
    async def describe(
        self,
        query: str,
        screenshot: bytes,
        ui_tree: dict[str, Any],
        lang: str,
    ) -> str:
        ...


class OpenAIPerceiveAgent:
    def __init__(self, model: str):
        self.model = model

    async def describe(
        self,
        query: str,
        screenshot: bytes,
        ui_tree: dict[str, Any],
        lang: str,
    ) -> str:
        from openai import AsyncOpenAI

        client = AsyncOpenAI()
        image_url = "data:image/png;base64," + base64.b64encode(screenshot).decode(
            "ascii"
        )
        response = await client.responses.create(
            model=self.model,
            input=[
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "input_text",
                            "text": (
                                "Answer the user's read-only screen question. "
                                "Do not suggest tapping or taking actions. "
                                f"Language: {lang}\n"
                                f"Question: {query}\n"
                                "UI tree:\n"
                                f"{json.dumps(ui_tree, ensure_ascii=True)}"
                            ),
                        },
                        {
                            "type": "input_image",
                            "image_url": image_url,
                        },
                    ],
                }
            ],
        )
        return response.output_text.strip()


def build_default_perceive_agent() -> PerceiveAgent:
    if not os.environ.get("OPENAI_API_KEY"):
        raise RuntimeError("OPENAI_API_KEY is required for read-only screen perception.")
    model = (
        os.environ.get("MILF_PERCEIVE_MODEL")
        or os.environ.get("OPENAI_MODEL")
        or "gpt-4o"
    )
    return OpenAIPerceiveAgent(model)


async def run_perceive(
    connection: AppConnection,
    query: str,
    lang: str,
    agent: PerceiveAgent | None = None,
    driver_factory: Callable[[AppConnection], WebSocketDriver] = WebSocketDriver,
) -> Any:
    agent = agent or build_default_perceive_agent()
    driver = driver_factory(connection)

    try:
        screenshot = await driver.screenshot()
        ui_tree = await driver.get_ui_tree()
        answer = await agent.describe(query, screenshot, ui_tree, lang)
        await connection.send_narration(answer, lang)
        await connection.send_task_complete(answer, lang)
        return SimpleNamespace(success=True, reason="perceive")
    except Exception as error:
        if caused_by_clean_client_close(error):
            logger.info("Mobile client closed during perceive run.")
            return SimpleNamespace(success=False, reason="client_closed")

        logger.exception("Perceive run failed.")
        await connection.send_task_failure(SAFE_FAILURE_COPY, lang)
        return SimpleNamespace(success=False, reason="perceive_error")
