package com.softeno.template.users

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.softeno.template.config.kafka.KafkaApplicationProperties
import org.apache.commons.logging.LogFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Integration event published by the users core — the END of the core write
 * path (ADR-0003). `notifications` consumes it on the other side of Kafka;
 * the core never knows which transports exist.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserCreatedEvent(val userId: String)

@Component
class UserEventsPublisher(
    private val producer: KafkaTemplate<String, UserCreatedEvent>,
    private val props: KafkaApplicationProperties,
) {
    private val log = LogFactory.getLog(javaClass)

    fun publishUserCreated(userId: String) {
        val event = UserCreatedEvent(userId)
        log.info("[kafka] tx (${props.tx}): $event")
        producer.send(props.tx, event)
    }
}
