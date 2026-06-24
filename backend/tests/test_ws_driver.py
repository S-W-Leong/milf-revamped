import asyncio
import base64

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


async def test_get_ui_tree_returns_mobilerun_state_payload_verbatim():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    payload = {
        "a11y_tree": {
            "boundsInScreen": {"left": 0, "top": 0, "right": 1080, "bottom": 2400},
            "children": [],
        },
        "phone_state": {"packageName": "com.whatsapp"},
        "device_context": {"screen_bounds": {"width": 1080, "height": 2400}},
    }
    task = asyncio.create_task(driver.get_ui_tree())
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "get_ui_tree"
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result=payload)))
    assert await task == payload


async def test_get_ui_tree_rejects_payload_missing_mobilerun_state_keys():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_ui_tree())
    await asyncio.sleep(0)
    action = decode(sent[0])
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result={"nodes": []})))
    with pytest.raises(
        RuntimeError,
        match="missing MobileRun state keys: a11y_tree, phone_state, device_context",
    ):
        await task


async def test_get_date_returns_device_date():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_date())
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert action.name == "get_date" and action.args == {}
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result="2026-06-24")))
    assert await task == "2026-06-24"


async def test_get_apps_is_advertised_and_returns_installed_apps():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.get_apps(include_system=False))
    await asyncio.sleep(0)
    action = decode(sent[0])
    assert "get_apps" in driver.supported
    assert action.name == "get_apps" and action.args == {"include_system": False}
    apps = [{"package": "com.whatsapp", "label": "WhatsApp"}]
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result=apps)))
    assert await task == apps


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


async def test_screenshot_decodes_base64_payload_to_bytes():
    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.screenshot())
    await asyncio.sleep(0)
    action = decode(sent[0])
    payload = base64.b64encode(b"png-bytes").decode("ascii")
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result=payload)))
    assert await task == b"png-bytes"
