package com.softeno.template.sample.websocket.security

import com.softeno.template.testsupport.R
import java.time.Instant

/**
 * [TicketRecord] fixtures for the ticket store / redeem / authentication tests.
 * Randomized via [R] — tests keep the record and assert against its values.
 */
interface WsTicketFixture {

    fun aTicketRecord(
        sub: String = R.sub(),
        sid: String = "sid-${R.word()}",
        exp: Instant = Instant.now().plusSeconds(300),
        roles: List<String> = listOf("ROLE_USER"),
    ): TicketRecord = TicketRecord(sub, sid, exp, roles)
}
