package com.softeno.template.app.event

import com.softeno.template.app.kafka.KafkaMessage
import com.softeno.template.app.kafka.KafkaSampleProducer
import kotlinx.coroutines.DelicateCoroutinesApi
import org.apache.commons.logging.LogFactory
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

data class UserAction(val source: String) : ApplicationEvent(source)

@Component
class SampleApplicationEventPublisher(

    private val kafkaProducer: KafkaSampleProducer,
) : ApplicationListener<UserAction> {
    private val log = LogFactory.getLog(javaClass)

    @OptIn(DelicateCoroutinesApi::class)
    override fun onApplicationEvent(event: UserAction) {
        log.debug("[app event handler]: Received event: $event")

        kafkaProducer.send(event.toKafkaMessage())
    }

}

fun UserAction.toKafkaMessage() = KafkaMessage(content = this.source)
