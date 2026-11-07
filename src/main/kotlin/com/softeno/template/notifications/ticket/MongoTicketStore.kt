package com.softeno.template.notifications.ticket

import com.softeno.template.notifications.ticket.db.TicketDocument
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.apache.commons.logging.LogFactory
import org.springframework.data.mongodb.core.ReactiveMongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

/**
 * Mongo-backed [TicketStore] — the multi-instance implementation.
 *
 * - Only SHA-256(ticket) is persisted as `_id`; the raw ticket never touches
 *   disk or logs.
 * - `redeem` is `findAndRemove`: atomic cluster-wide, so a ticket redeemed on
 *   one instance is gone for every other — the same exactly-one-winner
 *   guarantee the old in-memory map gave within a single process, now shared.
 * - Expiry is enforced twice, deliberately: the TTL capped-at index on
 *   `deleteAt` lets the database evict dead tickets on every instance with no
 *   sweeper code (crash-orphan-free), while redeem re-checks
 *   `deleteAt`/`exp` in code because the TTL monitor is periodic — the index
 *   is the janitor, the check is the gate.
 */
@Service
class MongoTicketStore(
    private val mongoTemplate: ReactiveMongoTemplate,
    private val config: TicketProperties,
) : TicketStore {
    private val log = LogFactory.getLog(javaClass)
    private val secureRandom = SecureRandom()

    override suspend fun issue(record: TicketRecord, ttlSeconds: Long): String {
        val bytes = ByteArray(TICKET_BYTES).also(secureRandom::nextBytes)
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        mongoTemplate.insert(
            TicketDocument(
                id = hash(ticket),
                sub = record.sub,
                sid = record.sid,
                exp = record.exp,
                roles = record.roles,
                origin = record.origin,
                deleteAt = Instant.now().plusSeconds(ttlSeconds),
            )
        ).awaitSingle()
        log.debug("ticket: issued ${record.origin} ticket for sub: ${record.sub}")
        return ticket
    }

    override fun redeem(rawTicket: String, origin: TicketOrigin): Mono<TicketRecord> =
        mongoTemplate
            .findAndRemove(
                Query.query(Criteria.where("_id").`is`(hash(rawTicket))),
                TicketDocument::class.java,
            )
            .flatMap { doc ->
                val now = Instant.now()
                when {
                    doc.origin != origin -> {
                        log.warn("ticket: ${doc.origin} ticket presented on $origin — rejected")
                        Mono.empty()
                    }
                    doc.deleteAt.isBefore(now) -> Mono.empty()
                    doc.exp.isBefore(now) -> Mono.empty()
                    else -> {
                        log.debug("ticket: redeemed ${origin} ticket for sub: ${doc.sub}")
                        Mono.just(TicketRecord(doc.sub, doc.sid, doc.exp, doc.roles, doc.origin))
                    }
                }
            }

    override suspend fun size(): Long =
        mongoTemplate.count(Query(), TicketDocument::class.java).awaitSingle()

    override suspend fun keys(): Set<String> =
        mongoTemplate.findAll(TicketDocument::class.java)
            .map { it.id }
            .collectList()
            .awaitSingleOrNull()
            .orEmpty()
            .toSet()

    override suspend fun clear() {
        mongoTemplate.remove(Query(), TicketDocument::class.java).awaitSingleOrNull()
    }

    companion object {
        private const val TICKET_BYTES = 32 // 256-bit

        // SHA-256 hex of the raw ticket — used only as the store key.
        fun hash(ticket: String): String =
            MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
