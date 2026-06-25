from milf.protocol import Audio, Action, ConfirmResponse, TextGoal, decode, encode


def test_action_roundtrip():
    a = Action(id="1", name="tap", args={"x": 10, "y": 20})
    raw = encode(a)
    back = decode(raw)
    assert isinstance(back, Action)
    assert back == a


def test_confirm_response_roundtrip():
    c = ConfirmResponse(id="c1", approved=True)
    assert decode(encode(c)) == c


def test_text_goal_round_trips():
    raw = encode(TextGoal(goal_text="I want to see my grandson", lang="en"))

    assert decode(raw) == TextGoal(
        goal_text="I want to see my grandson",
        lang="en",
    )


def test_text_goal_round_trips_session_id():
    raw = encode(
        TextGoal(
            goal_text="search movie",
            lang="en",
            session_id="session-123",
            memory="Wei is my grandson.",
        )
    )

    assert decode(raw) == TextGoal(
        goal_text="search movie",
        lang="en",
        session_id="session-123",
        memory="Wei is my grandson.",
    )


def test_audio_round_trips_session_id_and_memory():
    raw = encode(
        Audio(
            goal_audio_b64="AQID",
            lang="en",
            session_id="session-123",
            memory="Use WhatsApp for Wei.",
        )
    )

    assert decode(raw) == Audio(
        goal_audio_b64="AQID",
        lang="en",
        session_id="session-123",
        memory="Use WhatsApp for Wei.",
    )


def test_decode_unknown_type_raises():
    import pytest

    with pytest.raises(ValueError):
        decode('{"type": "Nope", "data": {}}')
