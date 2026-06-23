from milf.confirmation import build_confirmation_tool


class FakeConn:
    def __init__(self, approved):
        self.approved = approved
        self.calls = []

    async def request_confirmation(self, summary, lang):
        self.calls.append((summary, lang))
        return self.approved


async def test_tool_proceeds_when_approved():
    conn = FakeConn(True)
    tool = build_confirmation_tool(conn, "en")
    fn = tool["confirm_action"]["function"]
    out = await fn(summary="Call Wei now?", ctx=None)
    assert "proceed" in out.lower()
    assert conn.calls == [("Call Wei now?", "en")]


async def test_tool_stops_when_denied():
    conn = FakeConn(False)
    fn = build_confirmation_tool(conn, "ms")["confirm_action"]["function"]
    out = await fn(summary="Bayar bil?", ctx=None)
    assert "stop" in out.lower() or "do not" in out.lower()
