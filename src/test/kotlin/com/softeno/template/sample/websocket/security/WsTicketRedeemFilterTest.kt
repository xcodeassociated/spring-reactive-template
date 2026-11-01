package com.softeno.template.sample.websocket.security

import com.softeno.template.sample.websocket.WS_ALLOWED_ORIGIN
import com.softeno.template.sample.websocket.aChatConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.web.server.ServerWebExchange
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URLEncoder

/**
 * Filter-contract unit tests against mock exchanges: the exact pre-upgrade
 * decision table (Origin allowlist, ticket redeem, pass-throughs).
 */
class WsTicketRedeemFilterTest : WsTicketFixture {

    private val config = aChatConfig()
    private val store = TicketStore(config)
    private val filter = WsTicketRedeemFilter(store, config)

    private fun exchange(
        path: String = "/ws",
        method: HttpMethod = HttpMethod.GET,
        upgrade: String? = "websocket",
        origin: String? = WS_ALLOWED_ORIGIN,
        ticket: String? = null,
    ): ServerWebExchange {
        val query = ticket?.let { "?ticket=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val builder = MockServerHttpRequest.method(method, URI("http://localhost$path$query"))
        upgrade?.let { builder.header(HttpHeaders.UPGRADE, it) }
        origin?.let { builder.header(HttpHeaders.ORIGIN, it) }
        return MockServerWebExchange.from(builder)
    }

    /** Chain that records whether it was reached + the security context it sees. */
    private inner class RecordingChain : WebFilterChain {
        var called = false
        var seenAuthentication: Authentication? = null

        override fun filter(exchange: ServerWebExchange): Mono<Void> {
            called = true
            return ReactiveSecurityContextHolder.getContext()
                .doOnNext { seenAuthentication = it.authentication }
                .then()
        }
    }

    @Test
    fun `non-ws and non-upgrade requests pass through untouched`() {
        for (exchange in listOf(
            exchange(path = "/coroutine/users"),
            exchange(upgrade = null),
            exchange(method = HttpMethod.POST),
        )) {
            val chain = RecordingChain()
            filter.filter(exchange, chain).block()
            assertEquals(true, chain.called)
            assertNull(chain.seenAuthentication)
            assertEquals(200, exchange.response.statusCode?.value() ?: 200)
        }
    }

    @Test
    fun `foreign origin is rejected with 403 and the chain never runs`() {
        val exchange = exchange(origin = "http://evil.example", ticket = store.issue(aTicketRecord()))
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(403, exchange.response.statusCode!!.value())
        assertEquals(false, chain.called)
    }

    @Test
    fun `missing or invalid ticket is rejected with 401 before the handshake`() {
        for (ticket in listOf("never-existed", "")) {
            val exchange = exchange(ticket = ticket)
            val chain = RecordingChain()

            filter.filter(exchange, chain).block()

            assertEquals(401, exchange.response.statusCode!!.value(), "for ticket: '$ticket'")
            assertEquals(false, chain.called)
        }
    }

    @Test
    fun `no ticket falls through to the security chain (header-auth or anonymous 401)`() {
        val exchange = exchange(ticket = null)
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(true, chain.called)
        assertNull(chain.seenAuthentication) // nothing was injected
    }

    @Test
    fun `no Origin header passes the check (non-browser clients)`() {
        val record = aTicketRecord()
        val exchange = exchange(origin = null, ticket = store.issue(record))
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(true, chain.called)
        val auth = chain.seenAuthentication as JwtAuthenticationToken
        assertEquals(record.sub, auth.token.claims["sub"])
    }

    @Test
    fun `valid ticket is redeemed atomically and binds mint-time identity`() {
        val record = aTicketRecord()
        val ticket = store.issue(record)
        val chain = RecordingChain()

        filter.filter(exchange(ticket = ticket), chain).block()

        val auth = chain.seenAuthentication as JwtAuthenticationToken
        assertEquals(record.sub, auth.token.claims["sub"])
        assertEquals(record.roles, auth.authorities.map { it.authority })

        // single use: the second handshake with the same ticket is rejected pre-upgrade
        val chain2 = RecordingChain()
        val replay = exchange(ticket = ticket)
        filter.filter(replay, chain2).block()
        assertEquals(401, replay.response.statusCode!!.value())
        assertEquals(false, chain2.called)
    }

    @Test
    fun `trailing-slash ws path is normalized so the ticket is not wasted`() {
        val ticket = store.issue(aTicketRecord())
        val chain = RecordingChain()

        filter.filter(exchange(path = "/ws/", ticket = ticket), chain).block()

        assertEquals(true, chain.called)
        assert(chain.seenAuthentication is JwtAuthenticationToken) { "identity must be bound on /ws/ too" }
    }
}
