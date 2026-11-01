package com.softeno.template.app.user.notification

import com.softeno.template.app.user.api.UserDto
import com.softeno.template.testsupport.BaseIntegrationTest
import com.softeno.template.testsupport.R
import com.softeno.template.testsupport.awaitUntil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * SSE round-trip: a subscribed client gets an immediate heartbeat, and creating
 * a user (via the Kafka tx/rx loop) arrives as an `update` event. Auth is
 * permit-all under the `integration` profile; the production rule is
 * `.authenticated()` on the /update paths in the main SecurityConfig.
 */
class UserUpdateSseIT : BaseIntegrationTest() {

    @Test
    fun `subscribed client receives heartbeat and user-created broadcast`() = runBlocking {
        // hard bound: the stream is infinite by nature — every wait is bounded, and
        // the collector is joined below, so the test can fail but never hang
        withTimeout(60_000.milliseconds) {
            // given — subscribe before the trigger: the emitter is multicast without replay
            val events = webTestClient.get().uri("/update/user")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .responseBody

            val collected = Collections.synchronizedList(mutableListOf<ServerSentEvent<String>>())
            val collector = launch(Dispatchers.Default) { runCatching { events.asFlow().toList(collected) } }
            try {
                // heartbeat is emitted immediately on subscribe — proves the stream is live
                awaitUntil(what = "heartbeat event") { collected.any { it.event() == "heartbeat" } }

                // when
                val user = webTestClient.post().uri("/coroutine/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"name":"${R.name()}","email":"${R.email()}","role":[]}""")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserDto::class.java)
                    .returnResult()
                    .responseBody!!
                val userId = user.id

                // then — the Kafka round trip broadcasts USER_CREATED: <id>
                awaitUntil(timeoutMs = 15_000, intervalMs = 50, what = "update event for user $userId") {
                    collected.any { it.event() == "update" && it.data()?.contains(userId) == true }
                }
                assertTrue(collected.any { it.data()?.contains("USER_CREATED") == true })
            } finally {
                // join, not just cancel: the SSE connection must be fully closed
                // before the test returns (fire-and-forget teardown can outlive the
                // JVM shutdown in non-Gradle runners)
                collector.cancelAndJoin()
            }
        }
    }
}
