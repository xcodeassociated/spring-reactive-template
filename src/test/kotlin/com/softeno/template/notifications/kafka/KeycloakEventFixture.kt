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
}

private val eventJsonMapper = com.fasterxml.jackson.databind.ObjectMapper()
