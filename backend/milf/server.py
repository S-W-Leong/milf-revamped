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
DEFAULT_WS_MAX_SIZE = 8 * 1024 * 1024
logger = logging.getLogger(__name__)
_sessions: dict[str, MILFSession] = {}


async def _dispatch_first_frame(
    conn: AppConnection,
    first,
    session: MILFSession | None = None,
) -> None:
    if isinstance(first, Audio):
        stt = make_stt()
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
        await _call_with_optional_context(
            run_task,
            conn,
            audio,
            first.lang,
            stt,
            session=session,
            memory=first.memory,
        )
        return

    if isinstance(first, TextGoal):
        await _call_with_optional_context(
            run_intent,
            conn,
            first.goal_text,
            first.lang,
            session=session,
            memory=first.memory,
        )
        return

    raise TypeError("first frame must be Audio or TextGoal")


async def _handler(ws):
    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send)
    connection_session: MILFSession | None = None
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
    connection_session = _session_for_goal(first) or MILFSession()

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
                session = _session_for_goal(goal) or connection_session
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
    max_size = int(os.environ.get("MILF_WS_MAX_SIZE", DEFAULT_WS_MAX_SIZE))
    async with websockets.serve(_handler, host, port, max_size=max_size):
        await asyncio.Future()


async def _call_with_optional_context(
    fn: Callable[..., Any],
    *args: Any,
    session: MILFSession | None,
    memory: str,
) -> Any:
    fn_signature = signature(fn)
    parameters = list(fn_signature.parameters.values())
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
    ) or len(parameters) > len(args)

    if accepts_session_keyword or accepts_memory_keyword:
        kwargs = {}
        if accepts_session_keyword:
            kwargs["session"] = session
        if accepts_memory_keyword:
            kwargs["memory"] = memory
        return await fn(*args, **kwargs)
    if accepts_session_positionally:
        return await fn(*args, session)
    return await fn(*args)


def _session_for_goal(goal: Audio | TextGoal) -> MILFSession | None:
    session_id = goal.session_id
    if session_id is None or not session_id.strip():
        return None
    session_id = session_id.strip()
    session = _sessions.get(session_id)
    if session is None:
        session = MILFSession(session_id=session_id)
        _sessions[session_id] = session
    return session


def _configure_logging() -> None:
    level_name = os.environ.get("MILF_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    logging.getLogger("websockets.server").setLevel(logging.WARNING)


def main() -> None:
    _configure_logging()
    asyncio.run(serve())


if __name__ == "__main__":
    main()
