package com.softeno.template.notifications.sse

import com.softeno.template.users.api.UserDto
import com.softeno.template.users.permission.api.PermissionDto
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
import org.junit.jupiter.api.Test
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.http.codec.ServerSentEvent
import java.util.Collections
import kotlin.time.Duration.Companion.milliseconds

/**
 * SSE round-trip: a subscribed client gets an immediate heartbeat, and the user
 * lifecycle (create/update/delete, via the Kafka tx/rx loop) arrives as `update`
 * events. Auth is permit-all under the `integration` profile; the production rule
 * is `.authenticated()` on the /update paths in the main SecurityConfig.
 */
class UserUpdateSseIT : BaseIntegrationTest() {

    @Test
    fun `subscribed client receives heartbeat and user lifecycle broadcasts`() = runBlocking {
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
                    collected.any { it.event() == "update" && it.data()?.contains("USER_CREATED: $userId") == true }
                }

                // when — the user is updated (optimistic-locking version from the response)
                webTestClient.put().uri("/coroutine/users/$userId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"name":"${R.name()}","email":"${R.email()}","role":[],"version":${user.version}}""")
                    .exchange()
                    .expectStatus().isOk()

                // then — USER_UPDATED arrives on the same stream
                awaitUntil(timeoutMs = 15_000, intervalMs = 50, what = "USER_UPDATED event for user $userId") {
                    collected.any { it.event() == "update" && it.data()?.contains("USER_UPDATED: $userId") == true }
                }

                // when — the user is deleted
                webTestClient.delete().uri("/coroutine/users/$userId")
                    .exchange()
                    .expectStatus().isOk()

                // then — USER_DELETED arrives too (broadcast only: the app user id is
                // not a Keycloak sub, so nothing here can revoke live sessions)
                awaitUntil(timeoutMs = 15_000, intervalMs = 50, what = "USER_DELETED event for user $userId") {
                    collected.any { it.event() == "update" && it.data()?.contains("USER_DELETED: $userId") == true }
                }
            } finally {
                // join, not just cancel: the SSE connection must be fully closed
                // before the test returns (fire-and-forget teardown can outlive the
                // JVM shutdown in non-Gradle runners)
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun `permission fan-out emits a single USERS_SYNCED completion event`() = runBlocking {
        withTimeout(60_000.milliseconds) {
            val events = webTestClient.get().uri("/update/user")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(object : ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .responseBody

            val collected = Collections.synchronizedList(mutableListOf<ServerSentEvent<String>>())
            val collector = launch(Dispatchers.Default) { runCatching { events.asFlow().toList(collected) } }
            try {
                awaitUntil(what = "heartbeat event") { collected.any { it.event() == "heartbeat" } }

                // given — a permission held by a fresh user
                val permission = webTestClient.post().uri("/coroutine/permissions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"name":"${R.name()}","description":"fan-out probe"}""")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(PermissionDto::class.java)
                    .returnResult()
                    .responseBody!!
                val user = webTestClient.post().uri("/coroutine/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"name":"${R.name()}","email":"${R.email()}","role":["${permission.id}"]}""")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(UserDto::class.java)
                    .returnResult()
                    .responseBody!!

                // drain the USER_CREATED broadcast so it cannot be mistaken for the fan-out
                awaitUntil(what = "creation event") {
                    collected.any { it.data()?.contains(": ${user.id}") == true }
                }
                collected.clear()

                // when — the permission is renamed (the fan-out rewrites the snapshot)
                webTestClient.put().uri("/coroutine/permissions/${permission.id}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue("""{"name":"${R.name()}","description":"fan-out probe","version":${permission.version}}""")
                    .exchange()
                    .expectStatus().isOk()

                // then — exactly ONE completion event: the fan-out is one logical
                // change, so no per-user USER_UPDATED spam (and no client can
                // refetch mid-sync — the event follows the last save)
                awaitUntil(timeoutMs = 15_000, intervalMs = 50, what = "USERS_SYNCED fan-out for permission ${permission.id}") {
                    collected.any { it.event() == "update" && it.data()?.contains("USERS_SYNCED: ${permission.id}") == true }
                }
                val synced = collected.count { it.data()?.contains("USERS_SYNCED") == true }
                val perUserUpdates = collected.count { it.data()?.contains("USER_UPDATED: ${user.id}") == true }
                assert(perUserUpdates == 0) { "fan-out must not emit per-user events" }
                assert(synced == 1) { "exactly one completion event per permission mutation" }

                // when — the permission is deleted (pull from every holder)
                webTestClient.delete().uri("/coroutine/permissions/${permission.id}")
                    .exchange()
                    .expectStatus().isOk()

                // then — a SECOND completion event for the pull (count-based: the
                // payload is identical to the rename's, so content matching alone
                // would pass against the already-collected first event)
                awaitUntil(timeoutMs = 15_000, intervalMs = 50, what = "second USERS_SYNCED for permission ${permission.id}") {
                    collected.count { it.data()?.contains("USERS_SYNCED: ${permission.id}") == true } >= synced + 1
                }
            } finally {
                collector.cancelAndJoin()
            }
        }
    }
}
