package com.softeno.template.notifications.ticket.db

import com.softeno.template.notifications.ticket.TicketOrigin
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.index.Indexed
import org.springframework.data.mongodb.core.mapping.Document
import java.time.Instant

/**
 * A minted, not-yet-redeemed one-time ticket. Technical coordination record,
 * not a domain entity — deliberately outside [BaseDocument]: no audit fields,
 * no version, no history.
 *
 * - `id` is SHA-256(raw ticket) — the raw ticket is never persisted, a leaked
 *   collection dump cannot be replayed.
 * - `deleteAt` carries the TTL index (expireAfter = 0s = capped-at): the
 *   database evicts expired tickets on its own, on every instance, with no
 *   in-process sweeper. The TTL monitor runs periodically, so redeem re-checks
 *   `deleteAt`/`exp` in code — the index is the janitor, not the gate.
 */
@Document("ticket")
data class TicketDocument(
    @Id val id: String,
    val sub: String,
    val sid: String?,
    val exp: Instant,
    val roles: List<String>,
    val origin: TicketOrigin,
    @Indexed(expireAfter = "0s") val deleteAt: Instant,
)
