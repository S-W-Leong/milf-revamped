from types import SimpleNamespace

from milf.clarification import build_clarification_tool


class FakeConn:
    def __init__(self):
        self.narrations = []
        self.completions = []

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))


async def test_request_clarification_returns_successful_action_result():
    conn = FakeConn()
    state = SimpleNamespace(question=None)
    tool = build_clarification_tool(conn, "en", state)["request_clarification"][
        "function"
    ]

    result = await tool("Which WhatsApp contact is him?", ctx=None)

    assert result.success is True
    assert "Clarification requested" in result.summary
    assert state.question == "Which WhatsApp contact is him?"
    assert conn.narrations == [("Which WhatsApp contact is him?", "en")]
    assert conn.completions == []
