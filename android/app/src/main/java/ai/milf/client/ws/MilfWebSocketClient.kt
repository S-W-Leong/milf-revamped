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
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val audioEncoder: (ByteArray) -> String = {
        Base64.encodeToString(it, Base64.NO_WRAP)
    }
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

    private val lock = Any()
    private var sessionId = 0L
    private var socket: Socket? = null
    private var callbacks: Callbacks? = null
    private var pendingMessages: MutableList<String> = mutableListOf()

    fun start(goalAudio: ByteArray, lang: String, callbacks: Callbacks) {
        val newSessionId: Long
        val oldSocket = synchronized(lock) {
            sessionId += 1
            newSessionId = sessionId
            val existingSocket = socket
            socket = null
            pendingMessages.clear()
            this.callbacks = callbacks
            existingSocket
        }
        oldSocket?.close()

        val openedSocket = socketFactory.open(url, object : TextListener {
            override fun onOpen() {
                val encodedAudio = audioEncoder(goalAudio)
                send(newSessionId, Audio(goalAudioB64 = encodedAudio, lang = lang))
            }

            override fun onText(text: String) {
                handleText(newSessionId, text)
            }

            override fun onClosed(reason: String?) {
                callbacksFor(newSessionId)?.let { activeCallbacks ->
                    runCatching { activeCallbacks.onClosed(reason) }
                        .onFailure { reportFailure(newSessionId, it.failureMessage()) }
                }
            }

            override fun onFailure(message: String) {
                reportFailure(newSessionId, message)
            }
        })

        val shouldCloseOpenedSocket = synchronized(lock) {
            if (sessionId == newSessionId && this.callbacks === callbacks) {
                socket = openedSocket
                pendingMessages.forEach { openedSocket.send(it) }
                pendingMessages.clear()
                false
            } else {
                true
            }
        }
        if (shouldCloseOpenedSocket) {
            openedSocket.close()
        }
    }

    fun send(message: MilfMessage): Boolean =
        synchronized(lock) { socket }?.send(MilfProtocol.encode(message)) == true

    fun handleText(text: String) {
        val activeSessionId = synchronized(lock) {
            if (callbacks == null) null else sessionId
        } ?: return
        handleText(activeSessionId, text)
    }

    private fun handleText(messageSessionId: Long, text: String) {
        if (!isActive(messageSessionId)) return

        val message = runCatching { MilfProtocol.decode(text) }
            .getOrElse { error ->
                reportFailure(messageSessionId, error.failureMessage())
                return
            }
        val activeCallbacks = callbacksFor(messageSessionId) ?: return

        when (message) {
            is Action -> scope.launch {
                if (!isActive(messageSessionId)) return@launch
                val result = runCatching { activeCallbacks.onAction(message) }
                    .getOrElse { error ->
                        ActionResult(id = message.id, ok = false, error = error.message)
                    }
                if (isActive(messageSessionId)) {
                    send(messageSessionId, result)
                }
            }

            is Narration -> scope.launch {
                if (!isActive(messageSessionId)) return@launch
                runCatching { activeCallbacks.onNarration(message.text, message.lang) }
                    .onFailure { reportFailure(messageSessionId, it.failureMessage()) }
            }

            is ConfirmRequest -> scope.launch {
                if (!isActive(messageSessionId)) return@launch
                runCatching { activeCallbacks.onConfirmRequest(message.id, message.summary, message.lang) }
                    .onFailure { reportFailure(messageSessionId, it.failureMessage()) }
            }

            is ActionResult, is ConfirmResponse, is Audio -> Unit
        }
    }

    fun close() {
        val oldSocket = synchronized(lock) {
            sessionId += 1
            val existingSocket = socket
            socket = null
            callbacks = null
            pendingMessages.clear()
            existingSocket
        }
        oldSocket?.close()
    }

    private fun send(messageSessionId: Long, message: MilfMessage): Boolean {
        val encoded = MilfProtocol.encode(message)
        val activeSocket = synchronized(lock) {
            if (sessionId != messageSessionId) {
                null
            } else {
                val activeSocket = socket
                if (activeSocket == null) {
                    pendingMessages += encoded
                }
                activeSocket
            }
        }
        return activeSocket?.send(encoded) ?: isActiveOrOpening(messageSessionId)
    }

    private fun callbacksFor(messageSessionId: Long): Callbacks? =
        synchronized(lock) {
            if (sessionId == messageSessionId && socket != null) callbacks else null
        }

    private fun isActive(messageSessionId: Long): Boolean =
        synchronized(lock) {
            sessionId == messageSessionId && socket != null && callbacks != null
        }

    private fun isActiveOrOpening(messageSessionId: Long): Boolean =
        synchronized(lock) {
            sessionId == messageSessionId && callbacks != null
        }

    private fun reportFailure(messageSessionId: Long, message: String) {
        val activeCallbacks = callbacksFor(messageSessionId) ?: return
        runCatching { activeCallbacks.onFailed(message) }
    }

    private fun Throwable.failureMessage(): String =
        message ?: javaClass.simpleName
}

private class OkHttpSocketFactory : MilfWebSocketClient.SocketFactory {
    private val client = OkHttpClient()

    override fun open(url: String, listener: MilfWebSocketClient.TextListener): MilfWebSocketClient.Socket {
        val request = Request.Builder().url(url).build()
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {
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
            override fun send(text: String): Boolean =
                webSocket.send(text)

            override fun close() {
                webSocket.close(1000, "client closing")
            }
        }
    }
}
