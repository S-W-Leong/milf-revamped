package ai.milf.client.security

import ai.milf.client.BuildConfig
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

class ClientSecurity(
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val defaultBackendUrl: String = BuildConfig.MILF_DEFAULT_BACKEND_URL,
    private val deviceToken: String = BuildConfig.MILF_DEVICE_TOKEN
) {
    class SecurityException(message: String) : IllegalArgumentException(message)

    fun defaultBackendUrl(): String = defaultBackendUrl

    fun secureWebSocketUrl(rawUrl: String = defaultBackendUrl): String {
        val trimmedUrl = rawUrl.trim()
        val uri = parse(trimmedUrl)
        val scheme = uri.scheme?.lowercase(Locale.US)
            ?: throw SecurityException("Websocket URL must start with ws:// or wss://")
        if (scheme != "ws" && scheme != "wss") {
            throw SecurityException("Websocket URL must start with ws:// or wss://")
        }

        val host = uri.host?.lowercase(Locale.US)
            ?: throw SecurityException("Websocket URL must include a host")
        if (scheme == "ws" && !allowsCleartextHost(host)) {
            throw SecurityException("Cleartext websocket URLs are only allowed for local debug builds")
        }

        val token = deviceToken.trim()
        if (token.isEmpty()) {
            throw SecurityException("Device token is required before connecting")
        }

        return appendToken(trimmedUrl, token)
    }

    private fun parse(rawUrl: String): URI =
        try {
            URI(rawUrl)
        } catch (_: IllegalArgumentException) {
            throw SecurityException("Websocket URL is invalid")
        }

    private fun allowsCleartextHost(host: String): Boolean =
        isDebugBuild && host in localDebugHosts

    private fun appendToken(rawUrl: String, token: String): String {
        val fragmentStart = rawUrl.indexOf('#')
        val beforeFragment = if (fragmentStart == -1) rawUrl else rawUrl.substring(0, fragmentStart)
        val fragment = if (fragmentStart == -1) "" else rawUrl.substring(fragmentStart)
        val queryStart = beforeFragment.indexOf('?')
        val base = if (queryStart == -1) beforeFragment else beforeFragment.substring(0, queryStart)
        val query = if (queryStart == -1) "" else beforeFragment.substring(queryStart + 1)
        val preservedQuery = query
            .split('&')
            .filter { it.isNotBlank() && it.substringBefore('=').lowercase(Locale.US) != "token" }
        val securedQuery = preservedQuery + "token=${encodeQueryValue(token)}"
        return "$base?${securedQuery.joinToString("&")}$fragment"
    }

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private companion object {
        val localDebugHosts = setOf(
            "10.0.2.2",
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
        )
    }
}
