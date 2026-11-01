package com.softeno.template.notifications.sse

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.http.codec.ServerSentEvent
import java.time.Instant

/**
 * Unit tests for the SSE subscription lifecycle: reverse index, leak-free
 * unregister, revocation and ceiling close semantics (control event +
 * completion), broadcast fan-out.
 */
class SseSubscriptionRegistryTest {

    private val collectors = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
    )

    @org.junit.jupiter.api.AfterEach
    fun stopCollectors() = collectors.cancel()

    private fun registry() = SseSubscriptionRegistry(
        SseSubscriptionProperties(sweepIntervalSeconds = 1),
        tools.jackson.databind.ObjectMapper(),
    )

    private fun collectInto(
        registry: SseSubscriptionRegistry,
        sub: String?,
        exp: Instant?,
        sink: MutableList<ServerSentEvent<String>>,
    ): SseSubscriptionRegistry.Subscription {
        val handle = registry.register(sub, exp)
        // consume the registry channel directly (what the emitter's flow does)
        collectors.launch {
            runCatching { for (event in handle.channel) sink.add(event) }
        }
        return handle
    }

    @Test
    fun `register and unregister maintain the reverse index without leaks`() = runBlocking {
        val registry = registry()
        val a = registry.register("u1", Instant.now().plusSeconds(300))
        val b = registry.register("u1", Instant.now().plusSeconds(300))
        assertEquals(setOf(a.id, b.id), registry.subscriptionsFor("u1"))

        registry.unregister(a)
        assertEquals(setOf(b.id), registry.subscriptionsFor("u1"))

        registry.unregister(b)
        assertEquals(emptySet<String>(), registry.subscriptionsFor("u1"))
        assertEquals(0, registry.size())

        // idempotent unregister
        registry.unregister(b)
    }

    @Test
    fun `revokeUser sends logged_out then completes the stream`() = runBlocking {
        val registry = registry()
        val received = mutableListOf<ServerSentEvent<String>>()
        val handle = collectInto(registry, "u1", Instant.now().plusSeconds(300), received)

        assertEquals(1, registry.revokeUser("u1"))

        kotlinx.coroutines.withTimeout(5_000) {
            while (received.isEmpty() || !handle.closed.get()) kotlinx.coroutines.delay(20)
        }
        assertEquals(SseControl.LOGGED_OUT, received.first().event())
        // stream completed: the consumer loop ended
        kotlinx.coroutines.withTimeout(5_000) {
            while (registry.subscriptionsFor("u1").isNotEmpty()) kotlinx.coroutines.delay(20)
        }
        assertEquals(0, registry.size())
    }

    @Test
    fun `sweep closes past-ceiling streams with token_expired and leaves live ones`() = runBlocking {
        val registry = registry()
        val expiredEvents = mutableListOf<ServerSentEvent<String>>()
        val liveEvents = mutableListOf<ServerSentEvent<String>>()
        val expired = collectInto(registry, "u-expired", Instant.now().minusSeconds(1), expiredEvents)
        val live = collectInto(registry, "u-live", Instant.now().plusSeconds(3600), liveEvents)

        registry.sweep()

        kotlinx.coroutines.withTimeout(5_000) {
            while (expiredEvents.isEmpty() || !expired.closed.get()) kotlinx.coroutines.delay(20)
        }
        assertEquals(SseControl.TOKEN_EXPIRED, expiredEvents.first().event())
        assertTrue(liveEvents.isEmpty())
        assertEquals(setOf(live.id), registry.subscriptionsFor("u-live"))

        registry.unregister(live)
    }

    @Test
    fun `anonymous subscriptions have no ceiling and survive sweep`() = runBlocking {
        val registry = registry()
        val anonymous = registry.register(null, null)
        try {
            registry.sweep()
            assertTrue(registry.subscriptionsFor("u-none").isEmpty()) // not indexed by user
            assertEquals(1, registry.size())
            kotlinx.coroutines.delay(1_200) // one sweeper tick
            assertEquals(1, registry.size())
        } finally {
            registry.unregister(anonymous)
        }
    }

    @Test
    fun `broadcast fans out to every live subscription`() = runBlocking {
        val registry = registry()
        val a = mutableListOf<ServerSentEvent<String>>()
        val b = mutableListOf<ServerSentEvent<String>>()
        val ha = collectInto(registry, "u-a", Instant.now().plusSeconds(300), a)
        val hb = collectInto(registry, "u-b", Instant.now().plusSeconds(300), b)

        val event = ServerSentEvent.builder("""{"content":"x"}""").event("update").build()
        assertTrue(registry.broadcast(event))

        kotlinx.coroutines.withTimeout(5_000) {
            while (a.isEmpty() || b.isEmpty()) kotlinx.coroutines.delay(20)
        }
        assertEquals(event, a.first())
        assertEquals(event, b.first())

        registry.unregister(ha)
        registry.unregister(hb)
    }
}
