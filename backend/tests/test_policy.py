import pytest

from milf.policy import ConfirmationPolicy


class Clock:
    def __init__(self):
        self.now = 100.0

    def __call__(self):
        return self.now


def test_get_ui_tree_allowed_before_confirmation():
    policy = ConfirmationPolicy()
    policy.require_allowed("get_ui_tree", {})


@pytest.mark.parametrize(
    "action_name",
    ["tap", "input_text", "press_button", "start_app", "screenshot", "swipe"],
)
def test_sensitive_actions_blocked_before_confirmation(action_name):
    policy = ConfirmationPolicy()
    with pytest.raises(PermissionError, match=action_name):
        policy.require_allowed(action_name, {})


def test_sensitive_action_allowed_after_approval():
    policy = ConfirmationPolicy()
    policy.record_approval("Call Wei now?", "en")
    policy.require_allowed("tap", {"x": 1, "y": 2})


def test_sensitive_action_blocked_after_freshness_window_expires():
    clock = Clock()
    policy = ConfirmationPolicy(freshness_seconds=5, clock=clock)
    policy.record_approval("Call Wei now?", "en")
    clock.now += 6

    with pytest.raises(PermissionError, match="tap"):
        policy.require_allowed("tap", {"x": 1, "y": 2})
