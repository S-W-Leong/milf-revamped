import asyncio
import base64
import json
import logging

import pytest
import websockets

from milf.agent_runner import SAFE_FAILURE_COPY
from milf.protocol import Audio, TaskFailure, TextGoal, decode, encode
from milf.server import _dispatch_first_frame, _handler
from milf.stt import MockSTT


async def _serve_once():
    server = await websockets.serve(_handler, "127.0.0.1", 0)
    return server, server.sockets[0].getsockname()[1]


async def test_server_sends_safe_failure_when_make_stt_fails(monkeypatch):
    def fail_make_stt():
        raise RuntimeError("stt setup failed")

    monkeypatch.setattr("milf.server.make_stt", fail_make_stt)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(
                encode(
                    Audio(
                        goal_audio_b64=base64.b64encode(b"voice").decode("ascii"),
                        lang="en",
                    )
                )
            )

            failure = decode(await ws.recv())

    assert isinstance(failure, TaskFailure)
    assert failure.message == SAFE_FAILURE_COPY
    assert failure.lang == "en"
    assert failure.recovery_contact_id == "buyer-daughter"


async def test_server_sends_safe_failure_for_invalid_audio_base64(monkeypatch):
    called = False

    async def fake_run_task(conn, audio, lang, stt):
        nonlocal called
        called = True

    monkeypatch.setattr("milf.server.make_stt", lambda: MockSTT("unused"))
    monkeypatch.setattr("milf.server.run_task", fake_run_task)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(Audio(goal_audio_b64="%%%", lang="en")))

            failure = decode(await ws.recv())

    assert isinstance(failure, TaskFailure)
    assert failure.message == SAFE_FAILURE_COPY
    assert failure.recovery_contact_id == "buyer-daughter"
    assert called is False


class DispatchConnection:
    pass


async def test_dispatch_first_frame_routes_text_goal(monkeypatch):
    conn = DispatchConnection()
    called = []

    async def fake_run_intent(connection, intent, lang):
        called.append((connection, intent, lang))

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)

    await _dispatch_first_frame(conn, TextGoal(goal_text="call Wei", lang="en"))

    assert called == [(conn, "call Wei", "en")]


async def test_dispatch_first_frame_rejects_unknown_message():
    conn = DispatchConnection()

    with pytest.raises(TypeError, match="first frame must be Audio or TextGoal"):
        await _dispatch_first_frame(conn, json.loads("{}"))


async def test_server_ignores_setup_check_close(caplog):
    caplog.set_level(logging.ERROR)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.close(code=1000, reason="setup check")
        await asyncio.sleep(0)

    assert "connection handler failed" not in caplog.text


async def test_server_stops_quietly_when_client_closes_mid_task(monkeypatch, caplog):
    async def fake_run_intent(conn, intent, lang):
        await asyncio.sleep(0.05)
        await conn.send_narration("still working", lang)

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)
    caplog.set_level(logging.ERROR)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(TextGoal(goal_text="call Wei", lang="en")))
            await ws.close(code=1000, reason="client closing")
        await asyncio.sleep(0.1)

    assert "connection handler failed" not in caplog.text
    assert "Backend task handling failed." not in caplog.text
