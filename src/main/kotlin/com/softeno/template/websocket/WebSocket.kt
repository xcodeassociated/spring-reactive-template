package com.softeno.template.websocket

import com.softeno.template.event.AppEvent
import com.softeno.template.event.SampleApplicationEventPublisher
import org.apache.commons.logging.LogFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Flux
import java.util.concurrent.Executors
import java.util.concurrent.Executor


@Configuration
class WebSocketConfig {
    private val log = LogFactory.getLog(javaClass)

    @Bean(name = ["websocketExecutor"])
    fun executor(): Executor = Executors.newSingleThreadExecutor()

    @Bean
    fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()

    @Bean
    fun handlerMapping(applicationEventPublisher: SampleApplicationEventPublisher): HandlerMapping {
        val simpleMapping = SimpleUrlHandlerMapping()
        simpleMapping.order = 10
        simpleMapping.urlMap = mapOf("/ws/users" to webSocketHandler(applicationEventPublisher))
        return simpleMapping
    }

    @Bean
    fun webSocketHandler(applicationEventPublisher: SampleApplicationEventPublisher): WebSocketHandler {
        val publish: Flux<AppEvent> = Flux.create(applicationEventPublisher).share()
        return WebSocketHandler { session ->
            val messageFlux: Flux<WebSocketMessage> = publish
                .map { it.source }
                .doOnNext { log.info("websocket tx: $it") }
                .map { session.textMessage(it) }

            session.send(messageFlux)
        }
    }
}