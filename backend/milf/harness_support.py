from typing import Any, Callable

from milf.agent_runner import run_task
from milf.connection import AppConnection
from milf.mock_app import MockApp
from milf.stt import MockSTT
from milf.ws_driver import WebSocketDriver


async def run_once(
    scripted: dict[str, Any],
    transcript: str,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any],
) -> bool:
    mock = MockApp(scripted, auto_approve=True)
    conn = AppConnection(send=None)

    async def send(raw: str) -> None:
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)

    conn._send = send
    result = await run_task(
        conn,
        b"audio",
        "en",
        MockSTT(transcript),
        agent_factory,
    )
    return bool(result.success)


async def run_n(
    n: int,
    scripted: dict[str, Any],
    transcript: str,
    agent_factory: Callable[[str, WebSocketDriver, dict[str, Any]], Any],
) -> float:
    wins = 0
    for _ in range(n):
        if await run_once(scripted, transcript, agent_factory):
            wins += 1

    return wins / n
