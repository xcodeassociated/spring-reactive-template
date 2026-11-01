package com.softeno.template.sample.websocket.security

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exact-match Origin allowlist semantics for the /ws handshake (no wildcards,
 * no prefix/suffix matching — CSRF-style origin confusion must not pass).
 */
class OriginCheckTest {
    @Test
    fun `exact match allowlist`() {
        val allowed = listOf("http://localhost:3000", "http://localhost:8080")
        assertTrue(WsTicketRedeemFilter.isOriginAllowed("http://localhost:3000", allowed))
        assertTrue(WsTicketRedeemFilter.isOriginAllowed("http://localhost:8080", allowed))
        // no wildcards / no prefix-suffix matching
        assertFalse(WsTicketRedeemFilter.isOriginAllowed("http://localhost:3001", allowed))
        assertFalse(WsTicketRedeemFilter.isOriginAllowed("http://localhost:3000.evil.com", allowed))
        assertFalse(WsTicketRedeemFilter.isOriginAllowed("http://evil.com", allowed))
    }
}
