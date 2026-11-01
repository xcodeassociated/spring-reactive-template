package com.softeno.template.notifications.ticket

import com.softeno.template.testsupport.R
import java.time.Instant

/**
 * TicketRecord fixtures for the ticket store / redeem / authentication tests.
 * Randomized via [R]; tests keep the record and assert against its values.
 */
interface TicketFixture {

    fun aTicketRecord(
        sub: String = R.sub(),
        sid: String = "sid-${R.word()}",
        exp: Instant = Instant.now().plusSeconds(300),
        roles: List<String> = listOf("ROLE_USER"),
        origin: TicketOrigin = TicketOrigin.WEBSOCKET,
    ): TicketRecord = TicketRecord(sub, sid, exp, roles, origin)
}
