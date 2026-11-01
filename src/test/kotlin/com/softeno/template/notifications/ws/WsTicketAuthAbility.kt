package com.softeno.template.notifications.ws

import com.softeno.template.notifications.Message

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.testsupport.awaitUntil
import org.springframework.http.HttpHeaders
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Browser-equivalent WebSocket test client for the ticket-auth flow: mint,
 * connect, await frames/close, pre-upgrade rejection assertions. Implemented by
 * the tests driving the handshake; needs only the local server [port] and a
 * [webTestClient].
 */
interface WsTicketAuthAbility {

    val port: Int
    val webTestClient: WebTestClient

    /** POST /ws-ticket with the simulated bearer sub; returns the one-time ticket. */
    fun mint(sub: String, expSeconds: Long? = null): String =
        webTestClient.post().uri("/ws-ticket")
            .header("X-Test-Sub", sub)
            .apply { expSeconds?.let { header("X-Test-Exp-Seconds", it.toString()) } }
            .exchange()
            .expectStatus().isOk()
            .expectBody(TicketJson::class.java)
            .returnResult().responseBody!!
            .ticket

    /** Connects to /ws with the one-time ticket; `null` connects without one. */
    fun openSocket(ticket: String?, origin: String = WS_ALLOWED_ORIGIN): WsSocketHandle =
        openSocketUrl("ws://localhost:$port/ws" + (ticket?.let { "?ticket=$it" } ?: ""), origin)

    fun openSocketUrl(url: String, origin: String = WS_ALLOWED_ORIGIN): WsSocketHandle {
        val handle = WsSocketHandle()
        val headers = HttpHeaders().apply { set("Origin", origin) }
        wsClient.execute(URI(url), headers, WebSocketHandler { session ->
            handle.sessionRef.set(session)
            handle.connected.countDown()
            session.receive()
                .doOnNext { frame -> handle.frames.put(wsJson.readTree(frame.payloadAsText)) }
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
    fun expectHandshakeFailure(ticket: String, expectedStatus: Int, origin: String = WS_ALLOWED_ORIGIN) {
        webTestClient.get().uri("http://localhost:$port/ws?ticket=$ticket")
            .header("Connection", "Upgrade")
            .header("Upgrade", "websocket")
            .header("Sec-WebSocket-Version", "13")
            .header("Sec-WebSocket-Key", Base64.getEncoder().encodeToString(ByteArray(16)))
            .header("Origin", origin)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
    }
}

/** The connected client side: frame queue + close code, with await helpers. */
class WsSocketHandle {
    val sessionRef = AtomicReference<WebSocketSession>()
    val frames = LinkedBlockingQueue<JsonNode>()
    val closedCode = CompletableFuture<Int>()
    val connected = CountDownLatch(1)
    val error = AtomicReference<Throwable>()

    fun awaitFrame(timeoutMs: Long = 15_000): JsonNode =
        frames.poll(timeoutMs, TimeUnit.MILLISECONDS)
            ?: throw AssertionError("no frame received within ${timeoutMs}ms")

    fun awaitContent(content: String, timeoutMs: Long = 15_000): JsonNode {
        var matched: JsonNode? = null
        awaitUntil(timeoutMs, 500, "frame with content '$content'") {
            val frame = frames.poll(500, TimeUnit.MILLISECONDS)
            if (frame != null && frame.path("content").asText() == content) {
                matched = frame
                true
            } else {
                false
            }
        }
        return matched!!
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

data class TicketJson(val ticket: String, val expiresIn: Int)

private val wsJson = com.fasterxml.jackson.databind.ObjectMapper()
private val wsClient = ReactorNettyWebSocketClient()
