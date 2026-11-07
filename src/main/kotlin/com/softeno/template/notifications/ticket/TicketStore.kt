package com.softeno.template.notifications.ticket

import reactor.core.publisher.Mono

/**
 * One-time handshake tickets (port). The production bean is Mongo-backed
 * ([MongoTicketStore]) so every instance redeems tickets minted by any
 * instance (multi-instance per ADR-0008); tests may use an in-memory fake.
 *
 * Semantics every implementation must honour:
 * - 256-bit opaque ticket, single-use, short TTL ([TicketProperties.ttlSeconds]).
 * - [redeem] is atomic compare-and-delete across the whole store AND an
 *   origin check — exactly one concurrent caller wins cluster-wide, and a
 *   ticket minted for one transport can never redeem on another.
 */
interface TicketStore {
    /** Mint a single-use ticket for [record], redeemable for [ttlSeconds]. */
    suspend fun issue(record: TicketRecord, ttlSeconds: Long = 10L): String

    /**
     * Atomic single-use redemption. Empty Mono (caller rejects the handshake
     * pre-body) when the ticket is unknown, already redeemed, TTL-expired,
     * the bound token ceiling passed, or minted for a DIFFERENT transport.
     */
    fun redeem(rawTicket: String, origin: TicketOrigin): Mono<TicketRecord>

    // Test/ops introspection — the store is technical, short-lived state.
    suspend fun size(): Long
    suspend fun keys(): Set<String>
    suspend fun clear()
}
