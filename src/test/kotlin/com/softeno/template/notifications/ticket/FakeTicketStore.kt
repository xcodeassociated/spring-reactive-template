package com.softeno.template.notifications.ticket

import reactor.core.publisher.Mono
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [TicketStore] for FAST unit tests (the filter contract tests).
 * Production uses [MongoTicketStore]; this fake honours the same contract —
 * atomic compare-and-delete, origin match, TTL + token-ceiling checks — so a
 * test passing here exercises real semantics without spinning up Mongo.
 */
class FakeTicketStore : TicketStore {
    private val secureRandom = SecureRandom()
    private val tickets = ConcurrentHashMap<String, Pair<TicketRecord, Instant>>()

    override suspend fun issue(record: TicketRecord, ttlSeconds: Long): String {
        val bytes = ByteArray(32).also(secureRandom::nextBytes)
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tickets[MongoTicketStore.hash(ticket)] = record to Instant.now().plusSeconds(ttlSeconds)
        return ticket
    }

    override fun redeem(rawTicket: String, origin: TicketOrigin): Mono<TicketRecord> {
        val stored = tickets.remove(MongoTicketStore.hash(rawTicket)) ?: return Mono.empty()
        val (record, deleteAt) = stored
        val now = Instant.now()
        return when {
            record.origin != origin -> Mono.empty()
            deleteAt.isBefore(now) -> Mono.empty()
            record.exp.isBefore(now) -> Mono.empty()
            else -> Mono.just(record)
        }
    }

    override suspend fun size(): Long = tickets.size.toLong()

    override suspend fun keys(): Set<String> = tickets.keys.toSet()

    override suspend fun clear() = tickets.clear()
}
