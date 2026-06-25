package ai.milf.client.session

import ai.milf.client.protocol.MilfMessage
import ai.milf.client.ws.MilfWebSocketClient

interface SessionSocketClient {
    fun start(
        goalAudio: ByteArray,
        lang: String,
        callbacks: MilfWebSocketClient.Callbacks,
        backendSessionId: String? = null,
        memory: String = ""
    )

    fun startText(
        goalText: String,
        lang: String,
        callbacks: MilfWebSocketClient.Callbacks,
        backendSessionId: String? = null,
        memory: String = ""
    )

    fun send(message: MilfMessage): Boolean
    fun close()
}
