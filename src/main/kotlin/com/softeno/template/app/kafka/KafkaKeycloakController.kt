package com.softeno.template.app.kafka

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.app.user.notification.SseSubscriptionRegistry
import com.softeno.template.sample.websocket.WebSocketNotificationSender
import org.apache.commons.logging.LogFactory
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller
import tools.jackson.databind.ObjectMapper

@Controller
class KafkaKeycloakController(
    private val objectMapper: ObjectMapper,
    private val ws: WebSocketNotificationSender,
    private val sse: SseSubscriptionRegistry,
) {
    private val log = LogFactory.getLog(javaClass)

    // NOTE multi-instance: this listener uses the shared consumer group
    // `${spring.kafka.consumer.group-id}-keycloak`, so each event is delivered to
    // ONE instance only — and the WebSocket session registry is per-instance.
    // On a multi-instance deployment give this listener a PER-INSTANCE group id
    // (e.g. suffix with the pod name) so every instance receives every LOGOUT
    // event and can revoke its own sockets. # VERIFY per deployment.
    @KafkaListener(id = "\${spring.kafka.consumer.group-id}-keycloak", topics = ["\${com.softeno.kafka.keycloak}"])
    suspend fun listen(record: ConsumerRecord<String, JsonNode>) {
        log.debug("[kafka] rx keycloak raw: ${record.key()}: ${record.value()}")
        val dto: KeycloakUserEvent = objectMapper.readValue(record.value().toString(), KeycloakUserEvent::class.java)
        log.info("[kafka] rx keycloak mapped: $dto")

        // Logout propagation: close the user's live WebSocket sessions (LOGGED_OUT
        // frame + 4403) and SSE streams (logged_out event + completion) via the
        // reverse indexes. LOGIN/REGISTER are informational here.
        // Note: Keycloak logout does NOT revoke outstanding access tokens — the
        // token `exp` ceiling (sweeper) remains the backstop. Keep access tokens short.
        when (dto.type) {
            KeycloakEventType.LOGOUT -> {
                val closedWs = ws.revokeUser(dto.userId)
                val closedSse = sse.revokeUser(dto.userId)
                log.info("[kafka] logout: revoked $closedWs ws session(s) + $closedSse sse subscription(s) for user: ${dto.userId}")
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
    val details: Map<String, String>
)
