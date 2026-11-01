package com.softeno.template.sample.websocket.api

import com.softeno.template.sample.websocket.ChatConfigProperties
import com.softeno.template.sample.websocket.security.TicketRecord
import com.softeno.template.sample.websocket.security.TicketStore
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

/**
 * Ticket mint endpoint (one-time-ticket WebSocket handshake auth).
 *
 * Coroutine-style, matching the websocket feature's coroutine-only convention
 * (WebFlux supports `suspend` controller methods natively).
 *
 * Behind the normal JWT resource-server chain (`/ws-ticket` requires an
 * authenticated exchange in `SecurityConfig`); this controller additionally
 * answers 401 when no Authentication is present in the security context
 * (defense in depth, and what makes it testable under the permit-all test profile).
 *
 * Response contains only the opaque ticket + TTL — minting a ticket never
 * exposes or logs the access token.
 */
@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true")
@RestController
class WsTicketController(
    private val ticketStore: TicketStore,
    private val config: ChatConfigProperties,
) {
    private val log = LogFactory.getLog(javaClass)

    data class TicketResponse(val ticket: String, val expiresIn: Int)

    @PostMapping("/ws-ticket")
    suspend fun mint(): ResponseEntity<TicketResponse> {
        val authentication = ReactiveSecurityContextHolder.getContext()
            .awaitSingleOrNull()
            ?.authentication as? JwtAuthenticationToken

        // a principal without a usable subject cannot be bound to a session;
        // treat it like anonymous rather than minting a ticket for ""
        val sub = (authentication?.token?.claims?.get("sub") as? String)?.takeIf { it.isNotBlank() }
        if (authentication == null || sub == null) {
            log.debug("ws: [ticket] mint rejected: no usable authenticated principal")
            return ResponseEntity.status(401).build()
        }

        val claims = authentication.token.claims
        val record = TicketRecord(
            sub = sub,
            sid = claims["sid"] as? String,
            exp = authentication.token.expiresAt ?: Instant.now(),
            roles = authentication.authorities.mapNotNull { it.authority },
        )
        log.debug("ws: [ticket] mint requested for sub: ${record.sub}")
        return ResponseEntity.ok(TicketResponse(ticketStore.issue(record), config.ticketTtlSeconds))
    }
}
