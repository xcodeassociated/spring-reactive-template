package com.softeno.template.app.kafka

import com.softeno.template.sample.websocket.WebSocketNotificationSender
import com.softeno.template.testsupport.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper

/**
 * Unit tests for the Keycloak-events listener: LOGOUT revokes the user's
 * WebSocket sessions, LOGIN/REGISTER do not. Event payloads come from
 * [KeycloakEventFixture] with [R]-randomized subjects and ids.
 */
class KafkaKeycloakControllerTest : KeycloakEventFixture {

    private fun stubReadValue(objectMapper: ObjectMapper, type: String, userId: String, id: String) {
        every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns
            KeycloakUserEvent(
                id = id, time = 1, type = KeycloakEventType.valueOf(type),
                realmId = "app", clientId = "frontend", userId = userId, sessionId = "s1",
                ipAddress = "127.0.0.1", error = null, details = emptyMap(),
            )
    }

    @Test
    fun `LOGOUT event revokes the user's websocket sessions`() {
        val userId = R.sub("logout-target")
        val eventId = R.uuid()
        val sender = mockk<WebSocketNotificationSender>()
        every { sender.revokeUser(userId) } returns 2
        val objectMapper = mockk<ObjectMapper>()
        stubReadValue(objectMapper, "LOGOUT", userId, eventId)
        val controller = KafkaKeycloakController(objectMapper, sender)

        runBlocking { controller.listen(keycloakEvent("LOGOUT", userId, eventId)) }

        verify(exactly = 1) { sender.revokeUser(userId) }
    }

    @Test
    fun `LOGIN and REGISTER events do not revoke sessions`() {
        val userId = R.sub("quiet-user")
        val sender = mockk<WebSocketNotificationSender>(relaxed = true)
        val objectMapper = mockk<ObjectMapper>()
        val loginId = R.uuid()
        stubReadValue(objectMapper, "LOGIN", userId, loginId)
        val controller = KafkaKeycloakController(objectMapper, sender)

        runBlocking {
            controller.listen(keycloakEvent("LOGIN", userId, loginId))
            val registerEvent = KeycloakUserEvent(
                id = R.uuid(), time = 2, type = KeycloakEventType.REGISTER,
                realmId = "app", clientId = "frontend", userId = userId, sessionId = "s2",
                ipAddress = "127.0.0.1", error = null, details = emptyMap(),
            )
            every { objectMapper.readValue(any<String>(), KeycloakUserEvent::class.java) } returns registerEvent
            controller.listen(keycloakEvent("REGISTER", userId, registerEvent.id))
        }

        verify(exactly = 0) { sender.revokeUser(any()) }
    }
}
