import base64
from typing import Any, Optional

from mobilerun.tools import DeviceDriver

from milf.connection import AppConnection


class WebSocketDriver(DeviceDriver):
    supported = {
        "tap",
        "swipe",
        "input_text",
        "press_button",
        "start_app",
        "screenshot",
        "get_ui_tree",
    }
    supported_buttons = {"back", "home", "enter"}

    def __init__(self, connection: AppConnection):
        self._connection = connection

    async def connect(self) -> None:
        return None

    async def ensure_connected(self) -> None:
        return None

    async def tap(self, x: int, y: int) -> None:
        await self._send_supported("tap", {"x": x, "y": y})

    async def swipe(
        self,
        x1: int,
        y1: int,
        x2: int,
        y2: int,
        duration_ms: float = 1000,
    ) -> None:
        await self._send_supported(
            "swipe",
            {
                "x1": x1,
                "y1": y1,
                "x2": x2,
                "y2": y2,
                "duration_ms": duration_ms,
            },
        )

    async def input_text(
        self,
        text: str,
        clear: bool = False,
        stealth: bool = False,
        wpm: int = 0,
    ) -> bool:
        return await self._send_supported(
            "input_text",
            {"text": text, "clear": clear, "stealth": stealth, "wpm": wpm},
        )

    async def press_button(self, button: str) -> None:
        await self._send_supported("press_button", {"button": button})

    async def input_coordinate_size(
        self,
        screenshot_width: int,
        screenshot_height: int,
    ) -> tuple[int, int]:
        return screenshot_width, screenshot_height

    async def start_app(self, package: str, activity: Optional[str] = None) -> str:
        return await self._send_supported(
            "start_app", {"package": package, "activity": activity}
        )

    async def screenshot(self, hide_overlay: bool = True) -> bytes:
        payload = await self._send_supported("screenshot", {"hide_overlay": hide_overlay})
        if isinstance(payload, str):
            return base64.b64decode(payload)
        if isinstance(payload, bytes):
            return payload
        raise TypeError("screenshot action returned non-bytes payload")

    async def get_ui_tree(self) -> dict[str, Any]:
        return await self._send_supported("get_ui_tree", {})

    async def press_key_code(self, key_code: int) -> None:
        raise NotImplementedError

    async def drag(
        self,
        x1: int,
        y1: int,
        x2: int,
        y2: int,
        duration: float = 3.0,
    ) -> None:
        raise NotImplementedError

    async def install_app(self, path: str, **kwargs) -> str:
        raise NotImplementedError

    async def stop_app(self, package: str) -> str:
        raise NotImplementedError

    async def uninstall_app(self, package: str) -> str:
        raise NotImplementedError

    async def get_apps(self, include_system: bool = True) -> list[dict[str, str]]:
        raise NotImplementedError

    async def list_packages(self, include_system: bool = False) -> list[str]:
        raise NotImplementedError

    async def get_date(self) -> str:
        raise NotImplementedError

    async def _send_supported(self, name: str, args: dict[str, Any]) -> Any:
        result = await self._connection.send_action(name, args)
        if not result.ok:
            message = result.error or f"Action failed: {name}"
            raise RuntimeError(message)
        return result.result
