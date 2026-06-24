# MILF UX Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the build-ready MILF UX spec as a real Android overlay state machine for the hero WhatsApp video-call flow, with senior-safe confirmation, failure recovery to the buyer, demo watch mode, and buyer-only setup.

**Architecture:** Keep the existing backend, websocket, audio, TTS, and accessibility driver as the execution core. Extract the app run logic from `MainViewModel` into a process-level `MilfSessionController` that both the buyer setup Activity and a system overlay service can use. Add small protocol extensions so the backend can send contact-aware confirmation, explicit success, and explicit failure recovery states instead of relying on generic websocket close strings.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android `WindowManager` `TYPE_APPLICATION_OVERLAY`, Android assist intent entry, Android AccessibilityService, Android TextToSpeech, OkHttp websocket, Python 3.11, Pydantic v2, pytest, JUnit.

---

## Scope

This plan implements the UX spec's v1 cut:

- Senior State A: persistent draggable floating bubble.
- Senior State B: listening overlay with waveform and large captions.
- Senior State C: contact-aware confirmation gate with face/avatar, green YES, red NO, and voice yes/no.
- Senior State D: working state with calm bubble by default and watch mode for demos.
- Senior State E: success state with spoken and visible completion.
- Senior State F: failure recovery that never blames the senior and offers buyer rescue.
- Invocation layers 1 and 2: best-effort default assist entry and reliable floating bubble.
- Buyer setup for permissions, backend URL, language, demo mode, and overlay start/stop.
- Hardcoded relationship graph for the hero flow.

This plan does not build the post-v1 ambient reassurance dashboard, default launcher, or per-action confirmation preferences UI. It adds telemetry hooks for repeated failures but no buyer dashboard.

## Implementation Assumptions

- The existing Android client is the v0 infrastructure and should be evolved, not replaced.
- `MainActivity` becomes the buyer setup and debug surface. The senior-facing experience moves to a `SeniorOverlayService`.
- The app uses a system overlay via `SYSTEM_ALERT_WINDOW` and `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`. Android documents that this permission allows overlays of that type, while also warning that very few apps should use it because it is intended for system-level interaction. MILF's accessibility helper is exactly that category for this prototype. Reference: [Manifest.permission.SYSTEM_ALERT_WINDOW](https://developer.android.com/reference/android/Manifest.permission#SYSTEM_ALERT_WINDOW).
- Android 8.0+ requires alert-window apps to use `TYPE_APPLICATION_OVERLAY` instead of older system alert window types. Reference: [Android 8.0 alert window behavior changes](https://developer.android.com/about/versions/oreo/android-8.0-changes#all-aw).
- The assist entry is best-effort. A normal Activity can handle `Intent.ACTION_ASSIST`, but OEM power-button behavior must still be tested on real Xiaomi/Redmi, Samsung, and Oppo devices. Reference: [Intent API reference](https://developer.android.com/reference/android/content/Intent) and [Assistant contextual content](https://developer.android.com/training/articles/assistant).
- The hardcoded demo graph uses debug-safe values until live demo assets are supplied:
  - Contact: `wei-grandson`, display name `Wei`, relationship `grandson`, photo resource `contact_wei_avatar`.
  - Escape contact: `buyer-daughter`, display name `Daughter`, phone `+15551234567`. This is the Android emulator reserved phone-number range and should be overridden through `local.properties` for a physical demo device.

## Clarifying Questions Before Execution

These do not block writing the plan, but they should be answered before a live-device demo build:

- What real buyer rescue phone number should replace debug value `+15551234567`?
- Do we have a real demo contact photo for Wei? If yes, add it at `android/app/src/main/res/drawable-nodpi/contact_wei.png`; otherwise use the bundled high-contrast avatar from this plan.
- Which physical device is the first target for power-button assist testing: Xiaomi/Redmi, Samsung, Oppo, or another Android model?

---

## File Structure

### Android Client

- Modify: `android/app/build.gradle.kts` - enable BuildConfig and add icon/test dependencies.
- Modify: `android/app/src/main/AndroidManifest.xml` - add overlay/call permissions, application class, assist entry Activity, and overlay service.
- Create: `android/app/src/main/java/ai/milf/client/MilfApplication.kt` - owns singleton session controller and relationship graph.
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt` - buyer setup surface, permission launchers, overlay start/stop controls.
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt` - remove duplicated session orchestration and delegate to `MilfSessionController`.
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt` - replace the old senior screen with buyer setup controls.
- Create: `android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt` - handles assist invocation and starts listening through the overlay.
- Create: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt` - Android service that hosts the senior overlay window.
- Create: `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt` - adds, updates, drags, collapses, and removes the overlay Compose view.
- Create: `android/app/src/main/java/ai/milf/client/session/SessionModels.kt` - UX state enum, confirmation/failure/success models, demo/watch flags.
- Create: `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt` - testable websocket boundary for session orchestration.
- Create: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt` - records audio, runs websocket session, speaks narration, dispatches actions, and emits UX state.
- Create: `android/app/src/main/java/ai/milf/client/relationship/RelationshipGraph.kt` - hardcoded demo relationship graph and contact lookup.
- Create: `android/app/src/main/java/ai/milf/client/rescue/BuyerRescue.kt` - builds buyer call intents with `ACTION_CALL` or `ACTION_DIAL` fallback.
- Create: `android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt` - accessible colors, type, and minimum target constants.
- Create: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt` - senior overlay composables for States A-F.
- Create: `android/app/src/main/res/drawable/contact_wei_avatar.xml` - bundled contact avatar fallback.
- Create: `android/app/src/main/res/drawable/ic_helper.xml` - simple helper icon for the floating bubble.
- Modify: `android/app/src/main/res/values/strings.xml` - setup labels, notification text, permission labels.
- Create: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt` - state transition tests.
- Create: `android/app/src/test/java/ai/milf/client/relationship/RelationshipGraphTest.kt` - demo graph resolution tests.
- Create: `android/app/src/test/java/ai/milf/client/rescue/BuyerRescueTest.kt` - buyer rescue intent tests.
- Modify: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt` - protocol extensions.
- Modify: `android/app/src/test/java/ai/milf/client/MainViewModelTest.kt` - setup ViewModel delegation tests.
- Modify: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt` - task complete/failure callback tests.

### Backend

- Modify: `backend/milf/protocol.py` - add `contact_id` to `ConfirmRequest`; add `TaskComplete` and `TaskFailure`.
- Modify: `backend/milf/connection.py` - send contact-aware confirmations and explicit task outcome messages.
- Modify: `backend/milf/context.py` - replace flat contact map with rich relationship graph helpers.
- Modify: `backend/milf/confirmation.py` - attach resolved contact id to confirmation requests.
- Modify: `backend/milf/agent_runner.py` - emit `TaskComplete` or `TaskFailure` at the end of every run.
- Modify: `backend/milf/server.py` - catch unexpected run errors and send safe failure copy before closing.
- Modify: `backend/milf/contacts.json` - store the hardcoded graph with contact ids, aliases, relationship, channel, and escape contact.
- Create: `backend/tests/test_protocol_outcomes.py` - backend protocol tests for new messages.
- Modify: `backend/tests/test_context.py` - rich graph resolution tests.
- Modify: `backend/tests/test_connection.py` - confirmation and task outcome send tests.
- Modify: `backend/tests/test_agent_runner.py` - success/failure outcome emission tests.

### Docs

- Modify: `docs/android-demo-runbook.md` - add overlay permission, assist-app setup, demo/senior mode, failure rescue, and device test matrix.

---

### Task 1: Backend Protocol for UX State Outcomes

Add the smallest protocol extension needed for the app to render State C, E, and F deliberately.

**Files:**
- Modify: `backend/milf/protocol.py`
- Create: `backend/tests/test_protocol_outcomes.py`
- Modify: `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`
- Modify: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`

**Interfaces:**
- `ConfirmRequest` gains nullable `contact_id`.
- Backend and Android add `TaskComplete(summary: str, lang: str, contact_id: str | None)`.
- Backend and Android add `TaskFailure(message: str, lang: str, recovery_contact_id: str | None)`.

- [ ] **Step 1: Write backend protocol tests**

`backend/tests/test_protocol_outcomes.py`:
```python
from milf.protocol import (
    ConfirmRequest,
    TaskComplete,
    TaskFailure,
    decode,
    encode,
)


def test_confirm_request_round_trips_with_contact_id():
    raw = encode(
        ConfirmRequest(
            id="c1",
            summary="Calling Wei, your grandson?",
            lang="en",
            contact_id="wei-grandson",
        )
    )

    decoded = decode(raw)

    assert decoded == ConfirmRequest(
        id="c1",
        summary="Calling Wei, your grandson?",
        lang="en",
        contact_id="wei-grandson",
    )


def test_task_complete_round_trips():
    raw = encode(
        TaskComplete(
            summary="You're connected to Wei.",
            lang="en",
            contact_id="wei-grandson",
        )
    )

    assert decode(raw) == TaskComplete(
        summary="You're connected to Wei.",
        lang="en",
        contact_id="wei-grandson",
    )


def test_task_failure_round_trips():
    raw = encode(
        TaskFailure(
            message="I'm having a little trouble with that. Please try again.",
            lang="en",
            recovery_contact_id="buyer-daughter",
        )
    )

    assert decode(raw) == TaskFailure(
        message="I'm having a little trouble with that. Please try again.",
        lang="en",
        recovery_contact_id="buyer-daughter",
    )
```

- [ ] **Step 2: Run backend protocol tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_protocol_outcomes.py -v
```
Expected: FAIL with import errors for `TaskComplete` and `TaskFailure`, and/or `ConfirmRequest` rejecting `contact_id`.

- [ ] **Step 3: Implement backend protocol models**

In `backend/milf/protocol.py`, change the relevant definitions to:
```python
class ConfirmRequest(BaseModel):
    id: str
    summary: str
    lang: str
    contact_id: str | None = None


class TaskComplete(BaseModel):
    summary: str
    lang: str
    contact_id: str | None = None


class TaskFailure(BaseModel):
    message: str
    lang: str
    recovery_contact_id: str | None = None
```

Update `_MESSAGE_TYPES` to include the new classes:
```python
_MESSAGE_TYPES: dict[str, type[BaseModel]] = {
    cls.__name__: cls
    for cls in (
        Action,
        ActionResult,
        Narration,
        ConfirmRequest,
        ConfirmResponse,
        TaskComplete,
        TaskFailure,
        Audio,
    )
}
```

- [ ] **Step 4: Write Android protocol tests**

Append to `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`:
```kotlin
@Test
fun contactAwareConfirmRequestRoundTrips() {
    val raw = MilfProtocol.encode(
        ConfirmRequest(
            id = "c1",
            summary = "Calling Wei, your grandson?",
            lang = "en",
            contactId = "wei-grandson"
        )
    )

    assertEquals(
        ConfirmRequest(
            id = "c1",
            summary = "Calling Wei, your grandson?",
            lang = "en",
            contactId = "wei-grandson"
        ),
        MilfProtocol.decode(raw)
    )
}

@Test
fun taskCompleteRoundTrips() {
    val raw = MilfProtocol.encode(
        TaskComplete(
            summary = "You're connected to Wei.",
            lang = "en",
            contactId = "wei-grandson"
        )
    )

    assertEquals(
        TaskComplete(
            summary = "You're connected to Wei.",
            lang = "en",
            contactId = "wei-grandson"
        ),
        MilfProtocol.decode(raw)
    )
}

@Test
fun taskFailureRoundTrips() {
    val raw = MilfProtocol.encode(
        TaskFailure(
            message = "I'm having a little trouble with that. Please try again.",
            lang = "en",
            recoveryContactId = "buyer-daughter"
        )
    )

    assertEquals(
        TaskFailure(
            message = "I'm having a little trouble with that. Please try again.",
            lang = "en",
            recoveryContactId = "buyer-daughter"
        ),
        MilfProtocol.decode(raw)
    )
}
```

- [ ] **Step 5: Run Android protocol tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.protocol.MilfProtocolTest"
```
Expected: FAIL with unresolved references for `TaskComplete`, `TaskFailure`, and `contactId`.

- [ ] **Step 6: Implement Android protocol models**

In `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`, add:
```kotlin
data class TaskComplete(
    val summary: String,
    val lang: String,
    val contactId: String? = null
) : MilfMessage

data class TaskFailure(
    val message: String,
    val lang: String,
    val recoveryContactId: String? = null
) : MilfMessage
```

Change `ConfirmRequest` to:
```kotlin
data class ConfirmRequest(
    val id: String,
    val summary: String,
    val lang: String,
    val contactId: String? = null
) : MilfMessage
```

Update `encode`, `decode`, and `typeName` with exact JSON keys:
```kotlin
is ConfirmRequest -> JSONObject()
    .put("id", message.id)
    .put("summary", message.summary)
    .put("lang", message.lang)
    .putNullable("contact_id", message.contactId)

is TaskComplete -> JSONObject()
    .put("summary", message.summary)
    .put("lang", message.lang)
    .putNullable("contact_id", message.contactId)

is TaskFailure -> JSONObject()
    .put("message", message.message)
    .put("lang", message.lang)
    .putNullable("recovery_contact_id", message.recoveryContactId)
```

Decode cases:
```kotlin
"ConfirmRequest" -> ConfirmRequest(
    id = data.getString("id"),
    summary = data.getString("summary"),
    lang = data.getString("lang"),
    contactId = data.optStringOrNull("contact_id")
)

"TaskComplete" -> TaskComplete(
    summary = data.getString("summary"),
    lang = data.getString("lang"),
    contactId = data.optStringOrNull("contact_id")
)

"TaskFailure" -> TaskFailure(
    message = data.getString("message"),
    lang = data.getString("lang"),
    recoveryContactId = data.optStringOrNull("recovery_contact_id")
)
```

Type names:
```kotlin
is TaskComplete -> "TaskComplete"
is TaskFailure -> "TaskFailure"
```

- [ ] **Step 7: Run protocol tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_protocol_outcomes.py -v

cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.protocol.MilfProtocolTest"
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/milf/protocol.py backend/tests/test_protocol_outcomes.py android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt
git commit -m "feat: add UX outcome protocol messages"
```

---

### Task 2: Rich Relationship Graph

Make the hardcoded demo relationship graph explicit on both backend and Android, so "my grandson" resolves to the same contact id used by confirmation, contact photo, and failure recovery.

**Files:**
- Modify: `backend/milf/contacts.json`
- Modify: `backend/milf/context.py`
- Modify: `backend/tests/test_context.py`
- Create: `android/app/src/main/java/ai/milf/client/relationship/RelationshipGraph.kt`
- Create: `android/app/src/test/java/ai/milf/client/relationship/RelationshipGraphTest.kt`
- Create: `android/app/src/main/res/drawable/contact_wei_avatar.xml`

**Interfaces:**
- Backend exposes `resolve_contact(phrase: str) -> Contact | None`.
- Android exposes `RelationshipGraph.demo()` and `RelationshipGraph.contact(id)`.
- Android contact ids match backend ids.

- [ ] **Step 1: Write backend context tests**

Replace or extend `backend/tests/test_context.py` with:
```python
from milf.context import build_goal, escape_contact, resolve_contact


def test_resolve_contact_returns_rich_grandson_contact():
    contact = resolve_contact("I want to see my grandson")

    assert contact is not None
    assert contact.id == "wei-grandson"
    assert contact.display_name == "Wei"
    assert contact.relationship == "grandson"
    assert contact.preferred_app == "WhatsApp"
    assert contact.preferred_channel == "video"


def test_resolve_contact_supports_cucu_alias():
    contact = resolve_contact("nak tengok cucu")

    assert contact is not None
    assert contact.id == "wei-grandson"


def test_build_goal_includes_contact_id_and_display_name():
    goal = build_goal("I want to see my grandson")

    assert "Contact id: wei-grandson." in goal
    assert "Intended contact: Wei." in goal


def test_escape_contact_is_buyer_daughter():
    contact = escape_contact()

    assert contact.id == "buyer-daughter"
    assert contact.display_name == "Daughter"
```

- [ ] **Step 2: Run backend context tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_context.py -v
```
Expected: FAIL because `resolve_contact` currently returns a string and no `escape_contact` exists.

- [ ] **Step 3: Replace backend contact data**

`backend/milf/contacts.json`:
```json
{
  "contacts": [
    {
      "id": "wei-grandson",
      "display_name": "Wei",
      "relationship": "grandson",
      "aliases": ["grandson", "cucu", "Ah Xuan", "Ah Boy"],
      "preferred_app": "WhatsApp",
      "preferred_channel": "video",
      "photo_asset": "contact_wei",
      "escape": false
    },
    {
      "id": "buyer-daughter",
      "display_name": "Daughter",
      "relationship": "daughter",
      "aliases": ["daughter", "my daughter", "anak perempuan"],
      "preferred_app": "Phone",
      "preferred_channel": "voice",
      "phone": "+15551234567",
      "photo_asset": "contact_buyer",
      "escape": true
    }
  ]
}
```

- [ ] **Step 4: Implement backend graph helpers**

In `backend/milf/context.py`, add:
```python
from pydantic import BaseModel


class Contact(BaseModel):
    id: str
    display_name: str
    relationship: str
    aliases: list[str]
    preferred_app: str
    preferred_channel: str
    photo_asset: str
    escape: bool = False
    phone: str | None = None
```

Replace `_contacts`, `resolve_contact`, and `build_goal` contact handling with:
```python
@cache
def _contacts() -> list[Contact]:
    with CONTACTS_PATH.open(encoding="utf-8") as handle:
        payload = json.load(handle)
    return [Contact.model_validate(item) for item in payload["contacts"]]


def resolve_contact(phrase: str) -> Contact | None:
    phrase_lower = phrase.casefold()
    for contact in _contacts():
        for alias in contact.aliases:
            if alias.casefold() in phrase_lower:
                return contact
    return None


def escape_contact() -> Contact:
    for contact in _contacts():
        if contact.escape:
            return contact
    raise RuntimeError("contacts.json must contain one escape contact")
```

In `build_goal`, replace contact string handling with:
```python
contact = resolve_contact(intent)
parts = [f"Spoken intent: {intent!r}."]

if contact is not None:
    parts.append(f"Contact id: {contact.id}.")
    parts.append(f"Intended contact: {contact.display_name}.")
    parts.append(f"Relationship: {contact.relationship}.")
    parts.append(f"Preferred channel: {contact.preferred_app} {contact.preferred_channel}.")
```

In `acknowledgment`, use:
```python
contact = resolve_contact(intent)
if contact is not None:
    return f"Okay, let me help you reach {contact.display_name}."
return "Okay, let me help you with that."
```

- [ ] **Step 5: Write Android relationship tests**

`android/app/src/test/java/ai/milf/client/relationship/RelationshipGraphTest.kt`:
```kotlin
package ai.milf.client.relationship

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RelationshipGraphTest {
    @Test
    fun demoGraphContainsWeiGrandson() {
        val graph = RelationshipGraph.demo()
        val contact = graph.contact("wei-grandson")

        assertNotNull(contact)
        assertEquals("Wei", contact?.displayName)
        assertEquals("grandson", contact?.relationship)
        assertEquals("WhatsApp", contact?.preferredApp)
        assertEquals("video", contact?.preferredChannel)
    }

    @Test
    fun demoGraphContainsBuyerEscapeContact() {
        val graph = RelationshipGraph.demo()

        assertEquals("buyer-daughter", graph.escapeContact.id)
        assertEquals("+15551234567", graph.escapeContact.phone)
    }
}
```

- [ ] **Step 6: Run Android relationship tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.relationship.RelationshipGraphTest"
```
Expected: FAIL with unresolved reference `RelationshipGraph`.

- [ ] **Step 7: Implement Android relationship graph**

`android/app/src/main/java/ai/milf/client/relationship/RelationshipGraph.kt`:
```kotlin
package ai.milf.client.relationship

import ai.milf.client.R

data class RelationshipContact(
    val id: String,
    val displayName: String,
    val relationship: String,
    val aliases: List<String>,
    val preferredApp: String,
    val preferredChannel: String,
    val photoResId: Int,
    val phone: String? = null
)

class RelationshipGraph(
    private val contacts: List<RelationshipContact>,
    val escapeContact: RelationshipContact
) {
    fun contact(id: String?): RelationshipContact? =
        contacts.firstOrNull { it.id == id }

    companion object {
        fun demo(): RelationshipGraph {
            val wei = RelationshipContact(
                id = "wei-grandson",
                displayName = "Wei",
                relationship = "grandson",
                aliases = listOf("grandson", "cucu", "Ah Xuan", "Ah Boy"),
                preferredApp = "WhatsApp",
                preferredChannel = "video",
                photoResId = R.drawable.contact_wei_avatar
            )
            val daughter = RelationshipContact(
                id = "buyer-daughter",
                displayName = "Daughter",
                relationship = "daughter",
                aliases = listOf("daughter", "my daughter", "anak perempuan"),
                preferredApp = "Phone",
                preferredChannel = "voice",
                photoResId = R.drawable.contact_wei_avatar,
                phone = "+15551234567"
            )
            return RelationshipGraph(
                contacts = listOf(wei, daughter),
                escapeContact = daughter
            )
        }
    }
}
```

- [ ] **Step 8: Add fallback avatar drawable**

`android/app/src/main/res/drawable/contact_wei_avatar.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="128dp"
    android:height="128dp"
    android:viewportWidth="128"
    android:viewportHeight="128">
    <path
        android:fillColor="#FACC15"
        android:pathData="M64,64m-60,0a60,60 0,1 1,120 0a60,60 0,1 1,-120 0" />
    <path
        android:fillColor="#111827"
        android:pathData="M44,50m-8,0a8,8 0,1 1,16 0a8,8 0,1 1,-16 0" />
    <path
        android:fillColor="#111827"
        android:pathData="M84,50m-8,0a8,8 0,1 1,16 0a8,8 0,1 1,-16 0" />
    <path
        android:strokeColor="#111827"
        android:strokeWidth="7"
        android:strokeLineCap="round"
        android:fillColor="@android:color/transparent"
        android:pathData="M42,80Q64,98 86,80" />
</vector>
```

- [ ] **Step 9: Run graph tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_context.py -v

cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.relationship.RelationshipGraphTest"
```
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/milf/contacts.json backend/milf/context.py backend/tests/test_context.py android/app/src/main/java/ai/milf/client/relationship/RelationshipGraph.kt android/app/src/test/java/ai/milf/client/relationship/RelationshipGraphTest.kt android/app/src/main/res/drawable/contact_wei_avatar.xml
git commit -m "feat: add demo relationship graph"
```

---

### Task 3: Backend Emits Contact-Aware Confirmation and Safe Outcomes

Make every backend run end in an explicit success or failure message. The app should never have to display raw websocket errors as senior copy.

**Files:**
- Modify: `backend/milf/connection.py`
- Modify: `backend/milf/confirmation.py`
- Modify: `backend/milf/agent_runner.py`
- Modify: `backend/milf/server.py`
- Modify: `backend/tests/test_connection.py`
- Modify: `backend/tests/test_agent_runner.py`

**Interfaces:**
- `AppConnection.request_confirmation(summary, lang, contact_id=None)`.
- `AppConnection.send_task_complete(summary, lang, contact_id=None)`.
- `AppConnection.send_task_failure(message, lang, recovery_contact_id=None)`.
- `build_confirmation_tool(connection, lang, contact_id=None)`.

- [ ] **Step 1: Write connection tests**

Append to `backend/tests/test_connection.py`:
```python
import json

from milf.connection import AppConnection


async def test_request_confirmation_sends_contact_id():
    sent = []

    async def send(raw):
        sent.append(raw)

    conn = AppConnection(send)
    task = asyncio.create_task(
        conn.request_confirmation(
            "Calling Wei, your grandson?",
            "en",
            contact_id="wei-grandson",
        )
    )
    await asyncio.sleep(0)

    payload = json.loads(sent[0])
    assert payload["type"] == "ConfirmRequest"
    assert payload["data"]["contact_id"] == "wei-grandson"

    conn.on_message(
        json.dumps(
            {
                "type": "ConfirmResponse",
                "data": {"id": payload["data"]["id"], "approved": True},
            }
        )
    )
    assert await task is True


async def test_send_task_outcomes():
    sent = []

    async def send(raw):
        sent.append(json.loads(raw))

    conn = AppConnection(send)

    await conn.send_task_complete("You're connected to Wei.", "en", "wei-grandson")
    await conn.send_task_failure(
        "I'm having a little trouble with that. Please try again.",
        "en",
        "buyer-daughter",
    )

    assert sent[0] == {
        "type": "TaskComplete",
        "data": {
            "summary": "You're connected to Wei.",
            "lang": "en",
            "contact_id": "wei-grandson",
        },
    }
    assert sent[1] == {
        "type": "TaskFailure",
        "data": {
            "message": "I'm having a little trouble with that. Please try again.",
            "lang": "en",
            "recovery_contact_id": "buyer-daughter",
        },
    }
```

- [ ] **Step 2: Run connection tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_connection.py -v
```
Expected: FAIL because the new connection methods and `contact_id` parameter do not exist.

- [ ] **Step 3: Implement connection outcome methods**

In `backend/milf/connection.py`, import:
```python
from milf.protocol import TaskComplete, TaskFailure
```

Change `request_confirmation` and add outcome senders:
```python
async def request_confirmation(
    self,
    summary: str,
    lang: str,
    contact_id: str | None = None,
) -> bool:
    request = ConfirmRequest(
        id=self._new_id(),
        summary=summary,
        lang=lang,
        contact_id=contact_id,
    )
    future = self._create_pending(request.id, ConfirmResponse)
    try:
        await self._send(encode(request))
        response = await future
        return response.approved
    finally:
        self._pending.pop(request.id, None)


async def send_task_complete(
    self,
    summary: str,
    lang: str,
    contact_id: str | None = None,
) -> None:
    await self._send(
        encode(TaskComplete(summary=summary, lang=lang, contact_id=contact_id))
    )


async def send_task_failure(
    self,
    message: str,
    lang: str,
    recovery_contact_id: str | None = None,
) -> None:
    await self._send(
        encode(
            TaskFailure(
                message=message,
                lang=lang,
                recovery_contact_id=recovery_contact_id,
            )
        )
    )
```

- [ ] **Step 4: Pass contact id through the confirmation tool**

In `backend/milf/confirmation.py`, change the function signature and call:
```python
def build_confirmation_tool(
    connection: AppConnection,
    lang: str,
    contact_id: str | None = None,
) -> dict:
    async def confirm_action(summary: str, *, ctx=None, **kwargs) -> str:
        approved = await connection.request_confirmation(
            summary,
            lang,
            contact_id=contact_id,
        )
        if approved:
            return "User confirmed. Proceed with the action."
        return "User declined. Do not perform the action; stop and end the task."
```

- [ ] **Step 5: Write agent runner outcome tests**

Replace `backend/tests/test_agent_runner.py` fake connection with:
```python
class FakeConn:
    def __init__(self):
        self.narrations = []
        self.completions = []
        self.failures = []

    async def send_narration(self, text, lang):
        self.narrations.append((text, lang))

    async def send_task_complete(self, summary, lang, contact_id=None):
        self.completions.append((summary, lang, contact_id))

    async def send_task_failure(self, message, lang, recovery_contact_id=None):
        self.failures.append((message, lang, recovery_contact_id))
```

Update `test_run_task_acks_then_builds_and_runs` assertions:
```python
assert result.success is True
assert "Wei" in captured["goal"]
assert "confirm_action" in captured["tools"]
assert conn.narrations and "Wei" in conn.narrations[0][0]
assert conn.completions == [("You're connected to Wei.", "en", "wei-grandson")]
assert conn.failures == []
```

Add:
```python
class FailingHandler:
    async def stream_events(self):
        if False:
            yield None

    def __await__(self):
        async def _r():
            raise RuntimeError("agent exploded")

        return _r().__await__()


async def test_run_task_sends_safe_failure_on_agent_error():
    def fake_factory(goal, driver, custom_tools):
        return SimpleNamespace(run=lambda: FailingHandler())

    conn = FakeConn()

    result = await run_task(
        connection=conn,
        audio=b"x",
        lang="en",
        stt=MockSTT("I want to see my grandson"),
        agent_factory=fake_factory,
    )

    assert result.success is False
    assert conn.failures == [
        (
            "I'm having a little trouble with that. Please try again.",
            "en",
            "buyer-daughter",
        )
    ]
```

- [ ] **Step 6: Run agent runner tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_agent_runner.py -v
```
Expected: FAIL because `run_task` does not send task outcomes.

- [ ] **Step 7: Implement task outcome emission**

In `backend/milf/agent_runner.py`, import:
```python
from types import SimpleNamespace
from milf.context import escape_contact, resolve_contact
```

Change `run_task` after transcription:
```python
intent = await stt.transcribe(audio, lang)
contact = resolve_contact(intent)
escape = escape_contact()
await connection.send_narration(acknowledgment(intent), lang)

goal = build_goal(intent)
driver = WebSocketDriver(connection)
custom_tools = build_confirmation_tool(
    connection,
    lang,
    contact_id=contact.id if contact is not None else None,
)
agent = agent_factory(goal, driver, custom_tools)
handler = agent.run()

try:
    result = await narrate_events(handler, connection, lang)
except Exception:
    await connection.send_task_failure(
        "I'm having a little trouble with that. Please try again.",
        lang,
        recovery_contact_id=escape.id,
    )
    return SimpleNamespace(success=False, reason="agent_error")

success = bool(getattr(result, "success", True))
if success:
    if contact is not None:
        await connection.send_task_complete(
            f"You're connected to {contact.display_name}.",
            lang,
            contact_id=contact.id,
        )
    else:
        await connection.send_task_complete("Done.", lang)
else:
    await connection.send_task_failure(
        "I'm having a little trouble with that. Please try again.",
        lang,
        recovery_contact_id=escape.id,
    )
return result
```

- [ ] **Step 8: Make server send safe failure on setup errors**

In `backend/milf/server.py`, wrap `run_task`:
```python
try:
    await run_task(conn, audio, first.lang, stt)
except Exception:
    await conn.send_task_failure(
        "I'm having a little trouble with that. Please try again.",
        first.lang,
        recovery_contact_id="buyer-daughter",
    )
```

Keep the existing `finally` pump cancellation.

- [ ] **Step 9: Run backend tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest -v
```
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/milf/connection.py backend/milf/confirmation.py backend/milf/agent_runner.py backend/milf/server.py backend/tests/test_connection.py backend/tests/test_agent_runner.py
git commit -m "feat: emit safe task outcomes"
```

---

### Task 4: Android Session State Machine

Extract run orchestration into a reusable controller and model the senior UX states explicitly.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/session/SessionModels.kt`
- Create: `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt`
- Create: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`
- Create: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`
- Modify: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`
- Modify: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/test/java/ai/milf/client/MainViewModelTest.kt`

**Interfaces:**
- `MilfSessionController.uiState: StateFlow<SeniorUiState>`.
- `beginListening()`, `finishListeningAndRun()`, `approveConfirmation()`, `denyConfirmation()`, `onConfirmationSpeech(text)`, `setWatchMode(enabled)`, `setDemoMode(enabled)`, `retry()`, `callBuyer()`.
- `MainViewModel` delegates setup fields and senior actions to the controller.

- [ ] **Step 1: Create session model file**

`android/app/src/main/java/ai/milf/client/session/SessionModels.kt`:
```kotlin
package ai.milf.client.session

import ai.milf.client.relationship.RelationshipContact

enum class SeniorUxScreen {
    Idle,
    Listening,
    Confirming,
    Working,
    Success,
    Failure
}

data class PendingConfirmation(
    val id: String,
    val summary: String,
    val lang: String,
    val contact: RelationshipContact?
)

data class SuccessState(
    val summary: String,
    val lang: String,
    val contact: RelationshipContact?
)

data class FailureState(
    val message: String,
    val lang: String,
    val recoveryContact: RelationshipContact?
)

data class SeniorUiState(
    val backendUrl: String = "ws://10.0.2.2:8765",
    val lang: String = "en",
    val screen: SeniorUxScreen = SeniorUxScreen.Idle,
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val captions: String = "Ready",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val success: SuccessState? = null,
    val failure: FailureState? = null,
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val watchMode: Boolean = false,
    val demoMode: Boolean = false
)
```

- [ ] **Step 2: Add websocket interface and callbacks for task outcomes**

Create `android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt`:
```kotlin
package ai.milf.client.session

import ai.milf.client.protocol.MilfMessage
import ai.milf.client.ws.MilfWebSocketClient

interface SessionSocketClient {
    fun start(goalAudio: ByteArray, lang: String, callbacks: MilfWebSocketClient.Callbacks)
    fun send(message: MilfMessage): Boolean
    fun close()
}
```

Change the websocket class declaration in `MilfWebSocketClient.kt`:
```kotlin
import ai.milf.client.session.SessionSocketClient

class MilfWebSocketClient(
    private val url: String,
    private val socketFactory: SocketFactory = OkHttpSocketFactory(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val audioEncoder: (ByteArray) -> String = {
        Base64.encodeToString(it, Base64.NO_WRAP)
    }
) : SessionSocketClient {
```

In `MilfWebSocketClient.Callbacks`, add:
```kotlin
suspend fun onTaskComplete(summary: String, lang: String, contactId: String?)
suspend fun onTaskFailure(message: String, lang: String, recoveryContactId: String?)
```

In `handleText`, add:
```kotlin
is TaskComplete -> scope.launch {
    if (!isActive(messageSessionId)) return@launch
    runCatching {
        activeCallbacks.onTaskComplete(message.summary, message.lang, message.contactId)
    }.onFailure { reportFailure(messageSessionId, it.failureMessage()) }
}

is TaskFailure -> scope.launch {
    if (!isActive(messageSessionId)) return@launch
    runCatching {
        activeCallbacks.onTaskFailure(message.message, message.lang, message.recoveryContactId)
    }.onFailure { reportFailure(messageSessionId, it.failureMessage()) }
}
```

Update the ignored message branch to:
```kotlin
is ActionResult, is ConfirmResponse, is Audio -> Unit
```

- [ ] **Step 3: Write session controller tests**

`android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`:
```kotlin
package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.ws.MilfWebSocketClient
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MilfSessionControllerTest {
    @Test
    fun beginListeningMovesToListeningState() = runTest {
        val controller = fakeController()

        controller.beginListening()

        assertEquals(SeniorUxScreen.Listening, controller.uiState.value.screen)
        assertEquals(true, controller.uiState.value.isRecording)
    }

    @Test
    fun confirmationRequestShowsContactAwareGate() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onConfirmRequest(
            id = "c1",
            summary = "Calling Wei, your grandson?",
            lang = "en",
            contactId = "wei-grandson"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Confirming, state.screen)
        assertEquals("Wei", state.confirmation?.contact?.displayName)
    }

    @Test
    fun approveConfirmationSendsResponseAndReturnsToWorking() = runTest {
        val sent = mutableListOf<ConfirmResponse>()
        val client = FakeClient(onSendConfirm = { sent += it })
        val controller = fakeController(client = client)

        controller.showConfirmationForTest("c1", "Calling Wei?", "en", "wei-grandson")
        controller.approveConfirmation()

        assertEquals(listOf(ConfirmResponse("c1", true)), sent)
        assertEquals(SeniorUxScreen.Working, controller.uiState.value.screen)
    }

    @Test
    fun taskFailureShowsRecoveryContact() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskFailure(
            message = "I'm having a little trouble with that. Please try again.",
            lang = "en",
            recoveryContactId = "buyer-daughter"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    private fun fakeController(client: FakeClient = FakeClient()): MilfSessionController =
        MilfSessionController(
            dependencies = MilfSessionController.Dependencies.fake(client),
            graph = RelationshipGraph.demo()
        )
}
```

In the same test file, add:
```kotlin
private class FakeClient(
    private val onSendConfirm: (ConfirmResponse) -> Unit = {}
) : SessionSocketClient {
    var callbacks: MilfWebSocketClient.Callbacks? = null

    override fun start(goalAudio: ByteArray, lang: String, callbacks: MilfWebSocketClient.Callbacks) {
        this.callbacks = callbacks
    }

    override fun send(message: MilfMessage): Boolean {
        if (message is ConfirmResponse) {
            onSendConfirm(message)
        }
        return true
    }

    override fun close() = Unit
}
```

- [ ] **Step 4: Run session tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.session.MilfSessionControllerTest"
```
Expected: FAIL because `MilfSessionController` does not exist and `MilfWebSocketClient` does not expose outcome callbacks yet.

- [ ] **Step 5: Implement session controller dependencies**

`android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`:
```kotlin
package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.ConfirmationVoiceParser
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.relationship.RelationshipGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MilfSessionController(
    private val dependencies: Dependencies,
    private val graph: RelationshipGraph
) {
    private val _uiState = MutableStateFlow(SeniorUiState())
    val uiState: StateFlow<SeniorUiState> = _uiState.asStateFlow()

    fun setBackendUrl(url: String) {
        _uiState.update { it.copy(backendUrl = url.trim()) }
    }

    fun setLang(lang: String) {
        _uiState.update { it.copy(lang = lang) }
    }

    fun setWatchMode(enabled: Boolean) {
        _uiState.update { it.copy(watchMode = enabled) }
    }

    fun setDemoMode(enabled: Boolean) {
        _uiState.update { it.copy(demoMode = enabled, watchMode = enabled || it.watchMode) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(overlayEnabled = enabled) }
    }

    fun refreshAccessibilityStatus() {
        _uiState.update {
            it.copy(accessibilityEnabled = dependencies.accessibilityAvailable())
        }
    }

    fun beginListening() {
        dependencies.narrator.stop()
        dependencies.recorder.start()
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Listening,
                isRecording = true,
                isRunning = false,
                captions = "What would you like to do?",
                lastNarration = null,
                confirmation = null,
                success = null,
                failure = null
            )
        }
        dependencies.narrator.speak("What would you like to do?", _uiState.value.lang)
    }

    fun finishListeningAndRun() {
        val state = _uiState.value
        val bytes = dependencies.recorder.stop()
        val client = dependencies.clientFactory(state.backendUrl)
        dependencies.activeClient = client
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Working,
                isRecording = false,
                isRunning = true,
                captions = "Working on that."
            )
        }
        client.start(bytes, state.lang, callbacks())
    }
```

Continue the class with:
```kotlin
    fun approveConfirmation() {
        respondToConfirmation(approved = true)
    }

    fun denyConfirmation() {
        respondToConfirmation(approved = false)
    }

    fun onConfirmationSpeech(text: String) {
        when (ConfirmationVoiceParser.parse(text)) {
            true -> approveConfirmation()
            false -> denyConfirmation()
            null -> _uiState.update { it.copy(captions = "Please say yes or no") }
        }
    }

    fun retry() {
        beginListening()
    }

    fun showConfirmationForTest(id: String, summary: String, lang: String, contactId: String?) {
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Confirming,
                confirmation = PendingConfirmation(id, summary, lang, graph.contact(contactId))
            )
        }
    }

    fun shutdown() {
        dependencies.recorder.cancel()
        dependencies.narrator.shutdown()
        dependencies.activeClient?.close()
    }

    private fun callbacks(): MilfWebSocketClient.Callbacks =
        object : MilfWebSocketClient.Callbacks {
            override suspend fun onAction(action: Action): ActionResult =
                dependencies.dispatch(action)

            override suspend fun onNarration(text: String, lang: String) {
                dependencies.narrator.speak(text, lang)
                _uiState.update {
                    it.copy(lastNarration = text, captions = text)
                }
            }

            override suspend fun onConfirmRequest(
                id: String,
                summary: String,
                lang: String,
                contactId: String?
            ) {
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Confirming,
                        isRunning = true,
                        confirmation = PendingConfirmation(id, summary, lang, graph.contact(contactId)),
                        captions = summary
                    )
                }
            }

            override suspend fun onTaskComplete(summary: String, lang: String, contactId: String?) {
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Success,
                        isRunning = false,
                        success = SuccessState(summary, lang, graph.contact(contactId)),
                        captions = summary
                    )
                }
            }

            override suspend fun onTaskFailure(message: String, lang: String, recoveryContactId: String?) {
                dependencies.narrator.speak(message, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Failure,
                        isRunning = false,
                        failure = FailureState(message, lang, graph.contact(recoveryContactId)),
                        captions = message
                    )
                }
            }

            override fun onClosed(reason: String?) {
                _uiState.update { it.copy(isRunning = false) }
            }

            override fun onFailed(message: String) {
                val safe = "I'm having a little trouble with that. Please try again."
                dependencies.narrator.speak(safe, _uiState.value.lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Failure,
                        isRunning = false,
                        failure = FailureState(safe, it.lang, graph.escapeContact),
                        captions = safe
                    )
                }
            }
        }

    private fun respondToConfirmation(approved: Boolean) {
        val pending = _uiState.value.confirmation ?: return
        dependencies.activeClient?.send(ConfirmResponse(pending.id, approved))
        _uiState.update {
            it.copy(
                confirmation = null,
                screen = if (approved) SeniorUxScreen.Working else SeniorUxScreen.Idle,
                captions = if (approved) "Continuing." else "Okay, stopped."
            )
        }
    }
```

Add dependencies:
```kotlin
    class Dependencies(
        val recorder: AudioRecorderLike,
        val narrator: NarratorLike,
        val clientFactory: (String) -> SessionSocketClient,
        val accessibilityAvailable: () -> Boolean,
        val dispatch: suspend (Action) -> ActionResult
    ) {
        var activeClient: SessionSocketClient? = null

        companion object {
            fun fake(client: SessionSocketClient): Dependencies =
                Dependencies(
                    recorder = object : AudioRecorderLike {
                        override fun start() = Unit
                        override fun stop(): ByteArray = byteArrayOf(1)
                        override fun cancel() = Unit
                    },
                    narrator = object : NarratorLike {
                        override fun speak(text: String, lang: String) = Unit
                        override fun stop() = Unit
                        override fun shutdown() = Unit
                    },
                    clientFactory = { client },
                    accessibilityAvailable = { true },
                    dispatch = { action -> ActionResult(action.id, true) }
                )
        }
    }
}
```

In production, provide dependencies from `MainViewModel.Dependencies.real` using the existing `AudioRecorder`, `TtsNarrator`, `MilfWebSocketClient`, and `ActionDispatcher(MilfAccessibilityService.instance)`.

- [ ] **Step 6: Delegate MainViewModel to the controller**

Change `MainViewModel` to expose `uiState` from the controller:
```kotlin
class MainViewModel(
    private val controller: MilfSessionController
) : ViewModel() {
    val uiState: StateFlow<SeniorUiState> = controller.uiState

    fun setBackendUrl(url: String) = controller.setBackendUrl(url)
    fun setLang(lang: String) = controller.setLang(lang)
    fun setWatchMode(enabled: Boolean) = controller.setWatchMode(enabled)
    fun setDemoMode(enabled: Boolean) = controller.setDemoMode(enabled)
    fun refreshAccessibilityStatus() = controller.refreshAccessibilityStatus()
    fun startRecording() = controller.beginListening()
    fun stopAndRun() = controller.finishListeningAndRun()
    fun approveConfirmation() = controller.approveConfirmation()
    fun denyConfirmation() = controller.denyConfirmation()
    fun onConfirmationSpeech(text: String) = controller.onConfirmationSpeech(text)
    fun showConfirmationForTest(id: String, summary: String, lang: String) =
        controller.showConfirmationForTest(id, summary, lang, "wei-grandson")

    override fun onCleared() {
        controller.shutdown()
        super.onCleared()
    }
}
```

Keep `AudioRecorderLike`, `NarratorLike`, `AndroidAudioRecorder`, and `AndroidNarrator` in `MainViewModel.kt` or move them to `session/Dependencies.kt` if the file becomes hard to scan.

- [ ] **Step 7: Run Android tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/session/SessionModels.kt android/app/src/main/java/ai/milf/client/session/SessionSocketClient.kt android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt android/app/src/test/java/ai/milf/client/MainViewModelTest.kt
git commit -m "feat: model senior UX state machine"
```

---

### Task 5: Accessible Senior Overlay UI States

Build the Compose UI for States A-F, separated from setup/debug UI.

**Files:**
- Modify: `android/app/build.gradle.kts`
- Create: `android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt`
- Create: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`
- Create: `android/app/src/main/res/drawable/ic_helper.xml`

**Interfaces:**
- `SeniorOverlayUi(state, onBubbleTap, onStopListening, onApprove, onDeny, onSpeakDecision, onWatchModeChange, onRetry, onCallBuyer)`.
- Minimum primary touch target is 72dp.
- Captions are visible by default.
- Consequential confirmation never auto-proceeds.

- [ ] **Step 1: Add icon dependency**

In `android/app/build.gradle.kts`, add:
```kotlin
implementation("androidx.compose.material:material-icons-extended:1.7.6")
```

- [ ] **Step 2: Create accessible theme constants**

`android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt`:
```kotlin
package ai.milf.client.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MilfColors {
    val Ink = Color(0xFF111827)
    val Paper = Color(0xFFFFFBF0)
    val Scrim = Color(0xDD111827)
    val HelperYellow = Color(0xFFFACC15)
    val YesGreen = Color(0xFF047857)
    val NoRed = Color(0xFFB91C1C)
    val QuietBlue = Color(0xFF2563EB)
}

object MilfDimens {
    val PrimaryTarget = 72.dp
    val BubbleSize = 76.dp
    val Corner = 8.dp
}
```

- [ ] **Step 3: Add helper icon**

`android/app/src/main/res/drawable/ic_helper.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path
        android:fillColor="#111827"
        android:pathData="M24,4C14.1,4 6,11.4 6,20.6c0,5.7 3.1,10.8 7.8,13.8L12,44l9.8,-6.8c0.7,0.1 1.4,0.1 2.2,0.1c9.9,0 18,-7.5 18,-16.7S33.9,4 24,4z" />
    <path
        android:fillColor="#FACC15"
        android:pathData="M17,19m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0" />
    <path
        android:fillColor="#FACC15"
        android:pathData="M31,19m-3,0a3,3 0,1 1,6 0a3,3 0,1 1,-6 0" />
    <path
        android:strokeColor="#FACC15"
        android:strokeWidth="3"
        android:strokeLineCap="round"
        android:fillColor="@android:color/transparent"
        android:pathData="M18,27Q24,32 30,27" />
</vector>
```

- [ ] **Step 4: Implement senior overlay composable**

`android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`:
```kotlin
package ai.milf.client.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.milf.client.R
import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen

@Composable
fun SeniorOverlayUi(
    state: SeniorUiState,
    onBubbleTap: () -> Unit,
    onStopListening: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit,
    onWatchModeChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onCallBuyer: () -> Unit
) {
    MaterialTheme {
        when (state.screen) {
            SeniorUxScreen.Idle -> HelperBubble(onClick = onBubbleTap)
            SeniorUxScreen.Listening -> ListeningOverlay(state.captions, onStopListening)
            SeniorUxScreen.Confirming -> ConfirmationGate(state, onApprove, onDeny, onSpeakDecision)
            SeniorUxScreen.Working -> WorkingOverlay(state, onWatchModeChange)
            SeniorUxScreen.Success -> SuccessOverlay(state)
            SeniorUxScreen.Failure -> FailureOverlay(state, onRetry, onCallBuyer)
        }
    }
}
```

Add the state components in the same file:
```kotlin
@Composable
private fun HelperBubble(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(MilfDimens.BubbleSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.HelperYellow)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helper),
            contentDescription = "MILF helper",
            tint = Color.Unspecified,
            modifier = Modifier.size(44.dp)
        )
    }
}

@Composable
private fun ListeningOverlay(captions: String, onStopListening: () -> Unit) {
    FullOverlay {
        Text(
            text = "Listening",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MilfColors.Ink
        )
        Spacer(Modifier.height(24.dp))
        Waveform()
        Spacer(Modifier.height(28.dp))
        CaptionText(captions)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStopListening,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Text("Stop", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConfirmationGate(
    state: SeniorUiState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit
) {
    val pending = state.confirmation ?: return
    FullOverlay(scrim = true) {
        pending.contact?.let { contact ->
            Image(
                painter = painterResource(contact.photoResId),
                contentDescription = contact.displayName,
                modifier = Modifier
                    .size(144.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.height(18.dp))
        }
        Text(
            text = pending.summary,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MilfColors.Ink
        )
        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = onSpeakDecision,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget)
        ) {
            Icon(Icons.Default.Mic, contentDescription = null)
            Text("Speak yes or no", fontSize = 24.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onDeny,
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Text("NO", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = onApprove,
                modifier = Modifier
                    .weight(1f)
                    .height(88.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MilfColors.YesGreen)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("YES", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
```

Add working, success, failure, and helpers:
```kotlin
@Composable
private fun WorkingOverlay(state: SeniorUiState, onWatchModeChange: (Boolean) -> Unit) {
    if (!state.watchMode && !state.demoMode) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            HelperBubble(onClick = { onWatchModeChange(true) })
            Surface(
                shape = RoundedCornerShape(MilfDimens.Corner),
                color = MilfColors.Ink,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    text = state.captions,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
        return
    }

    FullOverlay {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Working", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Visibility, contentDescription = null)
                Switch(checked = state.watchMode || state.demoMode, onCheckedChange = onWatchModeChange)
            }
        }
        Spacer(Modifier.height(24.dp))
        Waveform()
        Spacer(Modifier.height(24.dp))
        CaptionText(state.captions)
    }
}

@Composable
private fun SuccessOverlay(state: SeniorUiState) {
    FullOverlay {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MilfColors.YesGreen,
            modifier = Modifier.size(104.dp)
        )
        Spacer(Modifier.height(24.dp))
        CaptionText(state.success?.summary ?: "Done.")
    }
}

@Composable
private fun FailureOverlay(state: SeniorUiState, onRetry: () -> Unit, onCallBuyer: () -> Unit) {
    FullOverlay {
        CaptionText(
            state.failure?.message
                ?: "I'm having a little trouble with that. Please try again."
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCallBuyer,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = MilfColors.YesGreen)
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Text("Cancel", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Try again", fontSize = 24.sp)
        }
    }
}

@Composable
private fun FullOverlay(scrim: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (scrim) MilfColors.Scrim else MilfColors.Paper)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text = text,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center,
        color = MilfColors.Ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Waveform() {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "phase"
    )
    Canvas(modifier = Modifier.size(width = 240.dp, height = 96.dp)) {
        val bars = 7
        val gap = size.width / (bars * 2)
        repeat(bars) { index ->
            val normalized = ((index + 1) / bars.toFloat())
            val height = size.height * (0.25f + 0.65f * kotlin.math.abs(phase - normalized))
            drawLine(
                color = MilfColors.QuietBlue,
                start = androidx.compose.ui.geometry.Offset(gap + index * gap * 2, (size.height - height) / 2),
                end = androidx.compose.ui.geometry.Offset(gap + index * gap * 2, (size.height + height) / 2),
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
```

- [ ] **Step 5: Build UI**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/build.gradle.kts android/app/src/main/java/ai/milf/client/ui/MilfTheme.kt android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt android/app/src/main/res/drawable/ic_helper.xml
git commit -m "feat: add senior overlay UI states"
```

---

### Task 6: Floating Overlay Service and Assist Invocation

Move the senior UI into a persistent draggable overlay and wire assist invocation to start listening.

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/java/ai/milf/client/MilfApplication.kt`
- Create: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`
- Create: `android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`
- Create: `android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt`
- Modify: `android/app/src/main/res/values/strings.xml`

**Interfaces:**
- `SeniorOverlayService.start(context, startListening: Boolean)`.
- `SeniorOverlayService.stop(context)`.
- `AssistEntryActivity` starts the overlay with `startListening=true` and finishes immediately.
- Overlay starts as a 76dp bubble; expands for Listening, Confirming, Success, and Failure; collapses during Working unless watch/demo mode is enabled.

- [ ] **Step 1: Add manifest permissions and components**

In `AndroidManifest.xml`, add permissions:
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.CALL_PHONE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Add the application class:
```xml
<application
    android:name=".MilfApplication"
    android:allowBackup="false"
    android:label="@string/app_name"
    android:supportsRtl="true"
    android:theme="@style/Theme.MILF">
```

Add assist activity:
```xml
<activity
    android:name=".assist.AssistEntryActivity"
    android:exported="true"
    android:theme="@style/Theme.MILF">
    <intent-filter>
        <action android:name="android.intent.action.ASSIST" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

Add overlay service:
```xml
<service
    android:name=".overlay.SeniorOverlayService"
    android:exported="false" />
```

- [ ] **Step 2: Add application singleton**

`android/app/src/main/java/ai/milf/client/MilfApplication.kt`:
```kotlin
package ai.milf.client

import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
import ai.milf.client.audio.TtsNarrator
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.session.MilfSessionController
import ai.milf.client.ws.MilfWebSocketClient
import android.app.Application

class MilfApplication : Application() {
    lateinit var sessionController: MilfSessionController
        private set

    override fun onCreate() {
        super.onCreate()
        sessionController = MilfSessionController(
            dependencies = MilfSessionController.Dependencies(
                recorder = AndroidAudioRecorder(AudioRecorder(this)),
                narrator = AndroidNarrator(TtsNarrator(this)),
                clientFactory = { MilfWebSocketClient(it) },
                accessibilityAvailable = { MilfAccessibilityService.instance != null },
                dispatch = { action ->
                    ActionDispatcher(MilfAccessibilityService.instance).dispatch(action)
                }
            ),
            graph = RelationshipGraph.demo()
        )
    }
}
```

Make `AndroidAudioRecorder` and `AndroidNarrator` internal instead of private if they remain in `MainViewModel.kt`, or move them to a new `android/app/src/main/java/ai/milf/client/session/AndroidSessionDependencies.kt`.

- [ ] **Step 3: Create assist entry Activity**

`android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt`:
```kotlin
package ai.milf.client.assist

import ai.milf.client.overlay.SeniorOverlayService
import android.app.Activity
import android.os.Bundle

class AssistEntryActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SeniorOverlayService.start(this, startListening = true)
        finish()
    }
}
```

- [ ] **Step 4: Create overlay window controller**

`android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt`:
```kotlin
package ai.milf.client.overlay

import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import ai.milf.client.ui.SeniorOverlayUi
import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner

class OverlayWindowController(
    private val context: Context,
    private val callbacks: Callbacks
) : LifecycleOwner, SavedStateRegistryOwner {
    interface Callbacks {
        fun onBubbleTap()
        fun onStopListening()
        fun onApprove()
        fun onDeny()
        fun onSpeakDecision()
        fun onWatchModeChange(enabled: Boolean)
        fun onRetry()
        fun onCallBuyer()
    }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private var composeView: ComposeView? = null
    private var params: WindowManager.LayoutParams? = null

    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry = savedStateController.savedStateRegistry

    fun show(initialState: SeniorUiState) {
        if (composeView != null) {
            update(initialState)
            return
        }
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        val view = ComposeView(context).also {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
            it.setOnTouchListener(DragTouchListener())
        }
        composeView = view
        params = paramsFor(initialState)
        view.setContent { SeniorOverlayUi(initialState, callbacks::onBubbleTap, callbacks::onStopListening, callbacks::onApprove, callbacks::onDeny, callbacks::onSpeakDecision, callbacks::onWatchModeChange, callbacks::onRetry, callbacks::onCallBuyer) }
        windowManager.addView(view, params)
    }

    fun update(state: SeniorUiState) {
        val view = composeView ?: return
        val next = paramsFor(state)
        val current = params
        if (current != null) {
            current.width = next.width
            current.height = next.height
            current.gravity = next.gravity
            params = current
            windowManager.updateViewLayout(view, current)
        }
        view.setContent { SeniorOverlayUi(state, callbacks::onBubbleTap, callbacks::onStopListening, callbacks::onApprove, callbacks::onDeny, callbacks::onSpeakDecision, callbacks::onWatchModeChange, callbacks::onRetry, callbacks::onCallBuyer) }
    }

    fun remove() {
        composeView?.let { windowManager.removeView(it) }
        composeView = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }

    private fun paramsFor(state: SeniorUiState): WindowManager.LayoutParams {
        val expanded = state.screen != SeniorUxScreen.Idle &&
            (state.screen != SeniorUxScreen.Working || state.watchMode || state.demoMode)
        return WindowManager.LayoutParams(
            if (expanded) WindowManager.LayoutParams.MATCH_PARENT else 96.dp,
            if (expanded) WindowManager.LayoutParams.MATCH_PARENT else 96.dp,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (expanded) Gravity.CENTER else Gravity.TOP or Gravity.END
            x = 24
            y = 240
        }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val current = params ?: return false
            if (current.width == WindowManager.LayoutParams.MATCH_PARENT) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = current.x
                    startY = current.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    current.x = startX - (event.rawX - downRawX).toInt()
                    current.y = startY + (event.rawY - downRawY).toInt()
                    windowManager.updateViewLayout(view, current)
                    return true
                }
            }
            return false
        }
    }
}

private val Int.dp: Int
    get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
```

- [ ] **Step 5: Create overlay service**

`android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`:
```kotlin
package ai.milf.client.overlay

import ai.milf.client.MilfApplication
import ai.milf.client.audio.ConfirmationVoiceRecognizer
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SeniorOverlayService : Service() {
    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null
    private var window: OverlayWindowController? = null
    private var confirmationVoice: ConfirmationVoiceRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        val controller = (application as MilfApplication).sessionController
        confirmationVoice = ConfirmationVoiceRecognizer(
            context = this,
            onText = controller::onConfirmationSpeech,
            onError = { }
        )
        window = OverlayWindowController(
            context = this,
            callbacks = object : OverlayWindowController.Callbacks {
                override fun onBubbleTap() = controller.beginListening()
                override fun onStopListening() = controller.finishListeningAndRun()
                override fun onApprove() = controller.approveConfirmation()
                override fun onDeny() = controller.denyConfirmation()
                override fun onSpeakDecision() {
                    controller.uiState.value.confirmation?.let {
                        confirmationVoice?.listen(it.lang)
                    }
                }
                override fun onWatchModeChange(enabled: Boolean) = controller.setWatchMode(enabled)
                override fun onRetry() = controller.retry()
                override fun onCallBuyer() {
                    // Implemented in Task 7.
                }
            }
        )
        collectJob = scope.launch {
            controller.uiState.collect { state ->
                window?.show(state)
                window?.update(state)
            }
        }
        controller.setOverlayEnabled(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val controller = (application as MilfApplication).sessionController
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra(EXTRA_START_LISTENING, false) == true) {
            controller.beginListening()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectJob?.cancel()
        confirmationVoice?.destroy()
        window?.remove()
        (application as MilfApplication).sessionController.setOverlayEnabled(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_START_LISTENING = "ai.milf.client.START_LISTENING"

        fun start(context: Context, startListening: Boolean = false) {
            val intent = Intent(context, SeniorOverlayService::class.java)
                .putExtra(EXTRA_START_LISTENING, startListening)
            context.startService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SeniorOverlayService::class.java))
        }
    }
}
```

- [ ] **Step 6: Add strings**

In `android/app/src/main/res/values/strings.xml`, add:
```xml
<string name="overlay_permission_label">Allow display over other apps</string>
<string name="assist_setup_label">Set MILF as assistant app</string>
<string name="overlay_running">MILF helper is ready</string>
```

- [ ] **Step 7: Build Android app**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 8: Manual overlay smoke test on emulator**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:installDebug
adb shell am start -n ai.milf.client/.MainActivity
```

Manual expected:
- Buyer setup opens.
- After overlay permission is granted, tapping "Start helper" shows a draggable 76dp bubble.
- Tapping the bubble expands to Listening.
- Pressing Stop sends the run to the backend if the backend is running.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/ai/milf/client/MilfApplication.kt android/app/src/main/java/ai/milf/client/assist/AssistEntryActivity.kt android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt android/app/src/main/java/ai/milf/client/overlay/OverlayWindowController.kt android/app/src/main/res/values/strings.xml
git commit -m "feat: add persistent senior overlay"
```

---

### Task 7: Buyer Setup, Permissions, and Rescue Call

Turn `MainActivity` into the buyer-only setup surface and wire State F's failure recovery path.

**Files:**
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`
- Create: `android/app/src/main/java/ai/milf/client/rescue/BuyerRescue.kt`
- Create: `android/app/src/test/java/ai/milf/client/rescue/BuyerRescueTest.kt`
- Modify: `android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt`

**Interfaces:**
- Buyer setup requests audio, call, overlay, accessibility, and assist settings.
- Setup can start and stop the helper overlay.
- Failure recovery uses `ACTION_CALL` when permission is granted; otherwise it opens `ACTION_DIAL`.

- [ ] **Step 1: Write buyer rescue tests**

`android/app/src/test/java/ai/milf/client/rescue/BuyerRescueTest.kt`:
```kotlin
package ai.milf.client.rescue

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class BuyerRescueTest {
    @Test
    fun buildsCallTargetWhenPermissionGranted() {
        val target = BuyerRescue.targetFor("+15551234567", hasCallPermission = true)

        assertEquals(Intent.ACTION_CALL, target.action)
        assertEquals("tel:+15551234567", target.uri)
    }

    @Test
    fun buildsDialTargetWhenPermissionMissing() {
        val target = BuyerRescue.targetFor("+15551234567", hasCallPermission = false)

        assertEquals(Intent.ACTION_DIAL, target.action)
        assertEquals("tel:+15551234567", target.uri)
    }
}
```

- [ ] **Step 2: Run rescue tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.rescue.BuyerRescueTest"
```
Expected: FAIL with unresolved reference `BuyerRescue`.

- [ ] **Step 3: Implement buyer rescue intent builder**

`android/app/src/main/java/ai/milf/client/rescue/BuyerRescue.kt`:
```kotlin
package ai.milf.client.rescue

import android.content.Intent
import android.net.Uri

data class BuyerRescueTarget(
    val action: String,
    val uri: String
)

object BuyerRescue {
    fun targetFor(phone: String, hasCallPermission: Boolean): BuyerRescueTarget =
        BuyerRescueTarget(
            action = if (hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL,
            uri = "tel:$phone"
        )

    fun intentFor(phone: String, hasCallPermission: Boolean): Intent {
        val target = targetFor(phone, hasCallPermission)
        return Intent(target.action).apply {
            data = Uri.parse(target.uri)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
```

- [ ] **Step 4: Wire rescue call into overlay service**

In `SeniorOverlayService`, import:
```kotlin
import ai.milf.client.rescue.BuyerRescue
import android.Manifest
import android.content.pm.PackageManager
```

Replace the Task 6 `onCallBuyer` body:
```kotlin
override fun onCallBuyer() {
    val contact = controller.uiState.value.failure?.recoveryContact ?: return
    val phone = contact.phone ?: return
    val hasCallPermission = checkSelfPermission(
        Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED
    startActivity(BuyerRescue.intentFor(phone, hasCallPermission))
}
```

- [ ] **Step 5: Implement buyer setup UI**

Replace `android/app/src/main/java/ai/milf/client/MilfUi.kt` with:
```kotlin
package ai.milf.client

import ai.milf.client.session.SeniorUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MilfUi(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAssistSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF8FAFC)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("MILF buyer setup", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = state.backendUrl,
                    onValueChange = onBackendUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Backend websocket") }
                )
                LanguageRow(state.lang, onLangChange)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Demo watch mode", fontSize = 20.sp)
                    Switch(checked = state.demoMode, onCheckedChange = onDemoModeChange)
                }
                PermissionButton("Microphone permission", onRequestAudioPermission)
                PermissionButton("Phone call permission", onRequestCallPermission)
                PermissionButton("Accessibility Service", onOpenAccessibility)
                PermissionButton("Display over other apps", onOpenOverlayPermission)
                PermissionButton("Default assistant app", onOpenAssistSettings)
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onStartOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text("Start helper", fontSize = 22.sp)
                }
                OutlinedButton(
                    onClick = onStopOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text("Stop helper", fontSize = 22.sp)
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Text(label, fontSize = 20.sp)
    }
}
```

Keep the existing `LanguageRow` function or move it below this code.

- [ ] **Step 6: Wire MainActivity permission actions**

In `MainActivity`, use the application controller through the ViewModel factory. Update the UI call:
```kotlin
MilfUi(
    state = state,
    onBackendUrlChange = viewModel::setBackendUrl,
    onLangChange = viewModel::setLang,
    onDemoModeChange = viewModel::setDemoMode,
    onOpenAccessibility = {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    },
    onOpenOverlayPermission = {
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
        )
    },
    onOpenAssistSettings = {
        startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
    },
    onRequestAudioPermission = {
        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    },
    onRequestCallPermission = {
        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
    },
    onStartOverlay = {
        SeniorOverlayService.start(this)
    },
    onStopOverlay = {
        SeniorOverlayService.stop(this)
    }
)
```

Create separate permission launchers:
```kotlin
val audioPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { }
val callPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { }
```

Remove old mic press and confirmation voice UI wiring from `MainActivity`; that now lives in the overlay.

- [ ] **Step 7: Update MainViewModel factory to use application singleton**

In `MainViewModel.Factory.create`:
```kotlin
val app = application as MilfApplication
return MainViewModel(app.sessionController) as T
```

- [ ] **Step 8: Run Android tests and build**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/main/java/ai/milf/client/MilfUi.kt android/app/src/main/java/ai/milf/client/rescue/BuyerRescue.kt android/app/src/test/java/ai/milf/client/rescue/BuyerRescueTest.kt android/app/src/main/java/ai/milf/client/overlay/SeniorOverlayService.kt
git commit -m "feat: add buyer setup and rescue call"
```

---

### Task 8: Demo Mode, Senior Mode, and UX Polish

Separate judge-visible watch mode from senior-calm mode and remove setup/debug controls from the senior path.

**Files:**
- Modify: `android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt`
- Modify: `android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt`
- Modify: `android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt`
- Modify: `docs/android-demo-runbook.md`

**Interfaces:**
- Senior mode: Working collapses to bubble plus narration.
- Demo mode: Working stays expanded with captions/waveform/watch status.
- The senior overlay contains no backend URL, setup, permission, or debug text.

- [ ] **Step 1: Add session tests for demo/watch mode**

Append to `MilfSessionControllerTest.kt`:
```kotlin
@Test
fun demoModeForcesWatchModeDuringWorking() = runTest {
    val client = FakeClient()
    val controller = fakeController(client)

    controller.setDemoMode(true)
    controller.beginListening()
    controller.finishListeningAndRun()

    val state = controller.uiState.value
    assertEquals(SeniorUxScreen.Working, state.screen)
    assertEquals(true, state.demoMode)
    assertEquals(true, state.watchMode)
}

@Test
fun seniorModeCanReturnToCalmBubbleDuringWorking() = runTest {
    val client = FakeClient()
    val controller = fakeController(client)

    controller.setDemoMode(false)
    controller.setWatchMode(false)
    controller.beginListening()
    controller.finishListeningAndRun()

    val state = controller.uiState.value
    assertEquals(SeniorUxScreen.Working, state.screen)
    assertEquals(false, state.demoMode)
    assertEquals(false, state.watchMode)
}
```

- [ ] **Step 2: Run demo mode tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.session.MilfSessionControllerTest"
```
Expected: PASS after Task 4 if `setDemoMode` already forces watch mode; otherwise FAIL and fix.

- [ ] **Step 3: Ensure senior UI has no setup/debug controls**

In `SeniorOverlayUi.kt`, verify only these strings appear in senior overlay states:
```text
MILF helper
Listening
Stop
Speak yes or no
NO
YES
Working
Done.
Cancel
Try again
```

Remove any senior-path text that mentions backend URL, permissions, accessibility setup, websocket, debug, or tests.

- [ ] **Step 4: Update runbook with UX demo path**

In `docs/android-demo-runbook.md`, add:
```markdown
## UX overlay demo path

1. Start the backend:
   ```bash
   cd backend
   python -m milf.server
   ```
2. Install the debug APK:
   ```bash
   cd android
   ./gradlew :app:installDebug
   ```
3. On the Android device, open MILF buyer setup.
4. Grant microphone, phone, overlay, and accessibility permissions.
5. Open default assistant settings and set MILF as the assistant app if the OEM allows it.
6. Turn on Demo watch mode for judges; leave it off for senior-mode testing.
7. Tap Start helper. The floating bubble should appear over any app.
8. Hero flow: tap bubble or invoke assistant, say "I want to see my grandson", wait for WhatsApp navigation, approve "Calling Wei, your grandson?", and verify the video-call screen opens.
9. Failure flow: stop the backend, repeat the hero request, and verify the failure screen shows the safe failure copy with Try again and Cancel.

## Device matrix

Record these results for each target device:

| Device | Android version | Overlay works | Assist invocation works | Accessibility actions work | Notes |
|---|---:|---|---|---|---|
| Emulator Pixel API 35 | 15 | yes | partial | yes | assist gesture depends on emulator settings |
```

During Task 9, add one row per physical device only after the device has been tested. Use measured values such as `yes`, `no`, or `partial`, and include the exact device model in the first column.

- [ ] **Step 5: Run all local tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest -v

cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/session/MilfSessionController.kt android/app/src/main/java/ai/milf/client/ui/SeniorOverlayUi.kt android/app/src/test/java/ai/milf/client/session/MilfSessionControllerTest.kt docs/android-demo-runbook.md
git commit -m "polish: separate demo and senior modes"
```

---

### Task 9: End-to-End UX Verification

Verify the UX state machine against the spec and capture device-specific risks before calling implementation complete.

**Files:**
- Modify: `docs/android-demo-runbook.md`

**Verification Matrix:**
- A Idle: bubble visible, draggable, at least 72dp visual target.
- B Listening: waveform animates, captions visible, TTS prompt plays, Stop is obvious.
- C Confirmation: contact face/avatar visible, spoken summary plays, green YES and red NO are distinct by color, icon, label, and position.
- D Working senior mode: overlay collapses to bubble with narration.
- D Working demo mode: watch view remains visible.
- E Success: calm completion copy is spoken and visible.
- F Failure: safe copy is spoken and visible; "Cancel" dismisses; "Try again" returns to listening.
- Invocation layer 1: assist entry works or device-specific failure is recorded.
- Invocation layer 2: bubble works from inside another app.

- [ ] **Step 1: Run automated tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest -v

cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS.

- [ ] **Step 2: Run backend and install APK**

Terminal 1:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
python -m milf.server
```

Terminal 2:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:installDebug
```

Expected: backend listens on `0.0.0.0:8765`; APK installs.

- [ ] **Step 3: Senior-mode manual test**

On device:
1. Open MILF buyer setup.
2. Turn Demo watch mode off.
3. Start helper.
4. Open WhatsApp or any other app.
5. Tap the helper bubble.
6. Say "I want to see my grandson."
7. Tap Stop.
8. Approve the confirmation.

Expected:
- Bubble is visible over the other app.
- Listening expands with waveform and captions.
- Working collapses to bubble with narration.
- Confirmation is full-screen and waits indefinitely.
- YES resumes the agent.
- Success copy is spoken and visible.

- [ ] **Step 4: Demo-mode manual test**

On device:
1. Return to MILF buyer setup.
2. Turn Demo watch mode on.
3. Start helper.
4. Repeat the hero flow.

Expected:
- Working remains expanded enough for judges to see progress.
- No setup/debug controls appear in the senior overlay.

- [ ] **Step 5: Failure manual test**

On device:
1. Stop the backend server.
2. Start a new helper run.
3. Say "I want to see my grandson."
4. Tap Stop.

Expected:
- Failure screen says: "I'm having a little trouble with that. Please try again."
- "Cancel" dismisses the failure screen.
- "Try again" returns to Listening.

- [ ] **Step 6: Record device matrix**

Update the table in `docs/android-demo-runbook.md` with the actual results from the emulator and the first physical target device. Do not mark assist invocation as working unless the power/assistant gesture actually opens `AssistEntryActivity`.

- [ ] **Step 7: Final commit**

```bash
git add docs/android-demo-runbook.md
git commit -m "docs: record UX overlay verification"
```

---

## Self-Review

Spec coverage:
- State A is implemented by `SeniorOverlayService` and `OverlayWindowController`.
- State B is implemented by `SeniorOverlayUi.ListeningOverlay` and `MilfSessionController.beginListening`.
- State C is implemented by contact-aware `ConfirmRequest`, `RelationshipGraph`, and `ConfirmationGate`.
- State D is implemented by `WorkingOverlay`, `watchMode`, and `demoMode`.
- State E is implemented by `TaskComplete` and `SuccessOverlay`.
- State F is implemented by `TaskFailure`, `FailureOverlay`, and `BuyerRescue`.
- Buyer setup is implemented as `MainActivity` + `MilfUi`.
- Relationship graph exists on backend and Android with matching contact ids.
- Invocation layers 1 and 2 are covered by `AssistEntryActivity` and the overlay bubble.
- Accessibility constraints are represented through large type, captions-on-default, TTS in every state, and primary 72dp controls.

Known execution risks:
- Some OEMs may not route power-button assistant invocation to `ACTION_ASSIST`; the floating bubble is the required fallback.
- `SYSTEM_ALERT_WINDOW` requires explicit user approval and may be affected by OEM battery/overlay restrictions.
- Direct call requires `CALL_PHONE`; without it, rescue falls back to the dialer.
- A real contact photo and real buyer phone number should be supplied before a public demo build.

Placeholder scan:
- No implementation step uses unresolved placeholder markers or undefined deferred work.
- Debug-safe phone value is explicit and intentionally non-production.
- The runbook table records the known emulator row first; physical-device rows are added only after testing.

Type consistency:
- Backend JSON uses `contact_id` and `recovery_contact_id`.
- Android Kotlin uses `contactId` and `recoveryContactId` with matching JSON keys.
- Contact id values are `wei-grandson` and `buyer-daughter` across backend, Android, and tests.
