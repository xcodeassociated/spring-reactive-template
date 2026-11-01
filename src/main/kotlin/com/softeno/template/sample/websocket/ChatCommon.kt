package com.softeno.template.sample.websocket

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.reactive.socket.CloseStatus
import tools.jackson.databind.ObjectMapper

/**
 * Shared WebSocket chat model (coroutine-only implementation; see CoroutineWebSocket.kt).
 */

/** Transport envelope for the WebSocket wire format — not a domain type. */
data class Message(
    // `from` is stamped server-side from the session's bound identity on relay;
    // client-claimed identity is never trusted. Nullable with a default so minimal
    // control frames (e.g. pong: {"to":"SYSTEM","content":"pong"}) parse: Jackson
    // passes null (not the Kotlin default) for absent constructor properties.
    val from: String? = null,
    val to: String,
    val content: String,
) {
    init {
        require(to.isNotBlank()) { "Message 'to' cannot be blank" }
        require(content.isNotBlank()) { "Message 'content' cannot be blank" }
        require(from == null || from.isNotBlank()) { "Message 'from' cannot be blank" }
    }
}

fun Message.toJson(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(this)

/**
 * Client-facing control-message and close-code contract (RFC 6455 7.4.2 private
 * range, mirroring HTTP semantics). These names and codes are a stable API:
 * - 4401 REAUTH_REQUIRED: token ceiling passed -> client refreshes, re-mints a
 *   fresh ticket and reconnects.
 * - 4403 LOGGED_OUT: logout/revocation -> client stops reconnecting, shows logged-out UI.
 */
object WsControl {
    const val REAUTH_REQUIRED = "REAUTH_REQUIRED"
    const val LOGGED_OUT = "LOGGED_OUT"

    val REAUTH_CLOSE_STATUS = CloseStatus(4401, REAUTH_REQUIRED)
    val LOGOUT_CLOSE_STATUS = CloseStatus(4403, LOGGED_OUT)

    // grace period for flushing the control frame through the session's send
    // channel before the socket is closed
    const val CONTROL_CLOSE_GRACE_MS = 250L
}

/** Implemented by the chat message service so app/ (kafka, notification) can fan out. */
interface WebSocketNotificationSender {
    fun broadcast(message: Message): Message

    /**
     * Close all live WebSocket sessions bound to [userId] (Keycloak `sub`) with the
     * LOGGED_OUT control frame + 4403, via the userId -> sessions reverse index.
     * Returns the number of sessions closed. No-op for senders without a registry.
     */
    fun revokeUser(userId: String): Int = 0
}

@ConfigurationProperties(value = "com.softeno.ws")
data class ChatConfigProperties(
    val staleCheck: Boolean,
    val heartbeatIntervalSeconds: Int,
    val staleCheckThresholdSeconds: Int,
    // one-time handshake ticket TTL (seconds); tickets are single-use
    val ticketTtlSeconds: Int = 10,
    // how often the sweeper checks token-ceiling expiry and heartbeat staleness
    val sweepIntervalSeconds: Int = 10,
    // exact-match Origin allowlist for the /ws handshake (comma-separated)
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)
