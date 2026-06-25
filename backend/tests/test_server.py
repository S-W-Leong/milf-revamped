import asyncio
import base64
import json
import logging

import pytest
import websockets

from milf.agent_runner import SAFE_FAILURE_COPY
from milf.intent_router import IntentAgentDecision
from milf.protocol import (
    Audio,
    Narration,
    TaskComplete,
    TaskFailure,
    TextGoal,
    decode,
    encode,
)
from milf.server import _configure_logging, _dispatch_first_frame, _handler, serve
from milf.stt import MockSTT


async def _serve_once():
    server = await websockets.serve(_handler, "127.0.0.1", 0)
    return server, server.sockets[0].getsockname()[1]


def test_configure_logging_uses_env_level(monkeypatch):
    captured = {}

    def fake_basic_config(**kwargs):
        captured.update(kwargs)

    monkeypatch.setenv("MILF_LOG_LEVEL", "DEBUG")
    monkeypatch.setattr("logging.basicConfig", fake_basic_config)

    _configure_logging()

    assert captured["level"] == logging.DEBUG
    assert captured["format"] == "%(asctime)s %(levelname)s %(name)s %(message)s"


def test_configure_logging_defaults_to_info(monkeypatch):
    captured = {}

    def fake_basic_config(**kwargs):
        captured.update(kwargs)

    monkeypatch.delenv("MILF_LOG_LEVEL", raising=False)
    monkeypatch.setattr("logging.basicConfig", fake_basic_config)

    _configure_logging()

    assert captured["level"] == logging.INFO


def test_configure_logging_suppresses_websocket_connection_noise(monkeypatch):
    captured = {}

    class FakeLogger:
        def setLevel(self, level):
            captured["level"] = level

    real_get_logger = logging.getLogger

    def fake_get_logger(name=None):
        if name is None:
            return real_get_logger()
        captured["name"] = name
        return FakeLogger()

    monkeypatch.setattr("logging.basicConfig", lambda **_: None)
    monkeypatch.setattr("logging.getLogger", fake_get_logger)

    _configure_logging()

    assert captured == {
        "name": "websockets.server",
        "level": logging.WARNING,
    }


async def test_serve_allows_large_phone_state_frames(monkeypatch):
    captured = {}

    class StopServe(Exception):
        pass

    class FakeServer:
        async def __aenter__(self):
            return self

        async def __aexit__(self, exc_type, exc, tb):
            return False

    class StopFuture:
        def __await__(self):
            async def stop():
                raise StopServe()

            return stop().__await__()

    def fake_serve(handler, host, port, **kwargs):
        captured.update(
            {
                "handler": handler,
                "host": host,
                "port": port,
                **kwargs,
            }
        )
        return FakeServer()

    monkeypatch.delenv("MILF_WS_MAX_SIZE", raising=False)
    monkeypatch.setattr("milf.server.websockets.serve", fake_serve)
    monkeypatch.setattr("milf.server.asyncio.Future", StopFuture)

    with pytest.raises(StopServe):
        await serve(host="127.0.0.1", port=9999)

    assert captured["handler"] is _handler
    assert captured["host"] == "127.0.0.1"
    assert captured["port"] == 9999
    assert captured["max_size"] >= 4 * 1024 * 1024


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
    assert not hasattr(failure, "recovery_contact_id")


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
    assert not hasattr(failure, "recovery_contact_id")
    assert called is False


class DispatchConnection:
    pass


async def test_dispatch_first_frame_routes_text_goal(monkeypatch):
    conn = DispatchConnection()
    called = []

    async def fake_run_intent(connection, intent, lang, session=None, memory=""):
        called.append((connection, intent, lang, session, memory))

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)

    await _dispatch_first_frame(
        conn,
        TextGoal(goal_text="call Wei", lang="en", memory="Wei is my grandson."),
    )

    assert called == [(conn, "call Wei", "en", None, "Wei is my grandson.")]


async def test_server_reuses_session_for_multiple_text_goals(monkeypatch):
    sessions = []

    async def fake_run_intent(conn, intent, lang, session=None):
        sessions.append((intent, session))
        await conn.send_task_complete(f"handled {intent}", lang)

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(encode(TextGoal(goal_text="send hello", lang="en")))
            first = decode(await ws.recv())

            await ws.send(encode(TextGoal(goal_text="Wei", lang="en")))
            second = decode(await ws.recv())

    assert isinstance(first, TaskComplete)
    assert first.summary == "handled send hello"
    assert isinstance(second, TaskComplete)
    assert second.summary == "handled Wei"
    assert sessions[0][0] == "send hello"
    assert sessions[1][0] == "Wei"
    assert sessions[0][1] is sessions[1][1]


async def test_server_reuses_durable_session_across_reconnected_text_goals(monkeypatch):
    sessions = []

    async def fake_run_intent(conn, intent, lang, session=None):
        sessions.append((intent, session))
        if intent == "search movie":
            session.recent_user_inputs.append("search movie")
            await conn.send_task_complete("Which app should I use?", lang)
            return
        await conn.send_task_complete("Searching movie on YouTube.", lang)

    monkeypatch.setattr("milf.server.run_intent", fake_run_intent)

    server, port = await _serve_once()
    async with server:
        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(
                encode(
                    TextGoal(
                        goal_text="search movie",
                        lang="en",
                        session_id="manual-test-session",
                    )
                )
            )
            first = decode(await ws.recv())

        async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
            await ws.send(
                encode(
                    TextGoal(
                        goal_text="YT",
                        lang="en",
                        session_id="manual-test-session",
                    )
                )
            )
            second = decode(await ws.recv())

    assert isinstance(first, TaskComplete)
    assert isinstance(second, TaskComplete)
    assert sessions[0][0] == "search movie"
    assert sessions[1][0] == "YT"
    assert sessions[0][1] is sessions[1][1]
    assert sessions[1][1].recent_user_inputs == ["search movie"]


async def test_server_short_circuits_greeting_text_goal():
    class FakeIntentAgent:
        async def classify(self, intent, lang):
            return IntentAgentDecision(
                route="chat",
                reply="Hi, what would you like me to help you do on your phone?",
                confidence=0.9,
            )

    monkeypatch = pytest.MonkeyPatch()
    monkeypatch.setattr(
        "milf.agent_runner.build_default_intent_agent",
        lambda: FakeIntentAgent(),
    )
    server, port = await _serve_once()
    try:
        async with server:
            async with websockets.connect(f"ws://127.0.0.1:{port}") as ws:
                await ws.send(encode(TextGoal(goal_text="helo", lang="en")))

                narration = decode(await ws.recv())
                complete = decode(await ws.recv())
    finally:
        monkeypatch.undo()

    assert isinstance(narration, Narration)
    assert narration.text == "Hi, what would you like me to help you do on your phone?"
    assert narration.lang == "en"
    assert isinstance(complete, TaskComplete)
    assert complete.summary == narration.text
    assert complete.lang == "en"


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
