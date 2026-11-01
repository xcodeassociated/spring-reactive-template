package com.softeno.template.app.user.notification.security

import com.softeno.template.app.ticket.TicketFixture
import com.softeno.template.app.ticket.TicketOrigin
import com.softeno.template.app.ticket.TicketProperties
import com.softeno.template.app.ticket.TicketStore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.net.URI
import java.net.URLEncoder

/**
 * Filter-contract unit tests for the SSE ticket redemption at the stream GET:
 * the pre-body decision table (pass-throughs, Origin, redeem, origin binding).
 */
class SseTicketRedeemFilterTest : TicketFixture {

    private val store = TicketStore(TicketProperties())
    private val filter = SseTicketRedeemFilter(store, TicketProperties())

    private fun exchange(
        path: String = "/update/user",
        method: HttpMethod = HttpMethod.GET,
        origin: String? = "http://localhost:3000",
        ticket: String? = null,
    ): ServerWebExchange {
        val query = ticket?.let { "?ticket=${URLEncoder.encode(it, "UTF-8")}" } ?: ""
        val builder = MockServerHttpRequest.method(method, URI("http://localhost$path$query"))
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
    fun `requests outside the update paths pass through untouched`() {
        for (path in listOf("/coroutine/users", "/sample/1", "/ws-ticket")) {
            val exchange = exchange(path = path)
            val chain = RecordingChain()

            filter.filter(exchange, chain).block()

            assertEquals(true, chain.called)
            assertNull(chain.seenAuthentication)
        }
    }

    @Test
    fun `stream request without a ticket passes through (bearer or anonymous is the chain's call)`() {
        val exchange = exchange(ticket = null)
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(true, chain.called)
        assertNull(chain.seenAuthentication)
    }

    @Test
    fun `valid ticket is redeemed and binds mint-time identity`() {
        val record = aTicketRecord(origin = TicketOrigin.SSE)
        val ticket = store.issue(record)
        val chain = RecordingChain()

        filter.filter(exchange(ticket = ticket), chain).block()

        assertEquals(true, chain.called)
        val auth = chain.seenAuthentication as JwtAuthenticationToken
        assertEquals(record.sub, auth.token.claims["sub"])
        assertEquals(record.roles, auth.authorities.map { it.authority })
    }

    @Test
    fun `expired replayed or unknown ticket is rejected with 401 before any body`() {
        for (ticket in listOf("never-existed", "")) {
            val exchange = exchange(ticket = ticket)
            val chain = RecordingChain()

            filter.filter(exchange, chain).block()

            assertEquals(401, exchange.response.statusCode!!.value(), "for ticket: '$ticket'")
            assertEquals(false, chain.called)
        }

        // replay: second redeem of a valid ticket fails
        val valid = store.issue(aTicketRecord(origin = TicketOrigin.SSE))
        filter.filter(exchange(ticket = valid), RecordingChain()).block()
        val replay = exchange(ticket = valid)
        filter.filter(replay, RecordingChain()).block()
        assertEquals(401, replay.response.statusCode!!.value())
    }

    @Test
    fun `WEBSOCKET-origin ticket cannot open an SSE stream`() {
        val ticket = store.issue(aTicketRecord(origin = TicketOrigin.WEBSOCKET))
        val exchange = exchange(ticket = ticket)
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(401, exchange.response.statusCode!!.value())
        assertEquals(false, chain.called)
    }

    @Test
    fun `foreign origin with a ticket is rejected with 403`() {
        val ticket = store.issue(aTicketRecord(origin = TicketOrigin.SSE))
        val exchange = exchange(origin = "http://evil.example", ticket = ticket)
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(403, exchange.response.statusCode!!.value())
        assertEquals(false, chain.called)
    }

    @Test
    fun `no Origin header passes the check (non-browser clients)`() {
        val record = aTicketRecord(origin = TicketOrigin.SSE)
        val ticket = store.issue(record)
        val exchange = exchange(origin = null, ticket = ticket)
        val chain = RecordingChain()

        filter.filter(exchange, chain).block()

        assertEquals(true, chain.called)
        assert(chain.seenAuthentication is JwtAuthenticationToken)
    }
}
