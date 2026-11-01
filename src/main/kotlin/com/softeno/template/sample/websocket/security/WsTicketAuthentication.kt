package com.softeno.template.sample.websocket.security

import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/**
 * Rebuilds an [JwtAuthenticationToken] from the mint-time claims captured in a
 * [TicketRecord], so consumers of the security context (the WebSocket handlers'
 * existing `claims["sub"]` reads, the Mongo auditor, `.authenticated()` rules)
 * keep working after a ticket redemption.
 *
 * The wrapped [Jwt] is a synthetic carrier: it is NOT a validated token and its
 * value must never be logged or forwarded anywhere. The security decision was
 * already made at mint time against a real, validated access token.
 */
object WsTicketAuthentication {
    // Jwt requires non-empty headers; this synthetic jwt is a claims carrier only.
    private val TICK_HEADERS = mapOf("typ" to "JWT")

    fun from(record: TicketRecord): JwtAuthenticationToken {
        val claims = mutableMapOf<String, Any>(
            OAuth2TokenIntrospectionClaimNames.SUB to record.sub,
            OAuth2TokenIntrospectionClaimNames.EXP to record.exp.epochSecond,
        )
        record.sid?.let { claims["sid"] = it }

        val jwt = Jwt(TICK_TOKEN_VALUE, Instant.now(), record.exp, TICK_HEADERS, claims)
        val authorities: Collection<GrantedAuthority> = record.roles.map(::SimpleGrantedAuthority)
        return JwtAuthenticationToken(jwt, authorities)
    }

    private const val TICK_TOKEN_VALUE = "ws-ticket"
}

/** Identity bound to a live WebSocket session at handshake time. */
data class SessionBinding(val sub: String, val sid: String?, val exp: Instant)

internal fun Authentication?.toSessionBinding(): SessionBinding? {
    val jwt = (this as? JwtAuthenticationToken)?.token ?: return null
    val sub = jwt.claims["sub"] as? String ?: return null
    return SessionBinding(sub = sub, sid = jwt.claims["sid"] as? String, exp = jwt.expiresAt ?: Instant.now())
}
