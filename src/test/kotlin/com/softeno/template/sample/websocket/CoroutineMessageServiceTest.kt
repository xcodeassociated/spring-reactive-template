package com.softeno.template.sample.websocket

import com.softeno.template.sample.websocket.security.SessionBinding
import com.softeno.template.testsupport.awaitUntil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Collections

class CoroutineMessageServiceTest {
    private fun service() = CoroutineMessageService(aChatConfig(), mockk<ObjectMapper>(relaxed = true))

    private fun session(id: String): WebSocketSession = mockk(relaxed = true) {
        every { this@mockk.id } returns id
    }

    private fun binding(sub: String, exp: Instant = Instant.now().plusSeconds(300)) =
        SessionBinding(sub, "sid-$sub", exp)

    private fun collect(flow: Flow<Message>, sink: MutableList<Message>): Job =
        CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            flow.collect { synchronized(sink) { sink.add(it) } }
        }

    private fun monoEmpty(): Mono<Void> = Mono.empty()

    @Test
    fun `register and unregister maintain the reverse index without leaks`() {
        val service = service()
        val s1 = session("s1")
        val s2 = session("s2")

        service.registerSession(s1, binding("u1"))
        service.registerSession(s2, binding("u1"))
        assertEquals(setOf(s1, s2), service.sessionsFor("u1"))

        service.unregisterSession(s1)
        assertEquals(setOf(s2), service.sessionsFor("u1"))

        // leak-free: empty index entries are removed entirely
        service.unregisterSession(s2)
        assertEquals(emptySet<WebSocketSession>(), service.sessionsFor("u1"))
        assertNull(service.bindingOf(s2))

        // idempotent unregister
        service.unregisterSession(s2)
    }

    @Test
    fun `routeMessage stamps from with the sending session bound sub`() {
        val service = service()
        val a = session("sa")
        val b = session("sb")
        service.registerSession(a, binding("user-a"))
        service.registerSession(b, binding("user-b"))

        val received = Collections.synchronizedList(mutableListOf<Message>())
        val job = collect(service.getMessageFlow(b), received)

        service.routeMessage(a, Message(from = "attacker-claims", to = "ALL", content = "hi"))

        awaitUntil(what = "relayed message") { received.isNotEmpty() }
        val relayed = received.first()
        assertEquals("hi", relayed.content)
        // server-stamped identity, NOT the client claim
        assertEquals("user-a", relayed.from)
        job.cancel()
    }

    @Test
    fun `routeMessage from an unregistered session is dropped`() {
        val service = service()
        // must not throw
        service.routeMessage(session("ghost"), Message(to = "ALL", content = "x"))
    }

    @Test
    fun `revokeUser sends LOGGED_OUT control and closes with 4403`() {
        val service = service()
        val s1 = session("s1")
        val s2 = session("s2")
        val other = session("s3")
        service.registerSession(s1, binding("u1"))
        service.registerSession(s2, binding("u1"))
        service.registerSession(other, binding("u2"))

        val closedStatus = slot<CloseStatus>()
        every { s1.close(capture(closedStatus)) } returns monoEmpty()
        every { s2.close(capture(closedStatus)) } returns monoEmpty()

        val frames = Collections.synchronizedList(mutableListOf<Message>())
        val jobs = listOf(
            collect(service.getMessageFlow(s1), frames),
            collect(service.getMessageFlow(s2), frames),
        )

        val closed = service.revokeUser("u1")
        assertEquals(2, closed)

        awaitUntil(what = "LOGGED_OUT control frame") { frames.any { it.content == WsControl.LOGGED_OUT } }
        assertTrue(frames.filter { it.content == WsControl.LOGGED_OUT }.all { it.from == "SYSTEM" })

        // after the close grace the sessions are unregistered from both maps
        awaitUntil(what = "session unregistration") { service.sessionsFor("u1").isEmpty() }
        assertTrue(closedStatus.isCaptured)
        assertEquals(4403, closedStatus.captured.code)
        verify(exactly = 0) { other.close(any()) }
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `sweep closes token-expired sessions with REAUTH_REQUIRED and 4401`() {
        val service = service()
        val s = session("s1")
        val statusSlot = slot<CloseStatus>()
        every { s.close(capture(statusSlot)) } returns monoEmpty()

        service.registerSession(s, binding("u1", exp = Instant.now().minusSeconds(1)))

        val frames = Collections.synchronizedList(mutableListOf<Message>())
        val job = collect(service.getMessageFlow(s), frames)

        service.sweep()

        awaitUntil(what = "REAUTH_REQUIRED control frame") { frames.any { it.content == WsControl.REAUTH_REQUIRED } }
        awaitUntil(what = "session close") { statusSlot.isCaptured }
        assertEquals(4401, statusSlot.captured.code)
        awaitUntil(what = "session unregistration") { service.sessionsFor("u1").isEmpty() }
        job.cancel()
    }

    @Test
    fun `sweep keeps live sessions open`() {
        val service = service()
        val s = session("s1")
        service.registerSession(s, binding("u1", exp = Instant.now().plusSeconds(3600)))
        service.updateLastPong(s)

        service.sweep()

        assertSame(s, service.getSession("s1"))
        assertEquals(setOf(s), service.sessionsFor("u1"))
    }

    @Test
    fun `pong racing in after unregister does not resurrect the liveness entry`() {
        val service = service()
        val s = session("s1")
        service.registerSession(s, binding("u1"))
        service.unregisterSession(s)

        service.updateLastPong(s) // late pong in flight while the session was removed

        // a resurrected lastPongTimes entry would never be swept again (binding gone) -> leak
        assertEquals(0, service.lastPongEntryCount())
    }

    @Test
    fun `concurrent register and unregister keep the reverse index consistent`() {
        val service = service()
        var previous: WebSocketSession? = null

        // reconnect churn: a new session registers while the old one unregisters.
        // The index update must be atomic (compute), otherwise the new session can
        // be added to a detached set and go missing for logout revocation.
        repeat(60) { i ->
            val current = session("s$i")
            val old = previous
            val t1 = Thread { service.registerSession(current, binding("u1")) }
            val t2 = Thread { old?.let { service.unregisterSession(it) } }
            t1.start(); t2.start(); t1.join(); t2.join()
            assertEquals(setOf(current), service.sessionsFor("u1"), "iteration $i")
            previous = current
        }
    }

    @Test
    fun `shutdown closes live sessions with 1001 instead of dropping them`() {
        val service = service()
        val s = session("s1")
        val statusSlot = slot<CloseStatus>()
        every { s.close(capture(statusSlot)) } returns monoEmpty()
        service.registerSession(s, binding("u1"))

        service.shutdown()

        assertTrue(statusSlot.isCaptured)
        assertEquals(1001, statusSlot.captured.code)
    }
}
