package com.softeno.template.notifications.sse.security

import com.softeno.template.notifications.ticket.TicketAuthentication
import com.softeno.template.notifications.ticket.TicketOrigin
import com.softeno.template.notifications.ticket.TicketProperties
import com.softeno.template.notifications.ticket.TicketStore
import com.softeno.template.notifications.ticket.TicketSupport
import org.apache.commons.logging.LogFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * Redeems a one-time SSE ticket at the update stream request, BEFORE the
 * body is written and before the security authorization check runs (this filter
 * is ordered ahead of the Spring Security web-filter chain). The stream twin of
 * `WsTicketRedeemFilter`.
 *
 * Contract:
 * - acts only when the request carries a `ticket` query param — requests without
 *   one pass through, so the normal bearer path is untouched.
 * - exact-match Origin allowlist when Origin is present (browser streams).
 * - valid ticket -> atomic compare-and-delete + origin check (WEBSOCKET-minted
 *   tickets cannot open an SSE stream); an Authentication rebuilt from the
 *   mint-time claims goes into the reactive security context, so the
 *   authenticated rule for the update paths and the controller's principal
 *   read work.
 * - expired/replayed/wrong-origin ticket -> 401 before any body bytes.
 *
 * Client contract (WHATWG): EventSource auto-reconnect replays the same URL,
 * so the ticket is single-use by design and the browser client must re-mint on
 * `error` before reconnecting — see ADR-0002. Never log tickets.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class SseTicketRedeemFilter(
    private val ticketStore: TicketStore,
    private val config: TicketProperties,
) : WebFilter {
    private val log = LogFactory.getLog(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.uri.path.trimEnd('/')
        if (request.method != HttpMethod.GET ||
            !(path == "/update" || path.startsWith("/update/"))
        ) {
            return chain.filter(exchange)
        }

        val ticket = request.queryParams.getFirst("ticket")
            ?: return chain.filter(exchange) // bearer or anonymous: the security chain decides

        val origin = request.headers.origin
        if (origin != null && !TicketSupport.isOriginAllowed(origin, config.allowedOrigins)) {
            log.warn("sse: [ticket] rejected stream request, foreign origin: $origin")
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return exchange.response.setComplete()
        }

        val record = ticketStore.redeem(ticket, TicketOrigin.SSE)
        if (record == null) {
            log.warn("sse: [ticket] rejected stream request, missing/expired/replayed ticket")
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        val authentication = TicketAuthentication.from(record)
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
    }
}
