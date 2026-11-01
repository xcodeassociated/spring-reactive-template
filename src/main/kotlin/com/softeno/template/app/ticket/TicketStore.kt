package com.softeno.template.app.ticket

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.apache.commons.logging.LogFactory
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * One-time handshake tickets, shared by the WebSocket and SSE transports
 * (see websocket-auth-session-lifecycle / sse-auth-session-lifecycle skills).
 *
 * - A ticket is 256 bits of SecureRandom, single-use, with a short TTL
 *   ([TicketProperties.ttlSeconds], default 10 s).
 * - Only SHA-256(ticket) is kept as the map key, so a leaked store dump cannot
 *   be replayed. The raw ticket is never logged.
 * - [redeem] is an atomic compare-and-delete AND origin check: exactly one
 *   concurrent caller can get a record out of a ticket, and a ticket minted for
 *   one transport (WEBSOCKET/SSE) can never redeem on the other.
 * - Per-process in-memory store: a crash or shutdown leaves NO orphans (the
 *   store dies with the process). A scheduled sweep evicts expired entries even
 *   when traffic stops, so idle stores never accumulate dead tickets.
 *   Multi-instance deployments must externalize (or instance-pin) the store.
 */
@Service
class TicketStore(private val config: TicketProperties) {
    private val log = LogFactory.getLog(javaClass)
    private val secureRandom = SecureRandom()
    private val tickets = ConcurrentHashMap<String, StoredTicket>()

    private data class StoredTicket(val record: TicketRecord, val deleteAt: Instant)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + MDCContext(emptyMap()))

    private val sweeper: Job by lazy {
        serviceScope.launch {
            while (isActive) {
                delay(config.sweepIntervalSeconds.toLong().seconds)
                val evicted = evictExpired()
                if (evicted > 0) log.debug("ticket: swept $evicted expired ticket(s)")
            }
        }
    }

    @PostConstruct
    fun start() {
        // hygiene pass on boot (no-orphans invariant; trivially empty in-memory,
        // meaningful once the store is externalized) + always-on sweep
        evictExpired()
        sweeper
    }

    @PreDestroy
    fun stop() = serviceScope.coroutineContext.cancel()

    /** Mint a single-use ticket for [record], valid for [ttlSeconds] (default: configured TTL). */
    fun issue(record: TicketRecord, ttlSeconds: Long = config.ttlSeconds.toLong()): String {
        evictExpired()
        val bytes = ByteArray(TICKET_BYTES).also { secureRandom.nextBytes(it) }
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tickets[hash(ticket)] = StoredTicket(record, Instant.now().plusSeconds(ttlSeconds))
        log.debug("ticket: issued ${record.origin} ticket for sub: ${record.sub}")
        return ticket
    }

    /**
     * Atomic single-use redemption. Returns null (and the caller must reject the
     * handshake pre-body) when the ticket is unknown, already redeemed,
     * TTL-expired, the bound token ceiling already passed, or minted for a
     * DIFFERENT transport than [origin].
     */
    fun redeem(rawTicket: String, origin: TicketOrigin): TicketRecord? {
        evictExpired()
        val stored = tickets.remove(hash(rawTicket)) ?: return null
        val now = Instant.now()
        if (stored.record.origin != origin) {
            log.warn("ticket: ${stored.record.origin} ticket presented on $origin — rejected")
            return null
        }
        if (stored.deleteAt.isBefore(now)) return null
        if (stored.record.exp.isBefore(now)) return null
        log.debug("ticket: redeemed ${origin} ticket for sub: ${stored.record.sub}")
        return stored.record
    }

    internal fun size(): Int = tickets.size

    internal fun keys(): Set<String> = tickets.keys.toSet()

    internal fun clear() = tickets.clear()

    // Bounded by design: minting requires an authenticated caller and entries
    // only live for the TTL; sweeps on every operation AND on the idle timer.
    private fun evictExpired(): Int {
        val now = Instant.now()
        val before = tickets.size
        tickets.entries.removeIf { it.value.deleteAt.isBefore(now) }
        return before - tickets.size
    }

    companion object {
        private const val TICKET_BYTES = 32 // 256-bit

        // SHA-256 hex of the raw ticket — used only as the store key.
        fun hash(ticket: String): String =
            MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
