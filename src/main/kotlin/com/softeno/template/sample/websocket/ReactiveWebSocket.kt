package com.softeno.template.sample.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.Many
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap


@ConditionalOnProperty(
    name = ["com.softeno.ws.type"],
    havingValue = "reactive",
    matchIfMissing = true
)
@Configuration
class WebSocketConfig(
    private val reactiveMessageService: ReactiveMessageService,
    private val config: ChatConfigProperties
) {
    private val log = LogFactory.getLog(javaClass)

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

    @Bean
    fun handlerMapping(
        objectMapper: ObjectMapper
    ): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.order = 10
        simpleMapping.urlMap = mapOf(
            "/ws" to webSocketHandler(objectMapper)
        )
        return simpleMapping
    }

    @Bean
    fun webSocketHandler(objectMapper: ObjectMapper): WebSocketHandler {
        return WebSocketHandler { session ->
            log.info("ws: [chat] new session: ${session.id}")
            reactiveMessageService.registerSession(session)

            // note: example of send out message right after connect as fire&forget
            val handshake = Message(from = "SYSTEM", to = session.id, content = "HANDSHAKE")
            reactiveMessageService.send(handshake, session)

            val authentication = ReactiveSecurityContextHolder.getContext().map { it.authentication }
            val userIdMessage: Flux<String> = authentication.flux().map {
                val token = (it as JwtAuthenticationToken).token
                val userId = token.claims["sub"]
                Message(from = "SYSTEM", to = session.id, content = "$userId").toJson(objectMapper)
            }

            // Heartbeat messages every 30 seconds
            val heartbeatMessages: Flux<String> = Flux.interval(Duration.ofSeconds(config.heartbeatIntervalSeconds.toLong()))
                .map { Message(from = "SYSTEM", to = session.id, content = "ping").toJson(objectMapper) }
                .doOnNext { log.debug("ws: [chat] sending heartbeat to session: ${session.id}") }

            val messages: Flux<WebSocketMessage> =
                Flux.merge(
                    userIdMessage,
                    heartbeatMessages,
                    reactiveMessageService.getMessages(session)
                )
                    .map {
                        log.info("ws: [chat] tx: $it")
                        it
                    }
                    .map(session::textMessage)

            val reading = session.receive()
                .doFinally { sig ->
                    log.info("ws: [chat] disconnect chat session: ${session.id} with sig: ${sig.name}")
                    reactiveMessageService.remove(session)
                }.doOnNext { wsMessage ->
                    try {
                        val message = objectMapper.readValue(wsMessage.payloadAsText, Message::class.java)
                        log.info("ws: [chat] rx: $message")

                        // Handle pong responses
                        if (message.content == "pong" && message.to == "SYSTEM") {
                            log.debug("ws: [chat] received pong from session: ${session.id}")
                            reactiveMessageService.updateLastPong(session)
                        } else {
                            reactiveMessageService.send(message, reactiveMessageService.getSession(message.to)!!)
                        }
                    } catch (e: Exception) {
                        log.error("ws: [chat] failed to parse message: ${wsMessage.payloadAsText}", e)
                        // optionally send error message back to client
                    }
                }.doOnError { error ->
                    log.error("ws: [chat] error in session: ${session.id}", error)
                    // handle the error
                }.onErrorContinue { error, _ ->
                    log.warn("ws: [chat] continuing after error: ${error.message}")
                }

            session.send(messages).and(reading)
        }
    }
}

interface WebSocketNotificationSender {
    fun broadcast(message: Message): Message
}

@ConditionalOnProperty(
    name = ["com.softeno.ws.type"],
    havingValue = "reactive",
    matchIfMissing = true
)
@Service
class ReactiveMessageService(
    private val objectMapper: ObjectMapper,
    private val config: ChatConfigProperties
) : WebSocketNotificationSender {
    private val log = LogFactory.getLog(javaClass)

    private val sinks: MutableMap<WebSocketSession, Many<String>> = ConcurrentHashMap()
    private val lastPongTimes: MutableMap<WebSocketSession, Instant> = ConcurrentHashMap()


    // Lazy initialization of heartbeat monitoring
    private val heartbeatMonitoring: Disposable by lazy {
        Flux.interval(Duration.ofSeconds(config.heartbeatIntervalSeconds.toLong()))
            .doOnNext {
                checkStaleConnections()
            }
            .subscribe()
    }

    fun registerSession(session: WebSocketSession) {
        if (sinks.containsKey(session)) {
            log.warn("ws: [chat] attempting to register existing session: ${session.id}")
            return
        }
        sinks[session] = Sinks.many().multicast().onBackpressureBuffer()
        lastPongTimes[session] = Instant.now()

        // Start heartbeat monitoring when first session is created
        if (config.staleCheck) {
            heartbeatMonitoring
        }
    }

    fun getSession(sessionId: String): WebSocketSession? {
        return sinks.keys.firstOrNull { it.id == sessionId }
    }

    fun send(message: Message, session: WebSocketSession): Message {
        if (!sinks.containsKey(session)) {
            log.warn("ws: [chat] attempting to send to non-existent session: ${session.id}")
            return message
        }

        val payload = message.toJson(objectMapper)
        getSink(session)?.emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST)
            ?: throw RuntimeException("error: no sink for session: ${session.id}")

        return message
    }

    override fun broadcast(message: Message): Message {
        val payload = message.toJson(objectMapper)
        sinks.forEach { (sessionId, _) ->
            getSink(sessionId)?.emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST)
        }
        return message
    }

    fun getMessages(session: WebSocketSession): Flux<String> {
        return getSink(session)?.asFlux() ?: Flux.empty()
    }

    fun updateLastPong(session: WebSocketSession) {
        lastPongTimes[session] = Instant.now()
    }

    private fun getSink(session: WebSocketSession): Many<String>? = sinks[session]

    private fun checkStaleConnections() {
        val staleThreshold = Instant.now().minus(Duration.ofSeconds(config.staleCheckThresholdSeconds.toLong()))

        lastPongTimes.entries.removeIf { (session, lastPong) ->
            if (lastPong.isBefore(staleThreshold)) {
                log.warn("ws: [chat] removing stale connection: ${session.id} (last pong: $lastPong)")
                remove(session)
                true
            } else {
                false
            }
        }
    }

    fun remove(session: WebSocketSession) {
        lastPongTimes.remove(session)
        session.close().subscribe()
    }
}

data class Message(val from: String, val to: String, val content: String) {
    init {
        require(from.isNotBlank()) { "Message 'from' cannot be blank" }
        require(to.isNotBlank()) { "Message 'to' cannot be blank" }
        require(content.isNotBlank()) { "Message 'content' cannot be blank" }
    }
}

fun Message.toJson(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(this)

@ConfigurationProperties(value = "com.softeno.ws")
data class ChatConfigProperties(
    val type: String,
    val staleCheck: Boolean,
    val heartbeatIntervalSeconds: Int,
    val staleCheckThresholdSeconds: Int,
)