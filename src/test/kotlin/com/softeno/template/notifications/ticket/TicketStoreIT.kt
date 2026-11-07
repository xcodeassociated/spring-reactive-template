package com.softeno.template.notifications.ticket

import com.softeno.template.notifications.ticket.db.TicketDocument
import com.softeno.template.testsupport.BaseIntegrationTest
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.reactor.awaitSingle
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.concurrent.atomic.AtomicInteger

/**
 * Contract tests for the SHARED (Mongo-backed) one-time ticket store. Runs
 * against real Mongo so the atomicity guarantees tested here are the ones
 * production relies on across instances (findAndRemove = exactly one winner
 * cluster-wide; TTL index = instance-independent eviction).
 */
class TicketStoreIT : BaseIntegrationTest(), TicketFixture {

    @Autowired
    lateinit var ticketStore: TicketStore

    @Autowired
    lateinit var mongoTemplate: ReactiveMongoTemplate

    @AfterEach
    fun wipeTickets() {
        runBlocking { ticketStore.clear() }
    }

    @Test
    fun `issue returns 256-bit url-safe ticket and stores only its SHA-256 hash`() {
        val ticket = runBlocking { ticketStore.issue(aTicketRecord()) }

        // 32 bytes base64url without padding = 43 chars
        assertEquals(43, ticket.length)
        assertEquals(1L, runBlocking { ticketStore.size() })
        // keyed by SHA-256, raw ticket never stored
        assertEquals(setOf(MongoTicketStore.hash(ticket)), runBlocking { ticketStore.keys() })
    }

    @Test
    fun `redeem is single use`() {
        val record = aTicketRecord()
        val ticket = runBlocking { ticketStore.issue(record) }

        val first = ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET).block()
        assertNotNull(first)
        assertEquals(record.sub, first!!.sub)

        // replay fails; store drained
        assertNull(ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET).block())
        assertEquals(0L, runBlocking { ticketStore.size() })
    }

    @Test
    fun `concurrent redeem - exactly one winner (atomic compare-and-delete)`() {
        val ticket = runBlocking { ticketStore.issue(aTicketRecord()) }
        val winners = AtomicInteger()

        runBlocking {
            List(16) {
                async { if (ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET).block() != null) winners.incrementAndGet() }
            }.awaitAll()
        }

        assertEquals(1, winners.get())
    }

    @Test
    fun `expired ticket TTL is rejected`() {
        val ticket = runBlocking { ticketStore.issue(aTicketRecord(), ttlSeconds = -1) }
        assertNull(ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET).block())
    }

    @Test
    fun `ticket whose token ceiling already passed is rejected`() {
        val ticket = runBlocking { ticketStore.issue(aTicketRecord(exp = Instant.now().minusSeconds(1))) }
        assertNull(ticketStore.redeem(ticket, TicketOrigin.WEBSOCKET).block())
    }

    @Test
    fun `unknown ticket is rejected`() {
        runBlocking { ticketStore.issue(aTicketRecord()) }
        assertNull(ticketStore.redeem("not-a-real-ticket", TicketOrigin.WEBSOCKET).block())
    }

    @Test
    fun `the ticket collection carries a capped-at TTL index so expiry needs no sweeper`() {
        // eviction is the database's job on EVERY instance: a TTL index with
        // expireAfter=0s on deleteAt removes documents once their value passes.
        val infos = runBlocking {
            mongoTemplate.indexOps(TicketDocument::class.java).indexInfo.collectList().awaitSingle()
        }

        assertTrue(
            infos.any { it.name.startsWith("deleteAt") && it.expireAfter == Optional.of(Duration.ZERO) },
            "expected a TTL (expireAfter=0s) index on deleteAt, got: ${infos.joinToString(" | ")}"
        )
    }
}
