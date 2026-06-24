import pytest

from milf.confirmation import ConfirmationDeclined, build_confirmation_tool


class FakeConn:
    def __init__(self, approved):
        self.approved = approved
        self.calls = []

    async def request_confirmation(self, summary, lang, contact_id=None):
        self.calls.append((summary, lang, contact_id))
        return self.approved


async def test_tool_proceeds_when_approved():
    conn = FakeConn(True)
    tool = build_confirmation_tool(conn, "en")
    fn = tool["confirm_action"]["function"]
    out = await fn(summary="Call Wei now?", ctx=None)
    assert "proceed" in out.lower()
    assert conn.calls == [("Call Wei now?", "en", None)]


async def test_tool_passes_contact_id_to_confirmation_request():
    conn = FakeConn(True)
    fn = build_confirmation_tool(
        conn,
        "en",
        contact_id="wei-grandson",
    )["confirm_action"]["function"]

    await fn(summary="Call Wei now?", ctx=None)

    assert conn.calls == [("Call Wei now?", "en", "wei-grandson")]


async def test_tool_raises_when_denied():
    conn = FakeConn(False)
    fn = build_confirmation_tool(conn, "zh")["confirm_action"]["function"]

    with pytest.raises(ConfirmationDeclined):
        await fn(summary="Bayar bil?", ctx=None)
