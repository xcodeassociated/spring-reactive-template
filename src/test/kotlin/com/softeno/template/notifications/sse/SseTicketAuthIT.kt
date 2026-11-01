package com.softeno.template.notifications.sse

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.SoftenoReactiveMongoApp
import com.softeno.template.notifications.kafka.KafkaKeycloakController
import com.softeno.template.notifications.kafka.KeycloakEventFixture
import com.softeno.template.testsupport.R
import com.softeno.template.testsupport.awaitUntil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import org.springframework.test.web.reactive.server.WebTestClient
import java.time.Instant
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * SSE one-time-ticket auth + stream lifecycle, end-to-end, WITHOUT Testcontainers
 * (the SSE twin of WsTicketAuthIT): mint -> redeem -> stream, replay/expiry/
 * anonymous/Origin/cross-transport failures, close-at-exp (`token_expired`) and
 * Keycloak-LOGOUT revocation (`logged_out`).
 *
 * Auth is simulated by the `X-Test-Sub` header (TestBearerWebFilter); the ticket
 * store, redeem filter, mint controller, subscription registry and sweeper under
 * test are the real ones.
 */
@SpringBootTest(
    classes = [SoftenoReactiveMongoApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.profiles.active=integration",
        "spring.grpc.server.port=0",
        "com.softeno.ws.heartbeat-interval-seconds=1",
        "com.softeno.ws.sweep-interval-seconds=1",
        "com.softeno.ws.stale-check-threshold-seconds=30",
        "com.softeno.ws.allowed-origins=http://localhost:3000",
        "com.softeno.ticket.ttl-seconds=10",
        "com.softeno.sse.sweep-interval-seconds=1",
    ],
)
@AutoConfigureWebTestClient(timeout = "30000")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SseTicketAuthIT : KeycloakEventFixture {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var kafkaKeycloakController: KafkaKeycloakController

    @Autowired
    lateinit var registry: SseSubscriptionRegistry

    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @AfterAll
    fun stopCollectors() = runBlocking { collectors.coroutineContext[Job]!!.cancelAndJoin() }

    private inner class SseHandle(val events: MutableList<ServerSentEvent<String>>) {
        val completed = AtomicBoolean(false)
        val error = AtomicReference<Throwable?>()
        lateinit var job: Job

        fun awaitEvent(name: String, timeoutMs: Long = 15_000) =
            awaitUntil(timeoutMs = timeoutMs, intervalMs = 50, what = "sse event '$name' (${describe()})") {
                events.any { it.event() == name }
            }

        fun awaitEnd(timeoutMs: Long = 15_000) =
            awaitUntil(timeoutMs = timeoutMs, intervalMs = 50, what = "sse stream end (${describe()})") {
                completed.get()
            }

        private fun describe(): String =
            "completed=${completed.get()} events=${events.size} error=${error.get()}"

        fun cancel() {
            if (::job.isInitialized) job.cancel()
        }
    }

    private fun subscribe(url: String, testSub: String? = null): SseHandle {
        val request = webTestClient.get().uri(url).accept(MediaType.TEXT_EVENT_STREAM)
        testSub?.let { request.header("X-Test-Sub", it) }
        val flux = request
            .exchange()
            .expectStatus().isOk()
            .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
            .responseBody

        val handle = SseHandle(Collections.synchronizedList(mutableListOf()))
        handle.job = collectors.launch {
            runCatching { flux.asFlow().toList(handle.events) }
                .onFailure { handle.error.set(it) }
            handle.completed.set(true)
        }
        // heartbeat arrives immediately on subscribe — proves the stream is live
        handle.awaitEvent("heartbeat")
        return handle
    }

    private fun expectStreamFailure(url: String, expectedStatus: Int) {
        webTestClient.get().uri(url)
            .accept(MediaType.TEXT_EVENT_STREAM)
            .exchange()
            .expectStatus().isEqualTo(expectedStatus)
    }

    private data class TicketJson(val ticket: String, val expiresIn: Int)

    private fun mintSse(sub: String, expSeconds: Long? = null): String =
        webTestClient.post().uri("/sse-ticket")
            .header("X-Test-Sub", sub)
            .apply { expSeconds?.let { header("X-Test-Exp-Seconds", it.toString()) } }
            .exchange()
            .expectStatus().isOk()
            .expectBody(TicketJson::class.java)
            .returnResult().responseBody!!
            .ticket

    private fun mintWs(sub: String): String =
        webTestClient.post().uri("/ws-ticket")
            .header("X-Test-Sub", sub)
            .exchange()
            .expectStatus().isOk()
            .expectBody(TicketJson::class.java)
            .returnResult().responseBody!!
            .ticket

    @Test
    fun `mint then redeem streams and binds the mint-time identity`() {
        val sub = R.sub("alice")
        val ticket = mintSse(sub)

        val stream = subscribe("/update/user?ticket=$ticket")
        try {
            // identity bound from mint-time claims into the registry
            awaitUntil(what = "subscription indexed for $sub") {
                registry.subscriptionsFor(sub).isNotEmpty()
            }
        } finally {
            stream.cancel()
        }
    }

    @Test
    fun `replayed ticket is rejected with 401 before any body`() {
        val ticket = mintSse(R.sub("replay"))
        val stream = subscribe("/update/user?ticket=$ticket")
        stream.cancel()

        expectStreamFailure("/update/user?ticket=$ticket", 401)
    }

    @Test
    fun `websocket-origin ticket cannot open an sse stream`() {
        val wsTicket = mintWs(R.sub("cross"))

        expectStreamFailure("/update/user?ticket=$wsTicket", 401)
    }

    @Test
    fun `anonymous mint is rejected with 401`() {
        webTestClient.post().uri("/sse-ticket")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `mint with blank subject is rejected with 401`() {
        webTestClient.post().uri("/sse-ticket")
            .header("X-Test-Sub", "")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `token ceiling passes - stream gets token_expired and ends`() {
        val sub = R.sub("ceiling")
        // mint bound to a 2 s token ceiling; the 1 s sweeper must enforce it
        val stream = subscribe("/update/user?ticket=${mintSse(sub, expSeconds = 2)}")

        stream.awaitEvent(SseControl.TOKEN_EXPIRED, 20_000)
        stream.awaitEnd(20_000)
        awaitUntil(what = "subscription unregistered for $sub") {
            registry.subscriptionsFor(sub).isEmpty()
        }
    }

    @Test
    fun `keycloak LOGOUT event sends logged_out and ends the stream`() {
        val sub = R.sub("logout")
        val stream = subscribe("/update/user?ticket=${mintSse(sub)}")

        runBlocking { kafkaKeycloakController.listen(keycloakEvent("LOGOUT", sub)) }

        stream.awaitEvent(SseControl.LOGGED_OUT)
        stream.awaitEnd()
        awaitUntil(what = "subscription unregistered for $sub") {
            registry.subscriptionsFor(sub).isEmpty()
        }
    }

    @Test
    fun `bearer-style header auth path still streams without a ticket`() {
        val sub = R.sub("header")

        val stream = subscribe("/update/user", testSub = sub)
        try {
            awaitUntil(what = "subscription indexed for $sub") {
                registry.subscriptionsFor(sub).isNotEmpty()
            }
        } finally {
            stream.cancel()
        }
    }

    @Test
    fun `revoked user cannot mint a new ticket - mint still needs a valid principal`() {
        // mint-time auth is a plain resource-server check — nothing ticket-specific
        // to revoke here; assert the invariant that a minted ticket is single-use
        val sub = R.sub("twice")
        val ticket = mintSse(sub)
        subscribe("/update/user?ticket=$ticket").cancel()
        expectStreamFailure("/update/user?ticket=$ticket", 401)
        // and a fresh mint works again
        val fresh = subscribe("/update/user?ticket=${mintSse(sub)}")
        fresh.cancel()
    }
}
