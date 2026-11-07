package com.softeno.template.users

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.softeno.template.config.kafka.KafkaApplicationProperties
import org.apache.commons.logging.LogFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

/**
 * Integration event published by the users core — the END of the core write
 * path (ADR-0003, ADR-0006). `notifications` consumes it on the other side of
 * Kafka; the core never knows which transports exist. The app user id is a
 * Mongo id — a dummy-implementation identity, never a Keycloak `sub`.
 */
/**
 * User lifecycle actions on the wire. `SYNCED` is the ONE completion event for
 * a permission fan-out (ADR-0005): the sync rewrites many users, but it is one
 * logical change — per-user events would spam every client into refetch loops
 * that can observe a half-synced state. `permissionId` carries the cause.
 */
enum class UserAction { CREATED, UPDATED, DELETED, SYNCED }

/**
 * One wire type for the whole user lifecycle. The `action` default keeps
 * pre-ADR-0006 payloads (`{"userId": …}`) on the dev topic readable as CREATED:
 * the field is nullable precisely so a missing enum value cannot NPE the
 * constructor (Jackson does not apply Kotlin parameter defaults without the
 * Kotlin module), and [effectiveAction] normalizes it. `userId` is null for
 * SYNCED, which instead names the triggering permission.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UserEvent(
    val userId: String? = null,
    val action: UserAction? = UserAction.CREATED,
    val permissionId: String? = null,
) {
    val effectiveAction: UserAction
        get() = action ?: UserAction.CREATED
}

@Component
class UserEventsPublisher(
    private val producer: KafkaTemplate<String, UserEvent>,
    private val props: KafkaApplicationProperties,
) {
    private val log = LogFactory.getLog(javaClass)

    fun publish(userId: String, action: UserAction) {
        val event = UserEvent(userId, action)
        log.info("[kafka] tx (${props.tx}): $event")
        producer.send(props.tx, event)
    }

    /** Single completion signal after a permission fan-out rewrote all holders. */
    fun publishSynced(permissionId: String) {
        val event = UserEvent(action = UserAction.SYNCED, permissionId = permissionId)
        log.info("[kafka] tx (${props.tx}): $event")
        producer.send(props.tx, event)
    }
}
