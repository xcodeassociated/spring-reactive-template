package com.softeno.template.sample.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.slf4j.MDCContext
import org.apache.commons.logging.LogFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

@ConditionalOnProperty(
    name = ["com.softeno.ws.type"],
    havingValue = "coroutine",
    matchIfMissing = false
)
@Configuration
class CoroutineWebSocketConfig(
    private val coroutineMessageService: CoroutineMessageService,
    private val config: ChatConfigProperties
) {
    private val log = LogFactory.getLog(javaClass)

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

    @Bean
    fun handlerMapping(objectMapper: ObjectMapper): HandlerMapping {
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
            // Convert reactive to coroutine context
            mono( Dispatchers.IO + MDCContext()) {
                try {
                    handleWebSocketSession(session, objectMapper)
                } catch (e: Exception) {
                    log.error("ws: [chat] error in session: ${session.id}", e)
                }
            }.then()
        }
    }

    private suspend fun handleWebSocketSession(
        session: WebSocketSession,
        objectMapper: ObjectMapper
    ) = withContext(Dispatchers.IO + MDCContext()) {
        // Get user authentication

        val userId = withContext(Dispatchers.IO + MDCContext()) {
            val authentication = ReactiveSecurityContextHolder.getContext().awaitSingle().authentication
            val token = (authentication as JwtAuthenticationToken).token
            val userId = token.claims["sub"] as String
            return@withContext userId
        }

        // Register session
        coroutineMessageService.registerSession(session.id)

        // Send initial messages
        coroutineMessageService.send(Message("SYSTEM", session.id, "HANDSHAKE"), session.id)
        coroutineMessageService.send(Message("SYSTEM", session.id, userId), session.id)

        // Start concurrent coroutines for sending and receiving
        coroutineScope {
            // Coroutine for sending messages (including heartbeat)
            val sendingJob = launch(Dispatchers.IO + MDCContext() +
                    SupervisorJob() + CoroutineExceptionHandler { context, throwable -> runBlocking(Dispatchers.IO + MDCContext()) {
                        log.error("ws: [chat] failed to send message in session: $session.id", throwable)
                        handleWebSocketSessionError(context, throwable, session)
                    }
            }) {
                handleOutgoingMessages(session, objectMapper)
            }

            // Coroutine for receiving messages
            val receivingJob = launch(Dispatchers.IO + MDCContext() +
                    SupervisorJob() + CoroutineExceptionHandler { context, throwable -> runBlocking(Dispatchers.IO + MDCContext()) {
                        log.error("ws: [chat] failed to receive message in session: ${session.id}", throwable)
                        handleWebSocketSessionError(context, throwable, session)
                    }
            }) {
                handleIncomingMessages(session, objectMapper)
            }

            // Coroutine for heartbeat
            val heartbeatJob = launch(Dispatchers.IO + MDCContext() +
                    SupervisorJob() + CoroutineExceptionHandler { context, throwable -> runBlocking(Dispatchers.IO + MDCContext()) {
                        log.error("ws: [chat] failed to send heartbeat in session: ${session.id}", throwable)
                        handleWebSocketSessionError(context, throwable, session)
                    }
            }) {
                handleHeartbeat(session)
            }

            // Wait for any job to complete (usually means disconnection)
            select {
                sendingJob.onJoin { }
                receivingJob.onJoin { }
                heartbeatJob.onJoin { }
            }

            // Cancel remaining jobs
            sendingJob.cancelAndJoin()
            receivingJob.cancelAndJoin()
            heartbeatJob.cancelAndJoin()
        }

        // Cleanup
        log.info("ws: [chat] disconnect chat session: ${session.id}")
        coroutineMessageService.unregisterSession(session.id)
    }

    private suspend fun handleWebSocketSessionError(context: CoroutineContext, throwable: Throwable, session: WebSocketSession) =
        withContext(Dispatchers.IO + MDCContext()) {
            log.error("ws: [chat] error: ${throwable.message}", throwable)
            log.info("ws: [chat] closing session: ${session.id}")

            closeSession(session)
            context.cancel()
    }

    private suspend fun closeSession(session: WebSocketSession) {
        coroutineMessageService.unregisterSession(session.id)
        session.close().awaitSingleOrNull()
    }

    private suspend fun handleOutgoingMessages(
        session: WebSocketSession,
        objectMapper: ObjectMapper
    ) = withContext(Dispatchers.IO + MDCContext()) {
        coroutineMessageService.getMessageFlow(session.id).collect { message ->
            val json = message.toJson(objectMapper)
            log.info("ws: [chat] tx: $json")
            session.send(Mono.just(session.textMessage(json))).awaitSingleOrNull()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun handleIncomingMessages(
        session: WebSocketSession,
        objectMapper: ObjectMapper
    ) = withContext(Dispatchers.IO + MDCContext()) {
        session.receive()
            .doOnNext { wsMessage ->
                // Process the message immediately within the reactive context
                try {
                    val payloadText = wsMessage.payloadAsText
                    val message = objectMapper.readValue(payloadText, Message::class.java)
                    log.info("ws: [chat] rx: $message")

                    // Launch a coroutine to handle the message asynchronously
                    GlobalScope.launch(Dispatchers.IO + MDCContext()) {
                        try {
                            when {
                                message.content == "pong" && message.to == "SYSTEM" -> {
                                    log.debug("ws: [chat] received pong from session: ${session.id}")
                                    coroutineMessageService.updateLastPong(session.id)
                                }
                                else -> {
                                    coroutineMessageService.routeMessage(message)
                                }
                            }
                        } catch (e: Exception) {
                            log.error("ws: [chat] failed to route message in session: ${session.id}", e)
                        }
                    }
                } catch (e: Exception) {
                    log.error("ws: [chat] failed to parse message in session: ${session.id}", e)
                }
            }
            .then()
            .awaitSingleOrNull()
    }

    private suspend fun handleHeartbeat(session: WebSocketSession) = withContext(Dispatchers.IO + MDCContext()) {
        while (currentCoroutineContext().isActive) {
            delay(config.heartbeatIntervalSeconds.toLong().seconds)
            log.debug("ws: [chat] sending heartbeat to session: ${session.id}")
            coroutineMessageService.send(
                Message("SYSTEM", session.id, "ping"),
                session.id
            )
        }
    }
}

@ConditionalOnProperty(
    name = ["com.softeno.ws.type"],
    havingValue = "coroutine",
    matchIfMissing = false
)
@Service
class CoroutineMessageService(
    private val config: ChatConfigProperties
) : WsMessageService {
    private val log = LogFactory.getLog(javaClass)

    private val messageChannels = ConcurrentHashMap<String, Channel<Message>>()
    private val lastPongTimes = ConcurrentHashMap<String, Instant>()

    // Lazy heartbeat monitoring
    @OptIn(DelicateCoroutinesApi::class)
    private val heartbeatMonitoring: Job by lazy {
        GlobalScope.launch {
            while (isActive) {
                delay(config.heartbeatIntervalSeconds.toLong().seconds)
                checkStaleConnections()
            }
        }
    }

    suspend fun registerSession(sessionId: String) = withContext(Dispatchers.IO + MDCContext()) {
        messageChannels[sessionId] = Channel(capacity = Channel.UNLIMITED)
        lastPongTimes[sessionId] = Instant.now()

        // Start heartbeat monitoring when first session connects
        if (config.staleCheck) {
            heartbeatMonitoring
        }

        log.info("ws: [chat] registered session: $sessionId")
    }

    fun unregisterSession(sessionId: String) {
        messageChannels.remove(sessionId)?.close()
        lastPongTimes.remove(sessionId)
        log.info("ws: [chat] unregistered session: $sessionId")
    }

    fun send(message: Message, sessionId: String): Message {
        messageChannels[sessionId]?.trySend(message)?.let { result ->
            if (result.isFailure) {
                log.warn("ws: [chat] failed to send message to session: $sessionId")
            }
        }
        return message
    }

    override fun broadcast(message: Message): Message {
        messageChannels.values.forEach { channel ->
            channel.trySend(message)
        }
        return message
    }

    fun routeMessage(message: Message) {
        when (message.to) {
            "ALL" -> broadcast(message)
            else -> {
                // Try to send to specific session first, then to user
                if (messageChannels.containsKey(message.to)) {
                    send(message, message.to)
                } else {
                   log.error("ws: [chat] failed to send message: $message to session: ${message.to} - unknown session")
                }
            }
        }
    }

    fun getMessageFlow(sessionId: String): Flow<Message> {
        return messageChannels[sessionId]?.receiveAsFlow() ?: emptyFlow()
    }

    fun updateLastPong(sessionId: String) {
        lastPongTimes[sessionId] = Instant.now()
    }

    private fun checkStaleConnections() {
        val staleThreshold = Instant.now().minusSeconds(config.staleCheckThresholdSeconds.toLong())
        val staleSessions = mutableListOf<String>()

        // First, identify stale sessions without modifying the collection
        lastPongTimes.forEach { (sessionId, lastPong) ->
            if (lastPong.isBefore(staleThreshold)) {
                staleSessions.add(sessionId)
            }
        }

        // Then, clean up stale sessions properly in coroutine context
        staleSessions.forEach { sessionId ->
            log.warn("ws: [chat] removing stale connection: $sessionId (last pong: ${lastPongTimes[sessionId]})")
            unregisterSession(sessionId)
        }
    }
}
