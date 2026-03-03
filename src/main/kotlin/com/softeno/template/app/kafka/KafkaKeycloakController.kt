package com.softeno.template.app.kafka

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

@Controller
class KafkaKeycloakController(
    private val objectMapper: ObjectMapper
) {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(id = "\${spring.kafka.consumer.group-id}-keycloak", topics = ["\${com.softeno.kafka.keycloak}"])
    suspend fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.debug("[kafka] rx keycloak raw: ${record.key()}: ${record.value()}")
        val dto: KeycloakUserEvent = objectMapper.readValue(record.value().toString(), KeycloakUserEvent::class.java)
        log.info("[kafka] rx keycloak mapped: $dto")
    }
}

enum class KeycloakEventType {
    LOGIN,
    LOGOUT,
    REGISTER;

    companion object {
        @JsonCreator
        fun byString(input: String): KeycloakEventType? {
            return KeycloakEventType.entries.firstOrNull { it.name.equals(input, true) }
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class KeycloakUserEvent(
    val id: String,
    val time: Long,
    val type: KeycloakEventType,
    val realmId: String,
    val clientId: String?,
    val userId: String,
    val sessionId: String?,
    val ipAddress: String,
    val error: String?,
    val details: Map<String, String>
)
