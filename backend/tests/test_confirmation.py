from milf.confirmation import build_confirmation_tool


class FakePolicy:
    def __init__(self):
        self.approvals = []
        self.denials = []

    def record_approval(self, summary, lang):
        self.approvals.append((summary, lang))

    def record_denial(self, summary, lang):
        self.denials.append((summary, lang))


class FakeConn:
    def __init__(self, approved):
        self.approved = approved
        self.calls = []

    async def request_confirmation(self, summary, lang):
        self.calls.append((summary, lang))
        return self.approved


async def test_tool_proceeds_when_approved():
    conn = FakeConn(True)
    policy = FakePolicy()
    tool = build_confirmation_tool(conn, "en", policy)
    fn = tool["confirm_action"]["function"]
    out = await fn(summary="Call Wei now?", ctx=None)
    assert "proceed" in out.lower()
    assert conn.calls == [("Call Wei now?", "en")]
    assert policy.approvals == [("Call Wei now?", "en")]


async def test_tool_stops_when_denied():
    conn = FakeConn(False)
    policy = FakePolicy()
    fn = build_confirmation_tool(conn, "ms", policy)["confirm_action"]["function"]
    out = await fn(summary="Bayar bil?", ctx=None)
    assert "stop" in out.lower() or "do not" in out.lower()
    assert policy.approvals == []
    assert policy.denials == [("Bayar bil?", "ms")]
