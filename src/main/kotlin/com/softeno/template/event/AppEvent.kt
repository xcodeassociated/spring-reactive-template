package com.softeno.template.event

import com.softeno.template.kafka.KafkaMessage
import com.softeno.template.kafka.ReactiveKafkaProducerService
import com.softeno.template.websocket.Message
import com.softeno.template.websocket.ReactiveMessageService
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

data class AppEvent(val source: String) : ApplicationEvent(source)

@Component
class SampleApplicationEventPublisher(
    private val reactiveMessageService: ReactiveMessageService,
    private val reactiveKafkaProducerService: ReactiveKafkaProducerService
) : ApplicationListener<AppEvent> {
    private val log = LogFactory.getLog(javaClass)

    override fun onApplicationEvent(event: AppEvent) {
        log.info("[event handler]: Received event: $event")
        reactiveMessageService.broadcast(event.toMessage())
        reactiveKafkaProducerService.send(event.toKafkaMessage())
    }

}

fun AppEvent.toMessage() = Message(from = "SYSTEM", to = "ALL", content = this.source)
fun AppEvent.toKafkaMessage() = KafkaMessage(content = this.source)
