import asyncio
import base64
from types import SimpleNamespace

import pytest
import websockets

from milf.connection import AppConnection
from milf.mock_app import MockApp
from milf.protocol import Action, Audio, ConfirmResponse, Narration, decode, encode
from milf.server import PROTOCOL_ERROR, _handler


async def test_driver_action_against_mock_app_returns_result():
    mock = MockApp(scripted={"get_ui_tree": {"nodes": []}, "tap": None})
    conn = AppConnection(send=None)  # send set below

    async def send(raw):
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)

    conn._send = send
    res = await conn.send_action("get_ui_tree", {})
    assert res.ok and res.result == {"nodes": []}


async def test_confirm_request_auto_approved():
    mock = MockApp(scripted={}, auto_approve=True)
    conn = AppConnection(send=None)

    async def send(raw):
        reply = await mock.handle(raw)
        if reply is not None:
            conn.on_message(reply)

    conn._send = send
    assert await conn.request_confirmation("Call Wei now?", "en") is True


async def test_unscripted_action_returns_error():
    mock = MockApp(scripted={})
    raw = await mock.handle(encode(Action(id="a1", name="missing", args={})))
    result = decode(raw)
    assert result.ok is False
    assert "missing" in result.error


async def test_server_rejects_non_audio_first_frame():
    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(Narration(text="hello", lang="en")))
            with pytest.raises(websockets.exceptions.ConnectionClosedError) as exc:
                await ws.recv()
            assert exc.value.rcvd.code == PROTOCOL_ERROR


async def test_server_routes_outbound_and_inbound_frames(monkeypatch):
    async def fake_run_task(conn, audio, lang, stt):
        result = await conn.request_confirmation("Call Wei now?", lang)
        return SimpleNamespace(success=result, audio=audio, lang=lang)

    monkeypatch.setattr("milf.server.run_task", fake_run_task)
    monkeypatch.setenv("MILF_STT_BACKEND", "mock")

    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(
                encode(
                    Audio(
                        goal_audio_b64=base64.b64encode(b"voice").decode("ascii"),
                        lang="en",
                    )
                )
            )
            request = decode(await ws.recv())
            assert request.summary == "Call Wei now?"
            await ws.send(encode(ConfirmResponse(id=request.id, approved=True)))
            await asyncio.sleep(0)
