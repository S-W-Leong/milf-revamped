package ai.milf.client.ws

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.Audio
import ai.milf.client.protocol.ConfirmRequest
import ai.milf.client.protocol.MilfProtocol
import ai.milf.client.protocol.Narration
import ai.milf.client.security.ClientSecurity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

class MilfWebSocketClientTest {
    @Test
    fun opensSocketWithSecuredUrl() {
        val factory = FakeSocketFactory()
        val client = MilfWebSocketClient(
            url = "wss://backend.example/ws?lang=en#session",
            socketFactory = factory,
            clientSecurity = ClientSecurity(
                isDebugBuild = false,
                defaultBackendUrl = "wss://backend.example/ws",
                deviceToken = "device-token"
            ),
            audioEncoder = testAudioEncoder
        )

        client.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks()
        )

        assertEquals(listOf("wss://backend.example/ws?lang=en&token=device-token#session"), factory.openedUrls)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun handlesActionAndSendsResult() = runTest {
        var handledAction: Action? = null
        val factory = FakeSocketFactory()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = factory,
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )
        client.start(
            goalAudio = byteArrayOf(1, 2, 3),
            lang = "en",
            callbacks = object : MilfWebSocketClient.Callbacks {
                override suspend fun onAction(action: Action): ActionResult {
                    handledAction = action
                    return ActionResult(id = action.id, ok = true, result = "ok")
                }

                override suspend fun onNarration(text: String, lang: String) = Unit
                override suspend fun onConfirmRequest(id: String, summary: String, lang: String) = Unit
                override fun onClosed(reason: String?) = Unit
                override fun onFailed(message: String) = Unit
            }
        )

        val socket = factory.sockets.single()
        client.handleText(MilfProtocol.encode(Action(id = "a1", name = "tap", args = emptyMap())))
        advanceUntilIdle()

        val result = MilfProtocol.decode(socket.sent.last()) as ActionResult
        assertEquals("a1", handledAction?.id)
        assertEquals("a1", result.id)
        assertEquals(true, result.ok)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sendsGoalAudioWhenSocketOpens() = runTest {
        val factory = FakeSocketFactory()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = factory,
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )

        client.start(
            goalAudio = byteArrayOf(1, 2, 3),
            lang = "ms",
            callbacks = noOpCallbacks()
        )

        val socket = factory.sockets.single()
        socket.open()
        advanceUntilIdle()

        val audio = MilfProtocol.decode(socket.sent.single()) as Audio
        assertEquals("AQID", audio.goalAudioB64)
        assertEquals("ms", audio.lang)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun sendsGoalAudioWhenSocketOpensSynchronously() = runTest {
        val factory = FakeSocketFactory(openImmediately = true)
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = factory,
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )

        client.start(
            goalAudio = byteArrayOf(1, 2, 3),
            lang = "en",
            callbacks = noOpCallbacks()
        )
        advanceUntilIdle()

        val audio = MilfProtocol.decode(factory.sockets.single().sent.single()) as Audio
        assertEquals("AQID", audio.goalAudioB64)
        assertEquals("en", audio.lang)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun ignoresActionLaunchedBeforeClose() = runTest {
        var handledActions = 0
        val factory = FakeSocketFactory()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = factory,
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )
        client.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks(
                onAction = {
                    handledActions += 1
                    ActionResult(id = it.id, ok = true)
                }
            )
        )

        val socket = factory.sockets.single()
        socket.text(MilfProtocol.encode(Action(id = "late", name = "tap", args = emptyMap())))
        client.close()
        advanceUntilIdle()

        assertEquals(0, handledActions)
        assertFalse(socket.sent.any { MilfProtocol.decode(it) is ActionResult })
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun startClosesOldSocketAndIgnoresOldFrames() = runTest {
        var firstActions = 0
        var secondActions = 0
        val factory = FakeSocketFactory()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = factory,
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )

        client.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks(
                onAction = {
                    firstActions += 1
                    ActionResult(id = it.id, ok = true)
                }
            )
        )
        val oldSocket = factory.sockets.single()

        client.start(
            goalAudio = byteArrayOf(2),
            lang = "en",
            callbacks = noOpCallbacks(
                onAction = {
                    secondActions += 1
                    ActionResult(id = it.id, ok = true)
                }
            )
        )
        val newSocket = factory.sockets.last()

        oldSocket.text(MilfProtocol.encode(Action(id = "old", name = "tap", args = emptyMap())))
        newSocket.text(MilfProtocol.encode(Action(id = "new", name = "tap", args = emptyMap())))
        advanceUntilIdle()

        assertEquals(true, oldSocket.closed)
        assertEquals(0, firstActions)
        assertEquals(1, secondActions)
        val result = MilfProtocol.decode(newSocket.sent.last()) as ActionResult
        assertEquals("new", result.id)
    }

    @Test
    fun reportsMalformedFramesViaOnFailed() {
        val failures = mutableListOf<String>()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = FakeSocketFactory(),
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )
        client.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks(onFailed = { failures += it })
        )

        client.handleText("""{"type":"Nope","data":{}}""")

        assertEquals(1, failures.size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun reportsNarrationAndConfirmRequestCallbackFailures() = runTest {
        val failures = mutableListOf<String>()
        val client = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = FakeSocketFactory(),
            scope = this,
            clientSecurity = testClientSecurity,
            audioEncoder = testAudioEncoder
        )
        client.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks(
                onNarration = { _, _ -> error("narration failed") },
                onConfirmRequest = { _, _, _ -> error("confirm failed") },
                onFailed = { failures += it }
            )
        )

        client.handleText(MilfProtocol.encode(Narration(text = "hello", lang = "en")))
        client.handleText(MilfProtocol.encode(ConfirmRequest(id = "c1", summary = "approve", lang = "en")))
        advanceUntilIdle()

        assertEquals(listOf("narration failed", "confirm failed"), failures)
    }
}

private class FakeSocketFactory(
    private val openImmediately: Boolean = false
) : MilfWebSocketClient.SocketFactory {
    val sockets = mutableListOf<FakeSocket>()
    val openedUrls = mutableListOf<String>()

    override fun open(
        url: String,
        listener: MilfWebSocketClient.TextListener
    ): MilfWebSocketClient.Socket {
        openedUrls += url
        return FakeSocket(listener).also {
            sockets += it
            if (openImmediately) {
                it.open()
            }
        }
    }
}

private class FakeSocket(
    private val listener: MilfWebSocketClient.TextListener
) : MilfWebSocketClient.Socket {
    val sent = mutableListOf<String>()
    var closed = false

    fun open() {
        listener.onOpen()
    }

    fun text(text: String) {
        listener.onText(text)
    }

    override fun send(text: String): Boolean {
        sent += text
        return true
    }

    override fun close() {
        closed = true
    }
}

private fun noOpCallbacks(
    onAction: suspend (Action) -> ActionResult = { ActionResult(id = it.id, ok = true) },
    onNarration: suspend (String, String) -> Unit = { _, _ -> },
    onConfirmRequest: suspend (String, String, String) -> Unit = { _, _, _ -> },
    onFailed: (String) -> Unit = {}
): MilfWebSocketClient.Callbacks =
    object : MilfWebSocketClient.Callbacks {
        override suspend fun onAction(action: Action): ActionResult = onAction(action)
        override suspend fun onNarration(text: String, lang: String) = onNarration(text, lang)
        override suspend fun onConfirmRequest(id: String, summary: String, lang: String) =
            onConfirmRequest(id, summary, lang)

        override fun onClosed(reason: String?) = Unit
        override fun onFailed(message: String) = onFailed(message)
    }

private val testAudioEncoder: (ByteArray) -> String = {
    Base64.getEncoder().encodeToString(it)
}

private val testClientSecurity = ClientSecurity(
    isDebugBuild = true,
    defaultBackendUrl = "ws://localhost:8765",
    deviceToken = "test-token"
)
