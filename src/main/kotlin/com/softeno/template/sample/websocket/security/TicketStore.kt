package com.softeno.template.sample.websocket.security

import com.softeno.template.sample.websocket.ChatConfigProperties
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Claims captured from the validated JWT at ticket-mint time.
 * Never contains the access token itself — only what is needed to rebuild an
 * Authentication at the WebSocket upgrade.
 */
data class TicketRecord(
    val sub: String,
    val sid: String?,
    val exp: Instant,
    val roles: List<String>,
)

/**
 * One-time WebSocket handshake tickets (see websocket-auth-session-lifecycle skill).
 *
 * - A ticket is 256 bits of SecureRandom, single-use, with a short TTL
 *   ([ChatConfigProperties.ticketTtlSeconds], default 10 s).
 * - Only SHA-256(ticket) is kept as the map key, so a leaked store dump cannot
 *   be replayed. The raw ticket is never logged.
 * - [redeem] is an atomic compare-and-delete: exactly one concurrent caller can
 *   ever get a record out of a ticket.
 * - Per-process in-memory store: fine for a single instance. Multi-instance
 *   deployments must externalize (or instance-pin) the ticket store. # VERIFY per deployment.
 */
@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true")
@Service
class TicketStore(private val config: ChatConfigProperties) {
    private val log = LogFactory.getLog(javaClass)
    private val secureRandom = SecureRandom()
    private val tickets = ConcurrentHashMap<String, StoredTicket>()

    private data class StoredTicket(val record: TicketRecord, val deleteAt: Instant)

    /** Mint a single-use ticket for [record], valid for [ttlSeconds] (default: configured TTL). */
    fun issue(record: TicketRecord, ttlSeconds: Long = config.ticketTtlSeconds.toLong()): String {
        evictExpired()
        val bytes = ByteArray(TICKET_BYTES).also { secureRandom.nextBytes(it) }
        val ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        tickets[hash(ticket)] = StoredTicket(record, Instant.now().plusSeconds(ttlSeconds))
        log.debug("ws: [ticket] issued ticket for sub: ${record.sub}")
        return ticket
    }

    /**
     * Atomic single-use redemption. Returns null (and the caller must reject the
     * handshake pre-upgrade) when the ticket is unknown, already redeemed,
     * TTL-expired, or the bound token ceiling already passed.
     */
    fun redeem(rawTicket: String): TicketRecord? {
        evictExpired()
        val stored = tickets.remove(hash(rawTicket)) ?: return null
        val now = Instant.now()
        if (stored.deleteAt.isBefore(now)) return null
        if (stored.record.exp.isBefore(now)) return null
        log.debug("ws: [ticket] redeemed ticket for sub: ${stored.record.sub}")
        return stored.record
    }

    internal fun size(): Int = tickets.size

    internal fun keys(): Set<String> = tickets.keys.toSet()

    internal fun clear() = tickets.clear()

    // Bounded by design: minting requires an authenticated caller and entries
    // only live for the TTL; a sweep on every operation keeps the map from growing.
    private fun evictExpired() {
        val now = Instant.now()
        tickets.entries.removeIf { it.value.deleteAt.isBefore(now) }
    }

    companion object {
        private const val TICKET_BYTES = 32 // 256-bit

        // SHA-256 hex of the raw ticket — used only as the store key.
        fun hash(ticket: String): String =
            MessageDigest.getInstance("SHA-256").digest(ticket.toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
