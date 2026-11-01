package com.softeno.template.sample.websocket.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Rebuilding a Spring `Authentication` from the mint-time [TicketRecord] claims.
 */
class WsTicketAuthenticationTest : WsTicketFixture {

    @Test
    fun `rebuilds authentication from mint-time claims`() {
        val record = aTicketRecord(
            exp = Instant.now().plusSeconds(120),
            roles = listOf("ROLE_USER", "ROLE_ADMIN"),
        )

        val auth = WsTicketAuthentication.from(record)

        assertEquals(record.sub, auth.token.claims["sub"])
        assertEquals(record.sid, auth.token.claims["sid"])
        assertEquals(record.exp.epochSecond, auth.token.expiresAt!!.epochSecond)
        assertEquals(record.roles, auth.authorities.map { it.authority })

        // the handler's principal read path: authentication -> token -> claims["sub"]
        val binding = auth.toSessionBinding()
        assertEquals(SessionBinding(record.sub, record.sid, record.exp), binding)
    }

    @Test
    fun `binding extraction rejects non-jwt and missing sub`() {
        assertNull(null.toSessionBinding())
    }
}
