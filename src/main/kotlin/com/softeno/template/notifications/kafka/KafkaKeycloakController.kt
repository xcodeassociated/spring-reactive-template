package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.notifications.Notification
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

/**
 * Keycloak auth events (LOGIN/LOGOUT/REGISTER) via Kafka. LOGOUT revokes the
 * user across every transport through the [Notification] port — this listener
 * knows no transport at all (ADR-0004).
 */
@Controller
class KafkaKeycloakController(
    private val objectMapper: ObjectMapper,
    private val ports: List<Notification>,
) {
    private val log = LogFactory.getLog(javaClass)

    // Fan-out group (ADR-0008): per-instance suffix so EVERY instance
    // receives every LOGOUT and revokes its OWN connections — a shared group
    // would deliver each event to one pod only, leaving the other pods'
    // streams alive after a kill-session. Side effects here must stay
    // local-only (see AdminDirective).
    @KafkaListener(id = "\${spring.kafka.consumer.group-id}-keycloak-\${com.softeno.kafka.instance-id:standalone}", topics = ["\${com.softeno.kafka.keycloak}"])
    suspend fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.debug("[kafka] rx keycloak raw: ${record.key()}: ${record.value()}")
        val dto: KeycloakUserEvent = objectMapper.readValue(record.value().toString(), KeycloakUserEvent::class.java)
        log.info("[kafka] rx keycloak mapped: $dto")

        // Logout propagation: close the user's live connections via each adapter's
        // reverse index. LOGIN/REGISTER are informational here.
        // Note: Keycloak logout does NOT revoke outstanding access tokens — the
        // token `exp` ceiling (sweepers) remains the backstop. Keep access tokens short.
        when (dto.type) {
            KeycloakEventType.LOGOUT -> {
                val closed = ports.sumOf { it.revokeUser(dto.userId) }
                log.info("[kafka] logout: revoked $closed connection(s) across ${ports.size} adapter(s) for user: ${dto.userId}")
            }
            else -> Unit
        }
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
    // real Keycloak events carry "details": null (e.g. LOGOUT from the account
    // client) — a non-null Map here deserializes to a Kotlin NPE and the
    // listener retry-burns the record
    val details: Map<String, String>?
)
