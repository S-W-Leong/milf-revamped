import asyncio
import logging
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
    TaskComplete,
    TaskFailure,
    decode,
    encode,
)

logger = logging.getLogger(__name__)
DEFAULT_RESPONSE_TIMEOUT_S = 30.0


@dataclass
class _Pending:
    expected_type: type[BaseModel]
    future: asyncio.Future


class AppConnection:
    def __init__(
        self,
        send: Callable[[str], Awaitable[None]],
        response_timeout_s: float = DEFAULT_RESPONSE_TIMEOUT_S,
    ):
        self._send = send
        self._response_timeout_s = response_timeout_s
        self._pending: dict[str, _Pending] = {}

    async def send_action(self, name: str, args: dict) -> ActionResult:
        action = Action(id=self._new_id(), name=name, args=args)
        future = self._create_pending(action.id)
        try:
            logger.debug(
                "Sending app action.",
                extra={"action_id": action.id, "action_name": name},
            )
            await self._send(encode(action))
            return await self._wait_for_response(
                future,
                timeout_message=f"Timed out waiting for action {name}",
                pending_id=action.id,
            )
        finally:
            self._pending.pop(action.id, None)

    async def request_confirmation(
        self, summary: str, lang: str, contact_id: str | None = None
    ) -> bool:
        request = ConfirmRequest(
            id=self._new_id(), summary=summary, lang=lang, contact_id=contact_id
        )
        future = self._create_pending(request.id, ConfirmResponse)
        try:
            logger.debug(
                "Sending confirmation request.",
                extra={"confirm_id": request.id, "contact_id": contact_id},
            )
            await self._send(encode(request))
            response = await self._wait_for_response(
                future,
                timeout_message="Timed out waiting for confirmation",
                pending_id=request.id,
            )
            return response.approved
        finally:
            self._pending.pop(request.id, None)

    async def send_narration(self, text: str, lang: str) -> None:
        await self._send(encode(Narration(text=text, lang=lang)))

    async def send_task_complete(
        self, summary: str, lang: str, contact_id: str | None = None
    ) -> None:
        await self._send(
            encode(TaskComplete(summary=summary, lang=lang, contact_id=contact_id))
        )

    async def send_task_failure(self, message: str, lang: str) -> None:
        await self._send(
            encode(
                TaskFailure(
                    message=message,
                    lang=lang,
                )
            )
        )

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

    async def _wait_for_response(
        self,
        future: asyncio.Future,
        timeout_message: str,
        pending_id: str,
    ) -> BaseModel:
        try:
            return await asyncio.wait_for(future, timeout=self._response_timeout_s)
        except TimeoutError:
            logger.warning(
                timeout_message,
                extra={
                    "pending_id": pending_id,
                    "response_timeout_s": self._response_timeout_s,
                },
            )
            raise TimeoutError(timeout_message)

    def _new_id(self) -> str:
        return uuid4().hex
