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
    def __init__(
        self,
        send: Callable[[str], Awaitable[None]],
        timeout_seconds: float = 30.0,
    ):
        self._send = send
        self._timeout_seconds = timeout_seconds
        self._pending: dict[str, _Pending] = {}

    async def send_action(self, name: str, args: dict) -> ActionResult:
        action = Action(id=self._new_id(), name=name, args=args)
        future = self._create_pending(action.id)
        try:
            await self._send(encode(action))
            return await self._wait_for(
                future,
                f"send_action({name})",
                action.id,
            )
        finally:
            self._pending.pop(action.id, None)

    async def request_confirmation(self, summary: str, lang: str) -> bool:
        request = ConfirmRequest(id=self._new_id(), summary=summary, lang=lang)
        future = self._create_pending(request.id, ConfirmResponse)
        try:
            await self._send(encode(request))
            response = await self._wait_for(
                future,
                "request_confirmation",
                request.id,
            )
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

    def fail_pending(self, error: BaseException) -> None:
        pending = list(self._pending.values())
        self._pending.clear()
        for item in pending:
            if not item.future.done():
                item.future.set_exception(error)

    def _create_pending(
        self, id: str, expected_type: type[BaseModel] = ActionResult
    ) -> asyncio.Future:
        future = asyncio.get_running_loop().create_future()
        self._pending[id] = _Pending(expected_type=expected_type, future=future)
        return future

    async def _wait_for(
        self,
        future: asyncio.Future,
        operation: str,
        request_id: str,
    ) -> BaseModel:
        try:
            return await asyncio.wait_for(future, timeout=self._timeout_seconds)
        except TimeoutError as exc:
            if not future.cancelled():
                raise
            self._pending.pop(request_id, None)
            raise TimeoutError(
                f"{operation} timed out waiting for response id={request_id}"
            ) from exc

    def _new_id(self) -> str:
        return uuid4().hex
