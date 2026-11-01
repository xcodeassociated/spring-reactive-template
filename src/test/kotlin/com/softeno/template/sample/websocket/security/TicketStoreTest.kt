package com.softeno.template.sample.websocket.security

import com.softeno.template.sample.websocket.ChatConfigProperties
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class TicketStoreTest {
    private fun config(ttl: Int = 10) = ChatConfigProperties(
        staleCheck = false,
        heartbeatIntervalSeconds = 10,
        staleCheckThresholdSeconds = 30,
        ticketTtlSeconds = ttl,
    )

    private fun record(expOffsetSeconds: Long = 300) =
        TicketRecord("sub-1", "sid-1", Instant.now().plusSeconds(expOffsetSeconds), listOf("ROLE_USER"))

    @Test
    fun `issue returns 256-bit url-safe ticket and stores only its SHA-256 hash`() {
        val store = TicketStore(config())
        val ticket = store.issue(record())

        // 32 bytes base64url without padding = 43 chars
        assertEquals(43, ticket.length)
        assertEquals(1, store.size())
        // keyed by SHA-256, raw ticket never stored
        assertEquals(setOf(TicketStore.hash(ticket)), store.keys())
    }

    @Test
    fun `redeem is single use`() {
        val store = TicketStore(config())
        val ticket = store.issue(record())

        val first = store.redeem(ticket)
        assertNotNull(first)
        assertEquals("sub-1", first!!.sub)

        // replay fails; store drained
        assertNull(store.redeem(ticket))
        assertEquals(0, store.size())
    }

    @Test
    fun `concurrent redeem - exactly one winner (atomic compare-and-delete)`() {
        val store = TicketStore(config())
        val ticket = store.issue(record())
        val winners = AtomicInteger()

        runBlocking {
            List(16) {
                async { if (store.redeem(ticket) != null) winners.incrementAndGet() }
            }.awaitAll()
        }

        assertEquals(1, winners.get())
    }

    @Test
    fun `expired ticket TTL is rejected`() {
        val store = TicketStore(config())
        val ticket = store.issue(record(), ttlSeconds = -1)
        assertNull(store.redeem(ticket))
    }

    @Test
    fun `ticket whose token ceiling already passed is rejected`() {
        val store = TicketStore(config())
        val ticket = store.issue(record(expOffsetSeconds = -1))
        assertNull(store.redeem(ticket))
    }

    @Test
    fun `unknown ticket is rejected`() {
        val store = TicketStore(config())
        store.issue(record())
        assertNull(store.redeem("not-a-real-ticket"))
    }

    @Test
    fun `expired entries are evicted so the store stays bounded`() {
        val store = TicketStore(config())
        repeat(50) { store.issue(record(), ttlSeconds = -1) }
        // eviction runs at the start of every operation: the next issue drops
        // all 50 dead entries and only its own survives
        store.issue(record())
        assertEquals(1, store.size())
    }
}
