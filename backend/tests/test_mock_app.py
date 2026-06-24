import asyncio
import base64
import json
import logging
from types import SimpleNamespace

import pytest
import websockets

from milf.connection import AppConnection
from milf.mock_app import MockApp
from milf.protocol import Action, Audio, ConfirmResponse, Narration, decode, encode
from milf.server import MESSAGE_TOO_BIG, POLICY_VIOLATION, PROTOCOL_ERROR, _handler


def _configure_local_server_env(monkeypatch):
    monkeypatch.setenv("MILF_ENV", "test")
    monkeypatch.setenv("MILF_STT_BACKEND", "mock")
    monkeypatch.setenv("MILF_MAX_AUDIO_BYTES", "5242880")
    monkeypatch.setenv("MILF_WS_MAX_SIZE_BYTES", "8388608")
    monkeypatch.delenv("MILF_DEVICE_TOKEN", raising=False)


async def _assert_recv_close(ws, code: int):
    with pytest.raises(websockets.exceptions.ConnectionClosedError) as exc:
        await asyncio.wait_for(ws.recv(), timeout=1)
    assert exc.value.rcvd is not None
    assert exc.value.rcvd.code == code
    return exc.value.rcvd


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


async def test_server_rejects_non_audio_first_frame(monkeypatch):
    _configure_local_server_env(monkeypatch)
    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(Narration(text="hello", lang="en")))
            await _assert_recv_close(ws, PROTOCOL_ERROR)


async def test_server_rejects_missing_token_when_configured(monkeypatch):
    _configure_local_server_env(monkeypatch)
    monkeypatch.setenv("MILF_DEVICE_TOKEN", "secret-token")

    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await _assert_recv_close(ws, POLICY_VIOLATION)


async def test_server_accepts_token_query_parameter(monkeypatch):
    _configure_local_server_env(monkeypatch)
    monkeypatch.setenv("MILF_DEVICE_TOKEN", "secret-token")
    completed = asyncio.Event()
    observed = {}

    async def fake_run_task(conn, audio, lang, stt):
        observed["audio"] = audio
        observed["lang"] = lang
        completed.set()
        return SimpleNamespace(success=True)

    monkeypatch.setattr("milf.server.run_task", fake_run_task)

    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(
            f"ws://127.0.0.1:{port}?token=secret-token"
        ) as ws:
            await ws.send(
                encode(
                    Audio(
                        goal_audio_b64=base64.b64encode(b"voice").decode("ascii"),
                        lang="en",
                    )
                )
            )
            await asyncio.wait_for(completed.wait(), timeout=1)

    assert observed == {"audio": b"voice", "lang": "en"}


async def test_server_closes_malformed_first_frame_with_protocol_error(monkeypatch):
    _configure_local_server_env(monkeypatch)
    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send("{")
            await _assert_recv_close(ws, PROTOCOL_ERROR)


async def test_server_closes_non_utf8_binary_first_frame_with_protocol_error(monkeypatch):
    _configure_local_server_env(monkeypatch)
    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(b"\xff")
            close = await _assert_recv_close(ws, PROTOCOL_ERROR)
            assert len(close.reason.encode("utf-8")) <= 100


async def test_server_bounds_protocol_error_close_reason(monkeypatch):
    _configure_local_server_env(monkeypatch)
    raw = json.dumps({"type": "A" * 1000, "data": {}})

    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(raw)
            close = await _assert_recv_close(ws, PROTOCOL_ERROR)
            assert len(close.reason.encode("utf-8")) <= 100
            assert "A" * 100 not in close.reason


async def test_server_closes_invalid_base64_with_protocol_error(monkeypatch):
    _configure_local_server_env(monkeypatch)
    async with websockets.serve(_handler, "127.0.0.1", 0) as server:
        port = server.sockets[0].getsockname()[1]
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(Audio(goal_audio_b64="not-base64###", lang="en")))
            await _assert_recv_close(ws, PROTOCOL_ERROR)


async def test_server_closes_oversized_audio_with_message_too_big(monkeypatch):
    _configure_local_server_env(monkeypatch)
    monkeypatch.setenv("MILF_MAX_AUDIO_BYTES", "4")
    monkeypatch.setenv("MILF_WS_MAX_SIZE_BYTES", "2048")

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
            await _assert_recv_close(ws, MESSAGE_TOO_BIG)


async def test_server_routes_outbound_and_inbound_frames(monkeypatch):
    async def fake_run_task(conn, audio, lang, stt):
        result = await conn.request_confirmation("Call Wei now?", lang)
        return SimpleNamespace(success=result, audio=audio, lang=lang)

    monkeypatch.setattr("milf.server.run_task", fake_run_task)
    _configure_local_server_env(monkeypatch)

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


async def test_server_cancels_run_task_when_client_disconnects(monkeypatch):
    _configure_local_server_env(monkeypatch)
    started = asyncio.Event()
    cancelled = asyncio.Event()
    blocker = asyncio.Event()

    async def fake_run_task(conn, audio, lang, stt):
        started.set()
        try:
            await blocker.wait()
        except asyncio.CancelledError:
            cancelled.set()
            raise

    monkeypatch.setattr("milf.server.run_task", fake_run_task)
    ws = _DisconnectingWebSocket(
        encode(
            Audio(
                goal_audio_b64=base64.b64encode(b"voice").decode("ascii"),
                lang="en",
            )
        )
    )

    handler_task = asyncio.create_task(_handler(ws))
    try:
        await asyncio.wait_for(started.wait(), timeout=1)
        await asyncio.wait_for(cancelled.wait(), timeout=1)
        await asyncio.wait_for(handler_task, timeout=1)
    finally:
        if not handler_task.done():
            handler_task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await handler_task


def test_server_startup_suppresses_websocket_debug_logging(monkeypatch):
    from milf.server import serve

    captured = {}
    logger = logging.getLogger("websockets")
    original_level = logger.level
    logger.setLevel(logging.DEBUG)

    class FakeServe:
        def __init__(self, handler, host, port, max_size):
            captured["host"] = host
            captured["port"] = port
            captured["max_size"] = max_size

        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

    async def never_finishes():
        raise asyncio.CancelledError

    monkeypatch.setenv("MILF_WS_HOST", "127.0.0.1")
    monkeypatch.setenv("MILF_WS_PORT", "8765")
    monkeypatch.setattr("milf.server.websockets.serve", FakeServe)
    monkeypatch.setattr("milf.server.asyncio.Future", never_finishes)

    try:
        with pytest.raises(asyncio.CancelledError):
            asyncio.run(serve())

        assert captured["host"] == "127.0.0.1"
        assert logging.getLogger("websockets").getEffectiveLevel() >= logging.WARNING
    finally:
        logger.setLevel(original_level)


class _DisconnectingWebSocket:
    def __init__(self, first_frame: str):
        self._first_frame = first_frame

    async def recv(self):
        return self._first_frame

    async def send(self, raw: str) -> None:
        return None

    def __aiter__(self):
        return self

    async def __anext__(self):
        raise StopAsyncIteration

    async def close(self, code: int, reason: str) -> None:
        return None
