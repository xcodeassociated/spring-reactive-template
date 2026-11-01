package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.config.kafka.KafkaApplicationProperties
import com.softeno.template.notifications.Message
import com.softeno.template.notifications.Notification
import com.softeno.template.users.UserCreatedEvent
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

/**
 * The notifications side of the users-core integration event (ADR-0003):
 * consume from the rx topic, render the broadcast text, fan out through every
 * [Notification] adapter. Depends on the port only — transports are pluggable,
 * the source is not.
 */
@Controller
class UserEventsListener(
    private val props: KafkaApplicationProperties,
    private val ports: List<Notification>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(id = "\${spring.kafka.consumer.group-id}", topics = ["\${com.softeno.kafka.rx}"])
    fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.info("[kafka] rx (${props.rx}): ${record.key()}: ${record.value()}")

        val event: UserCreatedEvent = objectMapper.readValue(record.value().toString(), UserCreatedEvent::class.java)
        val message = Message(from = "SYSTEM", to = "ALL", content = "USER_CREATED: ${event.userId}")
        ports.forEach { it.broadcast(message) }
    }
}
