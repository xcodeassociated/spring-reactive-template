package com.softeno.template.app.kafka

import com.softeno.template.sample.websocket.WebSocketNotificationSender
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerRecord
import com.fasterxml.jackson.databind.JsonNode
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

class KafkaKeycloakControllerTest {
    private fun record(type: String, userId: String): ConsumerRecord<String, JsonNode> {
        val json = """
            {"id":"e1","time":1,"type":"$type","realmId":"r1","clientId":"c1","userId":"$userId",
             "sessionId":"s1","ipAddress":"127.0.0.1","error":null,"details":{}}
        """.trimIndent()
        val jsonNode = com.fasterxml.jackson.databind.ObjectMapper().readTree(json)
        return ConsumerRecord("keycloak-events", 0, 0, null, jsonNode)
    }

    private fun controllerWith(sender: WebSocketNotificationSender): KafkaKeycloakController {
        val objectMapper = mockk<ObjectMapper>()
        return KafkaKeycloakController(objectMapper, sender)
    }

    private fun stubReadValue(objectMapper: ObjectMapper, type: String, userId: String) {
        every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns
            KeycloakUserEvent(
                id = "e1", time = 1, type = KeycloakEventType.valueOf(type),
                realmId = "r1", clientId = "c1", userId = userId, sessionId = "s1",
                ipAddress = "127.0.0.1", error = null, details = emptyMap(),
            )
    }

    @Test
    fun `LOGOUT event revokes the user's websocket sessions`() {
        val sender = mockk<WebSocketNotificationSender>()
        every { sender.revokeUser("user-1") } returns 2
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, "LOGOUT", "user-1")
        val controller = KafkaKeycloakController(objectMapper, sender)

        runBlocking { controller.listen(record("LOGOUT", "user-1")) }

        verify(exactly = 1) { sender.revokeUser("user-1") }
    }

    @Test
    fun `LOGIN and REGISTER events do not revoke sessions`() {
        val sender = mockk<WebSocketNotificationSender>(relaxed = true)
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, "LOGIN", "user-1")
        val controller = KafkaKeycloakController(objectMapper, sender)

        runBlocking {
            controller.listen(record("LOGIN", "user-1"))
            every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns
                KeycloakUserEvent(
                    id = "e2", time = 2, type = KeycloakEventType.REGISTER,
                    realmId = "r1", clientId = "c1", userId = "user-1", sessionId = "s2",
                    ipAddress = "127.0.0.1", error = null, details = emptyMap(),
                )
            controller.listen(record("REGISTER", "user-1"))
        }

        verify(exactly = 0) { sender.revokeUser(any()) }
    }
}
