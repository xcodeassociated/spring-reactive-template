package com.softeno.template.notifications.sse

import com.softeno.template.notifications.Message
import com.softeno.template.notifications.toJson
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.slf4j.MDCContext
import org.apache.commons.logging.LogFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** SSE termination-event contract — the stream analog of the WS 4401/4403 codes. */
object SseControl {
    const val TOKEN_EXPIRED = "token_expired"
    const val LOGGED_OUT = "logged_out"
}

/** Config for the SSE subscription lifecycle. */
@ConfigurationProperties(prefix = "com.softeno.sse")
data class SseSubscriptionProperties(
    /** how often the sweeper enforces the token ceiling on live streams, seconds */
    val sweepIntervalSeconds: Int = 10,
)

/**
 * Live SSE subscriptions with identity binding, reverse index, token-ceiling
 * sweep and logout revocation — the stream analog of the WebSocket
 * `CoroutineMessageService` registry (sse-auth-session-lifecycle skill):
 *
 * - identity `{sub, exp}` is bound at subscribe time from the authenticated
 *   principal; the stream never re-derives it from client data.
 * - the reverse index (`sub -> streams`) is what makes Keycloak LOGOUT events
 *   actionable; without it revocation is inert.
 * - the sweeper enforces the ceiling hard (window: exp + sweepInterval),
 *   independent of the logout event path.
 * - termination always travels as a named control event followed by stream
 *   completion: `token_expired` (client refreshes + reconnects) or
 *   `logged_out` (client stops).
 *
 * Per-process registries: multi-instance deployments need per-instance consumer
 * groups for the keycloak-events listener. # VERIFY per deployment.
 */
@Service
class SseSubscriptionRegistry(
    private val props: SseSubscriptionProperties,
    private val objectMapper: ObjectMapper,
) {
    private val log = LogFactory.getLog(javaClass)

    /** One live stream: bound identity + the channel feeding the response Flow. */
    class Subscription internal constructor(
        val id: String,
        val sub: String?,
        val exp: Instant?,
        internal val channel: Channel<ServerSentEvent<String>>,
        internal val closed: AtomicBoolean = AtomicBoolean(false),
    )

    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val userIndex = ConcurrentHashMap<String, MutableSet<String>>()

    // Service-scoped coroutines (sweeper + control-then-close); MDCContext(emptyMap())
    // so background passes never inherit a stale request MDC snapshot.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + MDCContext(emptyMap()))

    @Volatile
    private var sweeper: Job? = null

    /** Register a live stream. `sub == null` = anonymous/dev (no ceiling, not user-revocable). */
    fun register(sub: String?, exp: Instant?): Subscription {
        val subscription = Subscription(UUID.randomUUID().toString(), sub, exp, Channel(capacity = 64))
        subscriptions[subscription.id] = subscription
        // atomic read-modify-write under compute: a concurrent revoke/unregister can
        // never detach our add and hide this stream from logout revocation
        sub?.let { user ->
            userIndex.compute(user) { _, ids ->
                (ids ?: Collections.newSetFromMap(ConcurrentHashMap())).apply { add(subscription.id) }
            }
        }
        ensureSweeper()
        log.debug("sse: registered subscription ${subscription.id} for user: $sub")
        return subscription
    }

    /** Idempotent, leak-free unregister (both maps). */
    fun unregister(subscription: Subscription) {
        subscription.closed.set(true)
        subscriptions.remove(subscription.id)
        subscription.sub?.let { user ->
            userIndex.compute(user) { _, ids ->
                ids?.remove(subscription.id)
                if (ids.isNullOrEmpty()) null else ids
            }
        }
        runCatching { subscription.channel.close() }
        log.debug("sse: unregistered subscription ${subscription.id}")
    }

    /** Fan a broadcast event out to every live subscription. */
    fun broadcast(event: ServerSentEvent<String>): Boolean {
        subscriptions.values.forEach { subscription ->
            if (!subscription.closed.get()) {
                subscription.channel.trySend(event).let { result ->
                    if (result.isFailure) log.warn("sse: buffer full, dropped event for subscription ${subscription.id}")
                }
            }
        }
        return subscriptions.isNotEmpty()
    }

    /** Logout/revocation (Keycloak LOGOUT via Kafka): `logged_out` then complete. */
    fun revokeUser(userId: String): Int {
        val ids = userIndex[userId]?.toList() ?: return 0
        log.info("sse: revoking ${ids.size} subscription(s) for user: $userId")
        ids.forEach { id -> subscriptions[id]?.let { closeWithControl(it, SseControl.LOGGED_OUT) } }
        return ids.size
    }

    /** Live subscription ids bound to [userId] (reverse-index view, for tests/ops). */
    fun subscriptionsFor(userId: String): Set<String> = userIndex[userId]?.toSet() ?: emptySet()

    internal fun size(): Int = subscriptions.size

    internal fun sweep() {
        val now = Instant.now()
        subscriptions.values
            .filter { handle -> handle.exp != null && handle.exp.isBefore(now) && !handle.closed.get() }
            .forEach { handle ->
                log.info("sse: token expired for subscription ${handle.id} (user: ${handle.sub}), sending ${SseControl.TOKEN_EXPIRED}")
                closeWithControl(handle, SseControl.TOKEN_EXPIRED)
            }
    }

    private fun closeWithControl(subscription: Subscription, control: String) {
        if (!subscription.closed.compareAndSet(false, true)) return
        subscription.channel.trySend(controlEvent(control))
        serviceScope.launch {
            // small grace so the control event flushes before the stream completes
            delay(CONTROL_CLOSE_GRACE_MS.milliseconds)
            runCatching { subscription.channel.close() }
            unregister(subscription)
        }
    }

    private fun controlEvent(control: String): ServerSentEvent<String> =
        ServerSentEvent.builder(Message(from = "SYSTEM", to = "ALL", content = control).toJson(objectMapper))
            .event(control)
            .build()

    private fun ensureSweeper() {
        if (sweeper?.isActive == true) return
        synchronized(this) {
            if (sweeper?.isActive == true) return
            sweeper = serviceScope.launch {
                while (isActive) {
                    delay(props.sweepIntervalSeconds.toLong().seconds)
                    try {
                        sweep()
                    } catch (e: Exception) {
                        log.error("sse: sweep failed", e)
                    }
                }
            }
        }
    }

    /** Graceful shutdown: complete live streams instead of resetting them. */
    @PreDestroy
    fun shutdown() {
        val live = subscriptions.values.toList()
        if (live.isNotEmpty()) log.info("sse: shutdown: closing ${live.size} live subscription(s)")
        live.forEach { subscription ->
            subscription.closed.set(true)
            runCatching { subscription.channel.close() }
        }
        serviceScope.coroutineContext.cancel()
    }

    private companion object {
        const val CONTROL_CLOSE_GRACE_MS = 100L
    }
}
