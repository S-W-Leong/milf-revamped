# MILF Narration Policy

MILF narrates semantic progress, not phone mechanics. The senior should hear
what the agent is trying to achieve in human terms, while implementation details
such as screenshots, accessibility trees, selectors, coordinates, taps, and
swipes stay silent.

The backend policy lives in `backend/milf/narration.py`. It consumes MobileRun
events from `handler.stream_events()` and sends only approved lines over the
websocket as `Narration` messages.

## Principle

Narrate when the senior's mental model changes:

- a new user-level subgoal starts;
- the agent opens or searches for something;
- the agent finds, selects, sends, or calls something meaningful;
- the agent needs clarification;
- the agent reaches confirmation, completion, or safe failure.

Do not narrate internal reasoning, perception, or low-level gestures. The phone
should sound calm and helpful, not like a debug console.

## Event Policy

| Source event | Policy | Example output |
| --- | --- | --- |
| `ManagerPlanDetailsEvent` | Narrate `subgoal` when there is no final `answer`. | `Open Wei's chat` -> `I'm opening Wei's chat.` |
| `ManagerPlanEvent` | Narrate `current_subgoal` when there is no final `answer`. | `Find Wei in WhatsApp` -> `I'm finding Wei in WhatsApp.` |
| `ExecutorActionEvent` | Narrate user-worthy `description`. | `Opening WhatsApp` -> `I'm opening WhatsApp.` |
| `ExecutorActionResultEvent` | Narrate successful, meaningful summaries. | `Found Wei's chat` -> `I found Wei's chat.` |
| `FastAgentToolCallEvent` | Narrate supported fast-path tool calls from XML. | `start_app com.whatsapp` -> `I'm opening WhatsApp.` |
| `FastAgentResponseEvent` | Do not narrate thoughts. Only narrates if a future version adds a `description`. | Silent today. |
| `ToolExecutionEvent` for `request_clarification` | Stop the run and surface the clarification question. | `Which Wei do you mean?` |
| `ConfirmRequest` | Always spoken by the Android client. | `Calling Wei, your grandson?` |
| `TaskComplete` / `TaskFailure` | Spoken by the Android client as terminal state. | `Done.` / safe failure copy. |
| `ScreenshotEvent` | Silent. | Internal perception. |
| `RecordUIStateEvent` | Silent. | Internal perception. |
| `ManagerResponseEvent` / `ExecutorResponseEvent` | Silent. | Raw LLM response. |
| Unknown events | Silent. | Future events fail closed. |

## Wording Rules

Progress descriptions are normalized into short first-person lines:

- `Open` / `Opening` -> `I'm opening ...`
- `Find` / `Locate` -> `I'm finding ...`
- `Check` -> `I'm checking ...`
- `Search` -> `I'm searching ...`
- `Send` -> `I'm sending ...`
- `Call` -> `I'm calling ...`
- `Select` / `Choose` -> `I'm selecting ...`
- `Wait` -> `I'm waiting ...`

Successful result summaries use a smaller set of completed-action phrases:

- `Found ...` -> `I found ...`
- `Opened ...` -> `I opened ...`
- `Selected ...` -> `I selected ...`
- `Sent ...` -> `I sent ...`
- `Called ...` -> `I called ...`

Any repeated line is suppressed, so equivalent consecutive events do not cause
stuttering.

## Fast-Path Tool Calls

Fast-path MobileRun mode emits tool-call events rather than executor
descriptions. MILF currently narrates these supported calls:

| Tool call | Narration |
| --- | --- |
| `start_app` with known packages such as WhatsApp, YouTube, YouTube Music, Spotify, or Grab | `I'm opening <app>.` |
| `press_button` with `home` | `I'm going home.` |
| `press_button` with `back` | `I'm going back.` |
| `input_text` | `I'm entering the text.` |

Unknown tool calls stay silent until we can map them to safe senior-facing
language.

## Suppression Rules

The policy suppresses descriptions that start with raw gestures:

- tap / tapping
- click / clicking
- swipe / swiping
- scroll / scrolling
- press / pressing / long press
- drag / dragging
- type / typing
- input / enter text

It also suppresses lines containing internal implementation terms:

- coordinate / coordinates
- a11y
- accessibility tree / UI tree
- screenshot
- XPath
- selector
- element id

## Android Delivery

The backend sends narration over the websocket as `Narration(text, lang)`. The
Android session controller stores the latest narration and calls TTS. Confirmation
requests, task completion, and task failure are separate protocol messages and
are also spoken by Android.

If the agent acts silently, check where the silence begins:

- `Logs > Narration` remains empty: the backend policy emitted no narration.
- `Logs > Narration` has text but no audio: Android TTS or device audio is the
  likely issue.
- Initial acknowledgement is audible but progress is silent: MobileRun events
  are probably being filtered or are unsupported by the current policy.

## Testing

Narration behavior is covered in `backend/tests/test_narration.py`.

Run:

```bash
cd backend && ../.venv/bin/python -m pytest tests/test_narration.py -q
```

For full backend verification, run:

```bash
cd backend && ../.venv/bin/python -m pytest -q
```
