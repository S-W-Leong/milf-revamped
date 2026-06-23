import json
from typing import Any

from pydantic import BaseModel


class Action(BaseModel):
    id: str
    name: str
    args: dict


class ActionResult(BaseModel):
    id: str
    ok: bool
    result: Any = None
    error: str | None = None


class Narration(BaseModel):
    text: str
    lang: str


class ConfirmRequest(BaseModel):
    id: str
    summary: str
    lang: str


class ConfirmResponse(BaseModel):
    id: str
    approved: bool


class Audio(BaseModel):
    goal_audio_b64: str
    lang: str


_MESSAGE_TYPES: dict[str, type[BaseModel]] = {
    cls.__name__: cls
    for cls in (
        Action,
        ActionResult,
        Narration,
        ConfirmRequest,
        ConfirmResponse,
        Audio,
    )
}


def encode(msg: BaseModel) -> str:
    return json.dumps({"type": msg.__class__.__name__, "data": msg.model_dump()})


def decode(raw: str) -> BaseModel:
    envelope = json.loads(raw)
    msg_type = envelope.get("type")
    model = _MESSAGE_TYPES.get(msg_type)
    if model is None:
        raise ValueError(f"Unknown message type: {msg_type}")
    return model.model_validate(envelope.get("data", {}))
