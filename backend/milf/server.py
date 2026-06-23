import asyncio
import base64

import websockets

from milf.agent_runner import run_task
from milf.connection import AppConnection
from milf.protocol import Audio, decode
from milf.settings import Settings
from milf.stt import make_stt

PROTOCOL_ERROR = 1002


async def _handler(ws, settings: Settings | None = None):
    settings = settings or Settings.from_env()

    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send)
    stt = make_stt(settings)
    first = decode(await ws.recv())
    if not isinstance(first, Audio):
        await ws.close(code=PROTOCOL_ERROR, reason="first frame must be Audio")
        return
    audio = base64.b64decode(first.goal_audio_b64)

    async def pump() -> None:
        async for raw in ws:
            conn.on_message(raw)

    pump_task = asyncio.create_task(pump())
    try:
        await run_task(conn, audio, first.lang, stt)
    finally:
        pump_task.cancel()
        try:
            await pump_task
        except asyncio.CancelledError:
            pass


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
