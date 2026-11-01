package com.softeno.template.app.ticket

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Exact-match Origin allowlist semantics shared by the WS upgrade and the SSE
 * stream ticket redemption (no wildcards, no prefix/suffix matching —
 * CSRF-style origin confusion must not pass).
 */
class OriginCheckTest {
    @Test
    fun `exact match allowlist`() {
        val allowed = listOf("http://localhost:3000", "http://localhost:8080")
        assertTrue(TicketSupport.isOriginAllowed("http://localhost:3000", allowed))
        assertTrue(TicketSupport.isOriginAllowed("http://localhost:8080", allowed))
        // no wildcards / no prefix-suffix matching
        assertFalse(TicketSupport.isOriginAllowed("http://localhost:3001", allowed))
        assertFalse(TicketSupport.isOriginAllowed("http://localhost:3000.evil.com", allowed))
        assertFalse(TicketSupport.isOriginAllowed("http://evil.com", allowed))
    }
}
