package com.softeno.template.app.user.notification.api

import com.softeno.template.app.ticket.TicketOrigin
import com.softeno.template.app.ticket.TicketProperties
import com.softeno.template.app.ticket.TicketRecord
import com.softeno.template.app.ticket.TicketStore
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.logging.LogFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * SSE ticket mint endpoint (one-time-ticket SSE handshake auth — the stream
 * twin of `WsTicketController`, sharing the `app/ticket` infrastructure).
 *
 * POST (not GET): minting creates a credential, and a safe-method mint would be
 * a cross-site mint primitive (RFC 10017 §6.1.3.3 — non-simple method → CORS
 * preflight blocks cross-site minting for free).
 *
 * Behind the normal JWT resource-server chain (`/sse-ticket` requires an
 * authenticated exchange in `SecurityConfig`); this controller additionally
 * answers 401 when no Authentication is present in the security context
 * (defense in depth, and what makes it testable under the permit-all test profile).
 *
 * The minted ticket is bound to origin=SSE — it can never redeem on /ws.
 * Response contains only the opaque ticket + TTL — minting never exposes or
 * logs the access token.
 */
@RestController
class SseTicketController(
    private val ticketStore: TicketStore,
    private val config: TicketProperties,
) {
    private val log = LogFactory.getLog(javaClass)

    data class TicketResponse(val ticket: String, val expiresIn: Int)

    @PostMapping("/sse-ticket")
    suspend fun mint(): ResponseEntity<TicketResponse> {
        val authentication = ReactiveSecurityContextHolder.getContext()
            .awaitSingleOrNull()
            ?.authentication as? JwtAuthenticationToken

        // a principal without a usable subject cannot be bound to a stream;
        // treat it like anonymous rather than minting a ticket for ""
        val sub = (authentication?.token?.claims?.get("sub") as? String)?.takeIf { it.isNotBlank() }
        if (authentication == null || sub == null) {
            log.debug("sse: [ticket] mint rejected: no usable authenticated principal")
            return ResponseEntity.status(401).build()
        }

        val claims = authentication.token.claims
        val record = TicketRecord(
            sub = sub,
            sid = claims["sid"] as? String,
            exp = authentication.token.expiresAt ?: Instant.now(),
            roles = authentication.authorities.mapNotNull { it.authority },
            origin = TicketOrigin.SSE,
        )
        log.debug("sse: [ticket] mint requested for sub: ${record.sub}")
        return ResponseEntity.ok(TicketResponse(ticketStore.issue(record), config.ttlSeconds))
    }
}
