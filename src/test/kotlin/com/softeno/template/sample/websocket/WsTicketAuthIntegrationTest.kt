package com.softeno.template.sample.websocket

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.app.kafka.KafkaKeycloakController
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpHeaders
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

/**
 * Browser-usable WebSocket auth, end-to-end, WITHOUT Testcontainers:
 * mint -> redeem -> handshake frames, replay/expiry/anonymous/Origin failures,
 * logout (4403) and token-ceiling (4401) lifecycle closes.
 *
 * Auth is simulated by the `X-Test-Sub` header (TestBearerWebFilter) because the
 * `integration` profile security is permit-all; the ticket redeem filter, mint
 * controller, session registry and sweeper under test are the real ones.
 */
@SpringBootTest(
    classes = [SoftenoReactiveMongoApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.profiles.active=integration",
        "spring.grpc.server.port=0",
        "com.softeno.ws.heartbeat-interval-seconds=2",
        "com.softeno.ws.sweep-interval-seconds=1",
        "com.softeno.ws.stale-check-threshold-seconds=30",
        "com.softeno.ws.ticket-ttl-seconds=10",
        "com.softeno.ws.allowed-origins=http://localhost:3000",
    ],
)
@AutoConfigureWebTestClient(timeout = "30000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WsTicketAuthIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var kafkaKeycloakController: KafkaKeycloakController

    @Autowired
    lateinit var messageService: CoroutineMessageService

    private val json = com.fasterxml.jackson.databind.ObjectMapper()
    private val wsClient = ReactorNettyWebSocketClient()

    private val allowedOrigin = "http://localhost:3000"

    inner class SocketHandle {
        val sessionRef = AtomicReference<WebSocketSession>()
        val frames = LinkedBlockingQueue<JsonNode>()
        val closedCode = CompletableFuture<Int>()
        val connected = CountDownLatch(1)
        val error = AtomicReference<Throwable>()

        fun awaitFrame(timeoutMs: Long = 15_000): JsonNode =
            frames.poll(timeoutMs, TimeUnit.MILLISECONDS)
                ?: throw AssertionError("no frame received within ${timeoutMs}ms")

        fun awaitContent(content: String, timeoutMs: Long = 15_000): JsonNode {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                val frame = frames.poll(500, TimeUnit.MILLISECONDS) ?: continue
                if (frame.path("content").asText() == content) return frame
            }
            throw AssertionError("frame with content '$content' not received within ${timeoutMs}ms")
        }

        fun awaitClose(timeoutMs: Long = 15_000): Int =
            closedCode.get(timeoutMs, TimeUnit.MILLISECONDS)

        fun send(text: String) {
            val session = sessionRef.get() ?: throw IllegalStateException("not connected")
            session.send(Mono.just(session.textMessage(text))).block(Duration.ofSeconds(10))
        }

        fun closeGracefully() {
            sessionRef.get()?.close()?.block(Duration.ofSeconds(10))
        }
    }

    private fun openSocket(ticket: String, origin: String? = allowedOrigin): SocketHandle =
        openSocketUrl("ws://localhost:$port/ws?ticket=$ticket", origin)

    private fun openSocketUrl(url: String, origin: String? = allowedOrigin): SocketHandle {
        val handle = SocketHandle()
        val headers = HttpHeaders()
        origin?.let { headers.set("Origin", it) }
        wsClient.execute(URI(url), headers, WebSocketHandler { session ->
            handle.sessionRef.set(session)
            handle.connected.countDown()
            session.receive()
                .doOnNext { frame -> handle.frames.put(json.readTree(frame.payloadAsText)) }
                .then()
                .doFinally { session.closeStatus().subscribe({ sc -> handle.closedCode.complete(sc.code) }) }
        }).subscribe({ }, { err ->
            handle.error.set(err)
            handle.connected.countDown()
        })
        if (!handle.connected.await(15, TimeUnit.SECONDS)) throw AssertionError("handshake did not complete")
        handle.error.get()?.let { throw AssertionError("handshake failed: $it") }
        return handle
    }

    /**
     * The rejection must happen BEFORE the upgrade completes, so it is asserted
     * with an upgrade-style plain HTTP request (what a browser sends; Spring 7's
     * reactor-netty client surfaces non-101 responses without a typed exception).
     */
    private fun expectHandshakeFailure(ticket: String, expectedStatus: Int, origin: String? = allowedOrigin) {
        val request = webTestClient.get().uri("http://localhost:$port/ws?ticket=$ticket")
            .header("Connection", "Upgrade")
            .header("Upgrade", "websocket")
            .header("Sec-WebSocket-Version", "13")
            .header("Sec-WebSocket-Key", java.util.Base64.getEncoder().encodeToString(ByteArray(16)))
        (if (origin != null) request.header("Origin", origin) else request)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
    }

    private data class TicketJson(val ticket: String, val expiresIn: Int)

    private fun mint(sub: String, expSeconds: Long? = null): String =
        webTestClient.post().uri("/ws-ticket")
            .header("X-Test-Sub", sub)
            .apply { expSeconds?.let { header("X-Test-Exp-Seconds", it.toString()) } }
            .exchange()
            .expectStatus().isOk()
            .expectBody(TicketJson::class.java)
            .returnResult().responseBody!!
            .ticket

    private fun logoutEvent(userId: String): ConsumerRecord<String, JsonNode> {
        val event = """
            {"id":"e1","time":1,"type":"LOGOUT","realmId":"r1","clientId":"frontend","userId":"$userId",
             "sessionId":"s1","ipAddress":"127.0.0.1","error":null,"details":{}}
        """.trimIndent()
        return ConsumerRecord("keycloak-events", 0, 0, null, json.readTree(event))
    }

    @Test
    fun `mint then redeem completes the handshake and the server stamps from`() {
        val sub = "alice-${UUID.randomUUID()}"
        val ticket = mint(sub)
        val socket = openSocket(ticket)
        try {
            val handshake = socket.awaitFrame()
            assertEquals("SYSTEM", handshake.path("from").asText())
            assertEquals("HANDSHAKE", handshake.path("content").asText())

            val userIdFrame = socket.awaitFrame()
            assertEquals(sub, userIdFrame.path("content").asText())

            // client-claimed `from` must be ignored; ALL echo includes the sender
            val text = "hello-${UUID.randomUUID()}"
            socket.send("""{"from":"attacker-claims","to":"ALL","content":"$text"}""")
            val echoed = socket.awaitContent(text)
            assertEquals(sub, echoed.path("from").asText())
        } finally {
            socket.closeGracefully()
        }
    }

    @Test
    fun `replayed ticket is rejected with 401 pre-upgrade`() {
        val ticket = mint("replay-${UUID.randomUUID()}")
        val socket = openSocket(ticket)
        socket.closeGracefully()
        socket.awaitClose()

        expectHandshakeFailure(ticket, 401)
    }

    @Test
    fun `expired ticket is rejected with 401 pre-upgrade`() {
        val ticket = mint("expired-${UUID.randomUUID()}", expSeconds = -1)
        expectHandshakeFailure(ticket, 401)
    }

    @Test
    fun `anonymous mint is rejected with 401`() {
        webTestClient.post().uri("/ws-ticket")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `foreign origin is rejected with 403 before the handshake`() {
        val ticket = mint("dave-${UUID.randomUUID()}")
        expectHandshakeFailure(ticket, 403, origin = "http://evil.example")
        // ticket was not consumed by the rejected handshake attempt? (Origin is
        // checked first) — a valid ticket must still redeem afterwards
        val socket = openSocket(ticket)
        socket.awaitFrame()
        socket.closeGracefully()
    }

    @Test
    fun `keycloak LOGOUT event closes the session with LOGGED_OUT and 4403`() {
        val sub = "logout-${UUID.randomUUID()}"
        val socket = openSocket(mint(sub))
        socket.awaitContent("HANDSHAKE")

        kotlinx.coroutines.runBlocking { kafkaKeycloakController.listen(logoutEvent(sub)) }

        socket.awaitContent(WsControl.LOGGED_OUT)
        assertEquals(4403, socket.awaitClose())
    }

    @Test
    fun `token expiry closes the session with REAUTH_REQUIRED and 4401`() {
        val sub = "ceiling-${UUID.randomUUID()}"
        // mint with a 3 s token ceiling; the 1 s sweeper must enforce it
        val socket = openSocket(mint(sub, expSeconds = 3))
        socket.awaitContent("HANDSHAKE")

        socket.awaitContent(WsControl.REAUTH_REQUIRED, 20_000)
        assertEquals(4401, socket.awaitClose(20_000))
    }

    @Test
    fun `heartbeat ping arrives and pong is accepted`() {
        val socket = openSocket(mint("ping-${UUID.randomUUID()}"))
        socket.awaitContent("HANDSHAKE")
        socket.awaitContent("ping", 10_000)
        socket.send("""{"to":"SYSTEM","content":"pong"}""")
        // still alive: next ping arrives
        socket.awaitContent("ping", 10_000)
        socket.closeGracefully()
        assertEquals(1000, socket.awaitClose())
    }

    // ---- edge cases ----

    @Test
    fun `mint with blank subject is rejected with 401`() {
        webTestClient.post().uri("/ws-ticket")
            .header("X-Test-Sub", "")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `upgrade without any ticket never serves an unauthenticated session`() {
        // In production the security chain rejects this pre-upgrade (.authenticated());
        // under the permit-all test chain the handler's defensive close must kick in:
        // no handshake frames, close with 4401, never a registered session.
        val handle = SocketHandle()
        val headers = HttpHeaders().apply { set("Origin", allowedOrigin) }
        wsClient.execute(URI("ws://localhost:$port/ws"), headers, WebSocketHandler { session ->
            handle.sessionRef.set(session)
            handle.connected.countDown()
            session.receive()
                .doOnNext { frame -> handle.frames.put(json.readTree(frame.payloadAsText)) }
                .then()
                .doFinally { session.closeStatus().subscribe({ sc -> handle.closedCode.complete(sc.code) }) }
        }).subscribe({ }, { err -> handle.error.set(err); handle.connected.countDown() })
        check(handle.connected.await(15, TimeUnit.SECONDS))
        assertEquals(4401, handle.awaitClose())
        assert(handle.frames.isEmpty()) { "no frames may be sent to an unauthenticated socket" }
    }

    @Test
    fun `chat dev page is served at chat path`() {
        webTestClient.get().uri("http://localhost:$port/chat/chat.html")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String::class.java)
            .consumeWith { assert(it.responseBody!!.contains("ws-ticket chat")) { "chat page content missing" } }
    }

    @Test
    fun `trailing-slash upgrade completes the handshake without wasting the ticket`() {
        val sub = "slash-${UUID.randomUUID()}"
        val socket = openSocketUrl("ws://localhost:$port/ws/?ticket=${mint(sub)}")
        try {
            socket.awaitContent("HANDSHAKE", 5_000)
            assertEquals(sub, socket.awaitContent(sub).path("content").asText())
        } finally {
            socket.closeGracefully()
        }
    }

    @Test
    fun `protocol-violating frame closes the socket and unregisters the session`() {
        // RFC 6455 5.1: client frames MUST be masked; an unmasked frame must fail
        // the connection. Exercises the transport-error path of the session jobs:
        // the receive job dies, the session must be fully cleaned up.
        val sub = "violation-${UUID.randomUUID()}"
        val ticket = mint(sub)
        java.net.Socket("localhost", port).use { sock ->
            sock.soTimeout = 10_000
            val out = sock.getOutputStream()
            val key = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { (it * 7 + 1).toByte() })
            val req = "GET /ws?ticket=$ticket HTTP/1.1\r\n" +
                "Host: localhost:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\nOrigin: $allowedOrigin\r\n\r\n"
            out.write(req.toByteArray())
            out.flush()

            val ins = sock.getInputStream()
            val head = StringBuilder()
            while (!head.endsWith("\r\n\r\n")) {
                val c = ins.read()
                if (c < 0) break
                head.append(c.toChar())
            }
            assert(head.contains("101")) { "expected 101 handshake, got: $head" }

            // unmasked text frame -> server must fail the connection (close 1002)
            out.write(byteArrayOf(0x81.toByte(), 0x02, 'h'.code.toByte(), 'i'.code.toByte()))
            out.flush()

            val tail = ins.readNBytes(256) // returns at close frame + EOF
            if (tail.size >= 4 && tail[0] == 0x88.toByte()) {
                val code = ((tail[2].toInt() and 0xFF) shl 8) or (tail[3].toInt() and 0xFF)
                assertEquals(1002, code, "protocol violation close code")
            } // else: transport dropped the connection without a close frame — still must clean up
        }

        // the session must be gone from the registry + reverse index either way
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && messageService.sessionsFor(sub).isNotEmpty()) {
            Thread.sleep(50)
        }
        assertEquals(emptySet<WebSocketSession>(), messageService.sessionsFor(sub),
            "transport error must unregister the session")
    }
}
