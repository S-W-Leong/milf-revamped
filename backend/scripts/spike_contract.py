"""Print the real MobileRun surface we depend on."""

from __future__ import annotations

import inspect

from mobilerun.tools import DeviceDriver

PRINT_METHODS = [
    "connect",
    "ensure_connected",
    "tap",
    "swipe",
    "drag",
    "input_coordinate_size",
    "input_text",
    "press_button",
    "press_key_code",
    "start_app",
    "stop_app",
    "install_app",
    "uninstall_app",
    "list_packages",
    "get_apps",
    "screenshot",
    "get_ui_tree",
    "get_date",
]


def main() -> None:
    print("== DeviceDriver import ==")
    print(f"from mobilerun.tools import DeviceDriver")
    print(f"resolved: {DeviceDriver.__module__}.{DeviceDriver.__name__}")
    print()
    print("== DeviceDriver methods ==")
    for name in PRINT_METHODS:
        member = getattr(DeviceDriver, name, None)
        if member is None:
            print(f"{name}: MISSING")
            continue
        try:
            print(f"{name}{inspect.signature(member)}")
        except (TypeError, ValueError):
            print(f"{name}: <no signature>")
    print()
    print("== supported attr ==")
    print("supported:", getattr(DeviceDriver, "supported", "MISSING"))


if __name__ == "__main__":
    main()
