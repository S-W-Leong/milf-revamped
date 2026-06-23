import asyncio
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from uuid import uuid4

from pydantic import BaseModel

from milf.protocol import (
    Action,
    ActionResult,
    ConfirmRequest,
    ConfirmResponse,
    Narration,
    decode,
    encode,
)


@dataclass
class _Pending:
    expected_type: type[BaseModel]
    future: asyncio.Future


class AppConnection:
    def __init__(self, send: Callable[[str], Awaitable[None]]):
        self._send = send
        self._pending: dict[str, _Pending] = {}

    async def send_action(self, name: str, args: dict) -> ActionResult:
        action = Action(id=self._new_id(), name=name, args=args)
        future = self._create_pending(action.id)
        try:
            await self._send(encode(action))
            return await future
        finally:
            self._pending.pop(action.id, None)

    async def request_confirmation(self, summary: str, lang: str) -> bool:
        request = ConfirmRequest(id=self._new_id(), summary=summary, lang=lang)
        future = self._create_pending(request.id, ConfirmResponse)
        try:
            await self._send(encode(request))
            response = await future
            return response.approved
        finally:
            self._pending.pop(request.id, None)

    async def send_narration(self, text: str, lang: str) -> None:
        await self._send(encode(Narration(text=text, lang=lang)))

    def on_message(self, raw: str) -> None:
        msg = decode(raw)
        if not isinstance(msg, ActionResult | ConfirmResponse):
            return

        pending = self._pending.get(msg.id)
        if pending is None:
            return
        if not isinstance(msg, pending.expected_type):
            return

        self._pending.pop(msg.id, None)
        if not pending.future.done():
            pending.future.set_result(msg)

    def _create_pending(
        self, id: str, expected_type: type[BaseModel] = ActionResult
    ) -> asyncio.Future:
        future = asyncio.get_running_loop().create_future()
        self._pending[id] = _Pending(expected_type=expected_type, future=future)
        return future

    def _new_id(self) -> str:
        return uuid4().hex
