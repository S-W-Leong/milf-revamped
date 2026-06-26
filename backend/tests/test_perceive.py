from types import SimpleNamespace

from websockets.exceptions import ConnectionClosedOK

from milf.perceive import run_perceive


class FakeConn:
    def __init__(self):
        self.actions = []
        self.narrations = []
        self.completions = []
        self.failures = []

    async def send_action(self, name, args):
        self.actions.append((name, args))
        return SimpleNamespace(ok=True, result=None, error=None)

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang):
        self.failures.append((message, lang))


class FakeDriver:
    instances = []

    def __init__(self, connection):
        self.connection = connection
        self.screenshot_calls = 0
        self.ui_tree_calls = 0
        self.tap_calls = []
        FakeDriver.instances.append(self)

    async def screenshot(self):
        self.screenshot_calls += 1
        return b"screen"

    async def get_ui_tree(self):
        self.ui_tree_calls += 1
        return {"a11y_tree": [], "phone_state": {}, "device_context": {}}

    async def tap(self, x, y):
        self.tap_calls.append((x, y))


class FailingDriver(FakeDriver):
    async def screenshot(self):
        self.screenshot_calls += 1
        raise RuntimeError("screenshot failed")


class CleanCloseDriver(FakeDriver):
    async def screenshot(self):
        self.screenshot_calls += 1
        raise Exception("Failed to get state") from ConnectionClosedOK(None, None)


class FakePerceiveAgent:
    def __init__(self, answer="You are on the home screen."):
        self.answer = answer
        self.describe_calls = []

    async def describe(self, query, screenshot, ui_tree, lang):
        self.describe_calls.append((query, screenshot, ui_tree, lang))
        return self.answer


async def test_run_perceive_reads_screen_once_and_reports_answer():
    FakeDriver.instances = []
    conn = FakeConn()
    agent = FakePerceiveAgent()

    result = await run_perceive(
        conn,
        query="What is on screen?",
        lang="en",
        agent=agent,
        driver_factory=FakeDriver,
    )

    driver = FakeDriver.instances[0]
    assert driver.screenshot_calls == 1
    assert driver.ui_tree_calls == 1
    assert driver.tap_calls == []
    assert agent.describe_calls == [
        (
            "What is on screen?",
            b"screen",
            {"a11y_tree": [], "phone_state": {}, "device_context": {}},
            "en",
        )
    ]
    assert conn.narrations == [("You are on the home screen.", "en")]
    assert conn.completions == [("You are on the home screen.", "en", None)]
    assert conn.failures == []
    assert result.success is True
    assert result.reason == "perceive"


async def test_run_perceive_uses_safe_failure_on_driver_error():
    conn = FakeConn()

    result = await run_perceive(
        conn,
        query="Read this",
        lang="en",
        agent=FakePerceiveAgent(),
        driver_factory=FailingDriver,
    )

    assert result.success is False
    assert result.reason == "perceive_error"
    assert conn.narrations == []
    assert conn.completions == []
    assert conn.failures == [
        ("I'm having a little trouble with that. Please try again.", "en")
    ]


async def test_run_perceive_treats_clean_client_close_as_closed_session():
    conn = FakeConn()

    result = await run_perceive(
        conn,
        query="Read this",
        lang="en",
        agent=FakePerceiveAgent(),
        driver_factory=CleanCloseDriver,
    )

    assert result.success is False
    assert result.reason == "client_closed"
    assert conn.narrations == []
    assert conn.completions == []
    assert conn.failures == []
