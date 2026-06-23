from milf.protocol import Action, ConfirmResponse, decode, encode


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
    import pytest

    with pytest.raises(ValueError):
        decode('{"type": "Nope", "data": {}}')
