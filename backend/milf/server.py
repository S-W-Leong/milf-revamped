import asyncio
import base64
import os

import websockets

from milf.agent_runner import run_task
from milf.connection import AppConnection
from milf.protocol import Audio, decode
from milf.stt import make_stt

PROTOCOL_ERROR = 1002


async def _handler(ws):
    async def send(raw: str) -> None:
        await ws.send(raw)

    conn = AppConnection(send)
    stt = make_stt()
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
    host = host or os.environ.get("MILF_WS_HOST", "0.0.0.0")
    port = port or int(os.environ.get("MILF_WS_PORT", "8765"))
    async with websockets.serve(_handler, host, port):
        await asyncio.Future()


def main() -> None:
    asyncio.run(serve())


if __name__ == "__main__":
    main()
