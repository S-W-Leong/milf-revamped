from types import SimpleNamespace

from milf.intent_router import IntentRoute
from milf.session import MILFSession


def test_record_mobile_run_result_sets_last_contact_id():
    session = MILFSession()
    route = IntentRoute(
        kind="execute",
        normalized_intent="Send hello to Wei on WhatsApp.",
        contact_id="wei-grandson",
        requires_confirmation=True,
    )

    session.record_mobile_run_result(
        route=route,
        result=SimpleNamespace(success=True, reason="ok"),
    )

    assert session.last_contact_id == "wei-grandson"
    assert session.last_mobile_run is not None
    assert session.last_mobile_run.contact_id == "wei-grandson"


def test_perceive_route_records_recent_input_without_mobile_run_state():
    session = MILFSession()
    route = IntentRoute(
        kind="perceive",
        normalized_intent="Describe the current screen.",
    )

    session.record_user_route("what's on my screen?", route)

    assert session.recent_user_inputs == ["what's on my screen?"]
    assert session.pending_clarification is None
    assert session.last_normalized_intent is None
    assert session.last_mobile_run is None
