# MILF Intent Orchestration Architecture

```mermaid
flowchart TD
    User["User utterance<br/>(audio or text)"]
    STT["STT adapter<br/>(audio only)"]
    Session["MILFSession<br/>recent inputs<br/>pending clarification<br/>last contact<br/>last MobileRun result"]
    Intent["Intent model router<br/>non-actuating classifier"]

    Reply["Reply directly<br/>casual chat"]
    Clarify["Ask one clarification<br/>store pending question"]
    Execute["Executable phone task<br/>normalized intent + contact_id"]

    Runner["MILF agent runner<br/>build goal + confirmation tools"]
    MobileAgent["MobileRun MobileAgent"]
    Manager["Manager / planner agent<br/>subgoals + recovery"]
    Executor["Executor agent<br/>tap, type, swipe, tool calls"]
    Phone["Android app UI<br/>WhatsApp, Phone, etc."]
    Result["Result or blocker<br/>success, failure, confirmation declined"]

    User --> STT
    User --> Session
    STT --> Session
    Session --> Intent
    Intent --> Reply
    Intent --> Clarify
    Intent --> Execute

    Reply --> Session
    Clarify --> Session
    Execute --> Runner
    Runner --> MobileAgent
    MobileAgent --> Manager
    Manager --> Executor
    Executor --> Phone
    Phone --> Executor
    Executor --> Manager
    Manager --> Result
    Result --> Session

    Session -. "context for short follow-ups<br/>e.g. 'Wei' after 'send hello'" .-> Intent
    Result -. "blocker context for next turn" .-> Intent
```

## Pattern

MILF owns the conversational/session layer. Every user input goes through the intent model first, with `MILFSession` summarized into the prompt. That router decides whether to reply, clarify, refuse, or hand off an executable phone task.

MobileRun only receives concrete phone goals. Its manager/planner and executor are used for UI reasoning and device actions, while MILF records the outcome back into session state for the next turn.

This keeps casual speech and ambiguity out of MobileRun's action loop, while still letting MobileRun do the hard phone automation once the task is clear.
