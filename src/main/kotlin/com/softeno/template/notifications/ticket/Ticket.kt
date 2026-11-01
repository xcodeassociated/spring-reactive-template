package com.softeno.template.notifications.ticket

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.time.Instant

/** Which transport a ticket was minted for — redeem enforces the match. */
enum class TicketOrigin {
    WEBSOCKET,
    SSE,
}

/**
 * Claims captured from the validated JWT at ticket-mint time.
 * Never contains the access token itself — only what is needed to rebuild an
 * Authentication at the handshake (WebSocket upgrade or SSE stream request).
 */
data class TicketRecord(
    val sub: String,
    val sid: String?,
    val exp: Instant,
    val roles: List<String>,
    val origin: TicketOrigin,
)

/** Shared one-time-ticket infrastructure config (`notifications/ticket`). */
@ConfigurationProperties(prefix = "com.softeno.ticket")
data class TicketProperties(
    /** mint→redeem TTL of a single-use ticket, seconds */
    val ttlSeconds: Int = 10,
    /** how often the store sweeps expired entries (crash/idle hygiene) */
    val sweepIntervalSeconds: Int = 10,
    /** exact-match Origin allowlist checked at ticket redemption (CSWSH/CWE-1385) */
    val allowedOrigins: List<String> = listOf("http://localhost:3000"),
)

/**
 * Rebuilds a [JwtAuthenticationToken] from the mint-time claims captured in a
 * [TicketRecord], so consumers of the security context (handler `claims["sub"]`
 * reads, the Mongo auditor, `.authenticated()` rules) keep working after a
 * ticket redemption — WebSocket upgrade or SSE stream request alike.
 *
 * The wrapped [Jwt] is a synthetic carrier: it is NOT a validated token and its
 * value must never be logged or forwarded anywhere. The security decision was
 * already made at mint time against a real, validated access token.
 */
object TicketAuthentication {
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

    private const val TICK_TOKEN_VALUE = "one-time-ticket"
}

object TicketSupport {
    /** exact-match allowlist (no wildcards, no suffix matching) */
    fun isOriginAllowed(origin: String, allowedOrigins: List<String>): Boolean =
        allowedOrigins.any { it == origin }
}

/**
 * Rebuilds an [Authentication] into an identity tuple — shared by the transports'
 * session/subscription registries.
 */
internal fun Authentication?.toTicketIdentity(): Pair<String, Instant?>? {
    val jwt = (this as? JwtAuthenticationToken)?.token ?: return null
    val sub = jwt.claims["sub"] as? String ?: return null
    return sub to jwt.expiresAt
}
