package com.softeno.template.notifications.ticket

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Rebuilding a Spring `Authentication` from the mint-time [TicketRecord] claims
 * (shared by the WebSocket and SSE redeem filters).
 */
class TicketAuthenticationTest : TicketFixture {

    @Test
    fun `rebuilds authentication from mint-time claims`() {
        val record = aTicketRecord(
            exp = Instant.now().plusSeconds(120),
            roles = listOf("ROLE_USER", "ROLE_ADMIN"),
        )

        val auth = TicketAuthentication.from(record)

        assertEquals(record.sub, auth.token.claims["sub"])
        assertEquals(record.sid, auth.token.claims["sid"])
        assertEquals(record.exp.epochSecond, auth.token.expiresAt!!.epochSecond)
        assertEquals(record.roles, auth.authorities.map { it.authority })
    }

    @Test
    fun `identity tuple carries sub and exp from the rebuilt authentication`() {
        val record = aTicketRecord()

        val identity = TicketAuthentication.from(record).toTicketIdentity()

        assertEquals(record.sub, identity!!.first)
        assertEquals(record.exp.epochSecond, identity.second!!.epochSecond)
    }
}
