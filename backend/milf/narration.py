from typing import Any


def narration_for(event: object) -> str | None:
    name = event.__class__.__name__

    if name == "ExecutorActionEvent":
        return getattr(event, "description", None) or None
    if name == "ManagerPlanDetailsEvent":
        subgoal = getattr(event, "subgoal", None)
        if subgoal:
            return f"Next: {subgoal}"
        return None
    if name == "FastAgentResponseEvent":
        return getattr(event, "description", None) or getattr(event, "thought", None) or None

    return None


async def narrate_events(handler: Any, connection: Any, lang: str) -> Any:
    async for event in handler.stream_events():
        line = narration_for(event)
        if line is not None:
            await connection.send_narration(line, lang)

    return await handler
