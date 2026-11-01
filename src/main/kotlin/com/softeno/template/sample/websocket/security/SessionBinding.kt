package com.softeno.template.sample.websocket.security

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/** Identity bound to a live WebSocket session at handshake time. */
data class SessionBinding(val sub: String, val sid: String?, val exp: Instant)

internal fun Authentication?.toSessionBinding(): SessionBinding? {
    val jwt = (this as? JwtAuthenticationToken)?.token ?: return null
    val sub = jwt.claims["sub"] as? String ?: return null
    return SessionBinding(sub = sub, sid = jwt.claims["sid"] as? String, exp = jwt.expiresAt ?: Instant.now())
}
