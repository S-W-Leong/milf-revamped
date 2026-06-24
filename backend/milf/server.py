import asyncio
import base64
import logging
import os
from inspect import Parameter, signature
from typing import Any, Callable

import websockets

from milf.agent_runner import SAFE_FAILURE_COPY, run_intent, run_task
from milf.connection import AppConnection
from milf.protocol import Audio, TextGoal, decode
from milf.session import MILFSession
from milf.stt import make_stt

PROTOCOL_ERROR = 1002
logger = logging.getLogger(__name__)


async def _dispatch_first_frame(
    conn: AppConnection,
    first,
    session: MILFSession | None = None,
) -> None:
    if isinstance(first, Audio):
        stt = make_stt()
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
        await _call_with_optional_session(run_task, conn, audio, first.lang, stt, session)
        return

    if isinstance(first, TextGoal):
        await _call_with_optional_session(
            run_intent, conn, first.goal_text, first.lang, session
        )
        return

    raise TypeError("first frame must be Audio or TextGoal")


async def _handler(ws):
    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send)
    session = MILFSession()
    goal_queue = asyncio.Queue()
    pump_task = None

    try:
        first = decode(await ws.recv())
    except websockets.ConnectionClosedOK:
        logger.debug("Websocket closed before first goal frame.")
        return
    if not isinstance(first, Audio | TextGoal):
        await ws.close(code=PROTOCOL_ERROR, reason="first frame must be Audio or TextGoal")
        return

    async def pump() -> None:
        try:
            async for raw in ws:
                msg = decode(raw)
                if isinstance(msg, Audio | TextGoal):
                    await goal_queue.put(msg)
                else:
                    conn.on_message(raw)
        finally:
            await goal_queue.put(None)

    try:
        await goal_queue.put(first)
        pump_task = asyncio.create_task(pump())
        while True:
            goal = await goal_queue.get()
            if goal is None:
                break
            try:
                await _dispatch_first_frame(conn, goal, session=session)
            except websockets.ConnectionClosed:
                raise
            except Exception:
                logger.exception("Backend task handling failed.")
                lang = getattr(goal, "lang", "en")
                try:
                    await conn.send_task_failure(
                        SAFE_FAILURE_COPY,
                        lang,
                        recovery_contact_id="buyer-daughter",
                    )
                except websockets.ConnectionClosed:
                    logger.debug("Websocket closed before task failure could be sent.")
    except websockets.ConnectionClosed:
        logger.debug("Websocket closed during task handling.")
    finally:
        if pump_task is not None:
            pump_task.cancel()
            try:
                await pump_task
            except asyncio.CancelledError:
                pass


async def serve(host=None, port=None):
    host = host or os.environ.get("MILF_WS_HOST", "0.0.0.0")
    port = port or int(os.environ.get("MILF_WS_PORT", "8765"))
    async with websockets.serve(_handler, host, port):
        await asyncio.Future()


async def _call_with_optional_session(
    fn: Callable[..., Any],
    *args: Any,
) -> Any:
    session = args[-1]
    call_args = args[:-1]
    fn_signature = signature(fn)
    parameters = list(fn_signature.parameters.values())
    accepts_session_keyword = any(
        parameter.kind == Parameter.VAR_KEYWORD
        or parameter.name == "session"
        for parameter in parameters
    )
    accepts_session_positionally = any(
        parameter.kind == Parameter.VAR_POSITIONAL for parameter in parameters
    ) or len(parameters) > len(call_args)

    if accepts_session_keyword:
        return await fn(*call_args, session=session)
    if accepts_session_positionally:
        return await fn(*call_args, session)
    return await fn(*call_args)


def _configure_logging() -> None:
    level_name = os.environ.get("MILF_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )


def main() -> None:
    _configure_logging()
    asyncio.run(serve())


if __name__ == "__main__":
    main()
