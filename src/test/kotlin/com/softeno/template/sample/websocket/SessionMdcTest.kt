package com.softeno.template.sample.websocket

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.slf4j.MDC

/**
 * Locks in the MDC semantics the WebSocket session code relies on:
 * MDCContext(map) REPLACES the MDC, so session bindings must be merged on top
 * of whatever the incoming context carries (OTel traceId/spanId etc.).
 */
class SessionMdcTest {

    @AfterEach
    fun clear() = MDC.clear()

    @Test
    fun `naive MDCContext with a bare map wipes the trace fields (the pitfall)`() = runBlocking {
        MDC.put("traceId", "trace-1")
        withContext(MDCContext(mapOf("sessionId" to "s1"))) {
            assertNull(MDC.get("traceId"), "bare map REPLACES MDC — documents why merging is required")
            assertEquals("s1", MDC.get("sessionId"))
        }
        // and restoring on exit
        assertEquals("trace-1", MDC.get("traceId"))
    }

    @Test
    fun `merged session MDC keeps trace fields and survives dispatcher hops`() = runBlocking {
        MDC.put("traceId", "trace-1")
        MDC.put("spanId", "span-1")

        val merged = (MDC.getCopyOfContextMap() ?: emptyMap()) +
            mapOf("sessionId" to "s1", "userId" to "user-1")

        withContext(MDCContext(merged)) {
            assertEquals("trace-1", MDC.get("traceId"))
            assertEquals("span-1", MDC.get("spanId"))

            withContext(Dispatchers.Default) {  // real thread hop
                assertEquals("trace-1", MDC.get("traceId"), "trace correlation must survive the hop")
                assertEquals("span-1", MDC.get("spanId"))
                assertEquals("s1", MDC.get("sessionId"))
                assertEquals("user-1", MDC.get("userId"))
            }
        }

        // back to the caller's MDC — no session leftovers
        assertEquals("trace-1", MDC.get("traceId"))
        assertNull(MDC.get("sessionId"))
    }
}
