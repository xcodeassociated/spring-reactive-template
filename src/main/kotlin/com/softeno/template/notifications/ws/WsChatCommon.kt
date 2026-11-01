package com.softeno.template.notifications.ws

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.web.reactive.socket.CloseStatus

/**
 * WebSocket adapter specifics: control codes + config. The shared envelope and
 * the port live in the `notifications` root.
 */

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

@ConfigurationProperties(value = "com.softeno.ws")
data class ChatConfigProperties(
    val staleCheck: Boolean,
    val heartbeatIntervalSeconds: Int,
    val staleCheckThresholdSeconds: Int,
    // how often the sweeper checks token-ceiling expiry and heartbeat staleness
    val sweepIntervalSeconds: Int = 10,
    // exact-match Origin allowlist for the /ws handshake (comma-separated)
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)
