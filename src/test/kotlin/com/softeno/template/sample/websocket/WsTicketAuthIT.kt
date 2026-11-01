package com.softeno.template.sample.websocket

import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.app.kafka.KafkaKeycloakController
import com.softeno.template.app.kafka.KeycloakEventFixture
import com.softeno.template.testsupport.R
import com.softeno.template.testsupport.awaitUntil
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.WebSocketSession

/**
 * Browser-usable WebSocket auth, end-to-end, WITHOUT Testcontainers:
 * mint -> redeem -> handshake frames, replay/expiry/anonymous/Origin failures,
 * logout (4403) and token-ceiling (4401) lifecycle closes.
 *
 * Auth is simulated by the `X-Test-Sub` header (TestBearerWebFilter) because the
 * `integration` profile security is permit-all; the ticket redeem filter, mint
 * controller, session registry and sweeper under test are the real ones.
 * The client harness (mint / socket / failure assertions) lives in
 * [WsTicketAuthAbility]; keycloak events come from [KeycloakEventFixture].
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
        "com.softeno.ws.allowed-origins=$WS_ALLOWED_ORIGIN",
    ],
)
@AutoConfigureWebTestClient(timeout = "30000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WsTicketAuthIT : WsTicketAuthAbility, KeycloakEventFixture {

    @LocalServerPort
    override var port: Int = 0

    @Autowired
    override lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var kafkaKeycloakController: KafkaKeycloakController

    @Autowired
    lateinit var messageService: CoroutineMessageService

    @Test
    fun `mint then redeem completes the handshake and the server stamps from`() {
        val sub = R.sub("alice")
        val ticket = mint(sub)
        val socket = openSocket(ticket)
        try {
            val handshake = socket.awaitFrame()
            assertEquals("SYSTEM", handshake.path("from").asText())
            assertEquals("HANDSHAKE", handshake.path("content").asText())

            val userIdFrame = socket.awaitFrame()
            assertEquals(sub, userIdFrame.path("content").asText())

            // client-claimed `from` must be ignored; ALL echo includes the sender
            val text = R.data()
            socket.send("""{"from":"attacker-claims","to":"ALL","content":"$text"}""")
            val echoed = socket.awaitContent(text)
            assertEquals(sub, echoed.path("from").asText())
        } finally {
            socket.closeGracefully()
        }
    }

    @Test
    fun `replayed ticket is rejected with 401 pre-upgrade`() {
        val ticket = mint(R.sub("replay"))
        val socket = openSocket(ticket)
        socket.closeGracefully()
        socket.awaitClose()

        expectHandshakeFailure(ticket, 401)
    }

    @Test
    fun `expired ticket is rejected with 401 pre-upgrade`() {
        val ticket = mint(R.sub("expired"), expSeconds = -1)
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
        val ticket = mint(R.sub("dave"))
        expectHandshakeFailure(ticket, 403, origin = "http://evil.example")
        // ticket was not consumed by the rejected handshake attempt? (Origin is
        // checked first) — a valid ticket must still redeem afterwards
        val socket = openSocket(ticket)
        socket.awaitFrame()
        socket.closeGracefully()
    }

    @Test
    fun `keycloak LOGOUT event closes the session with LOGGED_OUT and 4403`() {
        val sub = R.sub("logout")
        val socket = openSocket(mint(sub))
        socket.awaitContent("HANDSHAKE")

        runBlocking { kafkaKeycloakController.listen(keycloakEvent("LOGOUT", sub)) }

        socket.awaitContent(WsControl.LOGGED_OUT)
        assertEquals(4403, socket.awaitClose())
    }

    @Test
    fun `token expiry closes the session with REAUTH_REQUIRED and 4401`() {
        val sub = R.sub("ceiling")
        // mint with a 3 s token ceiling; the 1 s sweeper must enforce it
        val socket = openSocket(mint(sub, expSeconds = 3))
        socket.awaitContent("HANDSHAKE")

        socket.awaitContent(WsControl.REAUTH_REQUIRED, 20_000)
        assertEquals(4401, socket.awaitClose(20_000))
    }

    @Test
    fun `heartbeat ping arrives and pong is accepted`() {
        val socket = openSocket(mint(R.sub("ping")))
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
        val socket = openSocket(null)

        assertEquals(4401, socket.awaitClose())
        assert(socket.frames.isEmpty()) { "no frames may be sent to an unauthenticated socket" }
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
        val sub = R.sub("slash")
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
        val sub = R.sub("violation")
        val ticket = mint(sub)
        java.net.Socket("localhost", port).use { sock ->
            sock.soTimeout = 10_000
            val out = sock.getOutputStream()
            val key = java.util.Base64.getEncoder().encodeToString(ByteArray(16) { (it * 7 + 1).toByte() })
            val req = "GET /ws?ticket=$ticket HTTP/1.1\r\n" +
                "Host: localhost:$port\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                "Sec-WebSocket-Version: 13\r\nSec-WebSocket-Key: $key\r\nOrigin: $WS_ALLOWED_ORIGIN\r\n\r\n"
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
        awaitUntil(timeoutMs = 5_000, what = "session unregistration of $sub") {
            messageService.sessionsFor(sub).isEmpty()
        }
        assertEquals(emptySet<WebSocketSession>(), messageService.sessionsFor(sub),
            "transport error must unregister the session")
    }
}
