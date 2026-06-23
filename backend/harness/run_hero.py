import asyncio
import sys
from typing import Any

from milf.agent_runner import build_agent
from milf.harness_support import run_n
from milf.ws_driver import WebSocketDriver


def _real_agent_factory(
    goal: str,
    driver: WebSocketDriver,
    custom_tools: dict[str, Any],
) -> Any:
    return build_agent(goal, driver, custom_tools)


async def _main(n: int) -> None:
    ratio = await run_n(
        n,
        scripted={"get_ui_tree": {"nodes": []}, "tap": None, "start_app": None},
        transcript="I want to see my grandson",
        agent_factory=_real_agent_factory,
    )
    print(f"Hero flow success: {ratio:.0%} over {n} runs (target >= 90%)")


if __name__ == "__main__":
    asyncio.run(_main(int(sys.argv[1]) if len(sys.argv) > 1 else 10))
