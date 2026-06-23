from types import SimpleNamespace

import pytest

from milf.harness_support import run_n


class ScriptedHandler:
    def __init__(self, conn, succeed):
        self._conn = conn
        self._succeed = succeed

    async def stream_events(self):
        await self._conn.send_action("get_ui_tree", {})
        await self._conn.request_confirmation("Call Wei now?", "en")
        if False:
            yield None

    def __await__(self):
        async def _r():
            return SimpleNamespace(success=self._succeed, reason="x")

        return _r().__await__()


@pytest.mark.asyncio
async def test_run_n_reports_full_success():
    def factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: ScriptedHandler(driver._connection, True))

    ratio = await run_n(
        5,
        scripted={"get_ui_tree": {"nodes": []}},
        transcript="see grandson",
        agent_factory=factory,
    )
    assert ratio == 1.0
