package com.softeno.template.app.event

import com.softeno.template.app.kafka.dto.KafkaMessage
import com.softeno.template.sample.kafka.ReactiveKafkaSampleProducer
import com.softeno.template.sample.websocket.Message
import com.softeno.template.sample.websocket.ReactiveMessageService
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

data class AppEvent(val source: String) : ApplicationEvent(source)

@Component
class SampleApplicationEventPublisher(
    private val reactiveMessageService: ReactiveMessageService,
    private val reactiveKafkaProducer: ReactiveKafkaSampleProducer
) : ApplicationListener<AppEvent> {
    private val log = LogFactory.getLog(javaClass)

    override fun onApplicationEvent(event: AppEvent) {
        log.info("[event handler]: Received event: $event")
        reactiveMessageService.broadcast(event.toMessage())
        reactiveKafkaProducer.send(event.toKafkaMessage())
    }

}

fun AppEvent.toMessage() = Message(from = "SYSTEM", to = "ALL", content = this.source)
fun AppEvent.toKafkaMessage() = KafkaMessage(content = this.source)
