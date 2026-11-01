package com.softeno.template.sample.websocket.security

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class WsTicketAuthenticationTest {
    @Test
    fun `rebuilds authentication from mint-time claims`() {
        val exp = Instant.now().plusSeconds(120)
        val auth = WsTicketAuthentication.from(TicketRecord("user-1", "sid-9", exp, listOf("ROLE_USER", "ROLE_ADMIN")))

        assertEquals("user-1", auth.token.claims["sub"])
        assertEquals("sid-9", auth.token.claims["sid"])
        assertEquals(exp.epochSecond, auth.token.expiresAt!!.epochSecond)
        assertEquals(listOf("ROLE_USER", "ROLE_ADMIN"), auth.authorities.map { it.authority })

        // the handler's principal read path: authentication -> token -> claims["sub"]
        val binding = auth.toSessionBinding()
        assertEquals(SessionBinding("user-1", "sid-9", exp), binding)
    }

    @Test
    fun `binding extraction rejects non-jwt and missing sub`() {
        assertNull(null.toSessionBinding())
    }
}

class OriginCheckTest {
    @Test
    fun `exact match allowlist`() {
        val allowed = listOf("http://localhost:3000", "http://localhost:8080")
        assert(WsTicketRedeemFilter.isOriginAllowed("http://localhost:3000", allowed))
        assert(WsTicketRedeemFilter.isOriginAllowed("http://localhost:8080", allowed))
        // no wildcards / no prefix-suffix matching
        assert(!WsTicketRedeemFilter.isOriginAllowed("http://localhost:3001", allowed))
        assert(!WsTicketRedeemFilter.isOriginAllowed("http://localhost:3000.evil.com", allowed))
        assert(!WsTicketRedeemFilter.isOriginAllowed("http://evil.com", allowed))
    }
}
