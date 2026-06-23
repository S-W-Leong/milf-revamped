import asyncio
import base64
import binascii
import hmac
from urllib.parse import parse_qs, urlsplit

import websockets

from milf.agent_runner import run_task
from milf.connection import AppConnection
from milf.protocol import Audio, ProtocolDecodeError, decode
from milf.settings import Settings
from milf.stt import make_stt

PROTOCOL_ERROR = 1002
POLICY_VIOLATION = 1008
MESSAGE_TOO_BIG = 1009


async def _handler(ws, settings: Settings | None = None):
    settings = settings or Settings.from_env()
    if not _is_authorized(ws, settings):
        await ws.close(code=POLICY_VIOLATION, reason="unauthorized")
        return

    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send)
    stt = make_stt(settings)

    try:
        first = decode(await ws.recv())
    except websockets.exceptions.ConnectionClosed:
        return
    except ProtocolDecodeError as exc:
        await ws.close(code=PROTOCOL_ERROR, reason=str(exc))
        return

    if not isinstance(first, Audio):
        await ws.close(code=PROTOCOL_ERROR, reason="first frame must be Audio")
        return

    try:
        audio = base64.b64decode(first.goal_audio_b64, validate=True)
    except (binascii.Error, ValueError):
        await ws.close(code=PROTOCOL_ERROR, reason="invalid audio encoding")
        return
    if len(audio) > settings.max_audio_bytes:
        await ws.close(code=MESSAGE_TOO_BIG, reason="audio too large")
        return

    async def pump() -> None:
        try:
            async for raw in ws:
                try:
                    conn.on_message(raw)
                except ProtocolDecodeError as exc:
                    await ws.close(code=PROTOCOL_ERROR, reason=str(exc))
                    break
        except websockets.exceptions.ConnectionClosed:
            pass

    pump_task = asyncio.create_task(pump())
    try:
        await run_task(conn, audio, first.lang, stt)
    finally:
        pump_task.cancel()
        try:
            await pump_task
        except asyncio.CancelledError:
            pass


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
