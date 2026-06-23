import asyncio

import pytest

from milf.connection import AppConnection
from milf.protocol import ActionResult, decode, encode
from milf.ws_driver import WebSocketDriver


def _wire():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    return sent, conn


async def test_tap_sends_action_and_returns_result():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.tap(100, 200))
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "tap" and action.args == {"x": 100, "y": 200}
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result="ok")))
    assert await task is None


async def test_get_ui_tree_returns_payload_verbatim():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_ui_tree())
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "get_ui_tree"
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result={"nodes": [1, 2]})))
    assert await task == {"nodes": [1, 2]}


async def test_unsupported_method_raises():
    _, conn = _wire()
    driver = WebSocketDriver(conn)
    with pytest.raises(NotImplementedError):
        await driver.install_app("com.example")


async def test_supported_buttons_are_declared():
    _, conn = _wire()
    driver = WebSocketDriver(conn)
    assert {"back", "home", "enter"} <= driver.supported_buttons


async def test_input_coordinate_size_matches_screenshot_pixels():
    _, conn = _wire()
    driver = WebSocketDriver(conn)
    assert await driver.input_coordinate_size(1080, 2400) == (1080, 2400)


async def test_failed_action_raises_runtime_error():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_ui_tree())
    await asyncio.sleep(0)
    action = decode(sent[0])
    conn.on_message(encode(ActionResult(id=action.id, ok=False, error="no ui")))
    with pytest.raises(RuntimeError, match="no ui"):
        await task
