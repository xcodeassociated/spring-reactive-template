package com.softeno.template.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.event.AppEvent
import com.softeno.template.event.SampleApplicationEventPublisher
import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Service
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.Many
import java.util.concurrent.Executor
import java.util.concurrent.Executors


@Configuration
class WebSocketConfig(private val chatMessagingService: ChatMessagingService) {
    private val log = LogFactory.getLog(javaClass)

    @Bean(name = ["websocketNotificationExecutor"])
    fun executor(): Executor = Executors.newSingleThreadExecutor()

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

    @Bean
    fun handlerMapping(applicationEventPublisher: SampleApplicationEventPublisher, objectMapper: ObjectMapper): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.order = 10
        simpleMapping.urlMap = mapOf(
            "/ws/users" to webSocketHandler(applicationEventPublisher),
            "/ws/chat" to webSocketChatHandler(objectMapper)
        )
        return simpleMapping
    }

    @Bean
    fun webSocketHandler(applicationEventPublisher: SampleApplicationEventPublisher): WebSocketHandler {
        return WebSocketHandler { session ->
            val publish: Flux<AppEvent> = Flux.create(applicationEventPublisher).share()
            log.info("ws: [notification] new session: ${session.id}")

            val messageFlux: Flux<WebSocketMessage> = publish
                .map { it.source }
                .doOnNext { log.info("ws: [notification] tx: $it") }
                .map { session.textMessage(it) }
                .doFinally { sig -> log.info("ws: [notification] disconnect chat session: ${session.id} with sig: ${sig.name}") }

            session.send(messageFlux)
        }
    }

    @Bean
    fun webSocketChatHandler(objectMapper: ObjectMapper): WebSocketHandler {
        return WebSocketHandler { session ->
            val sessionId = session.id
            log.info("ws: [chat] new session: $sessionId")

            val handshake = Message(from = sessionId, to = sessionId, content = "")
            chatMessagingService.onNext(handshake, handshake.to)

            val messages: Flux<WebSocketMessage> = chatMessagingService.getMessages(sessionId)
                .map {
                    log.info("ws: [chat] tx: $it")
                    it
                }
                .map(session::textMessage)

            val reading = session.receive()
                .doFinally { sig ->
                    log.info("ws: [chat] disconnect chat session: $sessionId with sig: ${sig.name}")
                    session.close()
                    chatMessagingService.removeSink(sessionId)
                }
                .doOnNext { wsMessage ->
                    val message = objectMapper.readValue(wsMessage.payloadAsText, Message::class.java)
                    log.info("ws: [chat] rx: $message")
                    chatMessagingService.onNext(message, message.to)
                }

            session.send(messages).and(reading)
        }
    }
}

data class Message(val from: String, val to: String, val content: String)

fun Message.toJson(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(this)

@Service
class ChatMessagingService(
    private val objectMapper: ObjectMapper) {
    private val sinks: MutableMap<String, Many<String>> = mutableMapOf()
    fun onNext(next: Message, session: String): Message {
        val payload = next.toJson(objectMapper)
        getSink(session).emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST)
        return next
    }

    fun getMessages(session: String): Flux<String> {
        return getSink(session).asFlux()
    }

    fun getSink(session: String): Many<String> {
        if (!sinks.containsKey(session)) {
            val sink = Sinks.many().multicast().onBackpressureBuffer<String>()
            sinks[session] = sink
        }
        return sinks[session]!!
    }

    fun removeSink(session: String): Many<String>? = sinks.remove(session)

}