import asyncio

from milf.connection import AppConnection
from milf.protocol import ActionResult, ConfirmResponse, decode, encode


async def test_send_action_resolves_on_result():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    task = asyncio.create_task(conn.send_action("tap", {"x": 1, "y": 2}))
    await asyncio.sleep(0)
    action = decode(sent[0])
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result="done")))
    res = await task
    assert res.ok and res.result == "done"


async def test_request_confirmation_returns_decision():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    task = asyncio.create_task(conn.request_confirmation("Call Wei now?", "en"))
    await asyncio.sleep(0)
    req = decode(sent[0])
    conn.on_message(encode(ConfirmResponse(id=req.id, approved=True)))
    assert await task is True


async def test_concurrent_actions_resolve_by_id_out_of_order():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    first = asyncio.create_task(conn.send_action("tap", {"x": 1, "y": 2}))
    second = asyncio.create_task(conn.send_action("tap", {"x": 3, "y": 4}))
    await asyncio.sleep(0)
    first_action = decode(sent[0])
    second_action = decode(sent[1])

    conn.on_message(encode(ActionResult(id=second_action.id, ok=True, result="second")))
    conn.on_message(encode(ActionResult(id=first_action.id, ok=True, result="first")))

    assert (await first).result == "first"
    assert (await second).result == "second"


async def test_wrong_response_type_does_not_resolve_pending_action():
    sent = []
    conn = AppConnection(send=lambda raw: sent.append(raw) or asyncio.sleep(0))
    task = asyncio.create_task(conn.send_action("tap", {"x": 1, "y": 2}))
    await asyncio.sleep(0)
    action = decode(sent[0])
    conn.on_message(encode(ConfirmResponse(id=action.id, approved=True)))
    await asyncio.sleep(0)

    assert not task.done()

    conn.on_message(encode(ActionResult(id=action.id, ok=True, result="done")))
    assert (await task).result == "done"
