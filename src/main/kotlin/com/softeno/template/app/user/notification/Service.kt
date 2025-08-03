package com.softeno.template.app.user.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.sample.websocket.Message
import com.softeno.template.sample.websocket.toJson
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.apache.commons.logging.LogFactory
import org.springframework.http.codec.ServerSentEvent
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.Sinks
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException

@Service
class ReactiveUserUpdateEmitter(
    private val objectMapper: ObjectMapper,
) {
    private val sink: Sinks.Many<ServerSentEvent<String>> = Sinks.many().multicast().onBackpressureBuffer(1, false)
    private val log = LogFactory.getLog(javaClass)

    fun getSink(): Flux<ServerSentEvent<String>> {
        val heartbeatFlux = Flux.interval(Duration.ofSeconds(10))
            .map {
                ServerSentEvent.builder<String>()
                    .event("heartbeat")
                    .data(Message(from = "SYSTEM", to = "ALL", content = "ping").toJson(objectMapper))
                    .build()
            }.doOnError {
                error -> log.warn("[reactive] Heartbeat error", error)
            }

        val events = sink.asFlux()
            .doOnSubscribe {
                log.info("[reactive] New SSE client subscribed")
            }
            .doOnCancel {
                log.info("[reactive] SSE client disconnected")
            }
            .doOnTerminate {
                log.info("[reactive] SSE client terminated")
            }
            .doOnError {
                error -> log.error("Event stream error", error)
            }


        return Flux.merge(heartbeatFlux, events)
            .doOnCancel {
                log.info("[reactive] Canceling SSE stream")
            }
            .doOnTerminate {
                log.info("[reactive] Terminating SSE stream")
            }
            .onErrorResume { error ->
                log.error("SSE stream error, sending error event", error)
                Mono.just(
                    ServerSentEvent.builder<String>()
                        .event("error")
                        .data(Message(from = "SYSTEM", to = "ALL", content = "Connection error: ${error.message}").toJson(objectMapper))
                        .build()
                )
            }
    }

    fun broadcast(message: Message): Boolean =
        try {
            val payload = message.toJson(objectMapper)
            val sse = ServerSentEvent.builder(payload).event("update").build()
            val result = sink.tryEmitNext(sse)

            when (result) {
                Sinks.EmitResult.OK -> {
                    log.debug("[reactive] Message broadcasted successfully: ${message.content}")
                    true
                }
                Sinks.EmitResult.FAIL_CANCELLED -> {
                    log.warn("[reactive] Failed to broadcast message - emitter cancelled")
                    false
                }
                Sinks.EmitResult.FAIL_OVERFLOW -> {
                    log.warn("[reactive] Failed to broadcast message - buffer overflow")
                    false
                }
                else -> {
                    log.error("[reactive] Failed to broadcast message: $result")
                    false
                }
            }
        } catch (e: Exception) {
            log.error("[reactive] Error broadcasting message", e)
            false
        }
}

@Service
class CoroutineUserUpdateEmitter(
    private val objectMapper: ObjectMapper,
) {
    private val _events = MutableSharedFlow<ServerSentEvent<String>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val events = _events.asSharedFlow()
    private val log = LogFactory.getLog(javaClass)

    private fun createHeartbeatFlow(): Flow<ServerSentEvent<String>> = flow {
        while (currentCoroutineContext().isActive) {
            try {
                val heartbeatEvent = ServerSentEvent.builder<String>()
                    .event("heartbeat")
                    .data(Message(from = "SYSTEM", to = "ALL", content = "ping").toJson(objectMapper))
                    .build()
                emit(heartbeatEvent)
                delay(10_000) // 10 seconds
            } catch (e: Exception) {
                log.error("[coroutine] Heartbeat error", e)
                delay(10_000) // Continue heartbeat even on error
            }
        }
    }

    fun getFlow(): Flow<ServerSentEvent<String>> = flow {
        log.info("[coroutine] New SSE client subscribed")
        val heartbeatFlow = createHeartbeatFlow()

        merge(heartbeatFlow, events)
            .collect { event -> emit(event) }
    }.onCompletion { cause ->
        when (cause) {
            is CancellationException -> log.info("[coroutine] SSE client disconnected")
            null -> log.info("[coroutine] SSE client terminated")
            else -> log.error("[coroutine] SSE stream terminated with error", cause)
        }
    }.catch { error ->
        log.error("[coroutine] SSE stream error, sending error event", error)
        val errorEvent = ServerSentEvent.builder<String>()
            .event("error")
            .data(Message(from = "SYSTEM", to = "ALL", content = "Connection error: ${error.message}").toJson(objectMapper))
            .build()
        emit(errorEvent)
    }

    fun broadcast(message: Message): Boolean =
        try {
            val payload = message.toJson(objectMapper)
            val sse = ServerSentEvent.builder(payload).event("update").build()
            val result = _events.tryEmit(sse)

            if (result) {
                log.debug("[coroutine] Message broadcasted successfully: ${message.content}")
                true
            } else {
                log.warn("[coroutine] Failed to broadcast message - buffer full or no subscribers")
                false
            }
        } catch (e: Exception) {
            log.error("[coroutine] Error broadcasting message", e)
            false
        }


}
