package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.softeno.template.notifications.Notification
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import com.fasterxml.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

/**
 * Keycloak ADMIN events via Kafka — events that fire NO user-facing `LOGOUT`
 * event and would otherwise silently fall back to the token-exp ceiling, like
 * an admin terminating a user's sessions ("Sign out of all sessions" →
 * `ACTION` on `users/{id}/logout`) or user deletion (`DELETE` on `users/{id}`).
 *
 * The listener itself stays generic: every event is interpreted into an
 * [AdminDirective] and executed. Reacting to a new kind of admin event means
 * adding a directive + branch — never a new matching rule inside the wire DTO.
 * Revocation goes through the same [Notification] port as the user-event
 * listener, so every transport is revoked identically (ADR-0004).
 *
 * NOTE multi-instance: same caveat as [KafkaKeycloakController] — give this
 * listener a per-instance group id per deployment. # VERIFY per deployment.
 */
@Controller
class KafkaKeycloakAdminController(
    private val objectMapper: ObjectMapper,
    private val ports: List<Notification>,
) {
    private val log = LogFactory.getLog(javaClass)

    @KafkaListener(
        id = "\${spring.kafka.consumer.group-id}-keycloak-admin",
        topics = ["\${com.softeno.kafka.keycloak-admin}"],
    )
    suspend fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.debug("[kafka] rx keycloak admin raw: ${record.value()}")
        val event = objectMapper.readValue(record.value().toString(), KeycloakAdminEvent::class.java)
        log.info("[kafka] rx keycloak admin mapped: $event")

        when (val directive = AdminDirective.of(event)) {
            is AdminDirective.RevokeUser -> {
                val closed = ports.sumOf { it.revokeUser(directive.userId) }
                log.info("[kafka] admin ${event.operationType} on ${event.resourcePath}: revoked $closed connection(s) for user: ${directive.userId}")
            }
            AdminDirective.Ignore ->
                log.debug("[kafka] admin ${event.operationType} on ${event.resourcePath}: nothing to do")
        }
    }
}

/**
 * What a [KeycloakAdminEvent] asks this service to do. This interpretation is
 * the single place that reads meaning into the raw wire shape: a new admin
 * event reaction is a new [of] case + controller branch, keeping the DTO a
 * dumb wire shape (mirrors the `List<Notification>` fan-out style, ADR-0004).
 */
sealed interface AdminDirective {
    /**
     * Terminate everything live bound to [userId] — the revocations no
     * user-facing `LOGOUT` covers (see [KafkaKeycloakAdminController]).
     */
    data class RevokeUser(val userId: String) : AdminDirective

    /** Everything the service has no reaction to: CREATE/UPDATE, realm-wide
     * "invalidate all sessions" (left to the token-exp ceiling sweepers),
     * other resource types, unrecognized shapes. */
    data object Ignore : AdminDirective

    companion object {
        fun of(event: KeycloakAdminEvent): AdminDirective {
            val parts = event.resourcePath?.split("/") ?: return Ignore
            if (event.resourceType != "USER" || parts.firstOrNull() != "users" || parts.size !in 2..3) return Ignore
            val userId = parts[1].takeIf { it.isNotBlank() } ?: return Ignore
            val action = parts.getOrNull(2)
            return when {
                // `DELETE users/<id>` removes the user; `ACTION users/<id>/logout`
                // is "Sign out of all sessions". Other actions (impersonate, …)
                // leave the user's access alone.
                event.operationType == "DELETE" && action == null -> RevokeUser(userId)
                event.operationType == "ACTION" && action == "logout" -> RevokeUser(userId)
                else -> Ignore
            }
        }
    }
}

/** Raw Keycloak admin-event wire shape — structure only, no interpretation (see [AdminDirective]). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KeycloakAdminEvent(
    val time: Long,
    val realmId: String,
    val resourceType: String?,
    val operationType: String?,
    /** Slash-joined admin endpoint path, e.g. `users/<id>/logout` or `users/<id>`. */
    val resourcePath: String?,
)
