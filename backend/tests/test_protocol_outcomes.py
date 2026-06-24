import json

from milf.protocol import ConfirmRequest, TaskComplete, TaskFailure, decode, encode


def test_confirm_request_roundtrips_contact_id():
    message = ConfirmRequest(id="c1", summary="Send message?", lang="en", contact_id="contact-42")

    raw = encode(message)
    payload = json.loads(raw)
    decoded = decode(raw)

    assert payload["data"]["contact_id"] == "contact-42"
    assert decoded == message


def test_task_complete_roundtrips_contact_id():
    message = TaskComplete(summary="Message sent", lang="en", contact_id="contact-42")

    raw = encode(message)
    payload = json.loads(raw)
    decoded = decode(raw)

    assert payload["type"] == "TaskComplete"
    assert payload["data"]["contact_id"] == "contact-42"
    assert decoded == message


def test_task_failure_roundtrips_recovery_contact_id():
    message = TaskFailure(
        message="Could not identify the recipient",
        lang="en",
        recovery_contact_id="buyer-daughter",
    )

    raw = encode(message)
    payload = json.loads(raw)
    decoded = decode(raw)

    assert payload["type"] == "TaskFailure"
    assert payload["data"]["recovery_contact_id"] == "buyer-daughter"
    assert decoded == message


def test_task_failure_roundtrips_null_recovery_contact_id():
    message = TaskFailure(
        message="Could not identify the recipient",
        lang="en",
        recovery_contact_id=None,
    )

    raw = encode(message)
    payload = json.loads(raw)

    assert payload["data"]["recovery_contact_id"] is None
    assert decode(raw) == message
