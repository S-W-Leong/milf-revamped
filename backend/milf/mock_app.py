from typing import Any

from milf.protocol import (
    Action,
    ActionResult,
    ConfirmRequest,
    ConfirmResponse,
    Narration,
    decode,
    encode,
)


class MockApp:
    def __init__(self, scripted: dict[str, Any], auto_approve: bool = True):
        self._scripted = scripted
        self._auto_approve = auto_approve

    async def handle(self, raw: str) -> str | None:
        msg = decode(raw)
        if isinstance(msg, Action):
            if msg.name not in self._scripted:
                return encode(
                    ActionResult(
                        id=msg.id,
                        ok=False,
                        error=f"Unscripted action: {msg.name}",
                    )
                )
            return encode(
                ActionResult(id=msg.id, ok=True, result=self._scripted.get(msg.name))
            )
        if isinstance(msg, ConfirmRequest):
            return encode(ConfirmResponse(id=msg.id, approved=self._auto_approve))
        if isinstance(msg, Narration):
            return None
        return None
