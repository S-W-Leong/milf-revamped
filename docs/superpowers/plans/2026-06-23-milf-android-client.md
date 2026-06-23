# MILF Android Client Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Android client that records the senior's spoken goal, connects to the implemented MILF backend over websocket, exposes on-device accessibility actions, speaks backend narration, and blocks on an explicit confirmation before the WhatsApp video call connects.

**Architecture:** A Kotlin Android app owns the senior-facing Compose UI, audio capture, TextToSpeech narration, websocket session, and confirmation state. An `AccessibilityService` provides the backend action surface (`get_ui_tree`, `screenshot`, `tap`, `swipe`, `input_text`, `press_button`, `start_app`) through a small action dispatcher. The backend remains the agent brain; the app is a typed, JSON-speaking device peer.

**Tech Stack:** Kotlin, Android Gradle Plugin 8.7.2, Kotlin 2.0.0, Jetpack Compose Material 3, AndroidX lifecycle, OkHttp websocket, Android TextToSpeech, Android AccessibilityService APIs, JUnit.

---

## Global Constraints

- Build the actual Android client. Do not make a web landing page or marketing screen.
- Keep the hero flow first: spoken goal -> backend run -> WhatsApp navigation -> spoken confirmation -> approve/deny.
- Client websocket protocol must match `backend/milf/protocol.py` exactly:
  - Envelope: `{"type": "<ClassName>", "data": {...}}`
  - App sends first: `Audio(goal_audio_b64, lang)`
  - Backend sends: `Action`, `Narration`, `ConfirmRequest`
  - App sends: `ActionResult`, `ConfirmResponse`
- Use a native Android APK controlling the user's own phone. Do not introduce ADB, MobileRun cloud devices, or a laptop-side driver.
- Main intent STT stays on the backend. The app only records and uploads audio bytes.
- On-device Android TextToSpeech is used for narration.
- Use button confirmation as the reliable path and add voice yes/no as the secondary confirmation input once the button path is green.
- All irreversible actions must wait for `ConfirmRequest` -> `ConfirmResponse`.
- No contact discovery scope creep. The backend owns seeded contact resolution.
- Keep code small and testable. Protocol, websocket session, audio, narration, accessibility actions, and UI state each get their own file.
- The current machine has Android SDK platforms `android-35` and `android-36`, AGP `8.7.2` cached, OkHttp `4.12.0` cached, Compose `1.7.6` cached, lifecycle `2.8.7` cached, and Kotlin `2.0.0` cached. There is no `gradle` binary on PATH, so create or use a Gradle wrapper before running builds.

## File Structure

### Android Project

- Create: `android/settings.gradle.kts` - Gradle project definition.
- Create: `android/build.gradle.kts` - root plugin versions.
- Create: `android/gradle.properties` - AndroidX and Kotlin build flags.
- Create: `android/app/build.gradle.kts` - app module config and dependencies.
- Create: `android/app/src/main/AndroidManifest.xml` - app permissions, activity, accessibility service.
- Create: `android/app/src/main/res/values/strings.xml` - app labels and service description.
- Create: `android/app/src/main/res/values/styles.xml` - minimal Activity theme.
- Create: `android/app/src/main/res/xml/milf_accessibility_service.xml` - accessibility capability declaration.
- Create: `android/app/src/main/java/ai/milf/client/MainActivity.kt` - Compose activity.
- Create: `android/app/src/main/java/ai/milf/client/MainViewModel.kt` - UI state, recording flow, websocket run orchestration.
- Create: `android/app/src/main/java/ai/milf/client/MilfUi.kt` - senior-facing Compose surface.
- Create: `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt` - typed message models and JSON encode/decode.
- Create: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt` - OkHttp websocket session.
- Create: `android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt` - records the goal to M4A bytes.
- Create: `android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt` - Android TextToSpeech wrapper.
- Create: `android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt` - listens for yes/no confirmation phrases.
- Create: `android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceParser.kt` - maps short confirmation phrases to approve/deny.
- Create: `android/app/src/main/java/ai/milf/client/accessibility/MilfAccessibilityService.kt` - service singleton and Android API bridge.
- Create: `android/app/src/main/java/ai/milf/client/accessibility/ActionDispatcher.kt` - converts backend `Action` into Android operations.
- Create: `android/app/src/main/java/ai/milf/client/accessibility/UiTreeSerializer.kt` - serializes the accessibility tree.
- Create: `android/app/src/main/java/ai/milf/client/accessibility/ScreenshotCapture.kt` - captures and base64-encodes PNG screenshots.
- Create: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`
- Create: `android/app/src/test/java/ai/milf/client/accessibility/ActionDispatcherTest.kt`
- Create: `android/app/src/test/java/ai/milf/client/MainViewModelTest.kt`

### Backend Compatibility

- Modify: `backend/milf/ws_driver.py` - decode screenshot base64 strings from the app into bytes before returning to MobileRun.
- Test: `backend/tests/test_ws_driver.py` - assert screenshot base64 is decoded.

---

### Task 1: Android scaffold and manifest

Create a minimal native Android project under `android/` with the app permissions and service declarations needed by the spec.

**Files:**
- Create: `android/settings.gradle.kts`
- Create: `android/build.gradle.kts`
- Create: `android/gradle.properties`
- Create: `android/app/build.gradle.kts`
- Create: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/values/strings.xml`
- Create: `android/app/src/main/res/values/styles.xml`
- Create: `android/app/src/main/res/xml/milf_accessibility_service.xml`

**Interfaces:**
- Produces package `ai.milf.client`.
- Produces a launchable `MainActivity`.
- Registers `MilfAccessibilityService` with `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Requests `RECORD_AUDIO`, `INTERNET`, and `FOREGROUND_SERVICE_MICROPHONE`.

- [ ] **Step 1: Create Gradle settings**

`android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MilfAndroidClient"
include(":app")
```

- [ ] **Step 2: Create root Gradle plugins**

`android/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0" apply false
}
```

- [ ] **Step 3: Create Gradle properties**

`android/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 4: Create app module build**

`android/app/build.gradle.kts`:
```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ai.milf.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "ai.milf.client"
        minSdk = 30
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui-android:1.7.6")
    implementation("androidx.compose.material3:material3-android:1.3.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 5: Create string resources**

`android/app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">MILF</string>
    <string name="accessibility_service_label">MILF phone control</string>
    <string name="accessibility_service_description">Lets MILF act on spoken requests after you start a session.</string>
</resources>
```

- [ ] **Step 6: Create styles XML**

`android/app/src/main/res/values/styles.xml`:
```xml
<resources>
    <style name="Theme.MILF" parent="@android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowNoTitle">true</item>
        <item name="android:windowActionBar">false</item>
    </style>
</resources>
```

- [ ] **Step 7: Create accessibility service XML**

`android/app/src/main/res/xml/milf_accessibility_service.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowContentChanged|typeWindowStateChanged|typeViewClicked|typeViewFocused"
    android:accessibilityFeedbackType="feedbackSpoken"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRetrieveWindowContent="true"
    android:canTakeScreenshot="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="50" />
```

- [ ] **Step 8: Create manifest**

`android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />

    <application
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MILF">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".accessibility.MilfAccessibilityService"
            android:exported="true"
            android:label="@string/accessibility_service_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/milf_accessibility_service" />
        </service>
    </application>
</manifest>
```

- [ ] **Step 9: Generate or copy a Gradle wrapper**

Run one of these from the repo root:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
gradle wrapper --gradle-version 8.10.2
```

If `gradle` is not installed, open `android/` in Android Studio once and let it generate/use a wrapper, then confirm:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew --version
```
Expected: Gradle runs and prints a version.

- [ ] **Step 10: Run scaffold build**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:assembleDebug
```
Expected: build fails only because source classes referenced in the manifest do not exist yet, or passes after later tasks.

- [ ] **Step 11: Commit**

```bash
git add android/settings.gradle.kts android/build.gradle.kts android/gradle.properties android/app/build.gradle.kts android/app/src/main/AndroidManifest.xml android/app/src/main/res/values/strings.xml android/app/src/main/res/values/styles.xml android/app/src/main/res/xml/milf_accessibility_service.xml
git commit -m "chore: scaffold Android client"
```

---

### Task 2: Protocol models and backend screenshot compatibility

Pin the JSON protocol on the Android side and close the one backend/client mismatch: screenshots must cross JSON as base64 strings, then become bytes for MobileRun.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`
- Create: `android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`
- Modify: `backend/milf/ws_driver.py`
- Modify: `backend/tests/test_ws_driver.py`

**Interfaces:**
- Produces `MilfMessage` sealed interface and data classes `Action`, `ActionResult`, `Narration`, `ConfirmRequest`, `ConfirmResponse`, `Audio`.
- Produces `MilfProtocol.encode(message: MilfMessage): String`.
- Produces `MilfProtocol.decode(raw: String): MilfMessage`.
- Backend `WebSocketDriver.screenshot()` returns decoded PNG bytes when the app result is a base64 string.

- [ ] **Step 1: Write Android protocol tests**

`android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt`:
```kotlin
package ai.milf.client.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MilfProtocolTest {
    @Test
    fun actionRoundTrips() {
        val raw = MilfProtocol.encode(
            Action(id = "1", name = "tap", args = mapOf("x" to 10, "y" to 20))
        )
        val decoded = MilfProtocol.decode(raw)
        assertTrue(decoded is Action)
        assertEquals("tap", (decoded as Action).name)
        assertEquals(10, decoded.args["x"])
    }

    @Test
    fun confirmResponseRoundTrips() {
        val raw = MilfProtocol.encode(ConfirmResponse(id = "c1", approved = true))
        assertEquals(ConfirmResponse(id = "c1", approved = true), MilfProtocol.decode(raw))
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownTypeRaises() {
        MilfProtocol.decode("""{"type":"Nope","data":{}}""")
    }
}
```

- [ ] **Step 2: Run Android protocol tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.protocol.MilfProtocolTest"
```
Expected: FAIL with unresolved reference `MilfProtocol`.

- [ ] **Step 3: Implement Android protocol models**

`android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt`:
```kotlin
package ai.milf.client.protocol

import org.json.JSONObject

sealed interface MilfMessage

data class Action(
    val id: String,
    val name: String,
    val args: Map<String, Any?>
) : MilfMessage

data class ActionResult(
    val id: String,
    val ok: Boolean,
    val result: Any? = null,
    val error: String? = null
) : MilfMessage

data class Narration(
    val text: String,
    val lang: String
) : MilfMessage

data class ConfirmRequest(
    val id: String,
    val summary: String,
    val lang: String
) : MilfMessage

data class ConfirmResponse(
    val id: String,
    val approved: Boolean
) : MilfMessage

data class Audio(
    val goalAudioB64: String,
    val lang: String
) : MilfMessage

object MilfProtocol {
    fun encode(message: MilfMessage): String {
        val data = when (message) {
            is Action -> JSONObject()
                .put("id", message.id)
                .put("name", message.name)
                .put("args", JSONObject(message.args))
            is ActionResult -> JSONObject()
                .put("id", message.id)
                .put("ok", message.ok)
                .put("result", message.result)
                .put("error", message.error)
            is Narration -> JSONObject()
                .put("text", message.text)
                .put("lang", message.lang)
            is ConfirmRequest -> JSONObject()
                .put("id", message.id)
                .put("summary", message.summary)
                .put("lang", message.lang)
            is ConfirmResponse -> JSONObject()
                .put("id", message.id)
                .put("approved", message.approved)
            is Audio -> JSONObject()
                .put("goal_audio_b64", message.goalAudioB64)
                .put("lang", message.lang)
        }
        return JSONObject()
            .put("type", typeName(message))
            .put("data", data)
            .toString()
    }

    fun decode(raw: String): MilfMessage {
        val envelope = JSONObject(raw)
        val type = envelope.getString("type")
        val data = envelope.getJSONObject("data")
        return when (type) {
            "Action" -> Action(
                id = data.getString("id"),
                name = data.getString("name"),
                args = data.getJSONObject("args").toMap()
            )
            "ActionResult" -> ActionResult(
                id = data.getString("id"),
                ok = data.getBoolean("ok"),
                result = data.optNullable("result"),
                error = data.optStringOrNull("error")
            )
            "Narration" -> Narration(
                text = data.getString("text"),
                lang = data.getString("lang")
            )
            "ConfirmRequest" -> ConfirmRequest(
                id = data.getString("id"),
                summary = data.getString("summary"),
                lang = data.getString("lang")
            )
            "ConfirmResponse" -> ConfirmResponse(
                id = data.getString("id"),
                approved = data.getBoolean("approved")
            )
            "Audio" -> Audio(
                goalAudioB64 = data.getString("goal_audio_b64"),
                lang = data.getString("lang")
            )
            else -> throw IllegalArgumentException("Unknown message type: $type")
        }
    }

    private fun typeName(message: MilfMessage): String = when (message) {
        is Action -> "Action"
        is ActionResult -> "ActionResult"
        is Narration -> "Narration"
        is ConfirmRequest -> "ConfirmRequest"
        is ConfirmResponse -> "ConfirmResponse"
        is Audio -> "Audio"
    }
}

private fun JSONObject.toMap(): Map<String, Any?> {
    val out = linkedMapOf<String, Any?>()
    keys().forEach { key ->
        out[key] = optNullable(key)
    }
    return out
}

private fun JSONObject.optNullable(key: String): Any? =
    if (!has(key) || isNull(key)) null else get(key)

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else getString(key)
```

- [ ] **Step 4: Run Android protocol tests to verify pass**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.protocol.MilfProtocolTest"
```
Expected: 3 tests passed.

- [ ] **Step 5: Write backend screenshot decode test**

Append to `backend/tests/test_ws_driver.py`:
```python
async def test_screenshot_decodes_base64_payload_to_bytes():
    import base64

    sent, conn = _wire()
    driver = WebSocketDriver(conn)
    task = asyncio.create_task(driver.screenshot())
    await asyncio.sleep(0)
    action = decode(sent[0])
    payload = base64.b64encode(b"png-bytes").decode("ascii")
    conn.on_message(encode(ActionResult(id=action.id, ok=True, result=payload)))
    assert await task == b"png-bytes"
```

- [ ] **Step 6: Run backend test to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_ws_driver.py::test_screenshot_decodes_base64_payload_to_bytes -v
```
Expected: FAIL because `driver.screenshot()` returns a string.

- [ ] **Step 7: Patch backend screenshot decode**

Modify `backend/milf/ws_driver.py`:
```python
import base64
```

Replace `screenshot()` with:
```python
    async def screenshot(self, hide_overlay: bool = True) -> bytes:
        payload = await self._send_supported("screenshot", {"hide_overlay": hide_overlay})
        if isinstance(payload, str):
            return base64.b64decode(payload)
        if isinstance(payload, bytes):
            return payload
        raise TypeError("screenshot action returned non-bytes payload")
```

- [ ] **Step 8: Run backend driver tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest tests/test_ws_driver.py -v
```
Expected: all tests in `test_ws_driver.py` pass.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/protocol/MilfProtocol.kt android/app/src/test/java/ai/milf/client/protocol/MilfProtocolTest.kt backend/milf/ws_driver.py backend/tests/test_ws_driver.py
git commit -m "feat: share Android websocket protocol"
```

---

### Task 3: Websocket session client

Implement a small OkHttp client that opens the backend websocket, sends the first `Audio` frame, handles backend messages, and sends action/confirmation responses.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`
- Create: `android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`

**Interfaces:**
- Consumes protocol models from Task 2.
- Produces `MilfWebSocketClient.start(goalAudio: ByteArray, lang: String, callbacks: Callbacks)`.
- `Callbacks.onAction(action)` returns `ActionResult`.
- `Callbacks.onNarration(narration)` speaks/updates UI.
- `Callbacks.onConfirmRequest(request)` displays UI and eventually returns `ConfirmResponse`.

- [ ] **Step 1: Write websocket client tests with a fake socket sink**

`android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt`:
```kotlin
package ai.milf.client.ws

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.MilfProtocol
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MilfWebSocketClientTest {
    @Test
    fun handlesActionAndSendsResult() = runTest {
        val sent = mutableListOf<String>()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = FakeSocketFactory(sent),
            scope = this
        )
        client.start(
            goalAudio = byteArrayOf(1, 2, 3),
            lang = "en",
            callbacks = object : MilfWebSocketClient.Callbacks {
                override suspend fun onAction(action: Action): ActionResult {
                    return ActionResult(id = action.id, ok = true, result = "ok")
                }
                override suspend fun onNarration(text: String, lang: String) = Unit
                override suspend fun onConfirmRequest(id: String, summary: String, lang: String) = Unit
                override fun onClosed(reason: String?) = Unit
                override fun onFailed(message: String) = Unit
            }
        )

        client.handleText(MilfProtocol.encode(Action(id = "a1", name = "tap", args = emptyMap())))
        advanceUntilIdle()

        val result = MilfProtocol.decode(sent.last()) as ActionResult
        assertEquals("a1", result.id)
        assertEquals(true, result.ok)
    }
}

private class FakeSocketFactory(
    private val sent: MutableList<String>
) : MilfWebSocketClient.SocketFactory {
    override fun open(url: String, listener: MilfWebSocketClient.TextListener): MilfWebSocketClient.Socket {
        return object : MilfWebSocketClient.Socket {
            override fun send(text: String): Boolean {
                sent += text
                return true
            }
            override fun close() = Unit
        }
    }
}
```

- [ ] **Step 2: Run websocket test to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.ws.MilfWebSocketClientTest"
```
Expected: FAIL with unresolved reference `MilfWebSocketClient`.

- [ ] **Step 3: Implement websocket client**

`android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt`:
```kotlin
package ai.milf.client.ws

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.Audio
import ai.milf.client.protocol.ConfirmRequest
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.protocol.MilfProtocol
import ai.milf.client.protocol.Narration
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class MilfWebSocketClient(
    private val url: String,
    private val socketFactory: SocketFactory = OkHttpSocketFactory(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    interface Callbacks {
        suspend fun onAction(action: Action): ActionResult
        suspend fun onNarration(text: String, lang: String)
        suspend fun onConfirmRequest(id: String, summary: String, lang: String)
        fun onClosed(reason: String?)
        fun onFailed(message: String)
    }

    interface TextListener {
        fun onOpen()
        fun onText(text: String)
        fun onClosed(reason: String?)
        fun onFailure(message: String)
    }

    interface Socket {
        fun send(text: String): Boolean
        fun close()
    }

    interface SocketFactory {
        fun open(url: String, listener: TextListener): Socket
    }

    private var socket: Socket? = null
    private var callbacks: Callbacks? = null

    fun start(goalAudio: ByteArray, lang: String, callbacks: Callbacks) {
        this.callbacks = callbacks
        socket = socketFactory.open(url, object : TextListener {
            override fun onOpen() {
                val b64 = Base64.encodeToString(goalAudio, Base64.NO_WRAP)
                send(Audio(goalAudioB64 = b64, lang = lang))
            }

            override fun onText(text: String) {
                handleText(text)
            }

            override fun onClosed(reason: String?) {
                callbacks.onClosed(reason)
            }

            override fun onFailure(message: String) {
                callbacks.onFailed(message)
            }
        })
    }

    fun handleText(text: String) {
        val cb = callbacks ?: return
        when (val message = MilfProtocol.decode(text)) {
            is Action -> scope.launch {
                val result = runCatching { cb.onAction(message) }
                    .getOrElse { ActionResult(id = message.id, ok = false, error = it.message) }
                send(result)
            }
            is Narration -> scope.launch {
                cb.onNarration(message.text, message.lang)
            }
            is ConfirmRequest -> scope.launch {
                cb.onConfirmRequest(message.id, message.summary, message.lang)
            }
            is ActionResult, is ConfirmResponse, is Audio -> Unit
        }
    }

    fun send(message: MilfMessage): Boolean {
        return socket?.send(MilfProtocol.encode(message)) == true
    }

    fun close() {
        socket?.close()
        socket = null
        callbacks = null
    }
}

private class OkHttpSocketFactory : MilfWebSocketClient.SocketFactory {
    private val client = OkHttpClient()

    override fun open(url: String, listener: MilfWebSocketClient.TextListener): MilfWebSocketClient.Socket {
        val request = Request.Builder().url(url).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                listener.onText(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                listener.onClosed(reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                listener.onFailure(t.message ?: "websocket failed")
            }
        })
        return object : MilfWebSocketClient.Socket {
            override fun send(text: String): Boolean = ws.send(text)
            override fun close() {
                ws.close(1000, "client closing")
            }
        }
    }
}
```

- [ ] **Step 4: Run websocket tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.ws.MilfWebSocketClientTest"
```
Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/ws/MilfWebSocketClient.kt android/app/src/test/java/ai/milf/client/ws/MilfWebSocketClientTest.kt
git commit -m "feat: add Android websocket session"
```

---

### Task 4: Audio recorder and TTS narrator

Add the voice surfaces required by the spec: record goal audio to bytes, then speak backend narration with Android TextToSpeech.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt`
- Create: `android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt`

**Interfaces:**
- Produces `AudioRecorder.start()` and `AudioRecorder.stop(): ByteArray`.
- Produces `TtsNarrator.speak(text, lang)` and `TtsNarrator.stop()`.

- [ ] **Step 1: Implement audio recorder**

`android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt`:
```kotlin
package ai.milf.client.audio

import android.content.Context
import android.media.MediaRecorder
import java.io.File

class AudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start() {
        stopSilently()
        val file = File.createTempFile("milf-goal-", ".m4a", context.cacheDir)
        outputFile = file
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop(): ByteArray {
        val active = recorder ?: error("Recorder is not running")
        active.stop()
        active.release()
        recorder = null
        val file = outputFile ?: error("Recording file missing")
        val bytes = file.readBytes()
        file.delete()
        outputFile = null
        return bytes
    }

    fun cancel() {
        stopSilently()
    }

    private fun stopSilently() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
```

- [ ] **Step 2: Implement TTS narrator**

`android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt`:
```kotlin
package ai.milf.client.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsNarrator(
    context: Context
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.ENGLISH
        }
    }

    fun speak(text: String, lang: String) {
        if (!ready || text.isBlank()) return
        tts.language = localeFor(lang)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "milf-${System.nanoTime()}")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private fun localeFor(lang: String): Locale = when (lang) {
        "yue" -> Locale.CHINESE
        "manglish" -> Locale("ms", "MY")
        "ms" -> Locale("ms", "MY")
        else -> Locale.ENGLISH
    }
}
```

- [ ] **Step 3: Build compile check**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:assembleDebug
```
Expected: build fails only for remaining missing source files from later tasks, or passes after those tasks are complete.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/audio/AudioRecorder.kt android/app/src/main/java/ai/milf/client/audio/TtsNarrator.kt
git commit -m "feat: add Android audio surfaces"
```

---

### Task 5: Accessibility action bridge

Implement the Android device I/O surface consumed by the backend `WebSocketDriver`.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/accessibility/MilfAccessibilityService.kt`
- Create: `android/app/src/main/java/ai/milf/client/accessibility/ActionDispatcher.kt`
- Create: `android/app/src/main/java/ai/milf/client/accessibility/UiTreeSerializer.kt`
- Create: `android/app/src/main/java/ai/milf/client/accessibility/ScreenshotCapture.kt`
- Create: `android/app/src/test/java/ai/milf/client/accessibility/ActionDispatcherTest.kt`

**Interfaces:**
- Produces `ActionDispatcher.dispatch(action: Action): ActionResult`.
- `tap`, `swipe`, and `screenshot` are backed by Android AccessibilityService APIs.
- `press_button` supports `back`, `home`, and `enter`.
- `start_app` launches a package via `PackageManager`.
- `get_ui_tree` returns JSON-safe maps.

- [ ] **Step 1: Write dispatcher tests for unsupported action**

`android/app/src/test/java/ai/milf/client/accessibility/ActionDispatcherTest.kt`:
```kotlin
package ai.milf.client.accessibility

import ai.milf.client.protocol.Action
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ActionDispatcherTest {
    @Test
    fun unsupportedActionFails() = runTest {
        val dispatcher = ActionDispatcher(FakeDeviceActions())
        val result = dispatcher.dispatch(Action("1", "install_app", emptyMap()))
        assertEquals(false, result.ok)
        assertEquals("1", result.id)
    }

    @Test
    fun tapCallsDeviceActions() = runTest {
        val fake = FakeDeviceActions()
        val dispatcher = ActionDispatcher(fake)
        val result = dispatcher.dispatch(Action("t1", "tap", mapOf("x" to 10, "y" to 20)))
        assertEquals(true, result.ok)
        assertEquals(listOf("tap:10,20"), fake.calls)
    }
}

private class FakeDeviceActions : DeviceActions {
    val calls = mutableListOf<String>()
    override suspend fun tap(x: Int, y: Int) {
        calls += "tap:$x,$y"
    }
    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) = Unit
    override suspend fun inputText(text: String, clear: Boolean): Boolean = true
    override suspend fun pressButton(button: String): Boolean = true
    override suspend fun startApp(packageName: String, activity: String?): String = packageName
    override suspend fun screenshot(hideOverlay: Boolean): String = "png"
    override suspend fun getUiTree(): Map<String, Any?> = emptyMap()
}
```

- [ ] **Step 2: Run dispatcher tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.accessibility.ActionDispatcherTest"
```
Expected: FAIL with unresolved reference `ActionDispatcher`.

- [ ] **Step 3: Implement action dispatcher**

`android/app/src/main/java/ai/milf/client/accessibility/ActionDispatcher.kt`:
```kotlin
package ai.milf.client.accessibility

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult

interface DeviceActions {
    suspend fun tap(x: Int, y: Int)
    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long)
    suspend fun inputText(text: String, clear: Boolean): Boolean
    suspend fun pressButton(button: String): Boolean
    suspend fun startApp(packageName: String, activity: String?): String
    suspend fun screenshot(hideOverlay: Boolean): String
    suspend fun getUiTree(): Map<String, Any?>
}

class ActionDispatcher(
    private val device: DeviceActions?
) {
    suspend fun dispatch(action: Action): ActionResult {
        if (device == null) {
            return ActionResult(action.id, ok = false, error = "Accessibility service is not enabled")
        }
        return runCatching {
            when (action.name) {
                "tap" -> {
                    device.tap(action.intArg("x"), action.intArg("y"))
                    null
                }
                "swipe" -> {
                    device.swipe(
                        action.intArg("x1"),
                        action.intArg("y1"),
                        action.intArg("x2"),
                        action.intArg("y2"),
                        action.longArg("duration_ms", 1000L)
                    )
                    null
                }
                "input_text" -> device.inputText(
                    text = action.stringArg("text"),
                    clear = action.boolArg("clear", false)
                )
                "press_button" -> device.pressButton(action.stringArg("button"))
                "start_app" -> device.startApp(
                    packageName = action.stringArg("package"),
                    activity = action.nullableStringArg("activity")
                )
                "screenshot" -> device.screenshot(action.boolArg("hide_overlay", true))
                "get_ui_tree" -> device.getUiTree()
                else -> error("Unsupported action: ${action.name}")
            }
        }.fold(
            onSuccess = { ActionResult(action.id, ok = true, result = it) },
            onFailure = { ActionResult(action.id, ok = false, error = it.message) }
        )
    }
}

private fun Action.intArg(name: String): Int = when (val value = args[name]) {
    is Int -> value
    is Long -> value.toInt()
    is Double -> value.toInt()
    is Number -> value.toInt()
    else -> error("Missing int arg: $name")
}

private fun Action.longArg(name: String, default: Long): Long = when (val value = args[name]) {
    null -> default
    is Long -> value
    is Int -> value.toLong()
    is Double -> value.toLong()
    is Number -> value.toLong()
    else -> error("Invalid long arg: $name")
}

private fun Action.stringArg(name: String): String =
    args[name] as? String ?: error("Missing string arg: $name")

private fun Action.nullableStringArg(name: String): String? =
    args[name] as? String

private fun Action.boolArg(name: String, default: Boolean): Boolean =
    args[name] as? Boolean ?: default
```

- [ ] **Step 4: Implement accessibility service**

`android/app/src/main/java/ai/milf/client/accessibility/MilfAccessibilityService.kt`:
```kotlin
package ai.milf.client.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MilfAccessibilityService : AccessibilityService(), DeviceActions {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override suspend fun tap(x: Int, y: Int) {
        dispatchPath {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
    }

    override suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long) {
        dispatchPath(durationMs) {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
    }

    override suspend fun inputText(text: String, clear: Boolean): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return false
        if (clear) {
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
            )
        }
        return node.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
        )
    }

    override suspend fun pressButton(button: String): Boolean = when (button) {
        "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "enter" -> rootInActiveWindow
            ?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?.performAction(AccessibilityNodeInfo.ACTION_IME_ENTER) == true
        else -> false
    }

    override suspend fun startApp(packageName: String, activity: String?): String {
        val intent = if (activity != null) {
            Intent().setClassName(packageName, activity)
        } else {
            packageManager.getLaunchIntentForPackage(packageName)
                ?: error("No launch intent for package: $packageName")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        return packageName
    }

    override suspend fun screenshot(hideOverlay: Boolean): String {
        return ScreenshotCapture.captureBase64Png(this)
    }

    override suspend fun getUiTree(): Map<String, Any?> {
        return UiTreeSerializer.serialize(rootInActiveWindow)
    }

    private suspend fun dispatchPath(
        durationMs: Long = 50L,
        build: Path.() -> Unit
    ) = suspendCancellableCoroutine<Unit> { cont ->
        val path = Path().apply(build)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.resume(Unit)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                if (cont.isActive) cont.cancel()
            }
        }, null)
    }

    companion object {
        @Volatile
        var instance: MilfAccessibilityService? = null
            private set
    }
}
```

- [ ] **Step 5: Implement UI tree serializer**

`android/app/src/main/java/ai/milf/client/accessibility/UiTreeSerializer.kt`:
```kotlin
package ai.milf.client.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

object UiTreeSerializer {
    fun serialize(root: AccessibilityNodeInfo?): Map<String, Any?> {
        if (root == null) return mapOf("nodes" to emptyList<Map<String, Any?>>())
        val nodes = mutableListOf<Map<String, Any?>>()
        visit(root, path = "0", nodes = nodes)
        return mapOf("nodes" to nodes)
    }

    private fun visit(
        node: AccessibilityNodeInfo,
        path: String,
        nodes: MutableList<Map<String, Any?>>
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        nodes += mapOf(
            "id" to path,
            "text" to node.text?.toString(),
            "contentDescription" to node.contentDescription?.toString(),
            "viewIdResourceName" to node.viewIdResourceName,
            "className" to node.className?.toString(),
            "packageName" to node.packageName?.toString(),
            "clickable" to node.isClickable,
            "enabled" to node.isEnabled,
            "focused" to node.isFocused,
            "selected" to node.isSelected,
            "bounds" to mapOf(
                "left" to bounds.left,
                "top" to bounds.top,
                "right" to bounds.right,
                "bottom" to bounds.bottom
            )
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                visit(child, "$path.$i", nodes)
                child.recycle()
            }
        }
    }
}
```

- [ ] **Step 6: Implement screenshot capture**

`android/app/src/main/java/ai/milf/client/accessibility/ScreenshotCapture.kt`:
```kotlin
package ai.milf.client.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.hardware.HardwareBuffer
import android.os.Build
import android.util.Base64
import android.view.Display
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object ScreenshotCapture {
    suspend fun captureBase64Png(service: AccessibilityService): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            error("Screenshots require Android 11 or newer")
        }
        val bitmap = takeScreenshot(service)
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private suspend fun takeScreenshot(service: AccessibilityService): Bitmap =
        suspendCancellableCoroutine { cont ->
            val executor = Executors.newSingleThreadExecutor()
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                executor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val bitmap = bitmapFromHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )
                        screenshot.hardwareBuffer.close()
                        executor.shutdown()
                        cont.resume(bitmap)
                    }

                    override fun onFailure(errorCode: Int) {
                        executor.shutdown()
                        cont.resumeWithException(IllegalStateException("Screenshot failed: $errorCode"))
                    }
                }
            )
        }

    private fun bitmapFromHardwareBuffer(
        buffer: HardwareBuffer,
        colorSpace: android.graphics.ColorSpace
    ): Bitmap {
        return Bitmap.wrapHardwareBuffer(buffer, colorSpace)
            ?.copy(Bitmap.Config.ARGB_8888, false)
            ?: error("Could not wrap screenshot buffer")
    }
}
```

- [ ] **Step 7: Run dispatcher tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.accessibility.ActionDispatcherTest"
```
Expected: tests pass.

- [ ] **Step 8: Run compile check**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:assembleDebug
```
Expected: compile succeeds after remaining UI files are added, or fails only on missing `MainActivity` and UI classes before Tasks 6 and 7.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/accessibility android/app/src/test/java/ai/milf/client/accessibility
git commit -m "feat: add Android accessibility bridge"
```

---

### Task 6: ViewModel orchestration

Wire recording, websocket, accessibility actions, narration, and confirmation into one state holder that the UI can render.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Create: `android/app/src/test/java/ai/milf/client/MainViewModelTest.kt`

**Interfaces:**
- Produces `MainUiState`.
- Produces events: `startRecording()`, `stopAndRun()`, `approveConfirmation()`, `denyConfirmation()`, `setLang(lang)`, `setBackendUrl(url)`.
- Uses `ActionDispatcher(MilfAccessibilityService.instance)` for backend actions.

- [ ] **Step 1: Write ViewModel confirmation test**

`android/app/src/test/java/ai/milf/client/MainViewModelTest.kt`:
```kotlin
package ai.milf.client

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MainViewModelTest {
    @Test
    fun approveConfirmationClearsPendingRequest() = runTest {
        val sent = mutableListOf<Boolean>()
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved }
            )
        )
        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()
        assertEquals(listOf(true), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
    }
}
```

- [ ] **Step 2: Run ViewModel test to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.MainViewModelTest"
```
Expected: FAIL with unresolved reference `MainViewModel`.

- [ ] **Step 3: Implement ViewModel**

`android/app/src/main/java/ai/milf/client/MainViewModel.kt`:
```kotlin
package ai.milf.client

import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
import ai.milf.client.audio.TtsNarrator
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.ws.MilfWebSocketClient
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val backendUrl: String = "ws://10.0.2.2:8765",
    val lang: String = "en",
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val status: String = "Ready",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val accessibilityEnabled: Boolean = false
)

data class PendingConfirmation(
    val id: String,
    val summary: String,
    val lang: String
)

class MainViewModel(
    private val dependencies: Dependencies
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setBackendUrl(url: String) {
        _uiState.update { it.copy(backendUrl = url.trim()) }
    }

    fun setLang(lang: String) {
        _uiState.update { it.copy(lang = lang) }
    }

    fun refreshAccessibilityStatus() {
        _uiState.update {
            it.copy(accessibilityEnabled = MilfAccessibilityService.instance != null)
        }
    }

    fun startRecording() {
        dependencies.narrator.stop()
        dependencies.recorder.start()
        _uiState.update {
            it.copy(isRecording = true, status = "Listening", lastNarration = null)
        }
    }

    fun stopAndRun() {
        val state = _uiState.value
        val bytes = dependencies.recorder.stop()
        val client = dependencies.clientFactory(state.backendUrl)
        dependencies.activeClient = client
        _uiState.update { it.copy(isRecording = false, isRunning = true, status = "Working") }
        client.start(bytes, state.lang, object : MilfWebSocketClient.Callbacks {
            override suspend fun onAction(action: Action): ActionResult {
                return ActionDispatcher(MilfAccessibilityService.instance).dispatch(action)
            }

            override suspend fun onNarration(text: String, lang: String) {
                dependencies.narrator.speak(text, lang)
                _uiState.update { it.copy(lastNarration = text, status = "Speaking") }
            }

            override suspend fun onConfirmRequest(id: String, summary: String, lang: String) {
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        confirmation = PendingConfirmation(id, summary, lang),
                        status = "Confirm"
                    )
                }
            }

            override fun onClosed(reason: String?) {
                _uiState.update { it.copy(isRunning = false, status = reason ?: "Done") }
            }

            override fun onFailed(message: String) {
                _uiState.update { it.copy(isRunning = false, status = message) }
            }
        })
    }

    fun approveConfirmation() {
        respondToConfirmation(approved = true)
    }

    fun denyConfirmation() {
        respondToConfirmation(approved = false)
    }

    private fun respondToConfirmation(approved: Boolean) {
        val pending = _uiState.value.confirmation ?: return
        dependencies.sendConfirm?.invoke(approved)
        dependencies.activeClient?.send(ConfirmResponse(pending.id, approved))
        _uiState.update { it.copy(confirmation = null, status = if (approved) "Continuing" else "Stopped") }
    }

    fun showConfirmationForTest(id: String, summary: String, lang: String) {
        _uiState.update { it.copy(confirmation = PendingConfirmation(id, summary, lang)) }
    }

    override fun onCleared() {
        dependencies.recorder.cancel()
        dependencies.narrator.shutdown()
        dependencies.activeClient?.close()
        super.onCleared()
    }

    class Dependencies(
        val recorder: AudioRecorderLike,
        val narrator: NarratorLike,
        val clientFactory: (String) -> MilfWebSocketClient,
        val sendConfirm: ((Boolean) -> Unit)? = null
    ) {
        var activeClient: MilfWebSocketClient? = null

        companion object {
            fun real(application: Application): Dependencies {
                return Dependencies(
                    recorder = AndroidAudioRecorder(AudioRecorder(application)),
                    narrator = AndroidNarrator(TtsNarrator(application)),
                    clientFactory = { MilfWebSocketClient(it) }
                )
            }

            fun fake(sendConfirm: (Boolean) -> Unit): Dependencies {
                return Dependencies(
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
                    clientFactory = { error("not used") },
                    sendConfirm = sendConfirm
                )
            }
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(Dependencies.real(application)) as T
        }
    }
}

interface AudioRecorderLike {
    fun start()
    fun stop(): ByteArray
    fun cancel()
}

interface NarratorLike {
    fun speak(text: String, lang: String)
    fun stop()
    fun shutdown()
}

private class AndroidAudioRecorder(
    private val recorder: AudioRecorder
) : AudioRecorderLike {
    override fun start() = recorder.start()
    override fun stop(): ByteArray = recorder.stop()
    override fun cancel() = recorder.cancel()
}

private class AndroidNarrator(
    private val narrator: TtsNarrator
) : NarratorLike {
    override fun speak(text: String, lang: String) = narrator.speak(text, lang)
    override fun stop() = narrator.stop()
    override fun shutdown() = narrator.shutdown()
}
```

- [ ] **Step 4: Run ViewModel tests**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.MainViewModelTest"
```
Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/test/java/ai/milf/client/MainViewModelTest.kt
git commit -m "feat: orchestrate Android client session"
```

---

### Task 7: Senior-facing Compose UI

Build the real first screen: language selector, backend URL field, large mic control, live status, narration text, accessibility settings button, and confirmation overlay with large approve/deny buttons.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
- Create: `android/app/src/main/java/ai/milf/client/MilfUi.kt`

**Interfaces:**
- Consumes `MainUiState` and ViewModel events from Task 6.
- Requests `RECORD_AUDIO`.
- Opens Android Accessibility Settings when the service is not enabled.

- [ ] **Step 1: Implement MainActivity**

`android/app/src/main/java/ai/milf/client/MainActivity.kt`:
```kotlin
package ai.milf.client

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) viewModel.startRecording()
            }

            LaunchedEffect(Unit) {
                viewModel.refreshAccessibilityStatus()
            }

            MilfUi(
                state = state,
                onBackendUrlChange = viewModel::setBackendUrl,
                onLangChange = viewModel::setLang,
                onMicPressed = {
                    if (state.isRecording) {
                        viewModel.stopAndRun()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onApprove = viewModel::approveConfirmation,
                onDeny = viewModel::denyConfirmation,
                onOpenAccessibility = {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAccessibilityStatus()
    }
}
```

- [ ] **Step 2: Implement Compose UI**

`android/app/src/main/java/ai/milf/client/MilfUi.kt`:
```kotlin
package ai.milf.client

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MilfUi(
    state: MainUiState,
    onBackendUrlChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onMicPressed: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "MILF",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Spacer(Modifier.height(16.dp))
                        LanguageRow(state.lang, onLangChange)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.backendUrl,
                            onValueChange = onBackendUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Backend") }
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.lastNarration ?: state.status,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF111827),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = onMicPressed,
                            enabled = !state.isRunning || state.isRecording,
                            modifier = Modifier
                                .size(168.dp)
                                .clip(CircleShape)
                        ) {
                            Text(
                                text = if (state.isRecording) "Stop" else "Speak",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        if (!state.accessibilityEnabled) {
                            OutlinedButton(
                                onClick = onOpenAccessibility,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Enable phone control", fontSize = 20.sp)
                            }
                        }
                    }
                }

                state.confirmation?.let {
                    ConfirmationOverlay(
                        pending = it,
                        onApprove = onApprove,
                        onDeny = onDeny
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageRow(
    selected: String,
    onLangChange: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("en" to "English", "manglish" to "Manglish", "yue" to "Cantonese").forEach { (code, label) ->
            if (selected == code) {
                Button(onClick = { onLangChange(code) }) {
                    Text(label)
                }
            } else {
                OutlinedButton(onClick = { onLangChange(code) }) {
                    Text(label)
                }
            }
        }
    }
}

@Composable
private fun ConfirmationOverlay(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC111827))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(8.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pending.summary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f).height(72.dp)
                ) {
                    Text("No", fontSize = 24.sp)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).height(72.dp)
                ) {
                    Text("Yes", fontSize = 24.sp)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Run Android unit tests and build**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: unit tests pass and debug APK builds.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/MilfUi.kt
git commit -m "feat: build senior-facing Android UI"
```

---

### Task 8: Voice yes/no confirmation

Add the spec's secondary confirmation path: the senior can tap Yes/No or say a short approval/denial phrase while the confirmation overlay is visible.

**Files:**
- Create: `android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceParser.kt`
- Create: `android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt`
- Create: `android/app/src/test/java/ai/milf/client/audio/ConfirmationVoiceParserTest.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainViewModel.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MainActivity.kt`
- Modify: `android/app/src/main/java/ai/milf/client/MilfUi.kt`

**Interfaces:**
- Produces `ConfirmationVoiceParser.parse(text: String): Boolean?`.
- Produces `ConfirmationVoiceRecognizer.listen(lang: String)`.
- Adds `MainViewModel.onConfirmationSpeech(text: String)`.
- Adds a `Speak yes/no` confirmation control that listens only while the confirmation overlay is active.

- [ ] **Step 1: Write parser tests**

`android/app/src/test/java/ai/milf/client/audio/ConfirmationVoiceParserTest.kt`:
```kotlin
package ai.milf.client.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationVoiceParserTest {
    @Test
    fun parsesApprovalPhrases() {
        assertEquals(true, ConfirmationVoiceParser.parse("yes"))
        assertEquals(true, ConfirmationVoiceParser.parse("betul"))
        assertEquals(true, ConfirmationVoiceParser.parse("okay call him"))
    }

    @Test
    fun parsesDenialPhrases() {
        assertEquals(false, ConfirmationVoiceParser.parse("no"))
        assertEquals(false, ConfirmationVoiceParser.parse("tak nak"))
        assertEquals(false, ConfirmationVoiceParser.parse("cancel"))
    }

    @Test
    fun ignoresUnclearSpeech() {
        assertNull(ConfirmationVoiceParser.parse("maybe later"))
    }
}
```

- [ ] **Step 2: Run parser tests to verify failure**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest --tests "ai.milf.client.audio.ConfirmationVoiceParserTest"
```
Expected: FAIL with unresolved reference `ConfirmationVoiceParser`.

- [ ] **Step 3: Implement parser**

`android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceParser.kt`:
```kotlin
package ai.milf.client.audio

object ConfirmationVoiceParser {
    private val approve = listOf(
        "yes", "yeah", "yup", "ya", "ok", "okay", "correct", "betul", "boleh", "can"
    )
    private val deny = listOf(
        "no", "nope", "cancel", "stop", "tak", "tak nak", "jangan", "bukan", "cannot"
    )

    fun parse(text: String): Boolean? {
        val normalized = text.lowercase().trim()
        if (normalized.isBlank()) return null
        if (deny.any { normalized.contains(it) }) return false
        if (approve.any { normalized.contains(it) }) return true
        return null
    }
}
```

- [ ] **Step 4: Implement voice recognizer**

`android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt`:
```kotlin
package ai.milf.client.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

class ConfirmationVoiceRecognizer(
    context: Context,
    private val onText: (String) -> Unit,
    private val onError: (String) -> Unit
) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)

    init {
        recognizer.setRecognitionListener(this)
    }

    fun listen(lang: String) {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag(lang))
        recognizer.startListening(intent)
    }

    fun destroy() {
        recognizer.destroy()
    }

    override fun onResults(results: Bundle) {
        val text = results
            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (text != null) onText(text) else onError("No confirmation heard")
    }

    override fun onError(error: Int) {
        onError("Could not hear confirmation")
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit

    private fun localeTag(lang: String): String = when (lang) {
        "manglish" -> Locale("ms", "MY").toLanguageTag()
        "yue" -> Locale.TRADITIONAL_CHINESE.toLanguageTag()
        else -> Locale.ENGLISH.toLanguageTag()
    }
}
```

- [ ] **Step 5: Add ViewModel speech handler**

Modify `android/app/src/main/java/ai/milf/client/MainViewModel.kt` by adding this function inside `MainViewModel`:
```kotlin
    fun onConfirmationSpeech(text: String) {
        when (ConfirmationVoiceParser.parse(text)) {
            true -> approveConfirmation()
            false -> denyConfirmation()
            null -> _uiState.update { it.copy(status = "Please say yes or no") }
        }
    }
```

Add the import:
```kotlin
import ai.milf.client.audio.ConfirmationVoiceParser
```

- [ ] **Step 6: Add voice recognizer to Activity**

Modify `android/app/src/main/java/ai/milf/client/MainActivity.kt` imports:
```kotlin
import ai.milf.client.audio.ConfirmationVoiceRecognizer
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
```

Inside `setContent`, after `val state by ...`, add:
```kotlin
            val confirmationVoice = remember {
                ConfirmationVoiceRecognizer(
                    context = this,
                    onText = viewModel::onConfirmationSpeech,
                    onError = { }
                )
            }
            DisposableEffect(Unit) {
                onDispose { confirmationVoice.destroy() }
            }
```

Pass this to `MilfUi`:
```kotlin
                onSpeakDecision = {
                    confirmationVoice.listen(state.lang)
                },
```

- [ ] **Step 7: Add UI control**

Modify `android/app/src/main/java/ai/milf/client/MilfUi.kt`:
```kotlin
fun MilfUi(
    state: MainUiState,
    onBackendUrlChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onMicPressed: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit,
    onOpenAccessibility: () -> Unit
)
```

Update the confirmation call:
```kotlin
                    ConfirmationOverlay(
                        pending = it,
                        onApprove = onApprove,
                        onDeny = onDeny,
                        onSpeakDecision = onSpeakDecision
                    )
```

Update `ConfirmationOverlay`:
```kotlin
private fun ConfirmationOverlay(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit
)
```

Add this button above the Yes/No row:
```kotlin
            OutlinedButton(
                onClick = onSpeakDecision,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text("Speak yes/no", fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
```

- [ ] **Step 8: Run tests and build**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: unit tests pass and debug APK builds.

- [ ] **Step 9: Commit**

```bash
git add android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceParser.kt android/app/src/main/java/ai/milf/client/audio/ConfirmationVoiceRecognizer.kt android/app/src/test/java/ai/milf/client/audio/ConfirmationVoiceParserTest.kt android/app/src/main/java/ai/milf/client/MainViewModel.kt android/app/src/main/java/ai/milf/client/MainActivity.kt android/app/src/main/java/ai/milf/client/MilfUi.kt
git commit -m "feat: add voice confirmation input"
```

---

### Task 9: Local backend integration run

Prove the client can connect to the implemented backend, send the initial audio frame, handle narration, answer actions, and return confirmation.

**Files:**
- Modify: `docs/superpowers/plans/2026-06-23-milf-android-client.md` only to check boxes during execution.
- No production source changes unless integration exposes a contract bug.

**Interfaces:**
- Uses backend server `milf.server`.
- Uses debug APK from Task 8.

- [ ] **Step 1: Start backend with mock STT**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
MILF_STT_BACKEND=mock PYTHONPATH=. python -m milf.server
```
Expected: websocket server listens on `0.0.0.0:8765`.

- [ ] **Step 2: Install debug APK**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:installDebug
```
Expected: app installs on connected emulator or phone.

- [ ] **Step 3: Enable accessibility service on the test device**

On the device:
```text
Settings -> Accessibility -> MILF phone control -> On
```
Expected: the app's bottom action no longer shows `Enable phone control` after returning to the app.

- [ ] **Step 4: Run one mock session**

In the app:
```text
Backend: ws://10.0.2.2:8765 on emulator, or ws://<Mac LAN IP>:8765 on a physical phone
Language: English
Tap Speak
Say: I want to see my grandson
Tap Stop
```
Expected:
- Backend accepts the first `Audio` frame.
- App speaks the acknowledgment narration.
- App receives at least one `Action`.
- If a confirmation appears, tapping `Yes` sends `ConfirmResponse(approved=true)`.

- [ ] **Step 5: Run backend tests after any integration fixes**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest
```
Expected: backend tests pass.

- [ ] **Step 6: Run Android tests and build**

Run:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: Android unit tests pass and debug APK builds.

- [ ] **Step 7: Commit integration fixes**

If files changed:
```bash
git add backend android
git commit -m "fix: align Android client integration"
```
If no files changed:
```bash
git status --short
```
Expected: no source changes to commit.

---

### Task 10: Hero demo hardening

Run the real WhatsApp path on the demo phone and tighten only the failure modes that block the hero flow.

**Files:**
- Modify source only where the run reveals a reproducible blocker.
- Create: `docs/android-demo-runbook.md`

**Interfaces:**
- Produces a short demo runbook with device setup, backend URL, language, contact device, and fallback recording checklist.

- [ ] **Step 1: Create demo runbook**

`docs/android-demo-runbook.md`:
```markdown
# MILF Android Demo Runbook

## Device Setup

- Use the demo Android phone with MILF installed.
- Enable `MILF phone control` in Android Accessibility settings.
- Keep WhatsApp logged in and the demo contact visible in recent chats when possible.
- Keep the second device signed in as the demo contact and ready to receive the call.

## Backend Setup

```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
PYTHONPATH=. MILF_STT_BACKEND=mock python -m milf.server
```

Use the Mac LAN websocket URL in the app:

```text
ws://<Mac LAN IP>:8765
```

## Live Flow

1. Select English or Manglish.
2. Tap Speak.
3. Say: I want to see my grandson.
4. Tap Stop.
5. Let MILF narrate.
6. When the app asks to confirm the call, tap Yes.

## Success Criteria

- The app captures audio and backend starts the run.
- MILF opens WhatsApp.
- MILF reaches the demo contact.
- MILF asks for confirmation before connecting.
- The call connects only after approval.

## Backup

- Record one clean full run after the first successful rehearsal.
- Keep the video available locally on the presentation laptop.
```

- [ ] **Step 2: Run 10 rehearsal attempts**

For each attempt, record:
```text
Run number:
Language:
Reached WhatsApp:
Reached contact:
Confirmation shown:
Call connected after yes:
Failure note:
```

Expected: at least 9 of 10 attempts connect after approval, or every failure has one repeated cause to fix.

- [ ] **Step 3: Fix the top repeated blocker**

Use the smallest patch that addresses the repeated blocker:
- If the app cannot launch WhatsApp, fix `start_app`.
- If the UI tree lacks labels, expand `UiTreeSerializer`.
- If taps are off, verify screenshot dimensions and gesture coordinates.
- If confirmation response is missed, fix websocket send timing.

Run after the fix:
```bash
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/backend
pytest
cd /Users/leongshiwei/ShiWei_Local_Mac/1_Projects/milf-revamped/android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```
Expected: tests pass and APK builds.

- [ ] **Step 4: Record backup run**

Use the device screen recorder:
```text
Start recording -> perform one clean successful hero flow -> stop recording -> save with date and take number
```
Expected: backup video clearly includes audio narration, confirmation, and successful call connection.

- [ ] **Step 5: Commit runbook and fixes**

```bash
git add docs/android-demo-runbook.md backend android
git commit -m "chore: harden Android hero demo"
```

---

## Self-Review

### Spec Coverage

- Native Android APK on the user's own phone: Tasks 1, 5, 7, 8.
- AccessibilityService driver methods: Task 5 implements `get_ui_tree`, `screenshot`, `tap`, `swipe`, `input_text`, `press_button`, `start_app`.
- WebSocket client and typed envelope: Tasks 2 and 3.
- Audio capture to backend STT: Tasks 4 and 6.
- Narration via Android TextToSpeech: Tasks 4 and 6.
- Confirmation screen with large buttons and voice yes/no: Tasks 6, 7, and 8.
- Confirmation response over websocket: Tasks 3, 6, and 8.
- English, Manglish, Cantonese language setting: Tasks 6 and 7.
- No ADB or cloud phone dependency: enforced in Global Constraints and Task 5.
- Live-feel immediate narration: supported by Task 3 handling `Narration` immediately and Task 4 TTS.
- Hero-flow reliability: Tasks 9 and 10.

### Contract Notes

- The backend currently sends and receives JSON only. Android screenshots must be base64 strings. Task 2 adds the backend decode so `WebSocketDriver.screenshot()` still returns bytes to MobileRun.
- `press_button("enter")` is best-effort through focused-node `ACTION_IME_ENTER`. The hero WhatsApp path should prefer accessibility-tree taps over enter key injection.
- The app records M4A/AAC bytes. The backend STT adapter must accept this container or convert server-side.

### Red-Flag Scan

- No unnamed files remain.
- Each task lists exact files and commands.
- Each implementation step includes concrete code or a concrete manual verification script.
