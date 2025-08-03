package com.softeno.template.app.event

import com.softeno.template.app.kafka.KafkaMessage
import com.softeno.template.app.kafka.ReactiveKafkaSampleProducer
import kotlinx.coroutines.DelicateCoroutinesApi
import org.apache.commons.logging.LogFactory
import org.slf4j.MDC
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

data class UserAction(val source: String, val traceId: String? = null, val spanId: String? = null) : ApplicationEvent(source)

@Component
class SampleApplicationEventPublisher(

    private val reactiveKafkaProducer: ReactiveKafkaSampleProducer,
) : ApplicationListener<UserAction> {
    private val log = LogFactory.getLog(javaClass)

    @OptIn(DelicateCoroutinesApi::class)
    override fun onApplicationEvent(event: UserAction) {
        // note: propagate traceId and spanId in MDC context
        if (!event.spanId.isNullOrBlank() && !event.traceId.isNullOrBlank()) {
            MDC.put("traceId", event.traceId)
            MDC.put("spanId", event.spanId)
        }
        log.debug("[app event handler]: Received event: $event")
        reactiveKafkaProducer.send(event.toKafkaMessage())
    }

}

fun UserAction.toKafkaMessage() = KafkaMessage(content = this.source, traceId = this.traceId, spanId = this.spanId)
