import asyncio
import base64
import binascii
import hmac
from urllib.parse import parse_qs, urlsplit

import websockets

from milf.agent_runner import run_task
from milf.connection import AppConnection
from milf.protocol import Audio, ProtocolDecodeError, decode
from milf.runtime_logging import configure_dependency_logging
from milf.settings import Settings
from milf.stt import make_stt

PROTOCOL_ERROR = 1002
POLICY_VIOLATION = 1008
MESSAGE_TOO_BIG = 1009
MAX_CLOSE_REASON_BYTES = 100
PROTOCOL_CLOSE_REASON = "protocol error"


async def _handler(ws, settings: Settings | None = None):
    settings = settings or Settings.from_env()
    if not _is_authorized(ws, settings):
        await _close(ws, POLICY_VIOLATION, "unauthorized")
        return

    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send, timeout_seconds=settings.action_timeout_seconds)
    stt = make_stt(settings)

    try:
        first = decode(await ws.recv())
    except websockets.exceptions.ConnectionClosed:
        return
    except ProtocolDecodeError:
        await _close(ws, PROTOCOL_ERROR, PROTOCOL_CLOSE_REASON)
        return

    if not isinstance(first, Audio):
        await _close(ws, PROTOCOL_ERROR, "first frame must be Audio")
        return

    try:
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
    except (binascii.Error, ValueError):
        await _close(ws, PROTOCOL_ERROR, "invalid audio encoding")
        return
    if len(audio) > settings.max_audio_bytes:
        await _close(ws, MESSAGE_TOO_BIG, "audio too large")
        return

    async def pump() -> None:
        try:
            async for raw in ws:
                try:
                    conn.on_message(raw)
                except ProtocolDecodeError:
                    await _close(ws, PROTOCOL_ERROR, PROTOCOL_CLOSE_REASON)
                    break
        except websockets.exceptions.ConnectionClosed:
            pass
        finally:
            conn.fail_pending(ConnectionError("websocket disconnected"))

    pump_task = asyncio.create_task(pump())
    run_task_task = asyncio.create_task(run_task(conn, audio, first.lang, stt))
    try:
        done, _pending = await asyncio.wait(
            {pump_task, run_task_task},
            return_when=asyncio.FIRST_COMPLETED,
        )
        if run_task_task in done:
            if pump_task in done:
                pump_task.result()
            await run_task_task
            return
        if pump_task in done:
            pump_task.result()
            await _cancel_task(run_task_task)
            return
    finally:
        await _cancel_task(pump_task)
        await _cancel_task(run_task_task)


async def _cancel_task(task: asyncio.Task) -> None:
    if task.done():
        return
    task.cancel()
    try:
        await task
    except asyncio.CancelledError:
        pass


async def _close(ws, code: int, reason: str) -> None:
    await ws.close(code=code, reason=_bounded_close_reason(reason))


def _bounded_close_reason(reason: str) -> str:
    encoded = reason.encode("utf-8")
    if len(encoded) <= MAX_CLOSE_REASON_BYTES:
        return reason
    return encoded[:MAX_CLOSE_REASON_BYTES].decode("utf-8", errors="ignore")


def _is_authorized(ws, settings: Settings) -> bool:
    if settings.device_token is None:
        return True
    token = _query_token(ws)
    return token is not None and hmac.compare_digest(token, settings.device_token)


def _query_token(ws) -> str | None:
    path = getattr(ws, "path", None)
    request = getattr(ws, "request", None)
    if path is None and request is not None:
        path = getattr(request, "path", None)
    if not path:
        return None
    values = parse_qs(urlsplit(path).query).get("token")
    return values[0] if values else None


async def serve(host=None, port=None):
    configure_dependency_logging()
    settings = Settings.from_env()
    host = host or settings.ws_host
    port = port or settings.ws_port
    async with websockets.serve(
        lambda ws: _handler(ws, settings),
        host,
        port,
        max_size=settings.ws_max_size_bytes,
    ):
        await asyncio.Future()


def main() -> None:
    asyncio.run(serve())


if __name__ == "__main__":
    main()
