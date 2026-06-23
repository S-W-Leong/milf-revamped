import pytest

from milf.protocol import Action, ConfirmResponse, ProtocolDecodeError, decode, encode


def test_action_roundtrip():
    a = Action(id="1", name="tap", args={"x": 10, "y": 20})
    raw = encode(a)
    back = decode(raw)
    assert isinstance(back, Action)
    assert back == a


def test_confirm_response_roundtrip():
    c = ConfirmResponse(id="c1", approved=True)
    assert decode(encode(c)) == c


def test_decode_unknown_type_raises():
    with pytest.raises(ProtocolDecodeError, match="Unknown message type: Nope"):
        decode('{"type": "Nope", "data": {}}')


def test_decode_malformed_json_raises_protocol_decode_error():
    with pytest.raises(ProtocolDecodeError, match="Malformed JSON"):
        decode("{")


def test_decode_schema_error_raises_protocol_decode_error():
    with pytest.raises(ProtocolDecodeError, match="Invalid Audio message"):
        decode('{"type": "Audio", "data": {"lang": "en"}}')
