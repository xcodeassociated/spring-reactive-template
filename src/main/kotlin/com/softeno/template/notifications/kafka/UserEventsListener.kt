package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.config.kafka.KafkaApplicationProperties
import com.softeno.template.notifications.Message
import com.softeno.template.notifications.Notification
import com.softeno.template.users.UserAction
import com.softeno.template.users.UserEvent
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

/**
 * The notifications side of the users-core integration events (ADR-0003, ADR-0006):
 * consume from the rx topic, render the broadcast text per action, fan out through
 * every [Notification] adapter. Depends on the port only — transports are pluggable,
 * the source is not. The app user id is a Mongo id (dummy implementation), so these
 * events are broadcasts only — session revocation stays with the Keycloak listeners.
 */
@Controller
class UserEventsListener(
    private val props: KafkaApplicationProperties,
    private val ports: List<Notification>,
    private val objectMapper: ObjectMapper,
) {
    private val log = LogFactory.getLog(javaClass)

    // Fan-out group (ADR-0008): the per-instance suffix means EVERY instance
    // consumes EVERY event and revokes/broadcasts on its own connections —
    // a shared group would deliver each event to one pod only, leaving the
    // other pods' clients silent. Side effects here must stay local-only.
    @KafkaListener(id = "\${spring.kafka.consumer.group-id}-\${com.softeno.kafka.instance-id:standalone}", topics = ["\${com.softeno.kafka.rx}"])
    fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.info("[kafka] rx (${props.rx}): ${record.key()}: ${record.value()}")

        val event: UserEvent = objectMapper.readValue(record.value().toString(), UserEvent::class.java)
        // SYNCED is the fan-out completion signal: it names the permission,
        // not a user — clients invalidate their user caches once, not per holder
        val content =
            if (event.effectiveAction == UserAction.SYNCED) "USERS_SYNCED: ${event.permissionId}"
            else "USER_${event.effectiveAction.name}: ${event.userId}"
        val message = Message(from = "SYSTEM", to = "ALL", content = content)
        ports.forEach { it.broadcast(message) }
    }
}
