package com.softeno.template.notifications.ws

import com.softeno.template.notifications.Message
import com.softeno.template.notifications.toJson
import com.softeno.template.notifications.ws.security.SessionBinding
import com.softeno.template.notifications.ws.security.toSessionBinding
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
import jakarta.annotation.PreDestroy
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.CloseStatus
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Mono
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true", matchIfMissing = true)
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
        val handler = webSocketHandler(objectMapper)
        // "/ws/" too: the redeem filter normalizes the trailing slash, and without
        // this mapping a /ws/ upgrade would consume its ticket and 404.
        simpleMapping.urlMap = mapOf(
            "/ws" to handler,
            "/ws/" to handler,
        )
        return simpleMapping
    }

    @Bean
    fun webSocketHandler(objectMapper: ObjectMapper): WebSocketHandler {
        return WebSocketHandler { session ->
            // Capture the MDC (OTel traceId/spanId via context-propagation) while
            // still on the request thread inside the reactive chain — this is the
            // point where the handshake's tracing scope is active. The coroutine
            // bridge below would otherwise capture an empty MDC.
            val handshakeMdc = MDC.getCopyOfContextMap()
            // Convert reactive to coroutine; MDCContext() carries the caller's
            // MDC across the dispatcher hop into the session coroutines.
            mono(Dispatchers.IO + MDCContext()) {
                try {
                    handleWebSocketSession(session, objectMapper, handshakeMdc)
                } catch (e: Exception) {
                    log.error("ws: [chat] error in session: ${session.id}", e)
                }
            }.then()
        }
    }

    private suspend fun handleWebSocketSession(
        session: WebSocketSession,
        objectMapper: ObjectMapper,
        handshakeMdc: Map<String, String>?,
    ) {
        // Identity was bound at the ticket redemption (or resource-server
        // authentication) and lives in the reactive security context now.
        // Client frames never assert identity.
        val binding = ReactiveSecurityContextHolder.getContext().awaitSingleOrNull()
            ?.authentication.toSessionBinding()

        if (binding == null) {
            // should not happen in the normal flow: anonymous /ws upgrades are
            // rejected by the security chain, ticket failures pre-upgrade by the
            // redeem filter. Defensive: never serve an unauthenticated session.
            log.warn("ws: [chat] no authenticated principal for session: ${session.id}, closing")
            session.close(WsControl.REAUTH_CLOSE_STATUS).awaitSingleOrNull()
            return
        }

        // Bind session identity into the MDC ONCE for the whole session lifetime,
        // MERGING on top of the handshake's tracing MDC (traceId/spanId).
        // MDCContext(map) REPLACES the MDC, so starting from the current copy is
        // what keeps OTel correlation additive — nothing is dropped.
        val sessionMdc = (handshakeMdc ?: emptyMap()) + (MDC.getCopyOfContextMap() ?: emptyMap()) + mapOf(
            "sessionId" to session.id,
            "userId" to binding.sub,
        )
        withContext(MDCContext(sessionMdc)) {
            log.info("ws: [chat] new session: ${session.id}")

            // Register session (binds {sub, sid, exp} + reverse index)
            coroutineMessageService.registerSession(session, binding)

            // Send initial messages
            coroutineMessageService.send(Message("SYSTEM", session.id, "HANDSHAKE"), session)
            coroutineMessageService.send(Message("SYSTEM", session.id, binding.sub), session)

            try {
                // Structured concurrency: one scope owns the session's jobs; a
                // failure in any job cancels the others and runs cleanup exactly once.
                coroutineScope {
                    val handler = sessionExceptionHandler(this, session)

                    val sendingJob = launch(SupervisorJob() + handler) {
                        handleOutgoingMessages(session, objectMapper)
                    }
                    val receivingJob = launch(SupervisorJob() + handler) {
                        handleIncomingMessages(this, session, objectMapper)
                    }
                    val heartbeatJob = launch(SupervisorJob() + handler) {
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
            } finally {
                // Cleanup is idempotent: may also run from the sweeper/revocation path.
                log.info("ws: [chat] disconnect chat session: ${session.id}")
                coroutineMessageService.unregisterSession(session)
            }
        }
    }

    /**
     * A failing job closes the session cleanly instead of killing siblings
     * silently: log, close the socket, cancel the session scope (the finally in
     * handleWebSocketSession then runs the idempotent unregister exactly once).
     */
    private fun sessionExceptionHandler(scope: CoroutineScope, session: WebSocketSession): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, throwable ->
            log.error("ws: [chat] session job failed for session: ${session.id}", throwable)
            session.close().subscribe()
            scope.cancel()
        }

    private suspend fun handleOutgoingMessages(
        session: WebSocketSession,
        objectMapper: ObjectMapper
    ) {
        coroutineMessageService.getMessageFlow(session).collect { message ->
            val json = message.toJson(objectMapper)
            log.debug("ws: [chat] tx: $json")
            session.send(Mono.just(session.textMessage(json))).awaitSingleOrNull()
        }
    }

    private suspend fun handleIncomingMessages(
        scope: CoroutineScope,
        session: WebSocketSession,
        objectMapper: ObjectMapper
    ) {
        // Parse + handle run on the SESSION scope (structured concurrency): they
        // inherit the session MDC + dispatcher and die with the socket. Never
        // GlobalScope: detached coroutines leak past disconnect and lose MDC.
        session.receive()
            .doOnNext { wsMessage ->
                val payloadText = wsMessage.payloadAsText
                scope.launch {
                    try {
                        val message = objectMapper.readValue(payloadText, Message::class.java)
                        log.debug("ws: [chat] rx: $message")
                        when {
                            message.content == "pong" && message.to == "SYSTEM" ->
                                coroutineMessageService.updateLastPong(session)

                            else -> coroutineMessageService.routeMessage(session, message)
                        }
                    } catch (e: Exception) {
                        log.error("ws: [chat] failed to parse/route message in session: ${session.id}", e)
                    }
                }
            }
            .then()
            .awaitSingleOrNull()
    }

    private suspend fun handleHeartbeat(session: WebSocketSession) {
        while (currentCoroutineContext().isActive) {
            delay(config.heartbeatIntervalSeconds.toLong().seconds)
            log.debug("ws: [chat] sending heartbeat to session: ${session.id}")
            coroutineMessageService.send(Message("SYSTEM", session.id, "ping"), session)
        }
    }
}

@ConditionalOnProperty(name = ["com.softeno.ws.enabled"], havingValue = "true", matchIfMissing = true)
@Service
class CoroutineMessageService(
    private val config: ChatConfigProperties
) {
    private val log = LogFactory.getLog(javaClass)

    private val messageChannels = ConcurrentHashMap<WebSocketSession, Channel<Message>>()
    private val lastPongTimes = ConcurrentHashMap<WebSocketSession, Instant>()

    // Session identity: bound at handshake from validated credentials.
    // The reverse index (sub -> sessions) is what makes logout/revocation events
    // find a user's sockets; without it they are inert.
    private val bindings = ConcurrentHashMap<WebSocketSession, SessionBinding>()
    private val userIndex = ConcurrentHashMap<String, MutableSet<WebSocketSession>>()

    // Service-scoped coroutines (sweeper + control-then-close); cancelled with the
    // bean. MDCContext(emptyMap()) so background passes never inherit a stale
    // request/session MDC snapshot — session ids are logged explicitly instead.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + MDCContext(emptyMap()))

    // Lazy lifecycle sweeping: enforces the token ceiling (4401) and heartbeat
    // staleness on every pass.
    private val sweeper: Job by lazy {
        serviceScope.launch {
            while (isActive) {
                delay(config.sweepIntervalSeconds.toLong().seconds)
                try {
                    sweep()
                } catch (e: Exception) {
                    log.error("ws: [chat] sweep failed", e)
                }
            }
        }
    }

    fun registerSession(session: WebSocketSession, binding: SessionBinding) {
        messageChannels[session] = Channel(capacity = Channel.UNLIMITED)
        lastPongTimes[session] = Instant.now()
        bindings[session] = binding
        // atomic: read-modify-write of the reverse-index entry under compute, so a
        // concurrent unregister(same sub) can never detach our add and hide this
        // session from logout revocation
        userIndex.compute(binding.sub) { _, sessions ->
            (sessions ?: Collections.newSetFromMap(ConcurrentHashMap())).apply { add(session) }
        }

        // Start lifecycle sweeping when first session connects
        if (config.staleCheck) {
            sweeper
        }

        log.info("ws: [chat] registered session: ${session.id} for user: ${binding.sub}")
    }

    fun unregisterSession(session: WebSocketSession) {
        messageChannels.remove(session)?.close()
        lastPongTimes.remove(session)
        bindings.remove(session)?.let { binding ->
            // leak-free unregister: remove from both the registry and the reverse index
            userIndex.compute(binding.sub) { _, sessions ->
                sessions?.remove(session)
                if (sessions.isNullOrEmpty()) null else sessions
            }
        }
        log.info("ws: [chat] unregistered session: ${session.id}")
    }

    fun send(message: Message, session: WebSocketSession): Message {
        messageChannels[session]?.trySend(message)?.let { result ->
            if (result.isFailure) {
                log.warn("ws: [chat] failed to send message to session: ${session.id}")
            }
        }
        return message
    }

    fun broadcast(message: Message): Message {
        messageChannels.values.forEach { channel ->
            channel.trySend(message)
        }
        return message
    }

    /**
     * Route an incoming frame. The `from` field is STAMPED from the sending
     * session's bound identity — client-claimed identity is not trusted.
     */
    fun routeMessage(session: WebSocketSession, message: Message) {
        val binding = bindings[session]
        if (binding == null) {
            log.warn("ws: [chat] message from unregistered session: ${session.id}, dropped")
            return
        }
        route(message.copy(from = binding.sub))
    }

    fun route(message: Message) {
        when (message.to) {
            "ALL" -> broadcast(message)
            else -> {
                val session = getSession(message.to)
                    ?: throw RuntimeException("ws: [chat] unknown session: ${message.to}")
                send(message, session)
            }
        }
    }

    fun getSession(sessionId: String): WebSocketSession? {
        return messageChannels.keys.firstOrNull { it.id == sessionId }
    }

    fun getMessageFlow(session: WebSocketSession): Flow<Message> {
        return messageChannels[session]?.receiveAsFlow() ?: emptyFlow()
    }

    fun updateLastPong(session: WebSocketSession) {
        // computeIfPresent, not put: a pong racing in AFTER unregister must not
        // resurrect the entry — a ghost entry is never cleaned by the stale sweep
        // (binding is gone) and would leak one map entry per raced pong.
        lastPongTimes.computeIfPresent(session) { _, _ -> Instant.now() }
    }

    /**
     * Revocation by user (Keycloak LOGOUT events via KafkaKeycloakController).
     * Note: the registry and index are PER-INSTANCE. In a multi-instance
     * deployment the keycloak-events consumer must run with a per-instance
     * consumer group so every instance receives every event; a shared group
     * load-balances each event to one — possibly wrong — instance. # VERIFY per deployment.
     */
    fun revokeUser(userId: String): Int {
        val sessions = userIndex[userId]?.toList() ?: return 0
        log.info("[ws] revoking ${sessions.size} session(s) for user: $userId")
        sessions.forEach { session ->
            closeWithControl(session, WsControl.LOGGED_OUT, WsControl.LOGOUT_CLOSE_STATUS)
        }
        return sessions.size
    }

    /** Sessions currently bound to [userId] (reverse index view, for tests/ops). */
    fun sessionsFor(userId: String): Set<WebSocketSession> =
        userIndex[userId]?.toSet() ?: emptySet()

    fun bindingOf(session: WebSocketSession): SessionBinding? = bindings[session]

    /** Send the control frame through the session's channel, then close with [status]. */
    private fun closeWithControl(session: WebSocketSession, control: String, status: CloseStatus) {
        val channel = messageChannels[session] ?: return
        channel.trySend(Message("SYSTEM", session.id, control))
        serviceScope.launch {
            // small grace so the outgoing coroutine flushes the control frame
            delay(WsControl.CONTROL_CLOSE_GRACE_MS.milliseconds)
            // close FIRST (with the contract status), unregister after: unregistering
            // closes the channel, which would let the handler's normal cleanup race
            // a plain 1000 close onto the wire before this status code.
            try {
                session.close(status).awaitSingleOrNull()
            } catch (e: Exception) {
                log.debug("ws: [chat] error closing session: ${session.id}", e)
            }
            unregisterSession(session)
        }
    }

    internal fun sweep() {
        val now = Instant.now()

        // Hard token ceiling: past `exp` -> REAUTH_REQUIRED + 4401.
        // The client contract is: refresh, mint a fresh ticket, reconnect.
        bindings.entries
            .filter { (_, binding) -> binding.exp.isBefore(now) }
            .forEach { (session, binding) ->
                log.info("ws: [chat] token expired for session: ${session.id} (user: ${binding.sub}), sending REAUTH_REQUIRED")
                closeWithControl(session, WsControl.REAUTH_REQUIRED, WsControl.REAUTH_CLOSE_STATUS)
            }

        // Heartbeat liveness (orthogonal to auth expiry): stale -> drop as before.
        val staleThreshold = now.minusSeconds(config.staleCheckThresholdSeconds.toLong())
        lastPongTimes.forEach { (session, lastPong) ->
            if (bindings.containsKey(session) && lastPong.isBefore(staleThreshold)) {
                log.warn("ws: [chat] removing stale connection: ${session.id} (last pong: $lastPong)")
                unregisterSession(session)
            }
        }
    }

    /** Cancel service coroutines and close live sessions cleanly on shutdown. */
    @PreDestroy
    fun shutdown() {
        // skill requirement: on graceful shutdown, clients get a clean close
        // (1001 GOING_AWAY) instead of a connection reset — important behind
        // rolling deploys where browsers would otherwise storm-reconnect.
        val sessions = messageChannels.keys.toList()
        if (sessions.isNotEmpty()) log.info("[ws] shutdown: closing ${sessions.size} live session(s)")
        sessions.forEach { session ->
            runCatching { session.close(CloseStatus(1001, "SERVER_SHUTDOWN")).subscribe() }
        }
        serviceScope.coroutineContext.cancel()
    }

    internal fun lastPongEntryCount(): Int = lastPongTimes.size
}
