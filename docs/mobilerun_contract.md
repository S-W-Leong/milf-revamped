# MobileRun Contract Spike

Task 1 pinned the MobileRun library surface for the MILF backend.

## Environment

- `mobilerun`: `0.6.7`
- `mobilerun-sdk`: `5.0.0`
- `mobilerun-core-cli`: `0.2.0`
- Spike command: `cd backend && ../.venv/bin/python scripts/spike_contract.py`

## Working Import Paths

Preferred public imports:

```python
from mobilerun import AgentConfig, DeviceConfig, MobileAgent, MobileConfig
from mobilerun.agent.droid.events import ResultEvent
from mobilerun.agent.executor.events import ExecutorActionEvent, ExecutorActionResultEvent
from mobilerun.agent.manager.events import ManagerPlanDetailsEvent
from mobilerun.tools import DeviceDriver
```

Resolved class modules:

- `DeviceDriver`: `mobilerun_core_cli.driver.base.DeviceDriver`
- `MobileAgent`: `mobilerun.agent.droid.droid_agent.MobileAgent`
- `MobileConfig`: `mobilerun.config_manager.config_manager.MobileConfig`
- `AgentConfig`: `mobilerun.config_manager.config_manager.AgentConfig`
- `DeviceConfig`: `mobilerun.config_manager.config_manager.DeviceConfig`
- `ResultEvent`: `mobilerun.agent.droid.events.ResultEvent`
- `ExecutorActionEvent`: `mobilerun.agent.executor.events.ExecutorActionEvent`
- `ExecutorActionResultEvent`: `mobilerun.agent.executor.events.ExecutorActionResultEvent`
- `ManagerPlanDetailsEvent`: `mobilerun.agent.manager.events.ManagerPlanDetailsEvent`

Other event modules discovered:

- `mobilerun.agent.droid.events`
- `mobilerun.agent.executor.events`
- `mobilerun.agent.manager.events`
- `mobilerun.agent.fast_agent.events`
- `mobilerun.agent.common.events`

## DeviceDriver Method Signatures

Output from `backend/scripts/spike_contract.py`:

```text
== DeviceDriver import ==
from mobilerun.tools import DeviceDriver
resolved: mobilerun_core_cli.driver.base.DeviceDriver

== DeviceDriver methods ==
connect(self) -> 'None'
ensure_connected(self) -> 'None'
tap(self, x: 'int', y: 'int') -> 'None'
swipe(self, x1: 'int', y1: 'int', x2: 'int', y2: 'int', duration_ms: 'float' = 1000) -> 'None'
drag(self, x1: 'int', y1: 'int', x2: 'int', y2: 'int', duration: 'float' = 3.0) -> 'None'
input_coordinate_size(self, screenshot_width: 'int', screenshot_height: 'int') -> 'tuple[int, int]'
input_text(self, text: 'str', clear: 'bool' = False, stealth: 'bool' = False, wpm: 'int' = 0) -> 'bool'
press_button(self, button: 'str') -> 'None'
press_key_code(self, key_code: 'int') -> 'None'
start_app(self, package: 'str', activity: 'Optional[str]' = None) -> 'str'
stop_app(self, package: 'str') -> 'str'
install_app(self, path: 'str', **kwargs) -> 'str'
uninstall_app(self, package: 'str') -> 'str'
list_packages(self, include_system: 'bool' = False) -> 'List[str]'
get_apps(self, include_system: 'bool' = True) -> 'List[Dict[str, str]]'
screenshot(self, hide_overlay: 'bool' = True) -> 'bytes'
get_ui_tree(self) -> 'Dict[str, Any]'
get_date(self) -> 'str'

== supported attr ==
supported: set()
```

## Notes

- The plan's initial sample import `from mobilerun.tools.base import DeviceDriver` is stale for this installed version.
- `DeviceDriver.supported` defaults to an empty `set()`; concrete drivers should override it with supported method names.
