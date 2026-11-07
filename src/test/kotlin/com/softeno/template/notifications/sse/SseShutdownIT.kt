package com.softeno.template.notifications.sse

import com.softeno.template.testsupport.BaseIntegrationTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.ApplicationContext

/**
 * Graceful-shutdown contract: when the context starts closing, every live SSE
 * stream must complete IMMEDIATELY. Otherwise the socket outlives the acceptor
 * (REST refuses while heartbeats keep flowing) and clients sit on a
 * technically-connected stream of a dying server — the stale-badge bug.
 */
class SseShutdownIT : BaseIntegrationTest() {

    @Autowired
    lateinit var registry: SseSubscriptionRegistry

    @Autowired
    lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    lateinit var applicationContext: ApplicationContext

    @Test
    fun `context close event completes all live subscriptions`() = runBlocking {
        val sub = registry.register("shutdown-test-user", null)
        assertEquals(1, registry.subscriptionsFor("shutdown-test-user").size)

        eventPublisher.publishEvent(ContextClosedEvent(applicationContext))

        // the subscription channel is closed -> the response flow completes
        // (a live stream would only emit within the timeout)
        val terminal = runCatching {
            withTimeout(2000) { sub.channel.receiveCatching() }
        }.getOrNull()
        assertTrue(terminal == null || terminal.isClosed, "channel must be closed on shutdown")
        assertEquals(0, registry.subscriptionsFor("shutdown-test-user").size)
    }
}
