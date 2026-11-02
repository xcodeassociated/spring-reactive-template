package com.softeno.template.notifications.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.softeno.template.testsupport.R
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
 * Keycloak admin event delivered as a Kafka `ConsumerRecord` — the payload
 * consumed by `KafkaKeycloakController`. Shared by the kafka listener unit test
 * and the WebSocket logout-revocation IT.
 */
interface KeycloakEventFixture {

    fun keycloakEventJson(type: String, userId: String, id: String = R.uuid()): String =
        """{"id":"$id","time":${System.currentTimeMillis() / 1000},"type":"$type",""" +
            """"realmId":"app","clientId":"frontend","userId":"$userId",""" +
            """"sessionId":"sid-${R.word()}","ipAddress":"127.0.0.1","error":null,"details":{}}"""

    fun keycloakEvent(type: String, userId: String, id: String = R.uuid()): ConsumerRecord<String, JsonNode> =
        ConsumerRecord("keycloak-events", 0, 0, null, eventJsonMapper.readTree(keycloakEventJson(type, userId, id)))

    /**
     * Admin-event payload as delivered on the admin topic: the wire shape uses a
     * slash-joined `resourcePath` string (e.g. `users/{id}/logout`) plus
     * authDetails/representation/error fields the listener must tolerate.
     */
    fun keycloakAdminEvent(resourceType: String, operationType: String, resourcePath: String?, id: String = R.uuid()): ConsumerRecord<String, JsonNode> =
        ConsumerRecord(
            "keycloak-admin-events", 0, 0, null,
            eventJsonMapper.readTree(
                """{"id":"$id","time":${System.currentTimeMillis()},"realmId":"app","realmName":"app",""" +
                    """"authDetails":{"realmId":"master","userId":"${R.uuid()}"},""" +
                    """"resourceType":"$resourceType","operationType":"$operationType",""" +
                    """"resourcePath":${resourcePath?.let { "\"$it\"" } ?: "null"},"representation":null,"error":null,""" +
                    """"resourceTypeAsString":"$resourceType"}"""
            )
        )
}

private val eventJsonMapper = com.fasterxml.jackson.databind.ObjectMapper()
