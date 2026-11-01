package com.softeno.template.sample.websocket.security

import com.softeno.template.app.ticket.TicketAuthentication
import com.softeno.template.app.ticket.TicketOrigin
import com.softeno.template.app.ticket.TicketStore
import com.softeno.template.app.ticket.TicketSupport
import com.softeno.template.sample.websocket.ChatConfigProperties
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
 * Redeems a one-time WebSocket ticket at the `/ws` handshake, BEFORE the
 * handshake completes and before the security authorization check runs
 * (this filter is ordered ahead of the Spring Security web-filter chain).
 *
 * Contract:
 * - exact-match Origin allowlist on every handshake (RFC 6455 10.2, CSWSH/CWE-1385);
 *   non-browser clients may omit Origin — browsers always send it.
 * - valid ticket -> atomic compare-and-delete + origin check (SSE-minted tickets
 *   cannot open a WebSocket), rebuild an Authentication from the mint-time claims
 *   and write it into the reactive security context, so the handler's existing
 *   principal read and the /ws authenticated rule keep working.
 * - expired/replayed/wrong-origin ticket -> 401 before the upgrade.
 * - no ticket -> pass through: the security chain then rejects anonymous
 *   upgrades with 401, while existing header-based (non-browser) clients still work.
 *
 * The ticket rides in the query string — acceptable because it is opaque,
 * single-use and 10 s-lived (never an `access_token=`, RFC 9700 4.3.2).
 * Never log tickets.
 */
@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true")
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
class WsTicketRedeemFilter(
    private val ticketStore: TicketStore,
    private val config: ChatConfigProperties,
) : WebFilter {
    private val log = LogFactory.getLog(javaClass)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        val path = request.uri.path.trimEnd('/')
        if (request.method != HttpMethod.GET ||
            !isWebSocketUpgrade(request.headers["Upgrade"]?.firstOrNull()) ||
            !(path == "/ws" || path.startsWith("/ws/"))
        ) {
            return chain.filter(exchange)
        }

        val origin = request.headers.origin
        if (origin != null && !TicketSupport.isOriginAllowed(origin, config.allowedOrigins)) {
            log.warn("ws: [ticket] rejected handshake, foreign origin: $origin")
            exchange.response.statusCode = HttpStatus.FORBIDDEN
            return exchange.response.setComplete()
        }

        val ticket = request.queryParams.getFirst("ticket")
            ?: return chain.filter(exchange) // header-auth or anonymous: the security chain decides

        val record = ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET)
        if (record == null) {
            log.warn("ws: [ticket] rejected handshake, missing/expired/replayed ticket")
            exchange.response.statusCode = HttpStatus.UNAUTHORIZED
            return exchange.response.setComplete()
        }

        val authentication = TicketAuthentication.from(record)
        return chain.filter(exchange)
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
    }

    private fun isWebSocketUpgrade(upgradeHeader: String?): Boolean =
        "websocket".equals(upgradeHeader, ignoreCase = true)
}
