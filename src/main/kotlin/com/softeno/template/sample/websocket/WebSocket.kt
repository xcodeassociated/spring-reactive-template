package com.softeno.template.sample.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.softeno.template.app.event.SampleApplicationEventPublisher
import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.ReactiveSecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Service
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.publisher.Sinks.Many


@Configuration
class WebSocketConfig(private val reactiveMessageService: ReactiveMessageService) {
    private val log = LogFactory.getLog(javaClass)

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

    @Bean
    fun handlerMapping(
        applicationEventPublisher: SampleApplicationEventPublisher,
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
            val authentication = ReactiveSecurityContextHolder.getContext().map { it.authentication }
            val sessionId = session.id
            log.info("ws: [chat] new session: $sessionId")

            // note: example of send out message right after connect as fire&forget
            val handshake = Message(from = "SYSTEM", to = sessionId, content = "HANDSHAKE")
            reactiveMessageService.send(handshake, handshake.to)

            // get user id from oauth2 token
            val userIdMessage: Flux<String> = authentication.flux().map {
                val token = (it as JwtAuthenticationToken).token
                val userId = token.claims["sub"]
                Message(from = "SYSTEM", to = sessionId, content = "$userId").toJson(objectMapper)
            }

            // todo: read messages from database as Flux
            val welcomeMessages: Flux<String> =
                Flux.just(Message(from = "SYSTEM", to = sessionId, content = "HELLO").toJson(objectMapper))

            val messages: Flux<WebSocketMessage> =
                Flux.concat(userIdMessage, welcomeMessages, reactiveMessageService.getMessages(sessionId))
                    .map {
                        log.info("ws: [chat] tx: $it")
                        it
                    }
                    .map(session::textMessage)

            val reading = session.receive()
                .doFinally { sig ->
                    log.info("ws: [chat] disconnect chat session: $sessionId with sig: ${sig.name}")
                    session.close()
                    reactiveMessageService.remove(sessionId)
                }
                .doOnNext { wsMessage ->
                    val message = objectMapper.readValue(wsMessage.payloadAsText, Message::class.java)
                    log.info("ws: [chat] rx: $message")
                    reactiveMessageService.send(message, message.to)
                }

            session.send(messages).and(reading)
        }
    }
}

data class Message(val from: String, val to: String, val content: String)

fun Message.toJson(objectMapper: ObjectMapper): String = objectMapper.writeValueAsString(this)

@Service
class ReactiveMessageService(
    private val objectMapper: ObjectMapper
) {
    private val sinks: MutableMap<String, Many<String>> = mutableMapOf()

    fun send(next: Message, session: String): Message {
        val payload = next.toJson(objectMapper)
        getSink(session).emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST)
        return next
    }

    fun broadcast(message: Message): Message {
        val payload = message.toJson(objectMapper)
        sinks.forEach { (t, _) ->
            getSink(t).emitNext(payload, Sinks.EmitFailureHandler.FAIL_FAST)
        }
        return message
    }

    fun getMessages(session: String): Flux<String> {
        return getSink(session).asFlux()
    }

    private fun getSink(session: String): Many<String> {
        if (!sinks.containsKey(session)) {
            val sink = Sinks.many().multicast().onBackpressureBuffer<String>()
            sinks[session] = sink
        }
        return sinks[session]!!
    }

    fun remove(session: String): Many<String>? = sinks.remove(session)

}