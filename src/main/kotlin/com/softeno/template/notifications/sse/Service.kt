package com.softeno.template.notifications.sse

import com.softeno.template.notifications.Message
import com.softeno.template.notifications.toJson
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.apache.commons.logging.LogFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

/**
 * Coroutine user-update SSE emitter.
 *
 * Each subscribe registers a live subscription (bound identity + channel) in the
 * [SseSubscriptionRegistry]; the response flow merges the per-subscription
 * channel with a heartbeat and deregisters on completion. Expiry/revocation
 * close streams through the registry, never here.
 */
@Service
class CoroutineUserUpdateEmitter(
    private val objectMapper: ObjectMapper,
    private val registry: SseSubscriptionRegistry,
) {
    private val log = LogFactory.getLog(javaClass)

    /**
     * Subscribe a new stream. `sub`/`exp` come from the authenticated principal
     * (null under permit-all dev/test → no ceiling, not user-revocable).
     */
    fun subscribe(sub: String?, exp: Instant?): Flow<ServerSentEvent<String>> {
        val subscription = registry.register(sub, exp)
        log.info("[coroutine] New SSE client subscribed: ${subscription.id}")

        return channelFlow {
            val heartbeat = launch {
                while (currentCoroutineContext().isActive) {
                    try {
                        send(heartbeatEvent())
                        delay(HEARTBEAT_INTERVAL_MS.milliseconds)
                    } catch (e: CancellationException) {
                        throw e // never swallow cancellation: a leaked heartbeat loop
                        // would outlive its context and spin on the shared dispatcher
                    } catch (e: Exception) {
                        log.error("[coroutine] Heartbeat error", e)
                        delay(HEARTBEAT_INTERVAL_MS.milliseconds)
                    }
                }
            }
            try {
                subscription.channel.receiveAsFlow().collect { event -> send(event) }
            } finally {
                heartbeat.cancel()
            }
        }.onCompletion { cause ->
            registry.unregister(subscription)
            when (cause) {
                is CancellationException -> log.info("[coroutine] SSE client disconnected: ${subscription.id}")
                null -> log.info("[coroutine] SSE client terminated: ${subscription.id}")
                else -> log.error("[coroutine] SSE stream terminated with error: ${subscription.id}", cause)
            }
        }
    }

    fun broadcast(message: Message): Boolean =
        try {
            val result = registry.broadcast(
                ServerSentEvent.builder(message.toJson(objectMapper)).event("update").build()
            )
            if (result) {
                log.debug("[coroutine] Message broadcasted successfully: ${message.content}")
            } else {
                log.warn("[coroutine] Failed to broadcast message - no subscribers")
            }
            result
        } catch (e: Exception) {
            log.error("[coroutine] Error broadcasting message", e)
            false
        }

    private fun heartbeatEvent(): ServerSentEvent<String> =
        ServerSentEvent.builder<String>()
            .event("heartbeat")
            .data(Message(from = "SYSTEM", to = "ALL", content = "ping").toJson(objectMapper))
            .build()

    private companion object {
        const val HEARTBEAT_INTERVAL_MS = 10_000L
    }
}
