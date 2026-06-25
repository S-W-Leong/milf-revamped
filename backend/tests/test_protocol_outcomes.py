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


def test_task_failure_has_no_recovery_contact_id():
    message = TaskFailure(message="Could not identify the recipient", lang="en")

    raw = encode(message)
    payload = json.loads(raw)
    decoded = decode(raw)

    assert payload["type"] == "TaskFailure"
    assert "recovery_contact_id" not in payload["data"]
    assert decoded == message
