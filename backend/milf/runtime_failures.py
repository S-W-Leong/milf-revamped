from __future__ import annotations

from websockets.exceptions import ConnectionClosedOK

SAFE_FAILURE_COPY = "I'm having a little trouble with that. Please try again."


def caused_by_clean_client_close(error: BaseException) -> bool:
    seen: set[int] = set()
    current: BaseException | None = error
    while current is not None and id(current) not in seen:
        if isinstance(current, ConnectionClosedOK):
            return True
        seen.add(id(current))
        current = current.__cause__ or current.__context__
    return False
